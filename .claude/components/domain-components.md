# 도메인 컴포넌트 분석

## 도메인 계층 구조

BuckPal의 도메인 계층은 **도메인 주도 설계(DDD)** 원칙에 따라 구성되어 있습니다.

### 패키지 구조
```
src/main/java/dev/haja/buckpal/account/domain/
├── Account.java        # 집합체 루트 (Aggregate Root)
├── Account.AccountId   # 엔티티 식별자 (내부 클래스)
├── Money.java          # 값 객체 (Value Object)
├── Activity.java       # 도메인 엔티티
└── ActivityWindow.java # 도메인 서비스
```

## 핵심 도메인 컴포넌트

### 1. Account (계좌) - 집합체 루트

**파일**: `src/main/java/dev/haja/buckpal/account/domain/Account.java`

#### 책임과 역할
- 계좌의 생명주기 관리
- 송금 비즈니스 규칙 캡슐화
- 활동 윈도우를 통한 거래 내역 관리

#### 주요 메서드 분석

**생성 팩토리 메서드**
```java
// 영속성에서 로드할 때 사용
public static Account withId(
    AccountId accountId,
    Money baselineBalance,
    ActivityWindow activityWindow)

// 새 계좌 생성 시 사용  
public static Account withoutId(
    Money baselineBalance,
    ActivityWindow activityWindow)
```

**비즈니스 로직 메서드**
```java
// 출금 처리 - 비즈니스 규칙 포함
public boolean withdraw(Money money, AccountId targetAccountId) {
    if (!mayWithdraw(money)) return false;
    
    Activity withdrawal = new Activity(
        this.id, this.id, targetAccountId,
        LocalDateTime.now(), money);
    this.activityWindow.addActivity(withdrawal);
    return true;
}

// 입금 처리 - 항상 성공
public boolean deposit(Money money, AccountId sourceAccountId) {
    Activity deposit = new Activity(
        this.id, sourceAccountId, this.id,
        LocalDateTime.now(), money);
    this.activityWindow.addActivity(deposit);
    return true;
}

// 현재 잔액 계산
public Money calculateBalance() {
    return Money.add(
        this.baselineBalance,
        this.activityWindow.calculateBalance(this.id));
}
```

#### 설계 특징
- **불변성**: 기본 필드들이 `final`로 선언
- **캡슐화**: 비즈니스 규칙이 엔티티 내부에 캡슐화
- **방어적 프로그래밍**: `mayWithdraw()` 메서드로 사전 조건 검사

### 2. AccountId - 식별자 값 객체

```java
@Value
public static class AccountId {
    Long value;
}
```

#### 특징
- Lombok의 `@Value`로 불변 객체 생성
- 타입 안전성 확보 (Long과 구분)
- 정적 내부 클래스로 응집도 향상

### 3. Money - 값 객체

**파일**: `src/main/java/dev/haja/buckpal/account/domain/Money.java`

#### 설계 원칙
```java
public class Money {
    public static final Money ZERO = new Money(BigInteger.ZERO);
    private final BigInteger amount;

    // 정적 팩토리 메서드
    public static Money of(long value) {
        return new Money(BigInteger.valueOf(value));
    }
    
    // 불변 연산 메서드들
    public Money add(Money money) {
        return new Money(this.amount.add(money.amount));
    }
    
    public Money subtract(Money money) {
        return new Money(this.amount.subtract(money.amount));
    }
    
    public Money negate() {
        return new Money(this.amount.negate());
    }
}
```

#### 핵심 특징
- **불변성**: 모든 연산이 새 인스턴스 반환
- **BigInteger 사용**: 정확한 금액 계산
- **도메인 언어**: `add()`, `subtract()`, `negate()` 등 직관적 메서드명
- **비교 메서드**: `isPositive()`, `isGreaterThan()` 등

### 4. Activity - 거래 활동 엔티티

**파일**: `src/main/java/dev/haja/buckpal/account/domain/Activity.java`

#### 역할
- 계좌 간 송금 활동을 나타내는 도메인 이벤트
- 감사(Audit) 로그 역할

#### 구조
```java
public class Activity {
    private ActivityId id;
    private final AccountId ownerAccountId;      // 활동 소유 계좌
    private final AccountId sourceAccountId;     // 출금 계좌
    private final AccountId targetAccountId;     // 입금 계좌  
    private final LocalDateTime timestamp;       // 거래 시점
    private final Money money;                   // 거래 금액
    
    // 생성자와 getter 메서드들
}
```

