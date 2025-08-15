# 테스트 규칙 (Testing Standards)

BuckPal 프로젝트의 테스트 전략과 구현 방법을 정의합니다.

## 1. 테스트 전략

### 1.1 테스트 피라미드

```
    E2E 테스트 (시스템 테스트)
   ───────────────────────
  통합 테스트 (어댑터 테스트)
 ─────────────────────────────
단위 테스트 (도메인 & 애플리케이션)
```

**구현 현황**:
- **단위 테스트**: `AccountTest`, `ActivityWindowTest`, `SendMoneyServiceTest`
- **통합 테스트**: `AccountPersistenceAdapterTest`, `SendMoneyControllerTest`
- **시스템 테스트**: `SendMoneySystemTest`

### 1.2 테스트 범위별 전략

#### 단위 테스트
**목적**: 비즈니스 로직 검증
**위치**: `src/test/java/dev/haja/buckpal/account/domain/`

```java
@Test
@DisplayName("충분한 잔액이 있을 때 출금이 성공한다")
void withdrawalSucceedsTest() {
    // given
    AccountId accountId = new AccountId(1L);
    Account account = defaultAccount()
            .withAccountId(accountId)
            .withBaselineBalance(Money.of(555L))
            .withActivityWindow(new ActivityWindow(/*...*/))
            .build();
    
    // when
    boolean success = account.withdraw(Money.of(555L), new AccountId(99L));
    
    // then
    assertThat(success).isTrue();
    assertThat(account.getActivityWindow().getActivities()).hasSize(3);
    assertThat(account.calculateBalance()).isEqualTo(Money.of(1000L));
}
```

#### 통합 테스트
**목적**: 어댑터와 외부 시스템 연동 검증
**위치**: `src/test/java/dev/haja/buckpal/account/adapter/`

```java
@SpringBootTest
@DataJpaTest
@Import({AccountPersistenceAdapter.class, AccountMapper.class})
class AccountPersistenceAdapterTest {
    
    @Test
    @DisplayName("계좌 정보를 성공적으로 로드한다")
    @Sql("AccountPersistenceAdapterTest.sql")
    void loadsAccount() {
        // given
        AccountId accountId = new AccountId(1L);
        
        // when
        Account account = adapter.loadAccount(accountId, LocalDateTime.of(2018, 8, 10, 0, 0));
        
        // then
        assertThat(account.getActivityWindow().getActivities()).hasSize(2);
        assertThat(account.calculateBalance()).isEqualTo(Money.of(500L));
    }
}
```

#### 시스템 테스트
**목적**: 전체 유스케이스 End-to-End 검증
**위치**: `src/test/java/dev/haja/buckpal/SendMoneySystemTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SendMoneySystemTest {
    
    @Test
    @DisplayName("sendMoney: 요청 생성 -> App에 보내고 응답상태와 계좌의 새로운 잔고를 검증")
    @Sql("SendMoneySystemTest.sql")
    void sendMoney() {
        // given
        Money initialSourceBalance = sourceAccount().calculateBalance();
        Money initialTargetBalance = targetAccount().calculateBalance();
        
        // when
        ResponseEntity<SendMoneyReqDto> responseEntity = whenSendMoney(sourceAccountId(), targetAccountId());
        
        // then
        then(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(sourceAccount().calculateBalance())
                .isEqualTo(initialSourceBalance.minus(transferredAmount()));
        then(targetAccount().calculateBalance())
                .isEqualTo(initialTargetBalance.plus(transferredAmount()));
    }
}
```

## 2. 테스트 작성 규칙

### 2.1 AAA 패턴 (Arrange-Act-Assert)

