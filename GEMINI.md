# GEMINI.md

이 파일은 Gemini CLI 에이전트가 이 저장소에서 작업할 때 필요한 핵심 지침과 프로젝트 문맥을 제공합니다.

## 프로젝트 개요
**BuckPal**은 "Clean Architecture" 실습을 위한 헥사고날 아키텍처 기반 은행 계좌 송금 시스템입니다.
- **핵심 목표**: 계층 간의 결합도를 낮추고 도메인 로직을 핵심에 두는 헥사고날 아키텍처 원칙 준수.
- **상태**: Java에서 Kotlin으로 마이그레이션 진행 중.

## 기술 스택
- **Framework**: Spring Boot 4.0 (Spring WebMVC, Data JPA)
- **Languages**: Kotlin (우선순위), Java (레거시/마이그레이션 대상)
- **Database**: H2 (Local/Test), PostgreSQL (Prod)
- **Testing**: JUnit 5, Kotest, MockK, Mockito, ArchUnit
- **Mapping**: MapStruct (Lombok과 병행 사용 중이나 Kotlin 전환 시 제거 예정)
- **Build Tool**: Gradle (Kotlin DSL)

## 핵심 개발 지침
1. **언어 및 스타일**:
   - 모든 에이전트 응답은 **한국어**를 기본으로 함.
   - 코드 예시는 **Kotlin**을 최우선으로 작성.
   - BDD 스타일 테스트 권장 (`given-when-then`).

2. **아키텍처 원칙 (헥사고날)**:
   - **의존성 방향**: `Adapter` -> `Application (Port/Service)` -> `Domain`. 의존성은 항상 안쪽으로 향해야 함.
   - **도메인 독립성**: `domain` 패키지는 외부 의존성(Spring, JPA 등)을 가지지 않는 순수 POJO/Kotlin 클래스로 유지.
   - **Port 사용**: 외부와의 통신은 항상 인터페이스(In/Out Port)를 통해 수행.
   - **ArchUnit**: `DependencyRuleTests.java` 및 관련 Kotlin 테스트를 통해 아키텍처 규칙이 강제됨. 변경 시 이를 위반하지 않도록 주의.

3. **Java -> Kotlin 마이그레이션 전략**:
   - 1순위: Value Objects (Money, AccountId)를 Kotlin Data Class로 변환.
   - 2순위: Domain Entities (Account, Activity) 변환.
   - 3순위: Application Services를 함수형 스타일을 가미하여 변환.

## 주요 명령어
- **빌드**: `./gradlew build`
- **테스트 실행**: `./gradlew test`
- **특정 테스트 실행**: `./gradlew test --tests "ClassName"`
- **애플리케이션 실행**: `./gradlew bootRun`
- **실행 JAR 생성**: `./gradlew bootJar`
- **네이티브 이미지**: `./gradlew nativeCompile` (GraalVM 전용)

## 디렉토리 구조
```
src/main/java/dev/haja/buckpal/account/
├── domain/           # 비즈니스 로직 및 엔티티 (가장 안쪽)
├── application/
│   ├── port/in/      # 유스케이스 인터페이스 (Incoming Port)
│   ├── port/out/     # 외부 통신 인터페이스 (Outgoing Port)
│   └── service/      # 유스케이스 구현 (Application Service)
└── adapter/
    ├── in/web/       # 웹 컨트롤러 (Incoming Adapter)
    └── out/persistence/ # JPA 리포지토리 및 어댑터 (Outgoing Adapter)
```

## 테스트 표준
- `@DisplayName`을 사용하여 한국어로 테스트 목적 명시.
- `Kotest`와 `MockK`를 활용한 Kotlin 테스트 코드 작성을 선호.
- 영속성 계층 테스트 시 `@DataJpaTest` 및 필요한 SQL 스크립트(`src/test/resources/.../*.sql`) 활용.
