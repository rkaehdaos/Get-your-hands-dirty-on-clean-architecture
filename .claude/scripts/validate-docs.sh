#!/bin/bash

# 문서 검증 스크립트
# BuckPal 프로젝트의 문서와 실제 코드 일치성, 링크 유효성, 정보 최신성을 검증

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CLAUDE_DIR="$PROJECT_ROOT/.claude"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

echo "🔍 BuckPal 문서 검증 시작..."
echo "📁 프로젝트 루트: $PROJECT_ROOT"
echo "⏰ 검증 시작 시간: $TIMESTAMP"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

print_section() {
    echo -e "\n${BLUE}═══ $1 ═══${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${PURPLE}ℹ️  $1${NC}"
}

# 검증 결과 저장
VALIDATION_RESULTS="$CLAUDE_DIR/.validation-results"
ERROR_COUNT=0
WARNING_COUNT=0
SUCCESS_COUNT=0

log_result() {
    local type="$1"
    local message="$2"
    local file="$3"
    
    echo "[$TIMESTAMP] $type: $message ${file:+($file)}" >> "$VALIDATION_RESULTS"
    
    case "$type" in
        "ERROR") ((ERROR_COUNT++)) ;;
        "WARNING") ((WARNING_COUNT++)) ;;
        "SUCCESS") ((SUCCESS_COUNT++)) ;;
    esac
}

# 1. 문서와 실제 코드 일치 검증
print_section "코드-문서 일치성 검증"

validate_code_documentation_sync() {
    local sync_errors=0
    
    print_info "API 엔드포인트 검증 중..."
    
    # API 문서에 명시된 엔드포인트가 실제 코드에 존재하는지 확인
    if [ -f "$CLAUDE_DIR/api/endpoints.md" ]; then
        # @RequestMapping, @PostMapping 등의 패턴 추출
        grep -o '@[A-Za-z]*Mapping[^)]*' "$CLAUDE_DIR/api/endpoints.md" 2>/dev/null | while read -r endpoint; do
            # 실제 코드에서 해당 엔드포인트 검색
            if ! find "$PROJECT_ROOT/src" -name "*.java" -o -name "*.kt" | xargs grep -l "$endpoint" > /dev/null 2>&1; then
                print_warning "API 문서에 기록된 엔드포인트가 코드에서 찾을 수 없음: $endpoint"
                log_result "WARNING" "Missing endpoint in code" "$endpoint"
                ((sync_errors++))
            fi
        done
    fi
    
    print_info "클래스 참조 검증 중..."
    
    # 문서에서 언급된 클래스들이 실제로 존재하는지 확인
    find "$CLAUDE_DIR" -name "*.md" -exec grep -H -o '[A-Z][a-zA-Z]*\(\.java\|\.kt\):[0-9]*' {} \; 2>/dev/null | while IFS=: read -r doc_file class_ref line_info; do
        class_file=$(echo "$class_ref" | sed 's/:[0-9]*$//')
        
        if [ ! -f "$PROJECT_ROOT/src/main/java/dev/haja/buckpal/$class_file" ] && 
           [ ! -f "$PROJECT_ROOT/src/main/kotlin/dev/haja/buckpal/$class_file" ] &&
           [ ! -f "$PROJECT_ROOT/src/test/java/dev/haja/buckpal/$class_file" ] &&
           [ ! -f "$PROJECT_ROOT/src/test/kotlin/dev/haja/buckpal/$class_file" ]; then
            print_warning "문서에서 참조한 클래스 파일이 존재하지 않음: $class_file"
            log_result "WARNING" "Missing class file referenced in documentation" "$class_file (referenced in $doc_file)"
            ((sync_errors++))
        fi
    done
    
    if [ $sync_errors -eq 0 ]; then
        print_success "코드-문서 일치성 검증 통과"
        log_result "SUCCESS" "Code-documentation sync validation passed" ""
    else
        print_error "$sync_errors개의 코드-문서 불일치 발견"
        log_result "ERROR" "$sync_errors code-documentation mismatches found" ""
    fi
}

validate_code_documentation_sync