```java
@Test
@DisplayName("계좌 이체가 성공적으로 처리되는 경우")
void givenValidAccounts_whenTransferMoney_thenTransactionSucceeds() {
    // given (Arrange) - 테스트 데이터 준비
    AccountId sourceAccountId = new AccountId(41L);
    Account sourceAccount = givenAnAccountWithId(sourceAccountId);
    Account targetAccount = givenAnAccountWithId(new AccountId(42L));
    givenWithdrawalWillSucceed(sourceAccount);
    givenDepositWillSucceed(targetAccount);
    
    // when (Act) - 테스트 대상 실행
    SendMoneyCommand command = new SendMoneyCommand(sourceAccountId, targetAccountId, Money.of(500L));
    boolean success = sendMoneyService.sendMoney(command);
    
    // then (Assert) - 결과 검증
    assertThat(success).isTrue();
    then(accountLock).should().lockAccount(eq(sourceAccountId));
    then(sourceAccount).should().withdraw(eq(Money.of(500L)), eq(targetAccountId));
}
```

### 2.2 BDD 스타일 네이밍

#### 메서드명 패턴
```java
// 패턴: given{상황}_when{행동}_then{결과}
@Test
void givenWithdrawalFails_thenOnlySourceAccountIsLockedAndReleased() { }

@Test  
void givenInvalidHistoryLookbackDays_thenThrowsIllegalArgumentException() { }

@Test
void givenCustomHistoryLookbackDays_thenTransactionSucceeds() { }
```

#### DisplayName 사용
```java
// 한국어로 명확한 설명
@DisplayName("주어진 출금 실패 후 소스 계정만 잠기고 해제됨")
@DisplayName("historyLookbackDays가 0 이하일 때 IllegalArgumentException 발생")
@DisplayName("계좌 정보를 성공적으로 로드한다")
@DisplayName("sendMoney: 요청 생성 -> App에 보내고 응답상태와 계좌의 새로운 잔고를 검증")
```

### 2.3 테스트 데이터 준비

#### 헬퍼 메서드 활용
```java
// SendMoneyServiceTest.java:139-146
private Account givenAnAccountWithId(AccountId accountId) {
    Account account = Mockito.mock(Account.class);
    given(account.getId()).willReturn(Optional.of(accountId));
    given(loadAccountPort.loadAccount(eq(accountId), any(LocalDateTime.class)))
            .willReturn(account);
    return account;
}

// 비즈니스 시나리오별 헬퍼 메서드
private void givenWithdrawalWillSucceed(Account sourceAccount) {
    given(sourceAccount.withdraw(any(Money.class), any(AccountId.class)))
            .willReturn(true);
}

private void givenWithdrawalWillFail(Account account) {
    given(account.withdraw(any(Money.class), any(AccountId.class)))
            .willReturn(false);
}
```

#### 테스트 빌더 패턴
```java
// AccountTestData.java 패턴
public class AccountTestData {
    public static AccountTestDataBuilder defaultAccount() {
        return new AccountTestDataBuilder()
                .withAccountId(new AccountId(42L))
                .withBaselineBalance(Money.of(999L))
                .withActivityWindow(defaultActivityWindow());
    }
    
    public static AccountTestDataBuilder emptyAccount() {
        return new AccountTestDataBuilder()
                .withAccountId(new AccountId(7L))
                .withBaselineBalance(Money.of(0L))
                .withActivityWindow(emptyActivityWindow());
    }
}
```

## 3. Mock 사용 규칙

### 3.1 BDDMockito 스타일

```java
// given - 목 동작 정의
given(loadAccountPort.loadAccount(eq(sourceAccountId), any(LocalDateTime.class)))
        .willReturn(sourceAccount);

// when - 실제 코드 실행
boolean result = sendMoneyService.sendMoney(command);

// then - 목 호출 검증
then(updateAccountStatePort).should().updateActivities(sourceAccount);
then(updateAccountStatePort).should().updateActivities(targetAccount);
then(accountLock).should(times(0)).lockAccount(eq(targetAccountId));
```

### 3.2 ArgumentCaptor 활용

```java
// SendMoneyServiceTest.java:112-127
private void thenAccountsHaveBeenUpdated(AccountId... accountIds) {
    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    then(updateAccountStatePort).should(times(accountIds.length))
            .updateActivities(accountCaptor.capture());

    List<AccountId> updatedAccountIds = accountCaptor.getAllValues()
            .stream()
            .map(Account::getId)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

    for (AccountId accountId : accountIds) {
        assertThat(updatedAccountIds).contains(accountId);
    }
}
```

