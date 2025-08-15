# BuckPal Claude 문서 가이드

> 🤖 **Claude Code**를 위한 포괄적인 프로젝트 문서 시스템

이 디렉토리는 **Get Your Hands Dirty on Clean Architecture** 책의 BuckPal 프로젝트를 위한 Claude Code 최적화 문서들을 포함합니다.

## 📚 문서 구조

```
.claude/
├── README.md                 # 이 파일 - 사용 가이드
├── .gitignore               # Git 제외 파일 설정
├── index.md                 # 📚 전체 문서 인덱스 (자동 생성)
├── 
├── conventions/             # 🏗️ 개발 규칙 및 컨벤션
│   ├── coding-standards.md  # 코딩 표준 (네이밍, 포맷팅, 주석)
│   ├── patterns.md          # 디자인 패턴 및 아키텍처 패턴
│   ├── testing.md           # 테스트 전략 및 규칙
│   └── security.md          # 보안 가이드라인 및 체크리스트
├── 
├── architecture/            # 🏛️ 아키텍처 문서
│   ├── overview.md          # 아키텍처 개요
│   ├── domain-model.md      # 도메인 모델 설계
│   └── hexagonal.md         # 헥사고날 아키텍처 상세
├── 
├── components/              # 🔧 컴포넌트 분석
│   ├── account-domain.md    # Account 도메인 분석
│   ├── money-value-object.md # Money 값 객체 분석
│   └── activity-tracking.md # Activity 추적 컴포넌트
├── 
├── database/                # 🗄️ 데이터베이스 문서
│   ├── schema-overview.md   # 스키마 개요
│   ├── jpa-mapping.md       # JPA 매핑 전략
│   └── current-schema.md    # 🤖 현재 스키마 (자동 생성)
├── 
├── scripts/                 # 🛠️ 자동화 스크립트
│   ├── update-docs.sh       # 문서 자동 업데이트
│   └── validate-docs.sh     # 문서 검증 스크립트
├── 
└── api/                     # 🌐 API 문서 (자동 생성)
    └── endpoints.md         # 🤖 API 엔드포인트 목록
```

## 🚀 빠른 시작

### 1. 문서 업데이트

```bash
# 모든 자동 생성 문서 업데이트
./.claude/scripts/update-docs.sh

# 문서 유효성 검증
./.claude/scripts/validate-docs.sh
```

### 2. Claude Code와 함께 사용

Claude Code에서 이 프로젝트를 열면 자동으로 CLAUDE.md 파일을 읽어 프로젝트 컨텍스트를 이해합니다.

```bash
# Claude Code에서 프로젝트 열기
claude-code .

# 또는 특정 파일과 함께
claude-code src/main/java/dev/haja/buckpal/account/domain/Account.java
```

## 📖 문서 카테고리별 가이드

### 🏗️ 개발 규칙 (conventions/)

프로젝트의 코딩 표준과 개발 패턴을 정의합니다.

- **coding-standards.md**: Java/Kotlin 네이밍 규칙, 포맷팅, 주석 스타일
- **patterns.md**: 헥사고날 아키텍처, 도메인 패턴, 애플리케이션 패턴
- **testing.md**: 단위/통합/시스템 테스트 전략, AAA 패턴, BDD 스타일
- **security.md**: 입력 검증, 비즈니스 규칙 보안, 동시성 제어

### 🏛️ 아키텍처 (architecture/)

시스템의 전체적인 구조와 설계 원칙을 문서화합니다.

- **overview.md**: 클린 아키텍처와 헥사고날 패턴 개요
- **domain-model.md**: Account, Money, Activity 도메인 모델
- **hexagonal.md**: 포트와 어댑터 패턴 상세 구현

### 🔧 컴포넌트 (components/)

재사용 가능한 컴포넌트들의 상세 분석입니다.

- **account-domain.md**: 계좌 도메인 엔티티 분석
- **money-value-object.md**: 금액 값 객체 설계
- **activity-tracking.md**: 거래 활동 추적 시스템

### 🤖 자동 생성 문서

다음 문서들은 스크립트에 의해 자동으로 생성됩니다:

- `api/endpoints.md` - REST API 엔드포인트 목록
- `database/current-schema.md` - 현재 데이터베이스 스키마
- `statistics/project-stats.md` - 프로젝트 통계 정보
- `dependencies/current.md` - 현재 의존성 목록
- `index.md` - 전체 문서 인덱스

## 🛠️ 자동화 스크립트 사용법

### update-docs.sh

프로젝트의 코드베이스를 스캔하여 문서를 자동으로 업데이트합니다.

```bash
# 전체 문서 업데이트
./.claude/scripts/update-docs.sh

# 특정 부분만 업데이트 (향후 추가 예정)
# ./.claude/scripts/update-docs.sh --api-only
# ./.claude/scripts/update-docs.sh --db-only
```

**업데이트 내용:**
- ✅ API 엔드포인트 자동 추출
- ✅ 데이터베이스 스키마 동기화  
- ✅ 파일 통계 업데이트
- ✅ 의존성 변경 감지
- ✅ 마지막 업데이트 시간 기록

### validate-docs.sh

문서의 정확성과 최신성을 검증합니다.

```bash
# 전체 문서 검증
./.claude/scripts/validate-docs.sh

# 검증 결과는 .validation-report.md에 저장됩니다
```

