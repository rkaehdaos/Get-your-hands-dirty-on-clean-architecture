# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고할 가이드를 제공합니다.

## 개발 명령어

### 빌드 및 테스트
- `./gradlew build` - 테스트 포함 전체 빌드
- `./gradlew test` - 모든 테스트 실행  
- `./gradlew clean` - 빌드 디렉토리 정리
- `./gradlew bootRun` - Spring Boot 애플리케이션 실행
- `./gradlew bootJar` - 실행 가능한 JAR 생성

### 단일 테스트 실행
- `./gradlew test --tests "클래스명"` - 특정 테스트 클래스 실행
- `./gradlew test --tests "클래스명.메서드명"` - 특정 테스트 메서드 실행

### 네이티브 이미지 (GraalVM)
- `./gradlew nativeCompile` - 네이티브 이미지 컴파일
- `./gradlew nativeRun` - 네이티브 실행 파일 실행
- `./gradlew nativeTest` - 네이티브 바이너리로 테스트 실행

## 프로젝트 아키텍처

이 프로젝트는 Spring Boot와 함께 클린 아키텍처 원칙을 보여주는 **헥사고날 아키텍처** 구현체입니다.

### 핵심 구조
```
├── domain/           # 비즈니스 엔티티 및 규칙 (Account, Money, Activity)
├── application/      # 유스케이스 및 포트
│   ├── port/in/     # 인커밍 포트 (유스케이스)
│   ├── port/out/    # 아웃고잉 포트 (리포지토리 인터페이스)  
│   └── service/     # 유스케이스를 구현하는 애플리케이션 서비스
└── adapter/         # 외부 세계 어댑터
    ├── in/web/      # REST 컨트롤러 (인커밍)
    └── out/persistence/ # JPA 리포지토리 (아웃고잉)
```

### 주요 패턴
- **포트 & 어댑터**: 비즈니스 로직과 외부 관심사의 명확한 분리
- **의존성 역전**: 도메인 계층은 외부 의존성이 없음
- **CQRS 스타일**: 조회와 명령 책임 분리
- **도메인 이벤트**: 송금 활동을 도메인 이벤트로 모델링

### 아키텍처 테스트
- ArchUnit 테스트가 `DependencyRuleTests.java:23`에서 헥사고날 아키텍처 규칙을 강제
- 커스텀 `HexagonalArchitecture` DSL이 계층 의존성을 검증

## 언어 마이그레이션 전략

**중요**: 이 프로젝트는 Java에서 Kotlin으로 마이그레이션 중입니다. 다음 가이드라인을 따르세요:

### AI 응답 요구사항
- **항상 한국어로 응답**
- **코드 예시는 항상 Kotlin으로 제공** (Java 아님)
- 변경 시 Java보다 Kotlin 코드를 우선

### 마이그레이션 우선순위
1. **값 객체** (Money, AccountId) → Kotlin data class
2. **도메인 엔티티** (Account, Activity) → 적절한 캡슐화를 가진 Kotlin
3. **애플리케이션 서비스** → 함수형 프로그래밍 요소를 포함한 Kotlin

## 테스트 표준

### 테스트 구조
- 한국어 설명이 포함된 `@DisplayName` 사용
- AAA 패턴 따르기 (Arrange, Act, Assert)
- BDD 스타일 명명: `given...when...then...`
- BDDMockito 스타일 사용: `given().willReturn()` 및 `then().should()`

### 예시 패턴
```kotlin
@Test
@DisplayName("계좌 이체가 성공적으로 처리되는 경우")
fun givenValidAccounts_whenTransferMoney_thenTransactionSucceeds() {
    // given, when, then
}
```

## 기술 구성

### Java/Kotlin 설정
- **JDK**: 25 (정식 LTS 버전)
- **Kotlin**: 2.2.0 with 엄격한 null 안전성 (`-Xjsr305=strict`)
- **Spring Boot**: 3.5.5 네이티브 이미지 지원

### 주요 의존성
- **MapStruct**: 객체 매핑 (Lombok과 통합)
- **ArchUnit**: 아키텍처 테스트
- **H2**: 테스트용 인메모리 데이터베이스
- **Spring Data JPA**: 영속성 계층

### 유지해야 할 아키텍처 원칙
- 도메인 계층 독립성 (외부 의존성 없음)
- 포트 중심 설계 (인터페이스가 계약 정의)
- 도메인 엔티티에 캡슐화된 비즈니스 규칙
- 계층 간 관심사의 깔끔한 분리
- 성능에 문제가 없다면 CLAUDE.md도 한국어로 저장해주면 좋겠어
- 쉘 작업시 gh active account가 `rkaehdaos`인지 확인할것. 아닌 경우 `gh auth switch`등을 이용해서 바꾸고 작업할 것.