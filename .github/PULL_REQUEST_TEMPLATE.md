# Pull Request Template

## 📋 JIRA 티켓 / Issue
**JIRA:** [SMART-XXX](링크 첨부)
**(선택적) 깃허브 이슈 Issues:** #issue_number

---

## 🏷️ PR 타입 / Change Type
<!-- 해당하는 항목에 체크 -->
- [ ] ✨ **Feature** - 새로운 기능 추가
- [ ] 🐛 **Bug Fix** - 버그 수정
- [ ] ♻️ **Refactor** - 코드 리팩토링
- [ ] 🎨 **Style** - 코드 포맷팅, 세미콜론 누락 등
- [ ] 📝 **Docs** - 문서 수정
- [ ] ⚡ **Performance** - 성능 개선
- [ ] ✅ **Test** - 테스트 코드 추가/수정
- [ ] 🔧 **Chore** - 빌드 과정, 보조 도구 변경
- [ ] 🔒 **Security** - 보안 관련 변경

## 🎯 영향 범위 / Scope
- [ ] 🎨 **Frontend** - React/TypeScript
- [ ] 🛠️ **Backend** - Spring Boot/Kotlin
- [ ] 📊 **Database** - Schema/Migration
- [ ] 🔧 **Config** - 설정 파일 변경
- [ ] 📖 **Documentation**

---

## 📋 작업 내용 / Summary
### 간단 요약 / Brief Description
<!-- 이 PR에서 수행한 작업을 간단하게 설명해 주세요 -->

### 상세 변경사항 / Detailed Changes
<!-- 주요 변경사항을 구체적으로 작성해 주세요 -->
- 
-
-

### 동기 및 배경 / Motivation
<!-- 왜 이 변경이 필요한지 설명해 주세요 -->

---

## 🧪 테스트 / Testing
### 테스트 방법 / How to Test
<!-- 이 변경사항을 어떻게 테스트할 수 있는지 설명해 주세요 -->
1.
2.
3.

### 테스트 결과 / Test Results
- [ ] Unit Test 통과
- [ ] Integration Test 통과
- [ ] Manual Test 완료

---

## ✅ 체크리스트 / Checklist

### 공통 / Common
- [ ] 🔍 **코드 리뷰가 가능한 상태입니다**
- [ ] 🎫 **JIRA 티켓이 연결되어 있습니다**
- [ ] 📝 **커밋 메시지가 명확합니다**
- [ ] 🔀 **Conflict가 해결되었습니다**

### Frontend (해당하는 경우만)
- [ ] ✅ **TypeScript 타입 체크 통과** (`yarn build`)
- [ ] 🧪 **Vitest 테스트 통과** (`yarn test --run`)
- [ ] 🎨 **ESLint 통과** (`yarn lint`)
- [ ] 📱 **반응형 디자인 확인 완료**
- [ ] ♿ **접근성 고려사항 검토 완료**

### Backend (해당하는 경우만)
- [ ] 🏗️ **Gradle 빌드 성공** (`./gradlew build`)
- [ ] 🧪 **단위 테스트 통과** (`./gradlew test`)
- [ ] 🔌 **API 문서 업데이트** (필요시)
- [ ] 🛡️ **보안 검토 완료** (인증/권한)
- [ ] 📊 **DB 스키마 변경 검토** (필요시)

---

## 📸 스크린샷 / Screenshots
<!-- UI 변경이 있는 경우 Before/After 스크린샷을 첨부해 주세요 -->

### Before
<!-- 변경 전 스크린샷 -->

### After
<!-- 변경 후 스크린샷 -->

---

## 🔄 API 변경사항 / API Changes
<!-- API 변경이 있는 경우 작성해 주세요 -->

### 신규 API
```
GET/POST/PUT/DELETE /api/v1/endpoint
```

### 변경된 API
```
기존: GET /api/v1/old-endpoint
신규: GET /api/v1/new-endpoint
```

### Request/Response 예시
```json
{
  "example": "data"
}
```

---

## 🚀 배포 고려사항 / Deployment Notes

### 환경변수 변경
- [ ] **새로운 환경변수 추가 없음**
- [ ] **환경변수 변경 사항 있음**
    - `NEW_ENV_VAR=value`

### 데이터베이스
- [ ] **DB 마이그레이션 필요 없음**
- [ ] **DB 마이그레이션 필요함**
    - 마이그레이션 스크립트:

### 의존성 변경
- [ ] **새로운 의존성 추가 없음**
- [ ] **새로운 의존성 추가됨**
    - Frontend:
    - Backend:

### 롤백 계획
<!-- 문제 발생 시 롤백 방법을 설명해 주세요 -->
- 

---

## 👥 리뷰어 / Reviewers
- [ ] **Frontend 리뷰 필요**: @frontend-reviewer
- [ ] **Backend 리뷰 필요**: @backend-reviewer
- [ ] **DevOps 리뷰 필요**: @devops-reviewer
- [ ] **PM/기획자 확인 필요**: @product-manager

---

## 📝 추가 노트 / Additional Notes
<!-- 리뷰어가 알아야 할 기타 정보를 작성해 주세요 -->

### 참고 문서
- 

### 관련 PR
- 

### 후속 작업
- 

---

> 💡 **리뷰어를 위한 팁**
> - 중요한 변경사항이나 복잡한 로직에는 코드 내 주석으로 설명을 추가했습니다
> - 특별히 검토가 필요한 부분이 있다면 PR 코멘트로 표시했습니다