### 3.3 Mock 생성 패턴

```java
// 필드 레벨 목 생성
private final LoadAccountPort loadAccountPort = Mockito.mock(LoadAccountPort.class);
private final AccountLock accountLock = Mockito.mock(AccountLock.class);
private final UpdateAccountStatePort updateAccountStatePort = Mockito.mock(UpdateAccountStatePort.class);

// 생성자에서 목 주입
private final SendMoneyService sendMoneyService = new SendMoneyService(
        loadAccountPort,
        accountLock, 
        updateAccountStatePort,
        moneyTransferProperties(),
        buckPalConfigurationProperties());
```

## 4. 예외 테스트

### 4.1 예외 발생 검증

```java
@Test
@DisplayName("historyLookbackDays가 0 이하일 때 IllegalArgumentException 발생")
void givenInvalidHistoryLookbackDays_thenThrowsIllegalArgumentException() {
    // given
    SendMoneyService serviceWithInvalidConfig = new SendMoneyService(/*...*/);
    SendMoneyCommand command = new SendMoneyCommand(/*...*/);
    
    // when & then
    assertThatThrownBy(() -> serviceWithInvalidConfig.sendMoney(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("historyLookbackDays must be positive");
}
```

### 4.2 경계값 테스트

```java
// 유효하지 않은 설정값 테스트
private BuckPalConfigurationProperties createInvalidBuckPalConfiguration(int invalidDays) {
    return new BuckPalConfigurationProperties(
            Long.MAX_VALUE,
            new BuckPalConfigurationProperties.Account(invalidDays)  // -1, 0 등 경계값
    );
}

// 커스텀 설정값 테스트
private BuckPalConfigurationProperties createBuckPalConfigurationWithCustomDays(int days) {
    return new BuckPalConfigurationProperties(
            Long.MAX_VALUE,
            new BuckPalConfigurationProperties.Account(days)  // 5, 30 등 다양한 값
    );
}
```

## 5. 데이터베이스 테스트

### 5.1 @Sql 어노테이션 활용

```java
@Test
@DisplayName("계좌 정보를 성공적으로 로드한다")
@Sql("AccountPersistenceAdapterTest.sql")
void loadsAccount() {
    // 테스트 실행 전 SQL 스크립트 자동 실행
    Account account = adapter.loadAccount(accountId, LocalDateTime.of(2018, 8, 10, 0, 0));
    assertThat(account.calculateBalance()).isEqualTo(Money.of(500L));
}

@Test
@DisplayName("sendMoney: 요청 생성 -> App에 보내고 응답상태와 계좌의 새로운 잔고를 검증")
@Sql("SendMoneySystemTest.sql")
void sendMoney() {
    // 시스템 테스트용 초기 데이터 설정
}
```

### 5.2 테스트 SQL 스크립트

```sql
-- src/test/resources/dev/haja/buckpal/SendMoneySystemTest.sql
insert into account (id) values (1);
insert into account (id) values (2);

insert into activity (id, timestamp, owner_account_id, source_account_id, target_account_id, amount)
values (1, '2018-08-08 08:00:00.0', 1, 1, 2, 500);

insert into activity (id, timestamp, owner_account_id, source_account_id, target_account_id, amount)
values (2, '2018-08-08 08:00:00.0', 2, 1, 2, 500);

insert into activity (id, timestamp, owner_account_id, source_account_id, target_account_id, amount)
values (3, '2018-08-09 10:00:00.0', 1, 2, 1, 1000);

insert into activity (id, timestamp, owner_account_id, source_account_id, target_account_id, amount)
values (4, '2018-08-09 10:00:00.0', 2, 2, 1, 1000);
```

## 6. 아키텍처 테스트

### 6.1 ArchUnit 활용

**위치**: `src/test/java/dev/haja/buckpal/DependencyRuleTests.java`

