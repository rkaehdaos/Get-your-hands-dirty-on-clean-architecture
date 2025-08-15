#!/bin/bash

# 문서 자동 업데이트 스크립트
# BuckPal 프로젝트의 API, 데이터베이스, 통계 정보를 자동으로 추출하여 문서 업데이트

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CLAUDE_DIR="$PROJECT_ROOT/.claude"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

echo "🚀 BuckPal 문서 자동 업데이트 시작..."
echo "📁 프로젝트 루트: $PROJECT_ROOT"
echo "⏰ 시작 시간: $TIMESTAMP"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

# 1. API 엔드포인트 자동 추출
print_section "API 엔드포인트 추출"

extract_api_endpoints() {
    local api_doc="$CLAUDE_DIR/api/endpoints.md"
    mkdir -p "$CLAUDE_DIR/api"
    
    cat > "$api_doc" << 'EOF'
# API 엔드포인트 목록

> 🤖 이 문서는 자동 생성됩니다. 수동으로 편집하지 마세요.
> 마지막 업데이트: TIMESTAMP_PLACEHOLDER

## REST 엔드포인트

### 계좌 관련
EOF

    # Spring Boot 컨트롤러에서 엔드포인트 추출
    if find "$PROJECT_ROOT/src" -name "*Controller.java" -o -name "*Controller.kt" | head -1 > /dev/null; then
        find "$PROJECT_ROOT/src" -name "*Controller.java" -o -name "*Controller.kt" | while read -r controller; do
            echo "" >> "$api_doc"
            echo "#### $(basename "$controller" | sed 's/\.[^.]*$//')" >> "$api_doc"
            echo "" >> "$api_doc"
            
            # @RequestMapping, @GetMapping, @PostMapping 등 추출
            grep -n -E '@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)' "$controller" | while IFS=: read -r line_num mapping; do
                # 다음 줄에서 메서드명 추출
                method_line=$((line_num + 1))
                method_name=$(sed -n "${method_line}p" "$controller" | grep -o 'public [^(]*(' | sed 's/public //' | sed 's/(//')
                
                if [[ -n "$method_name" ]]; then
                    echo "- \`$mapping\` → \`$method_name\`" >> "$api_doc"
                fi
            done
        done
        print_success "API 엔드포인트 추출 완료"
    else
        print_warning "컨트롤러 파일을 찾을 수 없습니다"
    fi
    
    # 타임스탬프 업데이트
    sed -i.bak "s/TIMESTAMP_PLACEHOLDER/$TIMESTAMP/" "$api_doc" && rm "$api_doc.bak"
}

extract_api_endpoints

# 2. 데이터베이스 스키마 동기화
print_section "데이터베이스 스키마 동기화"

