# 데이터베이스 모델 문서

## 모델 개요

BuckPal 프로젝트는 **JPA/Hibernate**를 사용하여 도메인 모델과 완전히 분리된 영속성 모델을 구현합니다.

### 아키텍처 패턴
- **도메인-영속성 분리**: 도메인 엔티티와 JPA 엔티티 독립적 설계
- **매핑 계층**: MapStruct 기반 자동 매핑
- **포트 & 어댑터**: 영속성 어댑터를 통한 도메인 계층 보호

## JPA 엔티티 모델

### 1. AccountJpaEntity

**파일 위치**: `src/main/java/dev/haja/buckpal/account/adapter/out/persistence/AccountJpaEntity.java`

```java
@Entity 
@Data
@Table(name = "account")
@EqualsAndHashCode(of = {"id"})
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountJpaEntity {
    @Id 
    @GeneratedValue
    private Long id;
}
```

#### 특징 분석
- **최소한의 설계**: 계좌 ID만 저장하는 극도로 단순한 구조
- **식별자 생성**: JPA `@GeneratedValue` 사용
- **도메인 분리**: 비즈니스 로직 완전 배제
- **Lombok 활용**: 보일러플레이트 코드 제거

#### 관계 정의
- **물리적 관계**: 없음 (외래키 제약조건 없음)
- **논리적 관계**: Activity 엔티티들이 account_id 참조
- **관계 타입**: 1:N (Account → Activities)

### 2. ActivityJpaEntity

**파일 위치**: `src/main/java/dev/haja/buckpal/account/adapter/out/persistence/ActivityJpaEntity.java`

```java
@Entity 
@Data
@Table(name = "Activity")
@EqualsAndHashCode(of = {"id"})
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityJpaEntity {
    @Id 
    @GeneratedValue
    private Long id;

    @Column 
    private LocalDateTime timestamp;
    
    @Column 
    private Long ownerAccountId;
    
    @Column 
    private Long sourceAccountId;
    
    @Column 
    private Long targetAccountId;
    
    @Column 
    private Long amount;
}
```

#### 필드별 상세 분석

| 필드명 | JPA 어노테이션 | Java 타입 | 비즈니스 의미 | 제약조건 |
|--------|----------------|-----------|---------------|----------|
| `id` | `@Id @GeneratedValue` | `Long` | 거래 고유 식별자 | PRIMARY KEY |
| `timestamp` | `@Column` | `LocalDateTime` | 거래 발생 시각 | NULL 허용 |
| `ownerAccountId` | `@Column` | `Long` | 기록 소유 계좌 | NULL 허용 |
| `sourceAccountId` | `@Column` | `Long` | 송금 출발 계좌 | NULL 허용 |
| `targetAccountId` | `@Column` | `Long` | 송금 도착 계좌 | NULL 허용 |
| `amount` | `@Column` | `Long` | 거래 금액 (원단위) | NULL 허용 |

#### 설계 특징
- **이중 기록 지원**: 하나의 송금 = 두 개의 Activity 레코드
- **계좌 관계**: 외래키 없이 ID만으로 논리적 참조
- **시간 추적**: LocalDateTime으로 정확한 거래 시점 기록
- **금액 저장**: Long 타입으로 소수점 오차 방지

## 도메인 모델

### 1. Account (도메인 엔티티)

**파일 위치**: `src/main/java/dev/haja/buckpal/account/domain/Account.java`

```java
public class Account {
    private final AccountId id;
    private final Money baselineBalance;
    private final ActivityWindow activityWindow;
    
    // 비즈니스 로직
    public Money calculateBalance() {
        return Money.add(
            this.baselineBalance,
            this.activityWindow.calculateBalance(this.id)
        );
    }
    
    public boolean withdraw(Money money, AccountId targetAccountId) {
        if (!mayWithdraw(money)) {
            return false;
        }
        
        Activity withdrawal = new Activity(
            this.id,
            this.id,
            targetAccountId,
            LocalDateTime.now(),
            money
        );
        
        this.activityWindow.addActivity(withdrawal);
        return true;
    }
}
```