# 2. 깨진 링크 확인
print_section "링크 유효성 검증"

validate_internal_links() {
    local broken_links=0
    
    print_info "내부 링크 검증 중..."
    
    # 모든 마크다운 파일에서 내부 링크 확인
    find "$CLAUDE_DIR" -name "*.md" | while read -r md_file; do
        # 상대 경로 링크 추출 ([텍스트](경로.md) 형태)
        grep -o '\[([^]]*)\]([^)]*\.md[^)]*' "$md_file" 2>/dev/null | while read -r link; do
            # 링크 경로 추출
            link_path=$(echo "$link" | sed 's/.*](\([^)]*\)).*/\1/')
            
            # 절대 경로로 변환
            if [[ "$link_path" =~ ^/ ]]; then
                # 절대 경로인 경우
                target_file="$CLAUDE_DIR$link_path"
            else
                # 상대 경로인 경우
                base_dir=$(dirname "$md_file")
                target_file="$base_dir/$link_path"
            fi
            
            # 파일 존재 여부 확인
            if [ ! -f "$target_file" ]; then
                print_warning "깨진 내부 링크 발견: $link_path (in $(basename "$md_file"))"
                log_result "WARNING" "Broken internal link" "$link_path in $md_file"
                ((broken_links++))
            fi
        done
    done
    
    print_info "코드 파일 참조 검증 중..."
    
    # 코드 파일 참조 확인 (src/main/java/... 형태)
    find "$CLAUDE_DIR" -name "*.md" -exec grep -H -o 'src/[^)]*\.\(java\|kt\)' {} \; 2>/dev/null | while IFS=: read -r md_file file_path; do
        if [ ! -f "$PROJECT_ROOT/$file_path" ]; then
            print_warning "참조된 코드 파일이 존재하지 않음: $file_path (in $(basename "$md_file"))"
            log_result "WARNING" "Missing referenced code file" "$file_path in $md_file"
            ((broken_links++))
        fi
    done
    
    if [ $broken_links -eq 0 ]; then
        print_success "모든 링크가 유효함"
        log_result "SUCCESS" "All links are valid" ""
    else
        print_error "$broken_links개의 깨진 링크 발견"
        log_result "ERROR" "$broken_links broken links found" ""
    fi
}

validate_internal_links

# 3. 오래된 정보 플래깅
print_section "정보 최신성 검증"

