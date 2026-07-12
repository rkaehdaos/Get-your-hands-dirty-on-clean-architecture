# Plan 2 — ArchUnit 사각지대 수정 + 설정 의존 정리

> **근거**: [아키텍처 평가](../reviews/2026-07-12-java-architecture-review.md) §3(application 계층의 인프라 설정 의존, ArchUnit 사각지대), §9-1(기본값 출처 이원화)
> **우선순위**: 2 (아키텍처 테스트가 거짓 안심을 주는 상태)
> **실행 순서**: Plan 1 다음

---

## 1. 배경과 문제

### 1-1. application 계층이 Spring Boot 설정 클래스에 직접 의존

`SendMoneyService.java:27`이 루트 패키지의 `BuckPalConfigurationProperties`(`@ConfigurationProperties`)를 직접 주입받는다. 같은 클래스가 `MoneyTransferProperties`(유스케이스별 설정 — 올바른 패턴)도 쓰고 있어 **한 클래스 안에 두 방식이 공존**한다. 헥사고날 원칙상 애플리케이션 계층은 프레임워크 설정 메커니즘을 몰라야 한다.

### 1-2. ArchUnit이 이 위반을 못 잡는다 (사각지대)

- `DependencyRuleTests.java:43`의 `withConfiguration("configuration")`은 `dev.haja.buckpal.account.configuration`이라는 **존재하지 않는 패키지**를 가리킨다.
- `HexagonalArchitecture.check()`(`HexagonalArchitecture.java:50-59`)의 configuration 관련 규칙 2개(`adapters.doesNotDependOn`, `applicationLayer.doesNotDependOn`)가 빈 패키지를 대상으로 **공허하게(vacuously) 통과**한다.
- 실제 설정 클래스(`BuckPalConfiguration`, `BuckPalConfigurationProperties`)는 루트 패키지 `dev.haja.buckpal`에 있어 DSL 검사 범위(`account` 하위) 밖이다.
- `check()`는 adapters·applicationLayer에는 `doesNotContainEmptyPackages()`를 요구하면서 configuration 패키지에는 존재 검증이 없다 — 오설정이 조용히 지나가는 근본 원인.

### 1-3. 설정 검증 시점 오류 + 기본값 이원화

- `SendMoneyService.java:33-36` — `historyLookbackDays > 0` 검증이 **요청마다** 실행된다. 설정 오류는 부팅 시점에 fail-fast 해야 한다.
- `MoneyTransferProperties.java:12`의 필드 기본값 `Money.of(1_000_000L)`은 `BuckPalConfiguration`이 항상 생성자로 덮어써 **죽은 값**이다. 실제 기본값은 `BuckPalConfigurationProperties` record의 `Long.MAX_VALUE`. 기본값 출처가 두 군데라 혼란.

## 2. 목표 / 비목표

### 목표

- `SendMoneyService`에서 `BuckPalConfigurationProperties` 의존 완전 제거
- 설정 클래스를 전용 패키지 `dev.haja.buckpal.configuration`으로 이동
- ArchUnit DSL이 빈/오설정 configuration 패키지에서 **시끄럽게 실패**하도록 수정
- application·domain → configuration 의존 금지 규칙 추가
- 설정 검증을 부팅 시점으로 이동, 기본값 출처 단일화

### 비목표 (Non-Goals)

- `@Transactional`·`@Component` 등 Spring 애너테이션의 application 계층 제거 (책의 pragmatic 노선 유지)
- Kotlin ArchUnit 테스트(`src/test/kotlin`) 수정 — Java 스코프만
- 락 관련 변경 (Plan 4)

## 3. 설계 결정

### 3-1. `historyLookbackDays`의 이동 위치

**선택**: `MoneyTransferProperties`에 필드 추가 (불변 record로 전환).

`historyLookbackDays`는 "송금 시 어느 기간의 활동을 로드할지"이므로 송금 유스케이스의 속성이 맞다. 별도 `AccountQueryProperties`를 만드는 대안은 클래스 수만 늘려 기각.

### 3-2. 설정 클래스 패키지 이동

**선택**: `dev.haja.buckpal.configuration` 패키지 신설, `BuckPalConfiguration`·`BuckPalConfigurationProperties` 이동.

루트 패키지에 두면 ArchUnit 패키지 매칭(`..` 패턴)이 하위 패키지 전체와 겹쳐 규칙을 쓸 수 없다. 전용 패키지로 격리해야 "여기에 의존하지 마라"를 표현할 수 있다.
`BuckpalApplication`(루트)의 `@SpringBootApplication` 컴포넌트 스캔은 하위 패키지를 포함하므로 이동해도 빈 등록에 영향 없다.

### 3-3. DSL 수정 방식

**선택**: 두 가지를 모두 적용.

