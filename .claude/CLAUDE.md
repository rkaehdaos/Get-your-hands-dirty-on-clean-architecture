# BuckPal - 헥사고날 아키텍처 프로젝트

## 프로젝트 개요

**Get Your Hands Dirty on Clean Architecture** 프로젝트의 구현체인 BuckPal은 클린 아키텍처와 헥사고날 아키텍처 원칙을 실제로 적용한 Spring Boot 애플리케이션입니다.

### 프로젝트 타입 및 기술 스택

**프로젝트 타입**: Spring Boot 기반 헥사고날 아키텍처 데모 애플리케이션
**주요 도메인**: 은행 계좌 송금 시스템

#### 핵심 기술 스택
- **JDK**: 24 (최신 미리보기 기능 사용)
- **Kotlin**: 2.2.0 (Java에서 마이그레이션 진행 중)
- **Spring Boot**: 3.5.4
- **Spring Data JPA**: 영속성 계층
- **H2 Database**: 개발/테스트용 인메모리 데이터베이스
- **Gradle**: 8.14.3 (Kotlin DSL)
- **GraalVM Native Image**: 네이티브 컴파일 지원

#### 개발 도구 및 라이브러리
- **Lombok**: 보일러플레이트 코드 제거
- **MapStruct**: 객체 매핑
- **ArchUnit**: 아키텍처 규칙 검증
- **JUnit 5**: 테스트 프레임워크
- **AssertJ**: 유창한 어설션

### 프로젝트 통계

#### 코드 구성
```
파일 유형별 통계:
├── Java 파일: 43개 (1,955 라인)
├── Kotlin 파일: 4개 (82 라인)
├── YAML 설정: 5개
├── SQL 스크립트: 2개
└── Markdown 문서: 4개

Spring 어노테이션별 통계:
├── @Component/@Service/@Repository/@Controller: 7개
├── @Entity/@Table: 4개 (2개 JPA 엔티티)
└── @Test 메서드: 22개 (8개 테스트 클래스)
```

#### 디렉토리 구조 및 역할

```
src/main/java/dev/haja/buckpal/
├── BuckpalApplication.java           # 메인 애플리케이션
├── BuckPalConfiguration.java         # 설정
├── account/                          # 계좌 바운디드 컨텍스트
│   ├── domain/                       # 도메인 계층
│   │   ├── Account.java             # 계좌 집합체
│   │   ├── Money.java               # 금액 값 객체
│   │   ├── Activity.java            # 거래 활동 엔티티
│   │   └── ActivityWindow.java      # 활동 윈도우
│   ├── application/                  # 애플리케이션 계층
│   │   ├── port/in/                 # 인커밍 포트 (유스케이스)
│   │   ├── port/out/                # 아웃고잉 포트 (리포지토리 인터페이스)
│   │   └── service/                 # 애플리케이션 서비스
│   └── adapter/                     # 어댑터 계층
│       ├── in/web/                  # 웹 어댑터 (컨트롤러)
│       └── out/persistence/         # 영속성 어댑터
└── common/                          # 공통 어노테이션

src/main/kotlin/dev/haja/
├── buckpal/sample/                   # Kotlin 샘플 코드
└── java2kotlin/                      # Java → Kotlin 마이그레이션 예시

src/test/java/dev/haja/buckpal/
├── DependencyRuleTests.java          # 아키텍처 규칙 테스트
├── SendMoneySystemTest.java          # 시스템 테스트
├── account/                          # 도메인별 테스트
├── archunit/                         # ArchUnit 커스텀 규칙
└── common/                           # 테스트 데이터 팩토리
```

### 의존성 분석

#### Spring Boot 스타터
- `spring-boot-starter-web`: REST API 제공
- `spring-boot-starter-data-jpa`: JPA 기반 영속성
- `spring-boot-starter-validation`: 입력 검증
- `spring-boot-starter-actuator`: 모니터링 엔드포인트
- `spring-boot-starter-test`: 테스트 환경

#### 아키텍처 품질 도구
- `archunit-junit5-engine:1.4.1`: 아키텍처 규칙 자동 검증
- 커스텀 헥사고날 아키텍처 DSL 구현

#### 개발 편의성 도구
- **MapStruct 1.6.3**: 컴파일 타임 매핑 코드 생성
- **Lombok**: `@Value`, `@Getter`, `@AllArgsConstructor` 등
- **Spring Boot DevTools**: 개발 시 핫 리로드

### 설정 파일 위치

#### 애플리케이션 설정
- `src/main/resources/application.yml`: 기본 설정
- `src/main/resources/application-local.yml`: 로컬 개발 설정
- `src/main/resources/application-prod.yml`: 운영 환경 설정

#### 빌드 설정
- `build.gradle.kts`: Gradle 빌드 스크립트 (Kotlin DSL)
- `settings.gradle.kts`: 프로젝트 설정
- `gradle.properties`: Gradle 실행 환경 설정

#### 테스트 설정
- `src/test/resources/application.yml`: 테스트 환경 설정
- `src/test/resources/**/*.sql`: 테스트 데이터 스크립트

### 아키텍처 특징

#### 헥사고날 아키텍처 구현
1. **도메인 중심**: 비즈니스 로직이 도메인 계층에 집중
2. **포트 & 어댑터**: 명확한 경계와 인터페이스 정의
3. **의존성 역전**: 도메인이 외부 기술에 의존하지 않음
4. **테스트 가능성**: 각 계층별 독립적인 테스트 가능

#### 도메인 모델링
- **Account**: 계좌 집합체 루트
- **Money**: 금액을 나타내는 값 객체
- **Activity**: 송금 활동 도메인 이벤트
- **ActivityWindow**: 특정 기간의 활동을 관리하는 도메인 서비스

#### Java → Kotlin 마이그레이션
현재 진행 상황:
- 전체 47개 파일 중 4개가 Kotlin으로 작성됨 (약 8.5%)
- 샘플 코드와 기본적인 데이터 클래스부터 마이그레이션 시작
- 점진적으로 값 객체 → 도메인 엔티티 → 서비스 순으로 변환 예정

### 코드 품질 및 규약

#### 아키텍처 규칙 강제
- ArchUnit을 통한 계층별 의존성 규칙 검증
- 커스텀 `HexagonalArchitecture` DSL로 포트 & 어댑터 패턴 검증
- 패키지별 역할과 책임 명확화

#### 테스트 전략
- 단위 테스트: 도메인 로직 중심
- 통합 테스트: 어댑터 계층 검증
- 시스템 테스트: 전체 유스케이스 검증
- 아키텍처 테스트: 설계 규칙 준수 확인

이 프로젝트는 클린 아키텍처의 실제 구현 사례를 제공하며, 특히 헥사고날 아키텍처 패턴을 통해 유지보수성과 테스트 용이성을 확보한 예시입니다.