check_outdated_information() {
    local outdated_count=0
    local current_date=$(date +%s)
    local one_month_ago=$((current_date - 2592000)) # 30일 전
    
    print_info "자동 생성 문서의 최신성 확인 중..."
    
    # 자동 생성된 문서들의 마지막 업데이트 시간 확인
    if [ -f "$CLAUDE_DIR/.meta" ]; then
        last_update=$(grep '"last_update"' "$CLAUDE_DIR/.meta" | sed 's/.*": "\([^"]*\)".*/\1/')
        if [ -n "$last_update" ]; then
            # 날짜 형식 변환 (YYYY-MM-DD HH:MM:SS → Unix timestamp)
            last_update_timestamp=$(date -j -f "%Y-%m-%d %H:%M:%S" "$last_update" +%s 2>/dev/null || echo "0")
            
            if [ "$last_update_timestamp" -lt "$one_month_ago" ]; then
                print_warning "자동 생성 문서가 30일 이상 업데이트되지 않음 (마지막 업데이트: $last_update)"
                log_result "WARNING" "Auto-generated docs outdated" "Last update: $last_update"
                ((outdated_count++))
            fi
        fi
    fi
    
    print_info "의존성 버전 확인 중..."
    
    # Gradle 파일과 의존성 문서 비교
    if [ -f "$PROJECT_ROOT/build.gradle.kts" ] && [ -f "$CLAUDE_DIR/dependencies/current.md" ]; then
        # Spring Boot 버전 비교
        gradle_spring_version=$(grep -o 'org.springframework.boot.*[0-9]\+\.[0-9]\+\.[0-9]\+' "$PROJECT_ROOT/build.gradle.kts" | head -1)
        doc_spring_version=$(grep -o 'Spring Boot.*[0-9]\+\.[0-9]\+\.[0-9]\+' "$CLAUDE_DIR/dependencies/current.md" | head -1)
        
        if [ -n "$gradle_spring_version" ] && [ -n "$doc_spring_version" ]; then
            gradle_version=$(echo "$gradle_spring_version" | grep -o '[0-9]\+\.[0-9]\+\.[0-9]\+')
            doc_version=$(echo "$doc_spring_version" | grep -o '[0-9]\+\.[0-9]\+\.[0-9]\+')
            
            if [ "$gradle_version" != "$doc_version" ]; then
                print_warning "Spring Boot 버전 불일치: Gradle($gradle_version) vs 문서($doc_version)"
                log_result "WARNING" "Spring Boot version mismatch" "Gradle: $gradle_version, Docs: $doc_version"
                ((outdated_count++))
            fi
        fi
    fi
    
    print_info "코드 예시의 유효성 확인 중..."
    
    # 문서 내 코드 블록이 실제 코드와 일치하는지 확인 (간단한 체크)
    find "$CLAUDE_DIR" -name "*.md" -exec grep -l '```java\|```kotlin' {} \; | while read -r md_file; do
        # Java/Kotlin 코드 블록에서 클래스명 추출
        awk '/```(java|kotlin)/,/```/' "$md_file" | grep -o 'class [A-Za-z][A-Za-z0-9_]*\|public class [A-Za-z][A-Za-z0-9_]*' | while read -r class_declaration; do
            class_name=$(echo "$class_declaration" | awk '{print $NF}')
            
            # 해당 클래스가 실제로 존재하는지 확인
            if [ -n "$class_name" ] && ! find "$PROJECT_ROOT/src" -name "*$class_name*.java" -o -name "*$class_name*.kt" | head -1 > /dev/null; then
                print_warning "문서의 코드 예시에 있는 클래스를 찾을 수 없음: $class_name (in $(basename "$md_file"))"
                log_result "WARNING" "Code example class not found" "$class_name in $md_file"
                ((outdated_count++))
            fi
        done
    done
    
    if [ $outdated_count -eq 0 ]; then
        print_success "모든 정보가 최신 상태임"
        log_result "SUCCESS" "All information is up to date" ""
    else
        print_warning "$outdated_count개의 오래된 정보 발견"
        log_result "WARNING" "$outdated_count outdated information items found" ""
    fi
}

check_outdated_information

# 4. 누락된 문서 섹션 감지
print_section "문서 완성도 검증"

check_missing_documentation() {
    local missing_count=0
    
    print_info "필수 문서 존재 확인 중..."
    
    # 핵심 문서들이 존재하는지 확인
    essential_docs=(
        "conventions/coding-standards.md"
        "conventions/patterns.md"
        "conventions/testing.md"
        "conventions/security.md"
        "README.md"
    )
    
    for doc in "${essential_docs[@]}"; do
        if [ ! -f "$CLAUDE_DIR/$doc" ]; then
            print_error "필수 문서 누락: $doc"
            log_result "ERROR" "Missing essential document" "$doc"
            ((missing_count++))
        fi
    done
    
    print_info "아키텍처 문서 확인 중..."
    
    # 주요 Java/Kotlin 클래스에 대한 문서화 확인
    critical_classes=(
        "Account"
        "SendMoneyService"
        "SendMoneyCommand"
        "Money"
    )
    
    for class_name in "${critical_classes[@]}"; do
        # 클래스가 문서에서 언급되는지 확인
        if ! find "$CLAUDE_DIR" -name "*.md" -exec grep -l "$class_name" {} \; | head -1 > /dev/null; then
            print_warning "핵심 클래스 $class_name에 대한 문서화 부족"
            log_result "WARNING" "Insufficient documentation for critical class" "$class_name"
            ((missing_count++))
        fi
    done
    
    print_info "TODO 및 FIXME 확인 중..."
    
    # 문서 내 TODO, FIXME 항목 확인
    find "$CLAUDE_DIR" -name "*.md" -exec grep -H -n -i 'todo\|fixme\|xxx\|hack' {} \; | while IFS=: read -r file line content; do
        print_info "미해결 항목 발견: $(basename "$file"):$line - $content"
        log_result "INFO" "Unresolved item found" "$(basename "$file"):$line"
    done
    
    if [ $missing_count -eq 0 ]; then
        print_success "모든 필수 문서가 존재함"
        log_result "SUCCESS" "All essential documentation exists" ""
    else
        print_error "$missing_count개의 누락된 문서 또는 섹션 발견"
        log_result "ERROR" "$missing_count missing documentation items found" ""
    fi
}

