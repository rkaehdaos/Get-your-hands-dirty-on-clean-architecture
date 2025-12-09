# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## AI 응답 요구사항

- **항상 한국어로 응답**
- **코드 예시는 Kotlin 우선** (Java보다 Kotlin 코드를 우선)
- GitHub 작업 시 `gh auth status`로 `rkaehdaos` 계정인지 확인 후 진행

## 개발 명령어

### 빌드 및 테스트
```bash
./gradlew build           # 테스트 포함 전체 빌드
./gradlew test            # 모든 테스트 실행
./gradlew bootRun         # Spring Boot 애플리케이션 실행
./gradlew bootJar         # 실행 가능한 JAR 생성
```

### 단일 테스트 실행
```bash
./gradlew test --tests "클래스명"           # 특정 테스트 클래스
./gradlew test --tests "클래스명.메서드명"   # 특정 테스트 메서드
```

### 네이티브 이미지 (GraalVM)
```bash
./gradlew nativeCompile   # 네이티브 이미지 컴파일
./gradlew nativeRun       # 네이티브 실행 파일 실행
./gradlew nativeTest      # 네이티브 바이너리로 테스트 실행
```

## 프로젝트 아키텍처

헥사고날 아키텍처 기반 은행 계좌 송금 시스템 (BuckPal)

### 핵심 구조
```
src/main/java/dev/haja/buckpal/account/
├── domain/           # 비즈니스 엔티티 (Account, Money, Activity, ActivityWindow)
├── application/
│   ├── port/in/     # 인커밍 포트 (유스케이스 인터페이스)
│   ├── port/out/    # 아웃고잉 포트 (리포지토리 인터페이스)
│   └── service/     # 애플리케이션 서비스 (유스케이스 구현)
└── adapter/
    ├── in/web/      # REST 컨트롤러 (인커밍 어댑터)
    └── out/persistence/  # JPA 리포지토리 (아웃고잉 어댑터)
```

### 주요 아키텍처 원칙
- **도메인 계층 독립성**: domain 패키지는 외부 의존성 없음
- **의존성 방향**: adapter → application → domain (안쪽으로만 의존)
- **ArchUnit 테스트**: `DependencyRuleTests.java`에서 헥사고날 아키텍처 규칙 강제

### 도메인 모델
- **Account**: 계좌 집합체 루트 (잔액 계산, 출금 가능 여부 판단)
- **Money**: 금액 값 객체 (불변)
- **Activity**: 송금 활동 엔티티
- **ActivityWindow**: 특정 기간의 활동 관리

## 기술 스택

- **JDK**: `gradle.properties`의 `javaVersion` 참조
- **Kotlin**: `gradle.properties`의 `kotlinVersion` 참조 (마이그레이션 진행 중)
- **Spring Boot**: `gradle.properties`의 `springBootVersion` 참조
- **영속성**: Spring Data JPA + H2 (개발/테스트) / PostgreSQL (운영)
- **테스트**: JUnit 5, Kotest, MockK, ArchUnit
- **매핑**: MapStruct + Lombok (Kotlin 마이그레이션 후 Lombok 제거 예정)

## 테스트 표준

### 명명 규칙
- `@DisplayName`에 한국어 설명 사용
- BDD 스타일: `given...when...then...`
- BDDMockito 사용: `given().willReturn()` / `then().should()`

### 예시
```kotlin
@Test
@DisplayName("계좌 이체가 성공적으로 처리되는 경우")
fun givenValidAccounts_whenTransferMoney_thenTransactionSucceeds() {
    // given, when, then
}
```

## Java → Kotlin 마이그레이션

우선순위:
1. 값 객체 (Money, AccountId) → Kotlin data class
2. 도메인 엔티티 (Account, Activity) → Kotlin class
3. 애플리케이션 서비스 → 함수형 프로그래밍 활용
