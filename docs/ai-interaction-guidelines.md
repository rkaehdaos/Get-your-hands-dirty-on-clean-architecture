# AI Interaction Guidelines

## 언어 및 응답 가이드라인

- 나에게 답변할 때는 반드시 한국말로 답변할 것
- 예시 코드를 추가할 때는 반드시 **Kotlin 코드**로 작성할 것
- Java 코드보다 Kotlin 코드를 우선적으로 사용할 것

## 코드 작성 가이드라인

### Kotlin 우선 정책
프로젝트는 Java에서 Kotlin으로 마이그레이션을 진행하고 있으므로:

1. 새로운 클래스 작성 시 Kotlin 사용
2. 기존 Java 클래스 수정 시 가능하면 Kotlin으로 변환 고려
3. 예시 코드는 항상 Kotlin으로 제공

### 코드 스타일
- Kotlin의 idiom을 적극 활용 (data class, sealed class, extension functions 등)
- 함수형 프로그래밍 요소 적용
- 타입 안정성 강화 (inline class, sealed interface 등)

### 아키텍처 관점
- 헥사고날 아키텍처 원칙 준수
- 도메인 중심 설계 패턴 적용
- 클린 코드 원칙 준수

## 응답 형식

### 기술적 질문
- 간결하고 명확한 답변
- 실용적인 예시 포함
- 아키텍처 관점에서의 설명

### 코드 리뷰
- 개선점과 근거 제시
- 클린 코드 원칙 기반 피드백
- Kotlin 관점에서의 개선 제안