1. `HexagonalArchitecture.check()`에 `denyEmptyPackage(configurationPackage)` 추가 — 존재하지 않는 패키지를 지정하면 테스트 실패. 공허한 통과의 재발 방지.
2. DSL의 `withConfiguration`은 basePackage(`account`) 상대 경로만 받으므로, **절대 패키지를 받는 변형** `withConfiguration(String pkg, boolean absolute)` 대신 명시적 별도 규칙을 `DependencyRuleTests`에 추가 (DSL 시그니처 변경 최소화):

```java
@Test
@DisplayName("애플리케이션·도메인 계층은 설정 패키지에 의존해서는 안된다")
void applicationAndDomainDoNotDependOnConfiguration() {
    noClasses().that()
            .resideInAnyPackage("..account.application..", "..account.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("dev.haja.buckpal.configuration..")
            .check(new ClassFileImporter().importPackages("dev.haja.buckpal"));
}
```

`withConfiguration("configuration")` 호출은 **제거**한다(존재하지 않는 account.configuration을 가리키는 현재 호출은 삭제가 정직). `HexagonalArchitecture.check()`는 `configurationPackage != null`일 때만 관련 규칙 + 비어있지 않음 검증을 수행하도록 수정.

## 4. 상세 변경 내역

### 4-1. `MoneyTransferProperties` — 불변 record화 + 필드 추가

**파일**: `account/application/service/MoneyTransferProperties.java`

```java
// Before: @Data @AllArgsConstructor @NoArgsConstructor class, 죽은 기본값 1_000_000
// After
package dev.haja.buckpal.account.application.service;

import dev.haja.buckpal.account.domain.Money;

/**
 * 송금 유스케이스 설정. 기본값·검증은 부팅 시점에
 * {@code BuckPalConfigurationProperties}가 책임진다.
 */
public record MoneyTransferProperties(
        Money maximumTransferThreshold,
        int historyLookbackDays) {
}
```

Lombok 의존 3개(@Data/@AllArgsConstructor/@NoArgsConstructor) 제거. 호출부는 `getMaximumTransferThreshold()` → `maximumTransferThreshold()`로 변경.

### 4-2. `SendMoneyService` — 설정 의존 정리

- 필드 `buckPalConfigurationProperties` 삭제 (`SendMoneyService.java:27`)
- 요청별 검증 블록 삭제 (`SendMoneyService.java:33-36`)
- `moneyTransferProperties.historyLookbackDays()` 사용:

```java
LocalDateTime baselineDate = LocalDateTime.now()
        .minusDays(moneyTransferProperties.historyLookbackDays());
```

import에서 `dev.haja.buckpal.BuckPalConfigurationProperties` 제거 — **이 커밋으로 아키텍처 위반 해소**.

### 4-3. 설정 클래스 패키지 이동 + 부팅 시점 검증

**이동**: `BuckPalConfiguration.java`, `BuckPalConfigurationProperties.java` → `src/main/java/dev/haja/buckpal/configuration/`

`BuckPalConfigurationProperties` 컴팩트 생성자에 검증 추가:

```java
public record BuckPalConfigurationProperties(Long transferThreshold, Account account) {
    public BuckPalConfigurationProperties {
        if (transferThreshold == null) transferThreshold = Long.MAX_VALUE;
        if (account == null) account = new Account(null);
    }
    public record Account(Integer historyLookbackDays) {
        public Account {
            if (historyLookbackDays == null) historyLookbackDays = 10;
            if (historyLookbackDays <= 0)
                throw new IllegalArgumentException(
                    "buckpal.account.history-lookback-days must be positive, but was: "
                    + historyLookbackDays);
        }
    }
}
```

record 접근자와 중복인 수동 `getTransferThreshold()`/`getAccount()`/`getHistoryLookbackDays()`는 제거하고 호출부를 record 접근자로 통일 (§9-2).

`BuckPalConfiguration.moneyTransferProperties()` 빈 팩토리 수정:

```java
@Bean
public MoneyTransferProperties moneyTransferProperties(BuckPalConfigurationProperties props) {
    return new MoneyTransferProperties(
            Money.of(props.transferThreshold()),
            props.account().historyLookbackDays());
}
```

### 4-4. ArchUnit 테스트 수정

**`HexagonalArchitecture.java`** (`check()`, 50-59행):

```java
public void check(JavaClasses classes) {
    this.adapters.doesNotContainEmptyPackages();
    this.adapters.dontDependOnEachOther(classes);
    this.applicationLayer.doesNotContainEmptyPackages();
    this.applicationLayer.doesNotDependOn(this.adapters.getBasePackage(), classes);
    this.applicationLayer.incomingAndOutgoingPortsDoNotDependOnEachOther(classes);
    this.domainDoesNotDependOnAdapters(classes);
    if (this.configurationPackage != null) {
        denyEmptyPackage(this.configurationPackage);          // ← 오설정 시 즉시 실패
        this.adapters.doesNotDependOn(this.configurationPackage, classes);
        this.applicationLayer.doesNotDependOn(this.configurationPackage, classes);
    }
}
```