#### 도메인 특징
- **풍부한 행위**: 송금, 잔액계산 등 비즈니스 로직 포함
- **불변 객체**: 생성 후 상태 변경 불가
- **값 객체 활용**: Money, AccountId 등으로 타입 안전성 확보

### 2. Activity (도메인 엔티티)

```java
public class Activity {
    private ActivityId id;
    private final AccountId ownerAccountId;
    private final AccountId sourceAccountId;
    private final AccountId targetAccountId;
    private final LocalDateTime timestamp;
    private final Money money;
    
    // 생성자와 getter만 존재 (행위 없음)
}
```

#### 도메인 특징
- **이벤트 성격**: 발생한 거래를 불변하게 기록
- **식별자**: 생성 후 JPA에서 할당 (nullable)
- **시간 정보**: 거래 발생 정확한 시점 저장

## 모델 간 매핑 전략

### AccountMapper 구현 분석

**파일 위치**: `src/main/java/dev/haja/buckpal/account/adapter/out/persistence/AccountMapper.java`

```java
@Component
public class AccountMapper {
    
    // JPA → Domain 변환
    Account mapToDomainEntity(
            AccountJpaEntity account,
            List<ActivityJpaEntity> activities,
            Long withdrawalBalance,
            Long depositBalance) {

        Money baselineBalance = Money.subtract(
                Money.of(depositBalance),
                Money.of(withdrawalBalance));

        return Account.withId(
                new AccountId(account.getId()),
                baselineBalance,
                mapToActivityWindow(activities));
    }

    // Activity 리스트 → ActivityWindow 변환
    ActivityWindow mapToActivityWindow(List<ActivityJpaEntity> activities) {
        List<Activity> mappedActivities = new ArrayList<>();

        for (ActivityJpaEntity activity : activities) {
            mappedActivities.add(
                    new Activity(
                            new ActivityId(activity.getId()),
                            new AccountId(activity.getOwnerAccountId()),
                            new AccountId(activity.getSourceAccountId()),
                            new AccountId(activity.getTargetAccountId()),
                            activity.getTimestamp(),
                            Money.of(activity.getAmount()))
            );
        }
        return new ActivityWindow(mappedActivities);
    }

    // Domain → JPA 변환
    public ActivityJpaEntity mapToJpaEntity(Activity activity) {
        return new ActivityJpaEntity(
                activity.getId() == null ? null : activity.getId().getValue(),
                activity.getTimestamp(),
                activity.getOwnerAccountId().getValue(),
                activity.getSourceAccountId().getValue(),
                activity.getTargetAccountId().getValue(),
                activity.getMoney().getAmount().longValue()
        );
    }
}
```

#### 매핑 전략 분석

1. **계좌 로딩 시 잔액 계산**:
   - JPA에서 입금/출금 총액을 별도 쿼리로 조회
   - `baselineBalance = depositBalance - withdrawalBalance`
   - 도메인 객체 생성 시 계산된 baseline 제공

2. **Activity 컬렉션 매핑**:
   - JPA Entity List → Domain Activity List
   - 각각의 primitive 값들을 값 객체로 래핑
   - ActivityWindow로 그룹화

3. **신규 Activity 저장**:
   - 도메인 Activity → JPA Entity 변환
   - ID가 null인 경우에만 저장 (신규 판단)

## Repository 계층

### 1. AccountRepository

**파일 위치**: `src/main/java/dev/haja/buckpal/account/adapter/out/persistence/AccountRepository.java`

```java
interface AccountRepository extends JpaRepository<AccountJpaEntity, Long> {}
```

#### 특징
- **최소 인터페이스**: Spring Data JPA 기본 기능만 사용
- **단순성**: 복잡한 쿼리 메서드 없음
- **도메인 독립성**: 도메인 계층과 완전 분리

### 2. ActivityRepository

**파일 위치**: `src/main/java/dev/haja/buckpal/account/adapter/out/persistence/ActivityRepository.java`