extract_database_schema() {
    local db_doc="$CLAUDE_DIR/database/current-schema.md"
    mkdir -p "$CLAUDE_DIR/database"
    
    cat > "$db_doc" << 'EOF'
# 현재 데이터베이스 스키마

> 🤖 이 문서는 자동 생성됩니다. 수동으로 편집하지 마세요.
> 마지막 업데이트: TIMESTAMP_PLACEHOLDER

## JPA 엔티티 구조

EOF

    # JPA 엔티티 파일 찾기
    if find "$PROJECT_ROOT/src" -name "*Entity.java" -o -name "*Entity.kt" -o -name "*JpaEntity.java" | head -1 > /dev/null; then
        find "$PROJECT_ROOT/src" -name "*Entity.java" -o -name "*Entity.kt" -o -name "*JpaEntity.java" | while read -r entity; do
            entity_name=$(basename "$entity" | sed 's/\.[^.]*$//' | sed 's/Entity$//' | sed 's/Jpa$//')
            echo "" >> "$db_doc"
            echo "### $entity_name" >> "$db_doc"
            echo "" >> "$db_doc"
            echo "\`\`\`" >> "$db_doc"
            echo "파일: $(echo "$entity" | sed "s|$PROJECT_ROOT/||")" >> "$db_doc"
            echo "\`\`\`" >> "$db_doc"
            echo "" >> "$db_doc"
            
            # @Table, @Column 정보 추출
            if grep -q "@Table" "$entity"; then
                table_name=$(grep "@Table" "$entity" | sed -n 's/.*name\s*=\s*"\([^"]*\)".*/\1/p')
                if [[ -n "$table_name" ]]; then
                    echo "**테이블명**: \`$table_name\`" >> "$db_doc"
                    echo "" >> "$db_doc"
                fi
            fi
            
            echo "**컬럼**:" >> "$db_doc"
            # @Column이 있는 필드들 추출
            grep -n -A 1 "@Column\|@Id\|@GeneratedValue" "$entity" | grep -E "(private|protected|public)" | while read -r field; do
                field_name=$(echo "$field" | sed -n 's/.*\s\+\([a-zA-Z_][a-zA-Z0-9_]*\)\s*;.*/\1/p')
                field_type=$(echo "$field" | sed -n 's/.*\s\+\([A-Za-z][A-Za-z0-9_<>]*\)\s\+[a-zA-Z_].*/\1/p')
                if [[ -n "$field_name" && -n "$field_type" ]]; then
                    echo "- \`$field_name\`: $field_type" >> "$db_doc"
                fi
            done
            echo "" >> "$db_doc"
        done
        print_success "데이터베이스 스키마 추출 완료"
    else
        print_warning "JPA 엔티티 파일을 찾을 수 없습니다"
    fi
    
    # SQL 마이그레이션 파일도 확인
    if find "$PROJECT_ROOT" -name "*.sql" | head -1 > /dev/null; then
        echo "" >> "$db_doc"
        echo "## SQL 스크립트" >> "$db_doc"
        echo "" >> "$db_doc"
        find "$PROJECT_ROOT" -name "*.sql" | while read -r sql_file; do
            echo "- \`$(echo "$sql_file" | sed "s|$PROJECT_ROOT/||")\`" >> "$db_doc"
        done
    fi
    
    # 타임스탬프 업데이트
    sed -i.bak "s/TIMESTAMP_PLACEHOLDER/$TIMESTAMP/" "$db_doc" && rm "$db_doc.bak"
}

extract_database_schema

# 3. 파일 통계 업데이트
print_section "프로젝트 통계 업데이트"