check_missing_documentation

# 5. 문서 형식 및 스타일 검증
print_section "문서 형식 검증"

validate_document_format() {
    local format_issues=0
    
    print_info "마크다운 형식 확인 중..."
    
    find "$CLAUDE_DIR" -name "*.md" | while read -r md_file; do
        local file_issues=0
        
        # 제목 구조 확인 (# 으로 시작하는 최상위 제목 필요)
        if ! grep -q '^# ' "$md_file"; then
            print_warning "최상위 제목(#)이 없음: $(basename "$md_file")"
            log_result "WARNING" "Missing top-level heading" "$md_file"
            ((format_issues++))
            ((file_issues++))
        fi
        
        # 코드 블록 닫힘 확인
        backtick_count=$(grep -c '```' "$md_file" 2>/dev/null || echo "0")
        if [ $((backtick_count % 2)) -ne 0 ]; then
            print_warning "코드 블록이 제대로 닫히지 않음: $(basename "$md_file")"
            log_result "WARNING" "Unclosed code block" "$md_file"
            ((format_issues++))
            ((file_issues++))
        fi
        
        # 빈 링크 확인
        if grep -q '\[\](' "$md_file"; then
            print_warning "빈 링크 텍스트 발견: $(basename "$md_file")"
            log_result "WARNING" "Empty link text" "$md_file"
            ((format_issues++))
            ((file_issues++))
        fi
        
        # 파일별 결과 출력
        if [ $file_issues -eq 0 ]; then
            print_success "$(basename "$md_file") 형식 검증 통과"
        fi
    done
    
    print_info "한국어 문서 인코딩 확인 중..."
    
    # UTF-8 인코딩 확인
    find "$CLAUDE_DIR" -name "*.md" | while read -r md_file; do
        if ! file "$md_file" | grep -q "UTF-8"; then
            print_warning "UTF-8 인코딩이 아닐 수 있음: $(basename "$md_file")"
            log_result "WARNING" "Possible non-UTF-8 encoding" "$md_file"
            ((format_issues++))
        fi
    done
    
    if [ $format_issues -eq 0 ]; then
        print_success "모든 문서의 형식이 올바름"
        log_result "SUCCESS" "All document formats are correct" ""
    else
        print_warning "$format_issues개의 형식 문제 발견"
        log_result "WARNING" "$format_issues format issues found" ""
    fi
}

validate_document_format

# 6. 자동 생성 문서 신뢰성 검증
print_section "자동 생성 문서 신뢰성 검증"

validate_auto_generated_docs() {
    local reliability_issues=0
    
    print_info "자동 생성 마커 확인 중..."
    
    # 자동 생성 파일들이 적절한 마커를 가지고 있는지 확인
    auto_generated_files=(
        "api/endpoints.md"
        "database/current-schema.md"
        "statistics/project-stats.md"
        "dependencies/current.md"
    )
    
    for auto_file in "${auto_generated_files[@]}"; do
        if [ -f "$CLAUDE_DIR/$auto_file" ]; then
            if ! grep -q "🤖.*자동 생성\|자동으로 생성\|자동 생성됩니다" "$CLAUDE_DIR/$auto_file"; then
                print_warning "자동 생성 마커가 없음: $auto_file"
                log_result "WARNING" "Missing auto-generation marker" "$auto_file"
                ((reliability_issues++))
            fi
            
            if ! grep -q "수동으로 편집하지 마세요\|직접 편집하지 마세요" "$CLAUDE_DIR/$auto_file"; then
                print_warning "편집 금지 경고가 없음: $auto_file"
                log_result "WARNING" "Missing edit warning" "$auto_file"
                ((reliability_issues++))
            fi
        fi
    done
    
    if [ $reliability_issues -eq 0 ]; then
        print_success "자동 생성 문서 신뢰성 검증 통과"
        log_result "SUCCESS" "Auto-generated docs reliability validation passed" ""
    else
        print_warning "$reliability_issues개의 신뢰성 문제 발견"
        log_result "WARNING" "$reliability_issues reliability issues found" ""
    fi
}