#### 설계 특징
- **이벤트 소싱 준비**: 모든 거래를 이벤트로 기록
- **시간 정보**: 정확한 거래 시점 보존
- **소유 계좌**: 어느 계좌 관점에서의 활동인지 명확히 구분

### 5. ActivityWindow - 도메인 서비스

**파일**: `src/main/java/dev/haja/buckpal/account/domain/ActivityWindow.java`

#### 책임
- 특정 기간의 거래 활동 관리
- 잔액 계산 로직 제공
- 활동 목록의 생명주기 관리

#### 핵심 메서드
```java
public class ActivityWindow {
    private List<Activity> activities;

    // 잔액 계산 - 계좌 관점에서
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
    
    // 새 활동 추가
    public void addActivity(Activity activity) {
        this.activities.add(activity);
    }
}
```

#### 설계 특징
- **시간 윈도우 개념**: 전체가 아닌 특정 기간만 관리
- **스트림 API 활용**: 함수형 프로그래밍 스타일
- **계산 로직 캡슐화**: 복잡한 잔액 계산을 한 곳에 집중

## 도메인 모델의 장점

### 1. 비즈니스 로직 집중화
- 송금 규칙이 Account 엔티티에 캡슐화
- 도메인 전문가와의 소통에 용이한 코드
- 비즈니스 규칙 변경 시 영향 범위 최소화

### 2. 타입 안전성
- `Money`, `AccountId` 값 객체를 통한 타입 안전성
- 컴파일 타임에 오류 발견 가능
- 의미 있는 도메인 언어 사용

### 3. 테스트 용이성
- 순수 Java 객체로 구성
- 외부 의존성 없이 단위 테스트 가능
- 비즈니스 규칙 검증에 집중

### 4. 불변성과 스레드 안전성
- 값 객체들의 불변성
- 방어적 복사를 통한 데이터 보호
- 멀티스레드 환경에서 안전한 사용

## Kotlin 마이그레이션 고려사항

### 현재 Java 구현의 한계
1. **보일러플레이트 코드**: Lombok 의존성
2. **Null 안전성**: Optional 사용으로 복잡성 증가
3. **표현력**: 제한적인 언어 기능

### Kotlin 마이그레이션 시 개선점
```kotlin
// Money 값 객체 Kotlin 버전
@JvmInline
value class Money(val amount: BigInteger) {
    companion object {
        val ZERO = Money(BigInteger.ZERO)
        fun of(value: Long) = Money(BigInteger.valueOf(value))
    }
    
    operator fun plus(other: Money) = Money(amount + other.amount)
    operator fun minus(other: Money) = Money(amount - other.amount)
    operator fun unaryMinus() = Money(-amount)
    
    fun isPositive() = amount > BigInteger.ZERO
    fun isPositiveOrZero() = amount >= BigInteger.ZERO
}

// AccountId Kotlin 버전
@JvmInline
value class AccountId(val value: Long)

// Account 엔티티 Kotlin 버전
class Account private constructor(
    private val id: AccountId?,
    private val baselineBalance: Money,
    private var activityWindow: ActivityWindow
) {
    companion object {
        fun withId(id: AccountId, baselineBalance: Money, activityWindow: ActivityWindow) =
            Account(id, baselineBalance, activityWindow)
            
        fun withoutId(baselineBalance: Money, activityWindow: ActivityWindow) =
            Account(null, baselineBalance, activityWindow)
    }
    
    fun getId(): AccountId? = id
    
    fun withdraw(money: Money, targetAccountId: AccountId): Boolean {
        if (!mayWithdraw(money)) return false
        
        val withdrawal = Activity(
            ownerAccountId = checkNotNull(id),
            sourceAccountId = checkNotNull(id), 
            targetAccountId = targetAccountId,
            timestamp = LocalDateTime.now(),
            money = money
        )
        activityWindow.addActivity(withdrawal)
        return true
    }
    
    private fun mayWithdraw(money: Money): Boolean =
        (calculateBalance() - money).isPositiveOrZero()
}
```

### Kotlin의 장점
1. **Null 안전성**: 컴파일 타임 Null 체크
2. **간결성**: 보일러플레이트 코드 제거
3. **표현력**: 연산자 오버로딩, inline class 등
4. **함수형**: 고차 함수, 확장 함수 활용

이러한 도메인 모델은 헥사고날 아키텍처의 핵심으로서, 비즈니스 로직을 순수하게 표현하고 외부 기술에 의존하지 않도록 설계되어 있습니다.