```java
@Test
@DisplayName("도메인 계층은 애플리케이션 계층에 의존해서는 안된다")
void domainLayerDoesNotDependOnApplicationLayer() {
    noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..")
            .check(new ClassFileImporter().importPackages("dev.haja.buckpal"));
}

@Test
@DisplayName("헥사고날 아키텍처 준수")
void validateRegistrationContextArchitecture() {
    HexagonalArchitecture.basePackage("dev.haja.buckpal.account")
            .withDomainLayer("domain")
            .withAdaptersLayer("adapter")
                .incoming("in.web")
                .outgoing("out.persistence")
            .and()
            .withApplicationLayer("application")
                .services("service")
                .incomingPorts("port.in")
                .outgoingPorts("port.out")
            .and()
            .withConfiguration("configuration")
            .check(new ClassFileImporter().importPackages("dev.haja.buckpal.."));
}
```

### 6.2 커스텀 아키텍처 규칙

```java
// HexagonalArchitecture DSL 활용
public class HexagonalArchitecture extends ArchitectureElement {
    
    public void check(JavaClasses classes) {
        this.adapters.doesNotContainEmptyPackages();
        this.adapters.dontDependOnEachOther(classes);
        this.adapters.doesNotDependOn(this.configurationPackage, classes);
        this.applicationLayer.doesNotContainEmptyPackages();
        this.applicationLayer.doesNotDependOn(this.adapters.getBasePackage(), classes);
        this.domainDoesNotDependOnAdapters(classes);
    }
}
```

## 7. 테스트 실행 전략

### 7.1 Gradle 테스트 설정

```kotlin
// build.gradle.kts
tasks.named<Test>("test") {
    useJUnitPlatform()
    maxParallelForks = Runtime.getRuntime().availableProcessors()  // 병렬 실행
    testLogging {
        events("passed", "skipped", "failed")
    }
}
```

### 7.2 테스트 명령어

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "AccountTest"

# 특정 테스트 메서드 실행  
./gradlew test --tests "AccountTest.withdrawalSucceedsTest"

# 통합 테스트만 실행
./gradlew test --tests "*IntegrationTest"

# 시스템 테스트만 실행
./gradlew test --tests "*SystemTest"
```

## 8. 테스트 모범 사례

### 8.1 해야 할 것 ✅

```java
// 명확한 테스트 이름과 설명
@DisplayName("충분한 잔액이 있을 때 출금이 성공한다")
void withdrawalSucceedsTest() { }

// 각 테스트는 독립적이어야 함
@Test
void eachTestShouldBeIndependent() {
    // 다른 테스트에 의존하지 않음
}

// BDD 스타일의 명확한 구조
@Test
void givenPrecondition_whenAction_thenResult() {
    // given
    // when  
    // then
}

// 의미 있는 어설션 메시지
assertThat(account.calculateBalance())
        .as("출금 후 계좌 잔액 확인")
        .isEqualTo(Money.of(1000L));
```

### 8.2 피해야 할 것 ❌

```java
// 여러 개의 관심사를 한 테스트에서 검증
@Test
void testEverything() {  // ❌ 너무 많은 것을 테스트
    // 출금, 입금, 잔액 계산, 락, 업데이트 모두 테스트
}

// 구현 세부사항에 의존하는 테스트
@Test  
void testImplementationDetails() {  // ❌ 내부 구현에 의존
    verify(somePrivateMethod, times(1));  // private 메서드 검증
}

// 테스트 간 의존성
@Test
@Order(1)
void createAccount() { }  // ❌ 다른 테스트에 영향

@Test  
@Order(2) 
void withdrawFromAccount() { }  // ❌ 이전 테스트에 의존
```

## 9. 커버리지 목표

### 9.1 계층별 커버리지 목표

- **도메인 계층**: 95% 이상 (핵심 비즈니스 로직)
- **애플리케이션 계층**: 90% 이상 (유스케이스 로직) 
- **어댑터 계층**: 80% 이상 (외부 연동 로직)
- **전체 프로젝트**: 85% 이상

### 9.2 커버리지 확인

```bash
# 테스트 커버리지 리포트 생성
./gradlew jacocoTestReport

# 커버리지 검증
./gradlew jacocoTestCoverageVerification
```

이러한 테스트 전략과 규칙을 통해 BuckPal 프로젝트는 높은 품질의 코드와 안정적인 비즈니스 로직을 보장합니다.