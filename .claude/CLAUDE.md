# BuckPal 상세 아키텍처 가이드

> 이 문서는 루트 CLAUDE.md의 상세 참조 문서입니다.

## 헥사고날 아키텍처 상세

### 계층별 책임

#### Domain Layer (`account/domain/`)
외부 의존성 없이 순수 비즈니스 로직만 포함
- `Account`: 계좌 집합체 - 잔액 계산, 출금 검증, 입출금 처리
- `Money`: 금액 값 객체 - BigDecimal 래핑, 연산 메서드 제공
- `Activity`: 개별 거래 활동 - 출금 계좌, 입금 계좌, 금액, 타임스탬프
- `ActivityWindow`: 활동 윈도우 - 특정 기간 활동 집합 관리

#### Application Layer (`account/application/`)
유스케이스 구현 및 포트 정의
- `port/in/`: 인커밍 포트 - `SendMoneyUseCase`, `GetAccountBalanceQuery`
- `port/out/`: 아웃고잉 포트 - `LoadAccountPort`, `UpdateAccountStatePort`
- `service/`: 서비스 구현 - `SendMoneyService`, `GetAccountBalanceService`

#### Adapter Layer (`account/adapter/`)
외부 시스템과의 연결
- `in/web/`: REST 컨트롤러 - `SendMoneyController`
- `out/persistence/`: JPA 구현 - `AccountPersistenceAdapter`, 엔티티, 매퍼

### 아키텍처 테스트 (ArchUnit)

`DependencyRuleTests.java`에서 다음 규칙 강제:
1. 도메인 → 애플리케이션 의존 금지
2. 어댑터 → 도메인 직접 접근 제한 (포트를 통해서만)
3. 애플리케이션 서비스 → 아웃고잉 포트만 의존 (구현체 직접 의존 금지)

커스텀 `HexagonalArchitecture` DSL로 포트 & 어댑터 패턴 검증

## 의존성 구성

### 빌드 설정 특이사항
- **KAPT + annotationProcessor 병행**: Java/Kotlin 혼용 기간 동안 MapStruct, Lombok 처리
- **duplicatesStrategy = EXCLUDE**: JAR 빌드 시 중복 파일 충돌 방지
- **allWarningsAsErrors = true**: Kotlin 컴파일 경고를 에러로 처리

### 주요 의존성 버전 관리
모든 버전은 `gradle.properties`에서 관리:
- `javaVersion`, `kotlinVersion`: 언어 버전
- `springBootVersion`: Spring Boot 버전
- `mapstructVersion`, `kotestVersion`, `mockkVersion`: 라이브러리 버전

## 설정 파일 구조

```
src/main/resources/
├── application.yml         # 기본 설정
├── application-local.yml   # 로컬 개발 (H2)
└── application-prod.yml    # 운영 환경 (PostgreSQL)
```

## 테스트 구조

```
src/test/java/dev/haja/buckpal/
├── DependencyRuleTests.java      # 아키텍처 규칙 테스트
├── SendMoneySystemTest.java      # E2E 시스템 테스트
├── account/
│   ├── domain/                   # 도메인 단위 테스트
│   ├── application/service/      # 서비스 단위 테스트 (Mock 사용)
│   └── adapter/
│       ├── in/web/               # 컨트롤러 슬라이스 테스트 (@WebMvcTest)
│       └── out/persistence/      # 영속성 통합 테스트 (@DataJpaTest)
├── archunit/                     # 커스텀 ArchUnit DSL
└── common/                       # 테스트 데이터 팩토리
```

### 테스트 라이브러리 사용
- **JUnit 5 + BDDMockito**: Java 코드 테스트
- **Kotest + MockK**: Kotlin 코드 테스트
- **SpringMockK**: Spring 통합 테스트에서 MockK 사용
