# 데이터베이스 스키마 문서

## 데이터베이스 구성

BuckPal 프로젝트는 H2 인메모리 데이터베이스를 사용하여 JPA 기반의 영속성 계층을 구현하고 있습니다.

### 기본 설정
- **데이터베이스**: H2 Database (인메모리)
- **JPA 구현체**: Hibernate
- **연결 URL**: `jdbc:h2:mem:testdb`
- **사용자**: `sa`
- **비밀번호**: (공백)

## JPA 엔티티 구조

### 1. AccountJpaEntity

**위치**: `src/main/java/dev/haja/buckpal/account/adapter/out/persistence/AccountJpaEntity.java`

#### 테이블 구조
```sql
CREATE TABLE account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    -- 추가 필드는 구현에 따라 달라질 수 있음
);
```

#### 엔티티 특징
```java
@Entity
@Table(name = "account")
public class AccountJpaEntity {
    @Id
    @GeneratedValue
    private Long id;
    
    // 기타 필드들
}
```

**특징**:
- JPA 자동 생성 식별자 사용
- 도메인 모델과 분리된 영속성 전용 엔티티
- AccountMapper를 통한 도메인 모델 변환

### 2. ActivityJpaEntity

**위치**: `src/main/java/dev/haja/buckpal/account/adapter/out/persistence/ActivityJpaEntity.java`

#### 테이블 구조
```sql
CREATE TABLE activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_account_id BIGINT NOT NULL,
    source_account_id BIGINT NOT NULL,
    target_account_id BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    amount BIGINT NOT NULL,
    
    FOREIGN KEY (owner_account_id) REFERENCES account(id),
    FOREIGN KEY (source_account_id) REFERENCES account(id),
    FOREIGN KEY (target_account_id) REFERENCES account(id)
);
```

#### 엔티티 특징
```java
@Entity
@Table(name = "activity")
public class ActivityJpaEntity {
    @Id
    @GeneratedValue
    private Long id;
    
    @Column(name = "owner_account_id")
    private Long ownerAccountId;
    
    @Column(name = "source_account_id") 
    private Long sourceAccountId;
    
    @Column(name = "target_account_id")
    private Long targetAccountId;
    
    @Column(name = "timestamp")
    private LocalDateTime timestamp;
    
    @Column(name = "amount")
    private Long amount;
}
```

**특징**:
- 송금 활동을 시간순으로 기록
- 계좌 ID들을 외래 키로 참조
- 금액을 Long 타입으로 저장 (소수점 문제 회피)

## Repository 인터페이스

### 1. AccountRepository

**위치**: `src/main/java/dev/haja/buckpal/account/adapter/out/persistence/AccountRepository.java`

```java
public interface AccountRepository extends JpaRepository<AccountJpaEntity, Long> {
    // Spring Data JPA 기본 메서드 사용
    // 필요시 커스텀 쿼리 메서드 추가
}
```

### 2. ActivityRepository

**위치**: `src/main/java/dev/haja/buckpal/account/adapter/out/persistence/ActivityRepository.java`

```java
public interface ActivityRepository extends JpaRepository<ActivityJpaEntity, Long> {
    // 계좌별 활동 내역 조회
    List<ActivityJpaEntity> findByOwnerAccountId(Long accountId);
    
    // 기간별 활동 내역 조회
    List<ActivityJpaEntity> findByOwnerAccountIdAndTimestampGreaterThanEqual(
        Long accountId, LocalDateTime since);
    
    // 잔액 계산용 집계 쿼리
    @Query("SELECT SUM(a.amount) FROM ActivityJpaEntity a WHERE a.targetAccountId = :accountId AND a.timestamp >= :since")
    Long getDepositBalanceUntil(Long accountId, LocalDateTime since);
    
    @Query("SELECT SUM(a.amount) FROM ActivityJpaEntity a WHERE a.sourceAccountId = :accountId AND a.timestamp >= :since")
    Long getWithdrawalBalanceUntil(Long accountId, LocalDateTime since);
}
```

## 매핑 전략

### AccountMapper

**위치**: `src/main/java/dev/haja/buckpal/account/adapter/out/persistence/AccountMapper.java`

MapStruct를 사용한 자동 매핑:

```java
@Mapper(componentModel = "spring")
public interface AccountMapper {
    
    // JPA 엔티티 → 도메인 모델
    Account mapToDomainEntity(
        AccountJpaEntity account,
        List<ActivityJpaEntity> activities,
        Long withdrawalBalance,
        Long depositBalance);
    
    // 도메인 모델 → JPA 엔티티
    ActivityJpaEntity mapToJpaEntity(Activity activity);
}
```

**특징**:
- 컴파일 타임 코드 생성으로 런타임 오버헤드 없음
- 타입 안전성 보장
- 도메인 모델과 영속성 모델의 완전한 분리

## 영속성 어댑터

### AccountPersistenceAdapter

**위치**: `src/main/java/dev/haja/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.java`

#### 주요 기능

1. **계좌 로딩 (LoadAccountPort 구현)**
```java
@Override
public Account loadAccount(AccountId accountId, LocalDateTime baselineDate) {
    AccountJpaEntity account = accountRepository
        .findById(accountId.getValue())
        .orElseThrow(() -> new AccountNotFoundException(accountId));
    
    List<ActivityJpaEntity> activities = activityRepository
        .findByOwnerAccountIdAndTimestampGreaterThanEqual(
            accountId.getValue(), baselineDate);
    
    Long withdrawalBalance = activityRepository
        .getWithdrawalBalanceUntil(accountId.getValue(), baselineDate);
    
    Long depositBalance = activityRepository  
        .getDepositBalanceUntil(accountId.getValue(), baselineDate);
    
    return accountMapper.mapToDomainEntity(
        account, activities, withdrawalBalance, depositBalance);
}
```

2. **계좌 상태 업데이트 (UpdateAccountStatePort 구현)**
```java
@Override
public void updateActivities(Account account) {
    for (Activity activity : account.getActivityWindow().getActivities()) {
        if (activity.getId() == null) {
            activityRepository.save(accountMapper.mapToJpaEntity(activity));
        }
    }
}
```

## 트랜잭션 관리

### 설정
- `@Transactional` 어노테이션을 통한 선언적 트랜잭션
- 서비스 레이어에서 트랜잭션 경계 관리
- 롤백 조건: RuntimeException 발생 시

### 동시성 제어
```java
@Component
public class NoOpsAccountLock implements AccountLock {
    @Override
    public void lockAccount(AccountId accountId) {
        // 현재는 No-op 구현
        // 실제 운영에서는 Redis, DB 락 등 활용
    }
    
    @Override
    public void releaseAccount(AccountId accountId) {
        // Lock 해제 로직
    }
}
```

## 테스트 데이터

### 테스트 SQL 스크립트

**시스템 테스트용**: `src/test/resources/dev/haja/buckpal/SendMoneySystemTest.sql`
**영속성 어댑터 테스트용**: `src/test/resources/dev/haja/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.sql`

### 테스트 데이터 예시
```sql
-- 계좌 생성
INSERT INTO account (id) VALUES (1);
INSERT INTO account (id) VALUES (2);

-- 초기 활동 내역
INSERT INTO activity (id, owner_account_id, source_account_id, target_account_id, timestamp, amount)
VALUES (1, 1, 1, 2, '2023-08-08 08:00:00', 500);

INSERT INTO activity (id, owner_account_id, source_account_id, target_account_id, timestamp, amount)
VALUES (2, 2, 1, 2, '2023-08-08 08:00:00', 500);
```

## 성능 최적화 고려사항

### 1. 쿼리 최적화
- ActivityWindow 조회 시 페이징 적용 검토
- 인덱스 전략 (owner_account_id, timestamp)
- 집계 쿼리 성능 모니터링

### 2. 캐싱 전략
- 계좌 잔액 계산 결과 캐싱
- 자주 조회되는 계좌 정보 캐싱
- Spring Cache 추상화 활용

### 3. 배치 처리
- 대량 활동 내역 처리 시 배치 업데이트
- JPA Batch 설정 활용

이러한 데이터베이스 설계는 헥사고날 아키텍처의 원칙에 따라 도메인 모델과 완전히 분리되어 있어, 데이터베이스 기술 변경 시에도 비즈니스 로직에 영향을 주지 않습니다.