# 디자인 패턴 (Design Patterns)

BuckPal 프로젝트에서 사용하는 디자인 패턴과 구현 방식을 정의합니다.

## 1. 아키텍처 패턴

### 1.1 헥사고날 아키텍처 (포트 & 어댑터)

**위치**: 전체 프로젝트 구조
**목적**: 비즈니스 로직과 외부 세계의 분리

```java
// 포트 정의 (인터페이스)
public interface SendMoneyUseCase {  // 인커밍 포트
    boolean sendMoney(SendMoneyCommand command);
}

public interface LoadAccountPort {   // 아웃고잉 포트
    Account loadAccount(AccountId accountId, LocalDateTime baselineDate);
}

// 어댑터 구현
@Component
public class SendMoneyService implements SendMoneyUseCase {  // 애플리케이션 서비스
    private final LoadAccountPort loadAccountPort;          // 포트 의존성
    
    @Override
    public boolean sendMoney(SendMoneyCommand command) {
        // 비즈니스 로직 구현
    }
}

@PersistenceAdapter
public class AccountPersistenceAdapter implements LoadAccountPort {  // 아웃고잉 어댑터
    // 영속성 구현
}
```

### 1.2 의존성 역전 원칙 (DIP)

**구현 위치**: `dev.haja.buckpal.account.application.service.SendMoneyService:23-27`

```java
@Component
@RequiredArgsConstructor
public class SendMoneyService implements SendMoneyUseCase {
    // 구체적인 구현이 아닌 인터페이스에 의존
    private final LoadAccountPort loadAccountPort;           // 포트 인터페이스
    private final AccountLock accountLock;                   // 추상화된 락
    private final UpdateAccountStatePort updateAccountStatePort;
    
    // 외부 설정 값도 추상화를 통해 주입
    private final MoneyTransferProperties moneyTransferProperties;
}
```

## 2. 도메인 패턴

### 2.1 집합체 (Aggregate)

**구현 위치**: `dev.haja.buckpal.account.domain.Account`
**목적**: 도메인 객체의 일관성 보장

```java
@Getter @ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Account {
    private final AccountId id;                    // 집합체 식별자
    private final Money baselineBalance;
    private ActivityWindow activityWindow;         // 관리되는 엔티티

    // 팩토리 메서드로 생성 제어
    public static Account withId(AccountId accountId, Money baselineBalance, ActivityWindow activityWindow) {
        return new Account(accountId, baselineBalance, activityWindow);
    }

    // 비즈니스 규칙을 도메인 안에 캡슐화
    public boolean withdraw(Money money, AccountId targetAccountId) {
        if (!mayWithdraw(money)) return false;  // 비즈니스 규칙 검증
        
        Activity withdrawal = new Activity(/*...*/);
        this.activityWindow.addActivity(withdrawal);
        return true;
    }
    
    private boolean mayWithdraw(Money money) {
        return Money.add(this.calculateBalance(), money.negate()).isPositiveOrZero();
    }
}
```

### 2.2 값 객체 (Value Object)

**구현 위치**: 
- `dev.haja.buckpal.account.domain.Money`
- `dev.haja.buckpal.account.domain.Account.AccountId:129`

```java
// Lombok을 사용한 불변 값 객체
@Value
public static class AccountId {
    Long value;
}

// 값 객체의 비즈니스 로직 캡슐화
public class Money {
    private final BigInteger amount;
    
    public Money add(Money money) {
        return new Money(this.amount.add(money.amount));
    }
    
    public Money negate() {
        return new Money(this.amount.negate());
    }
    
    public boolean isPositiveOrZero() {
        return this.amount.compareTo(BigInteger.ZERO) >= 0;
    }
}
```

### 2.3 도메인 서비스

**구현 위치**: `dev.haja.buckpal.account.domain.ActivityWindow`
**목적**: 여러 도메인 객체에 걸친 로직

```java
public class ActivityWindow {
    private List<Activity> activities;
    
    // 도메인 로직: 특정 계좌의 잔액 계산
    public Money calculateBalance(AccountId accountId) {
        Money depositBalance = activities.stream()
            .filter(a -> a.getTargetAccountId().equals(accountId))
            .map(Activity::getMoney)
            .reduce(Money.ZERO, Money::add);
            
        Money withdrawalBalance = activities.stream()
            .filter(a -> a.getSourceAccountId().equals(accountId))
            .map(Activity::getMoney)
            .reduce(Money.ZERO, Money::add);
            
        return Money.add(depositBalance, withdrawalBalance.negate());
    }
}
```

## 3. 애플리케이션 패턴

### 3.1 유스케이스 패턴

**구현 위치**: `dev.haja.buckpal.account.application.service.SendMoneyService`
**목적**: 비즈니스 유스케이스의 명확한 구현