**검증 항목:**
- ✅ 문서와 실제 코드 일치 여부
- ✅ 깨진 링크 확인
- ✅ 오래된 정보 플래깅
- ✅ 누락된 문서 섹션 감지
- ✅ 문서 형식 검증
- ✅ 자동 생성 문서 신뢰성

## 💡 Claude Code 활용 팁

### 1. 효과적인 프롬프트

```
# 좋은 예시
"Account 클래스의 withdraw 메서드에 비즈니스 규칙 검증을 추가해줘"

"SendMoneyService에서 계좌 락 메커니즘을 개선해서 동시성 문제를 해결해줘"

"테스트 코드에서 BDD 스타일로 given-when-then 패턴을 적용해줘"

# 피해야 할 예시
"코드를 좀 고쳐줘" (너무 모호함)
"버그가 있어" (구체적인 위치나 증상 미제공)
```

### 2. 컨텍스트 제공

```
# 관련 문서 먼저 참조
"패턴 문서를 참고해서 새로운 유스케이스를 추가해줘"

"보안 가이드라인에 따라 입력 검증을 강화해줘"

"테스트 규칙에 맞게 새로운 테스트를 작성해줘"
```

### 3. 아키텍처 준수

```
# 헥사고날 아키텍처 유지
"새로운 어댑터를 추가할 때 계층 분리 원칙을 지켜줘"

"도메인 로직이 외부 의존성을 갖지 않도록 해줘"

"포트와 어댑터 패턴을 따라 구현해줘"
```

## 🤝 팀 협업 가이드

### 문서 편집 규칙

1. **수동 편집 파일**: 자유롭게 편집 가능
   - `conventions/` 디렉토리의 모든 파일
   - `architecture/` 디렉토리의 모든 파일
   - `components/` 디렉토리의 모든 파일
   - 이 `README.md` 파일

2. **자동 생성 파일**: 직접 편집 금지 🚫
   - `api/endpoints.md`
   - `database/current-schema.md`
   - `statistics/project-stats.md`
   - `dependencies/current.md`
   - `index.md`

### 문서 업데이트 워크플로우

```bash
# 1. 코드 변경 후 문서 업데이트
git add .
git commit -m "feat: 새로운 유스케이스 추가"

# 2. 문서 자동 업데이트
./.claude/scripts/update-docs.sh

# 3. 문서 검증
./.claude/scripts/validate-docs.sh

# 4. 문서 변경사항 커밋
git add .claude/
git commit -m "docs: 자동 생성 문서 업데이트"
```

### 코드 리뷰 시 체크리스트

- [ ] 새로운 클래스나 메서드가 적절히 문서화되었는가?
- [ ] 아키텍처 규칙을 준수하는가?
- [ ] 보안 가이드라인을 따르는가?
- [ ] 테스트 코드가 규칙에 맞게 작성되었는가?
- [ ] 자동 생성 문서가 최신 상태인가?

## 🔧 고급 사용법

### 1. Git Hooks 설정

코드 변경 시 자동으로 문서 업데이트를 알려주는 훅을 설정할 수 있습니다:

```bash
# pre-commit 훅 설정 (별도 섹션에서 설명)
# .git/hooks/pre-commit 파일 생성
```

### 2. CI/CD 통합

```yaml
# GitHub Actions 예시
name: Documentation Validation
on: [push, pull_request]
jobs:
  validate-docs:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Validate Documentation
        run: ./.claude/scripts/validate-docs.sh
```

### 3. 문서 검색

```bash
# 특정 키워드로 문서 검색
grep -r "SendMoneyService" .claude/

# 특정 패턴의 코드 예시 찾기
grep -r "@Transactional" .claude/
```

## 🆘 문제 해결

### 자주 발생하는 문제

1. **스크립트 실행 권한 오류**
   ```bash
   chmod +x ./.claude/scripts/*.sh
   ```

2. **문서 경로 문제**
   ```bash
   # 프로젝트 루트에서 실행해야 함
   cd /path/to/project/root
   ./.claude/scripts/update-docs.sh
   ```

3. **인코딩 문제**
   ```bash
   # 한국어 포함 파일은 UTF-8로 저장
   file .claude/conventions/coding-standards.md
   ```

### 로그 확인

```bash
# 최근 업데이트 이력 확인
cat .claude/.update-history

# 검증 결과 확인  
cat .claude/.validation-results

# 상세 검증 보고서
cat .claude/.validation-report.md
```

## 📈 지속적인 개선

### 문서 품질 향상

1. **정기적인 검증**: 주 1회 `validate-docs.sh` 실행
2. **피드백 수집**: 팀원들의 문서 사용 경험 공유
3. **자동화 개선**: 스크립트 기능 지속적 확장

### 기여 방법

1. **문서 개선**: 불명확한 부분 발견 시 즉시 개선
2. **스크립트 확장**: 새로운 자동화 요구사항 제안
3. **템플릿 추가**: 새로운 문서 타입 템플릿 작성

---

## 📞 도움이 필요하신가요?

- 📖 **Claude Code 문서**: https://docs.anthropic.com/claude/docs
- 🏗️ **Clean Architecture**: Robert C. Martin의 "Clean Architecture" 참고
- 📚 **프로젝트 기반**: "Get Your Hands Dirty on Clean Architecture" by Tom Hombergs

---

*이 문서는 BuckPal 프로젝트의 Claude Code 최적화를 위해 작성되었습니다. 궁금한 점이 있으시면 언제든 문의해주세요! 🚀*