validate_auto_generated_docs

# 7. 검증 결과 보고서 생성
print_section "검증 결과 요약"

generate_validation_report() {
    local report_file="$CLAUDE_DIR/.validation-report.md"
    
    cat > "$report_file" << EOF
# 문서 검증 보고서

> 🔍 검증 실행 시간: $TIMESTAMP

## 📊 검증 결과 요약

- ✅ **성공**: $SUCCESS_COUNT
- ⚠️ **경고**: $WARNING_COUNT  
- ❌ **오류**: $ERROR_COUNT

## 🔍 상세 결과

EOF

    # 검증 결과를 카테고리별로 정리
    if [ -f "$VALIDATION_RESULTS" ]; then
        echo "### ❌ 오류 항목" >> "$report_file"
        grep "ERROR:" "$VALIDATION_RESULTS" | sed 's/.*ERROR: /- /' >> "$report_file"
        echo "" >> "$report_file"
        
        echo "### ⚠️ 경고 항목" >> "$report_file"
        grep "WARNING:" "$VALIDATION_RESULTS" | sed 's/.*WARNING: /- /' >> "$report_file"
        echo "" >> "$report_file"
        
        echo "### ✅ 성공 항목" >> "$report_file"
        grep "SUCCESS:" "$VALIDATION_RESULTS" | sed 's/.*SUCCESS: /- /' >> "$report_file"
        echo "" >> "$report_file"
    fi
    
    cat >> "$report_file" << EOF

## 🛠️ 권장 조치사항

EOF

    if [ $ERROR_COUNT -gt 0 ]; then
        cat >> "$report_file" << EOF
### 🚨 즉시 조치 필요
- 오류 항목들을 즉시 수정하세요
- 누락된 필수 문서를 생성하세요
- 깨진 링크를 수정하세요

EOF
    fi

    if [ $WARNING_COUNT -gt 0 ]; then
        cat >> "$report_file" << EOF
### ⚠️ 권장 조치
- 자동 문서 업데이트 실행: \`./claude/scripts/update-docs.sh\`
- 오래된 정보를 최신화하세요
- 형식 문제를 수정하세요

EOF
    fi

    cat >> "$report_file" << EOF
### 📝 정기 점검 권장
- 주 1회 문서 검증 실행
- 코드 변경 시 관련 문서 업데이트 확인
- 새로운 기능 추가 시 문서화 여부 확인

---
*이 보고서는 validate-docs.sh 스크립트에 의해 자동 생성되었습니다.*
EOF

    print_success "검증 보고서 생성 완료: $report_file"
}

generate_validation_report

# 최종 결과 출력
echo ""
print_section "최종 검증 결과"

if [ $ERROR_COUNT -eq 0 ] && [ $WARNING_COUNT -eq 0 ]; then
    echo -e "${GREEN}🎉 모든 검증을 통과했습니다!${NC}"
    echo "   📚 문서가 코드와 완벽히 동기화되어 있습니다."
    exit 0
elif [ $ERROR_COUNT -eq 0 ]; then
    echo -e "${YELLOW}⚠️  경고가 있지만 심각한 문제는 없습니다.${NC}"
    echo "   📋 총 $WARNING_COUNT개의 경고 항목을 확인해주세요."
    exit 1
else
    echo -e "${RED}❌ 심각한 문제가 발견되었습니다!${NC}"
    echo "   🚨 총 $ERROR_COUNT개의 오류와 $WARNING_COUNT개의 경고를 수정해야 합니다."
    exit 2
fi