```java
@Component
@Transactional
public class SendMoneyService implements SendMoneyUseCase {
    
    @Override
    public boolean sendMoney(SendMoneyCommand command) {
        // 1. 입력 검증
        checkThreshold(command);
        
        // 2. 도메인 객체 로드
        Account sourceAccount = loadAccount(command.getSourceAccountId(), baselineDate);
        Account targetAccount = loadAccount(command.getTargetAccountId(), baselineDate);
        
        // 3. 비즈니스 로직 실행
        return executeMoneyTransfer(command, sourceAccountId, sourceAccount, targetAccountId, targetAccount);
    }
    
    // 복잡한 유스케이스를 작은 단계로 분해
    private boolean executeMoneyTransfer(/*...*/) {
        if (!withdrawFromSourceAccount(/*...*/)) return false;
        if (!depositToTargetAccount(/*...*/)) return false;
        updateAccountStates(sourceAccount, targetAccount);
        releaseLock(sourceAccountId, targetAccountId);
        return true;
    }
}
```

### 3.2 명령 객체 패턴 (Command)

**구현 위치**: `dev.haja.buckpal.account.application.port.in.SendMoneyCommand`
**목적**: 입력 데이터의 유효성 검증

```java
@Getter
public class SendMoneyCommand extends SelfValidating<SendMoneyCommand> {
    @NotNull
    private final AccountId sourceAccountId;
    
    @NotNull
    private final AccountId targetAccountId;
    
    @NotNull
    private final Money money;
    
    public SendMoneyCommand(AccountId sourceAccountId, AccountId targetAccountId, Money money) {
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.money = money;
        this.validateSelf();  // 생성 시점에 검증
    }
}
```

### 3.3 템플릿 메서드 패턴

**구현 위치**: `dev.haja.buckpal.common.SelfValidating`
**목적**: 검증 로직의 표준화

```java
public abstract class SelfValidating<T> {
    private final Validator validator;
    
    public SelfValidating() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    protected void validateSelf() {
        Set<ConstraintViolation<T>> violations = validator.validate((T) this);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
```

## 4. 어댑터 패턴

### 4.1 웹 어댑터

**구현 위치**: `dev.haja.buckpal.account.adapter.in.web.SendMoneyController`
**목적**: HTTP 요청을 유스케이스로 변환

```java
@WebAdapter                           // 커스텀 어노테이션으로 역할 명시
@RestController
@RequiredArgsConstructor
@Validated
public class SendMoneyController {
    private final SendMoneyUseCase sendMoneyUseCase;
    
    @PostMapping("/accounts/send/{sourceAccountId}/{targetAccountId}/{amount}")
    public void sendMoney(
            @PathVariable("sourceAccountId") Long sourceAccountId,
            @PathVariable("targetAccountId") Long targetAccountId,
            @PathVariable("amount") Long amount) {
        
        // 웹 입력을 도메인 명령으로 변환
        SendMoneyCommand command = new SendMoneyCommand(
                new AccountId(sourceAccountId),
                new AccountId(targetAccountId),
                Money.of(amount));
        
        sendMoneyUseCase.sendMoney(command);
    }
}
```

### 4.2 영속성 어댑터

**구현 위치**: `dev.haja.buckpal.account.adapter.out.persistence.AccountPersistenceAdapter`
**목적**: 도메인 모델과 영속성 모델 간 변환

```java
@PersistenceAdapter                    // 커스텀 어노테이션
@RequiredArgsConstructor
public class AccountPersistenceAdapter implements LoadAccountPort, UpdateAccountStatePort {
    
    private final AccountRepository accountRepository;
    private final ActivityRepository activityRepository;
    private final AccountMapper accountMapper;       // MapStruct 매퍼
    
    @Override
    public Account loadAccount(AccountId accountId, LocalDateTime baselineDate) {
        AccountJpaEntity account = accountRepository.findById(accountId.getValue())
                .orElseThrow(EntityNotFoundException::new);
        
        List<ActivityJpaEntity> activities = activityRepository
                .findByOwnerSince(accountId.getValue(), baselineDate);
        
        // JPA 엔티티를 도메인 모델로 변환
        return accountMapper.mapToDomainEntity(account, activities, /*...*/);
    }
}
```

## 5. 매퍼 패턴

### 5.1 MapStruct 기반 매핑

**구현 위치**: `dev.haja.buckpal.account.adapter.out.persistence.AccountMapper`
**목적**: 계층 간 객체 변환

```java
@Mapper
interface AccountMapper {
    
    Account mapToDomainEntity(
            AccountJpaEntity account,
            List<ActivityJpaEntity> activities,
            Long withdrawalBalance,
            Long depositBalance);
    
    ActivityJpaEntity mapToJpaEntity(Activity activity);
    
    // 복잡한 매핑 로직
    @Mapping(target = "account", ignore = true)
    @Mapping(source = "activity.id", target = "id")
    ActivityJpaEntity mapToJpaEntity(Activity activity);
}
```

## 6. 에러 처리 패턴

### 6.1 도메인 예외

**구현 위치**: `dev.haja.buckpal.account.application.service.ThresholdExceededException`
**목적**: 비즈니스 규칙 위반 시 명확한 예외 처리

