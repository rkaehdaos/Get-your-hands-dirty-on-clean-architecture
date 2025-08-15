# 보안 체크리스트 (Security Guidelines)

BuckPal 프로젝트의 보안 정책과 구현 방법을 정의합니다.

## 1. 입력 검증 및 데이터 유효성 검사

### 1.1 명령 객체 검증

**구현 위치**: `dev.haja.buckpal.account.application.port.in.SendMoneyCommand`

```java
@Getter
@EqualsAndHashCode(callSuper = false)
public class SendMoneyCommand extends SelfValidating<SendMoneyCommand> {
    
    @NotNull private final AccountId sourceAccountId;    // Null 체크
    @NotNull private final AccountId targetAccountId;    // Null 체크
    @NotNull private final Money money;                  // Null 체크

    public SendMoneyCommand(AccountId sourceAccountId, AccountId targetAccountId, Money money) {
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.money = money;
        
        // 비즈니스 규칙 검증
        if (!money.isPositiveOrZero()) {
            throw new IllegalArgumentException("The money amount must be greater than or equal to zero");
        }
        
        validateSelf();  // Bean Validation 실행
    }
}
```

### 1.2 자체 검증 패턴

**구현 위치**: `dev.haja.buckpal.common.SelfValidating`

```java
public abstract class SelfValidating<T> {
    private final Validator validator;

    public SelfValidating() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            validator = validatorFactory.getValidator();
        }
    }

    protected void validateSelf() {
        Set<ConstraintViolation<T>> violations = validator.validate((T) this);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);  // 검증 실패 시 예외 발생
        }
    }
}
```

### 1.3 웹 계층 검증

**구현 위치**: `dev.haja.buckpal.account.adapter.in.web.SendMoneyController`

```java
@WebAdapter
@RestController
@RequiredArgsConstructor
@Validated  // 클래스 레벨 검증 활성화
public class SendMoneyController {
    
    @PostMapping("/accounts/send/{sourceAccountId}/{targetAccountId}/{amount}")
    public void sendMoney(
            @PathVariable("sourceAccountId") Long sourceAccountId,       // 경로 변수 검증
            @PathVariable("targetAccountId") Long targetAccountId,       // 경로 변수 검증
            @PathVariable("amount") Long amount) {                       // 경로 변수 검증
        
        // 입력값을 안전한 도메인 객체로 변환
        SendMoneyCommand command = new SendMoneyCommand(
                new AccountId(sourceAccountId),
                new AccountId(targetAccountId),
                Money.of(amount));
        
        sendMoneyUseCase.sendMoney(command);
    }
}
```

## 2. 비즈니스 규칙 보안

### 2.1 도메인 규칙 강제

**구현 위치**: `dev.haja.buckpal.account.domain.Account:80-106`

```java
public boolean withdraw(Money money, AccountId targetAccountId) {
    // 비즈니스 규칙을 도메인 엔티티 안에 캡슐화
    if (!mayWithdraw(money)) return false;  // 출금 가능 여부 검증
    
    Activity withdrawal = new Activity(
            this.id,
            this.id, 
            targetAccountId,
            LocalDateTime.now(),
            money);
    this.activityWindow.addActivity(withdrawal);
    return true;
}

private boolean mayWithdraw(Money money) {
    // 잔액 검증 로직 - 음수 잔액 방지
    return Money.add(this.calculateBalance(), money.negate()).isPositiveOrZero();
}
```

### 2.2 송금 한도 검증

**구현 위치**: `dev.haja.buckpal.account.application.service.SendMoneyService:90-94`

```java
private void checkThreshold(SendMoneyCommand command) {
    // 설정된 최대 송금 한도 검증
    if (command.getMoney().isGreaterThan(moneyTransferProperties.getMaximumTransferThreshold())) {
        throw new ThresholdExceededException(
                moneyTransferProperties.getMaximumTransferThreshold(), 
                command.getMoney());
    }
}
```

### 2.3 계좌 ID 유효성 검증

**구현 위치**: `dev.haja.buckpal.account.application.service.SendMoneyService:98-101`

```java
private AccountId getAccountId(Account account, String accountDescription) {
    return account.getId().orElseThrow(() ->
            new IllegalStateException(String.format("%s ID가 비어있습니다.", accountDescription)));
}
```

## 3. 동시성 및 무결성 보안

### 3.1 계좌 락 메커니즘

**구현 위치**: `dev.haja.buckpal.account.application.service.SendMoneyService:81-88`