update_project_stats() {
    local stats_doc="$CLAUDE_DIR/statistics/project-stats.md"
    mkdir -p "$CLAUDE_DIR/statistics"
    
    cat > "$stats_doc" << 'EOF'
# 프로젝트 통계

> 🤖 이 문서는 자동 생성됩니다. 수동으로 편집하지 마세요.
> 마지막 업데이트: TIMESTAMP_PLACEHOLDER

## 코드 통계

EOF

    cd "$PROJECT_ROOT"
    
    # 언어별 파일 수 계산
    echo "### 파일 수" >> "$stats_doc"
    echo "" >> "$stats_doc"
    echo "| 언어 | 파일 수 | 라인 수 |" >> "$stats_doc"
    echo "|------|---------|---------|" >> "$stats_doc"
    
    # Java 파일
    java_files=$(find src -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
    java_lines=$(find src -name "*.java" -exec wc -l {} + 2>/dev/null | tail -1 | awk '{print $1}' || echo "0")
    echo "| Java | $java_files | $java_lines |" >> "$stats_doc"
    
    # Kotlin 파일
    kotlin_files=$(find src -name "*.kt" 2>/dev/null | wc -l | tr -d ' ')
    kotlin_lines=$(find src -name "*.kt" -exec wc -l {} + 2>/dev/null | tail -1 | awk '{print $1}' || echo "0")
    echo "| Kotlin | $kotlin_files | $kotlin_lines |" >> "$stats_doc"
    
    # 테스트 파일
    test_files=$(find src/test -name "*.java" -o -name "*.kt" 2>/dev/null | wc -l | tr -d ' ')
    test_lines=$(find src/test -name "*.java" -o -name "*.kt" -exec wc -l {} + 2>/dev/null | tail -1 | awk '{print $1}' || echo "0")
    echo "| Test | $test_files | $test_lines |" >> "$stats_doc"
    
    echo "" >> "$stats_doc"
    
    # 패키지 구조
    echo "### 패키지 구조" >> "$stats_doc"
    echo "" >> "$stats_doc"
    
    if [ -d "src/main/java" ] || [ -d "src/main/kotlin" ]; then
        echo "\`\`\`" >> "$stats_doc"
        find src/main -type d -name "dev" -o -name "haja" -o -name "buckpal" | head -10 | while read -r dir; do
            echo "$dir" >> "$stats_doc"
            find "$dir" -maxdepth 2 -type d | sed 's/^/  /' >> "$stats_doc"
        done
        echo "\`\`\`" >> "$stats_doc"
    fi
    
    # 타임스탬프 업데이트
    sed -i.bak "s/TIMESTAMP_PLACEHOLDER/$TIMESTAMP/" "$stats_doc" && rm "$stats_doc.bak"
    
    print_success "프로젝트 통계 업데이트 완료"
}

update_project_stats

# 4. 의존성 변경 감지
print_section "의존성 분석"

analyze_dependencies() {
    local deps_doc="$CLAUDE_DIR/dependencies/current.md"
    mkdir -p "$CLAUDE_DIR/dependencies"
    
    cat > "$deps_doc" << 'EOF'
# 현재 의존성 목록

> 🤖 이 문서는 자동 생성됩니다. 수동으로 편집하지 마세요.
> 마지막 업데이트: TIMESTAMP_PLACEHOLDER

## Gradle 의존성

EOF

    if [ -f "$PROJECT_ROOT/build.gradle.kts" ]; then
        echo "### 주요 의존성" >> "$deps_doc"
        echo "" >> "$deps_doc"
        
        # Spring Boot 버전
        spring_boot_version=$(grep -o "org.springframework.boot.*['\"].*['\"]" "$PROJECT_ROOT/build.gradle.kts" | head -1 || echo "")
        if [[ -n "$spring_boot_version" ]]; then
            echo "- **Spring Boot**: $(echo "$spring_boot_version" | sed 's/.*"\([^"]*\)".*/\1/')" >> "$deps_doc"
        fi
        
        # Kotlin 버전
        kotlin_version=$(grep -o "kotlin.*['\"].*['\"]" "$PROJECT_ROOT/build.gradle.kts" | head -1 || echo "")
        if [[ -n "$kotlin_version" ]]; then
            echo "- **Kotlin**: $(echo "$kotlin_version" | sed 's/.*"\([^"]*\)".*/\1/')" >> "$deps_doc"
        fi
        
        echo "" >> "$deps_doc"
        echo "### 전체 의존성" >> "$deps_doc"
        echo "" >> "$deps_doc"
        echo "\`\`\`kotlin" >> "$deps_doc"
        
        # dependencies 블록 추출
        awk '/dependencies\s*{/,/^}/' "$PROJECT_ROOT/build.gradle.kts" >> "$deps_doc"
        
        echo "\`\`\`" >> "$deps_doc"
        
        print_success "의존성 분석 완료"
    else
        print_warning "build.gradle.kts 파일을 찾을 수 없습니다"
    fi
    
    # 타임스탬프 업데이트
    sed -i.bak "s/TIMESTAMP_PLACEHOLDER/$TIMESTAMP/" "$deps_doc" && rm "$deps_doc.bak"
}

analyze_dependencies

# 5. 마지막 업데이트 시간 기록
print_section "업데이트 이력 기록"

record_update_history() {
    local history_file="$CLAUDE_DIR/.update-history"
    
    echo "$TIMESTAMP - 문서 자동 업데이트 실행" >> "$history_file"
    
    # 최근 10개 기록만 유지
    tail -10 "$history_file" > "$history_file.tmp" && mv "$history_file.tmp" "$history_file"
    
    # 메타 정보 파일 업데이트
    cat > "$CLAUDE_DIR/.meta" << EOF
{
  "last_update": "$TIMESTAMP",
  "script_version": "1.0.0",
  "auto_generated_files": [
    "api/endpoints.md",
    "database/current-schema.md", 
    "statistics/project-stats.md",
    "dependencies/current.md"
  ]
}
EOF

    print_success "업데이트 이력 기록 완료"
}

record_update_history

# 6. 문서 인덱스 업데이트
print_section "문서 인덱스 생성"

update_documentation_index() {
    local index_file="$CLAUDE_DIR/index.md"
    
    cat > "$index_file" << 'EOF'
# BuckPal 프로젝트 문서 인덱스

> 📚 이 인덱스는 자동 생성됩니다.
> 마지막 업데이트: TIMESTAMP_PLACEHOLDER

## 📋 문서 카테고리

### 🏗️ 아키텍처 및 설계
- [아키텍처 개요](architecture/overview.md)
- [도메인 모델](architecture/domain-model.md)
- [헥사고날 아키텍처](architecture/hexagonal.md)

### 📏 개발 규칙
- [코딩 표준](conventions/coding-standards.md)
- [디자인 패턴](conventions/patterns.md)
- [테스트 규칙](conventions/testing.md)
- [보안 가이드라인](conventions/security.md)

### 🔧 자동 생성 문서
EOF

    # 자동 생성된 파일들을 인덱스에 추가
    if [ -f "$CLAUDE_DIR/api/endpoints.md" ]; then
        echo "- [API 엔드포인트](api/endpoints.md) 🤖" >> "$index_file"
    fi
    
    if [ -f "$CLAUDE_DIR/database/current-schema.md" ]; then
        echo "- [데이터베이스 스키마](database/current-schema.md) 🤖" >> "$index_file"
    fi
    
    if [ -f "$CLAUDE_DIR/statistics/project-stats.md" ]; then
        echo "- [프로젝트 통계](statistics/project-stats.md) 🤖" >> "$index_file"
    fi
    
    if [ -f "$CLAUDE_DIR/dependencies/current.md" ]; then
        echo "- [의존성 목록](dependencies/current.md) 🤖" >> "$index_file"
    fi
    
    cat >> "$index_file" << 'EOF'

### 🛠️ 컴포넌트 분석
EOF

    # components 디렉토리의 파일들 추가
    if [ -d "$CLAUDE_DIR/components" ]; then
        find "$CLAUDE_DIR/components" -name "*.md" | sort | while read -r comp_file; do
            comp_name=$(basename "$comp_file" .md)
            comp_path=$(echo "$comp_file" | sed "s|$CLAUDE_DIR/||")
            echo "- [${comp_name}](${comp_path})" >> "$index_file"
        done
    fi
    
    cat >> "$index_file" << 'EOF'

### 🧰 유틸리티
- [스크립트 사용법](README.md)
- [문서 검증](scripts/validate-docs.sh)

---

🤖 **자동 생성 표시**: 이 파일들은 자동으로 생성되므로 직접 편집하지 마세요.
📝 **수동 편집**: 나머지 파일들은 필요에 따라 수동으로 편집할 수 있습니다.
EOF

    # 타임스탬프 업데이트
    sed -i.bak "s/TIMESTAMP_PLACEHOLDER/$TIMESTAMP/" "$index_file" && rm "$index_file.bak"
    
    print_success "문서 인덱스 생성 완료"
}

update_documentation_index

# 완료 메시지
print_section "업데이트 완료"
echo -e "${GREEN}🎉 모든 문서 업데이트가 완료되었습니다!${NC}"
echo ""
echo "📄 업데이트된 파일들:"
echo "  - API 엔드포인트 문서"
echo "  - 데이터베이스 스키마 문서"  
echo "  - 프로젝트 통계"
echo "  - 의존성 목록"
echo "  - 문서 인덱스"
echo ""
echo "⏰ 총 소요 시간: $(date '+%Y-%m-%d %H:%M:%S') (시작: $TIMESTAMP)"
echo ""
echo "💡 팁: ./claude/scripts/validate-docs.sh 를 실행하여 문서 유효성을 검증하세요."