```java
public class ThresholdExceededException extends RuntimeException {
    
    public ThresholdExceededException(Money threshold, Money actual) {
        super(String.format("Maximum threshold of %s exceeded: was %s", threshold, actual));
    }
}

// 사용 예시: SendMoneyService.java:90-94
private void checkThreshold(SendMoneyCommand command) {
    if (command.getMoney().isGreaterThan(moneyTransferProperties.getMaximumTransferThreshold())) {
        throw new ThresholdExceededException(
                moneyTransferProperties.getMaximumTransferThreshold(), 
                command.getMoney());
    }
}
```

### 6.2 검증 실패 패턴

**구현 위치**: `dev.haja.buckpal.account.application.service.SendMoneyService:98-101`

```java
private AccountId getAccountId(Account account, String accountDescription) {
    return account.getId().orElseThrow(() ->
            new IllegalStateException(String.format("%s ID가 비어있습니다.", accountDescription)));
}
```

## 7. 동시성 패턴

### 7.1 계좌 락 패턴

**구현 위치**: `dev.haja.buckpal.account.application.service.SendMoneyService:82-87`
**목적**: 동시 수정 방지

```java
private boolean withdrawFromSourceAccount(SendMoneyCommand command, AccountId sourceAccountId, Account sourceAccount, AccountId targetAccountId) {
    accountLock.lockAccount(sourceAccountId);           // 락 획득
    if (!sourceAccount.withdraw(command.getMoney(), targetAccountId)) {
        accountLock.releaseAccount(sourceAccountId);    // 실패 시 락 해제
        return false;
    }
    return true;
}

// 트랜잭션 완료 후 락 해제
private void releaseLock(AccountId sourceAccountId, AccountId targetAccountId) {
    accountLock.releaseAccount(sourceAccountId);
    accountLock.releaseAccount(targetAccountId);
}
```

## 8. 테스트 패턴

### 8.1 테스트 빌더 패턴

**구현 위치**: `dev.haja.buckpal.common.AccountTestData`
**목적**: 테스트 데이터 생성의 일관성

```java
public class AccountTestData {
    
    public static AccountTestDataBuilder defaultAccount() {
        return new AccountTestDataBuilder()
                .withAccountId(new AccountId(42L))
                .withBaselineBalance(Money.of(999L))
                .withActivityWindow(defaultActivityWindow());
    }
    
    public static class AccountTestDataBuilder {
        private AccountId accountId;
        private Money baselineBalance;
        private ActivityWindow activityWindow;
        
        public AccountTestDataBuilder withAccountId(AccountId accountId) {
            this.accountId = accountId;
            return this;
        }
        
        public Account build() {
            return Account.withId(this.accountId, this.baselineBalance, this.activityWindow);
        }
    }
}
```

### 8.2 아키텍처 테스트 패턴

**구현 위치**: `dev.haja.buckpal.archunit.HexagonalArchitecture`
**목적**: 아키텍처 규칙 자동 검증

```java
public class HexagonalArchitecture extends ArchitectureElement {
    
    public static HexagonalArchitecture basePackage(String basePackage) {
        return new HexagonalArchitecture(basePackage);
    }
    
    public void check(JavaClasses classes) {
        this.adapters.doesNotContainEmptyPackages();
        this.adapters.dontDependOnEachOther(classes);
        this.applicationLayer.doesNotDependOn(this.adapters.getBasePackage(), classes);
        this.domainDoesNotDependOnAdapters(classes);
    }
}

// 사용 예시: DependencyRuleTests.java:23-43
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
    .check(importedClasses);
```

## 9. 피해야 할 안티패턴

### 9.1 빈약한 도메인 모델

```java
// ❌ 안티패턴: 로직이 없는 데이터 클래스
public class Account {
    private AccountId id;
    private Money balance;
    
    // getter/setter만 존재
}

public class AccountService {
    public void withdraw(Account account, Money amount) {
        // 도메인 로직이 서비스에 있음
        if (account.getBalance().isLessThan(amount)) {
            throw new InsufficientFundsException();
        }
        account.setBalance(account.getBalance().subtract(amount));
    }
}

// ✅ 올바른 패턴: 도메인 모델에 로직 캡슐화
public class Account {
    public boolean withdraw(Money money, AccountId targetAccountId) {
        if (!mayWithdraw(money)) return false;
        // 비즈니스 로직이 도메인 안에 있음
    }
}
```

### 9.2 계층 간 직접 의존성

```java
// ❌ 안티패턴: 어댑터 간 직접 의존
@RestController
public class SendMoneyController {
    @Autowired
    private AccountRepository accountRepository;  // 웹이 영속성에 직접 의존
}

// ✅ 올바른 패턴: 포트를 통한 의존성
@RestController
public class SendMoneyController {
    private final SendMoneyUseCase sendMoneyUseCase;  // 유스케이스 포트에만 의존
}
```

이러한 패턴들은 헥사고날 아키텍처의 원칙을 구현하고, 유지보수성과 테스트 용이성을 확보하기 위해 적용되었습니다.