```java
private boolean withdrawFromSourceAccount(SendMoneyCommand command, AccountId sourceAccountId, Account sourceAccount, AccountId targetAccountId) {
    accountLock.lockAccount(sourceAccountId);           // 동시 접근 방지
    if (!sourceAccount.withdraw(command.getMoney(), targetAccountId)) {
        accountLock.releaseAccount(sourceAccountId);    // 실패 시 즉시 락 해제
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

### 3.2 트랜잭션 경계 설정

**구현 위치**: `dev.haja.buckpal.account.application.service.SendMoneyService:18-21`

```java
@Component
@RequiredArgsConstructor
@Transactional  // 트랜잭션 경계 - 원자성 보장
public class SendMoneyService implements SendMoneyUseCase {
    // 전체 송금 프로세스가 하나의 트랜잭션으로 처리됨
}
```

## 4. 데이터 보호

### 4.1 불변 객체 사용

```java
// 값 객체는 불변으로 설계
@Value  // Lombok - 불변 객체 생성
public static class AccountId {
    Long value;
}

// Money 클래스도 불변으로 설계
public class Money {
    private final BigInteger amount;
    
    // 새로운 인스턴스 반환 (기존 인스턴스 변경 없음)
    public Money add(Money money) {
        return new Money(this.amount.add(money.amount));
    }
    
    public Money negate() {
        return new Money(this.amount.negate());
    }
}
```

### 4.2 생성자 접근 제어

**구현 위치**: `dev.haja.buckpal.account.domain.Account:16`

```java
@Getter @ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)  // 생성자 접근 제한
public class Account {
    
    // 팩토리 메서드를 통해서만 생성 가능
    public static Account withId(AccountId accountId, Money baselineBalance, ActivityWindow activityWindow) {
        return new Account(accountId, baselineBalance, activityWindow);
    }
    
    public static Account withoutId(Money baselineBalance, ActivityWindow activityWindow) {
        return new Account(null, baselineBalance, activityWindow);
    }
}
```

## 5. 설정 보안

### 5.1 설정값 검증

**구현 위치**: `dev.haja.buckpal.account.application.service.SendMoneyService:33-37`

```java
@Override
public boolean sendMoney(SendMoneyCommand command) {
    checkThreshold(command);
    
    // 설정값 검증
    int historyLookbackDays = buckPalConfigurationProperties.getAccount().getHistoryLookbackDays();
    if (historyLookbackDays <= 0) {
        throw new IllegalArgumentException("historyLookbackDays must be positive, but was: " + historyLookbackDays);
    }
    
    LocalDateTime baselineDate = LocalDateTime.now().minusDays(historyLookbackDays);
    // ...
}
```

### 5.2 환경별 설정 분리

**설정 파일 구조**:
```yaml
# application.yml - 기본 설정
spring:
  profiles:
    active: local
  application:
    name: Get-your-hands-dirty-on-clean-architecture

buckpal:
  account:
    history-lookback-days: 10

# application-local.yml - 로컬 개발 환경
# application-prod.yml - 운영 환경 (민감한 정보 별도 관리)
```

## 6. 아키텍처 보안

### 6.1 계층 간 의존성 제한

**구현 위치**: `dev.haja.buckpal.DependencyRuleTests.java:12-20`

```java
@Test
@DisplayName("도메인 계층은 애플리케이션 계층에 의존해서는 안된다")
void domainLayerDoesNotDependOnApplicationLayer() {
    noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..")
            .check(new ClassFileImporter().importPackages("dev.haja.buckpal"));
}
```

### 6.2 헥사고날 아키텍처 경계 강제

**구현 위치**: `dev.haja.buckpal.archunit.HexagonalArchitecture:45-59`

```java
public void check(JavaClasses classes) {
    // 어댑터 간 직접 의존성 금지
    this.adapters.dontDependOnEachOther(classes);
    
    // 애플리케이션 계층의 어댑터 의존성 금지
    this.applicationLayer.doesNotDependOn(this.adapters.getBasePackage(), classes);
    
    // 도메인 계층의 어댑터 의존성 금지
    this.domainDoesNotDependOnAdapters(classes);
    
    // 포트 간 직접 의존성 금지
    this.applicationLayer.incomingAndOutgoingPortsDoNotDependOnEachOther(classes);
}
```

## 7. 로깅 및 감사

### 7.1 안전한 로깅

```java
// 민감한 정보 로깅 금지 원칙
@Slf4j
public class SendMoneyService {
    