```java
interface ActivityRepository extends JpaRepository<ActivityJpaEntity, Long> {

    @Query("SELECT a FROM ActivityJpaEntity a " +
            "WHERE a.ownerAccountId = :ownerAccountId " +
            "AND a.timestamp >= :since")
    List<ActivityJpaEntity> findByOwnerSince(
            @Param("ownerAccountId") Long ownerAccountId,
            @Param("since") LocalDateTime since);

    @Query("SELECT SUM(a.amount) FROM ActivityJpaEntity a " +
            "WHERE a.targetAccountId = :accountId " +
            "AND a.ownerAccountId = :accountId " +
            "AND a.timestamp < :until")
    Long getDepositBalanceUntil(
            @Param("accountId") Long accountId,
            @Param("until") LocalDateTime until);

    @Query("select sum(a.amount) from ActivityJpaEntity a " +
            "where a.sourceAccountId = :accountId " +
            "and a.ownerAccountId = :accountId " +
            "and a.timestamp < :until")
    Long getWithdrawalBalanceUntil(
            @Param("accountId") Long accountId,
            @Param("until") LocalDateTime until);
}
```

#### 쿼리 메서드 분석

1. **findByOwnerSince**: 
   - 특정 계좌의 특정 날짜 이후 활동 조회
   - ActivityWindow 구성용 데이터 제공

2. **getDepositBalanceUntil**:
   - 특정 시점까지의 입금 총액 계산
   - `targetAccountId = accountId AND ownerAccountId = accountId` 조건

3. **getWithdrawalBalanceUntil**:
   - 특정 시점까지의 출금 총액 계산  
   - `sourceAccountId = accountId AND ownerAccountId = accountId` 조건

## 값 객체 (Value Objects)

### Money 클래스
```java
@Value
public class Money {
    public static Money ZERO = Money.of(0);

    private final BigDecimal amount;

    public static Money of(long value) {
        return new Money(BigDecimal.valueOf(value));
    }
    
    public static Money add(Money a, Money b) {
        return new Money(a.amount.add(b.amount));
    }
}
```

### AccountId 클래스
```java
public record AccountId(Long value) {}
```

## 모델 검증 및 제약조건

### 1. JPA 레벨 검증
- **현재 상태**: 기본적인 PRIMARY KEY만 적용
- **누락 사항**: NULL 제약, 외래키 제약조건, 비즈니스 제약조건

### 2. 도메인 레벨 검증
```java
// Account 클래스 내부
private boolean mayWithdraw(Money money) {
    return Money.add(
        this.calculateBalance(),
        money.negate()
    ).isPositiveOrZero();
}
```

### 3. 권장 개선사항
```java
// JPA Entity 레벨 제약조건 추가
@Entity
@Table(name = "activity")
@Check(constraints = {
    "amount > 0",
    "source_account_id <> target_account_id",
    "timestamp <= CURRENT_TIMESTAMP"
})
public class ActivityJpaEntity {
    @Column(nullable = false)
    private Long amount;
    
    @Column(nullable = false) 
    private LocalDateTime timestamp;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_account_id")
    private AccountJpaEntity ownerAccount;
}
```

## 트랜잭션 및 동시성

### 1. 트랜잭션 경계
- **서비스 레이어**: `@Transactional` 선언
- **영속성 어댑터**: 트랜잭션 내에서 실행
- **롤백 전략**: RuntimeException 발생 시 자동 롤백

### 2. 동시성 제어
```java
// 현재 구현 (No-op)
@Component
public class NoOpsAccountLock implements AccountLock {
    @Override
    public void lockAccount(AccountId accountId) {
        // 현재는 빈 구현
    }
}
```

### 3. 개선 방안
- **낙관적 잠금**: `@Version` 필드 추가
- **비관적 잠금**: `@Lock(LockModeType.PESSIMISTIC_WRITE)` 적용
- **분산 잠금**: Redis 기반 분산 락 구현

이 모델 설계는 **헥사고날 아키텍처**의 핵심 원칙인 **도메인-인프라 분리**를 완벽하게 구현하여, 데이터베이스 기술 변경 시에도 비즈니스 로직에 영향을 주지 않는 유연한 구조를 제공합니다.