**`DependencyRuleTests.java`**:

- `withConfiguration("configuration")` 호출 삭제 (43행)
- §3-3의 `applicationAndDomainDoNotDependOnConfiguration` 테스트 추가
- 추가 방어 규칙 (application 계층의 Boot 설정 메커니즘 의존 금지):

```java
@Test
@DisplayName("애플리케이션 계층은 Spring Boot 설정 메커니즘에 의존해서는 안된다")
void applicationDoesNotDependOnBootConfigProperties() {
    noClasses().that().resideInAPackage("..account.application..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.springframework.boot..")
            .check(new ClassFileImporter().importPackages("dev.haja.buckpal"));
}
```

### 4-5. 영향받는 호출부 정리

| 파일 | 변경 |
|---|---|
| `SendMoneyService` | §4-2 |
| `SendMoneyServiceTest` | 생성자 5개 → 4개 인자. `buckPalConfigurationProperties()` 헬퍼 삭제, `moneyTransferProperties()`가 `new MoneyTransferProperties(Money.of(Long.MAX_VALUE), 10)` 반환. `createInvalidBuckPalConfiguration`/`createBuckPalConfigurationWithCustomDays` **중복 헬퍼 삭제** (§11). "historyLookbackDays가 0 이하" 테스트는 서비스 테스트에서 **설정 record 테스트로 이동** (신규 `BuckPalConfigurationPropertiesTest`) |
| `BuckpalApplicationTests` 등 | import 경로만 조정 |

## 5. 테스트 계획

| 테스트 | 변경/신규 | 내용 |
|---|---|---|
| `DependencyRuleTests` | 수정+신규 | §4-4의 규칙 2개 추가. 기존 테스트 통과 유지 |
| **신규** `BuckPalConfigurationPropertiesTest` | 신규 | `historyLookbackDays <= 0`이면 생성 시점에 `IllegalArgumentException`; null이면 기본값 10; `transferThreshold` null이면 `Long.MAX_VALUE`. 한국어 `@DisplayName` |
| `SendMoneyServiceTest` | 수정 | 생성자 인자 축소, 요청별 검증 테스트(`givenInvalidHistoryLookbackDays_...`) 삭제(부팅 검증으로 대체됨) |
| 회귀 검증 | — | **일부러 `withConfiguration("없는패키지")`를 지정해 테스트가 실패하는지 확인** 후 되돌리기 (사각지대 수정 검증) |

## 6. 작업 순서와 커밋 분할

1. **커밋 1 — 설정 재배치**: 패키지 이동 + record 정리 + 부팅 검증 + `BuckPalConfigurationPropertiesTest`
2. **커밋 2 — 서비스 의존 제거**: `MoneyTransferProperties` record화, `SendMoneyService`·테스트 수정
3. **커밋 3 — ArchUnit 강화**: DSL `check()` 수정 + `DependencyRuleTests` 규칙 추가. 이 커밋에서 커밋 2 이전 코드로는 테스트가 실패했을 것임을 커밋 메시지에 명시(규칙이 실제로 물었는지 근거)

## 7. 리스크와 롤백

- 패키지 이동은 기계적이지만 import 누락 시 컴파일 에러로 즉시 드러남 — 리스크 낮음
- 부팅 검증 추가로 **잘못된 설정의 기존 환경은 기동 실패**하게 된다 — 의도된 fail-fast이나, `application-prod.yml` 등 프로파일별 설정값 사전 확인 필요 (`application.yml`의 `history-lookback-days: 10`은 유효)
- `SendMoneyServiceTest`의 시그니처 변경이 Plan 1의 수정과 겹침 — 순서 준수(Plan 1 먼저)로 회피
- 롤백: 커밋 단위 revert

## 8. 완료 기준 (DoD)

- [ ] `./gradlew check` 통과
- [ ] `grep -r "BuckPalConfigurationProperties" src/main/java/dev/haja/buckpal/account/` 결과 0건
- [ ] `DependencyRuleTests`에 configuration 의존 금지 규칙 존재, 규칙이 위반 코드에서 실제로 실패함을 확인한 기록
- [ ] `MoneyTransferProperties`가 불변이며 기본값 출처는 `BuckPalConfigurationProperties` 한 곳
- [ ] 부팅 시 잘못된 `history-lookback-days` 값으로 기동 실패 (수동 확인)

## 9. 다른 Plan과의 의존 관계

- **선행**: Plan 1 (같은 `SendMoneyService`·`SendMoneyServiceTest`를 수정 — 충돌 최소화 위해 순차)
- **후행**: Plan 5, Plan 4
- `.claude/CLAUDE.md`의 아키텍처 문서에 설정 패키지 이동을 반영해야 함 (구조도에 `configuration/` 추가)