    @Override
    public boolean sendMoney(SendMoneyCommand command) {
        // ❌ 피해야 할 로깅
        // log.info("Sending money: {}", command);  // 계좌 정보와 금액이 노출됨
        
        // ✅ 안전한 로깅  
        log.info("Processing money transfer request");  // 일반적인 정보만 로깅
        
        // 비즈니스 로직 실행
        return executeMoneyTransfer(command, /*...*/);
    }
}
```

### 7.2 시스템 이벤트 로깅

```java
// Account.java:90 - 출금 활동 로깅 (개발/디버깅 용도)
Activity withdrawal = new Activity(/*...*/);
System.out.println("withdrawalActivity: " + withdrawal);  // 임시 로깅
```

## 8. 테스트 보안

### 8.1 테스트 데이터 보안

```java
// 테스트용 가짜 데이터 사용
private MoneyTransferProperties moneyTransferProperties() {
    return new MoneyTransferProperties(Money.of(Long.MAX_VALUE));  // 최대값으로 설정
}

private BuckPalConfigurationProperties buckPalConfigurationProperties() {
    return new BuckPalConfigurationProperties(
            Long.MAX_VALUE,
            new BuckPalConfigurationProperties.Account(10)  // 안전한 기본값
    );
}
```

### 8.2 민감한 정보 테스트 제외

```sql
-- 테스트 SQL: 실제 데이터가 아닌 가짜 데이터 사용
-- SendMoneySystemTest.sql
insert into account (id) values (1);  -- 단순한 ID만 사용
insert into account (id) values (2);

-- 실제 계좌번호, 개인정보 등은 사용하지 않음
```

## 9. 런타임 보안

### 9.1 예외 처리 보안

```java
// 민감한 정보를 노출하지 않는 예외 메시지
public class ThresholdExceededException extends RuntimeException {
    public ThresholdExceededException(Money threshold, Money actual) {
        // 구체적인 한도 금액은 로그에만 기록, 사용자에게는 일반적인 메시지
        super(String.format("Maximum threshold exceeded"));  // 간소화된 메시지
    }
}

// 계좌 ID 검증 실패
private AccountId getAccountId(Account account, String accountDescription) {
    return account.getId().orElseThrow(() ->
            new IllegalStateException(String.format("%s ID가 비어있습니다.", accountDescription)));
}
```

### 9.2 설정 검증

```java
// 런타임 시 설정값 무결성 검증
int historyLookbackDays = buckPalConfigurationProperties.getAccount().getHistoryLookbackDays();
if (historyLookbackDays <= 0) {
    throw new IllegalArgumentException("historyLookbackDays must be positive, but was: " + historyLookbackDays);
}
```

## 10. 보안 체크리스트

### 10.1 개발 시 확인 사항 ✅

- [ ] 모든 입력값에 대한 검증 로직 구현
- [ ] 비즈니스 규칙을 도메인 계층에 캡슐화
- [ ] 민감한 정보 로깅 방지
- [ ] 불변 객체 사용으로 데이터 무결성 보장
- [ ] 트랜잭션 경계 명확히 설정
- [ ] 동시성 제어 메커니즘 구현
- [ ] 설정값 검증 로직 추가
- [ ] 계층 간 의존성 규칙 준수
- [ ] 예외 메시지에서 민감한 정보 제거
- [ ] 테스트에서 실제 데이터 사용 금지

### 10.2 코드 리뷰 시 확인 사항 ✅

- [ ] Bean Validation 어노테이션 적절히 사용
- [ ] SelfValidating 패턴 적용
- [ ] 팩토리 메서드를 통한 객체 생성 제어
- [ ] 계좌 락 메커니즘 올바른 구현
- [ ] 트랜잭션 어노테이션 적절한 배치
- [ ] ArchUnit 테스트로 아키텍처 규칙 검증
- [ ] 예외 처리에서 스택 트레이스 노출 방지
- [ ] 로그 레벨과 내용 적절성 검토

### 10.3 배포 전 확인 사항 ✅

- [ ] 모든 설정값이 환경별로 적절히 분리
- [ ] 운영 환경 설정에 민감한 정보 노출 없음
- [ ] 아키텍처 테스트 통과
- [ ] 보안 관련 단위/통합 테스트 통과
- [ ] 로그 레벨이 운영 환경에 적절히 설정
- [ ] 예외 메시지가 사용자에게 적절히 마스킹

이러한 보안 가이드라인을 통해 BuckPal 프로젝트는 견고하고 안전한 금융 애플리케이션으로 구현됩니다.