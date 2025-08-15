# 데이터베이스 쿼리 패턴 문서

## 쿼리 패턴 개요

BuckPal 프로젝트는 **잔액 계산 중심**의 쿼리 패턴을 사용하며, **Spring Data JPA**와 **Custom JPQL**을 조합하여 데이터를 조회합니다.

### 쿼리 특징
- **집계 중심**: 잔액 계산을 위한 SUM 쿼리 활용
- **시간 필터링**: timestamp 기반 범위 조회
- **이중 기록 처리**: owner_account_id 조건으로 소유권 구분

## 핵심 쿼리 패턴

### 1. 계좌 로딩 쿼리

**파일**: `ActivityRepository.java:12`

```java
@Query("SELECT a FROM ActivityJpaEntity a " +
        "WHERE a.ownerAccountId = :ownerAccountId " +
        "AND a.timestamp >= :since")
List<ActivityJpaEntity> findByOwnerSince(
        @Param("ownerAccountId") Long ownerAccountId,
        @Param("since") LocalDateTime since);
```

#### 실행되는 SQL
```sql
SELECT a.id, a.timestamp, a.owner_account_id, a.source_account_id, 
       a.target_account_id, a.amount
FROM activity a 
WHERE a.owner_account_id = ? 
  AND a.timestamp >= ?
ORDER BY a.id;  -- JPA 기본 정렬
```

#### 쿼리 분석
- **목적**: 특정 계좌의 특정 날짜 이후 모든 활동 조회
- **필터링**: 소유권(`owner_account_id`) + 시간 범위(`timestamp`)
- **사용처**: ActivityWindow 구성용 데이터 제공
- **성능 고려**: `(owner_account_id, timestamp)` 복합 인덱스 필요

### 2. 입금 잔액 계산 쿼리

**파일**: `ActivityRepository.java:19`

```java
@Query("SELECT SUM(a.amount) FROM ActivityJpaEntity a " +
        "WHERE a.targetAccountId = :accountId " +
        "AND a.ownerAccountId = :accountId " +
        "AND a.timestamp < :until")
Long getDepositBalanceUntil(
        @Param("accountId") Long accountId,
        @Param("until") LocalDateTime until);
```

#### 실행되는 SQL
```sql
SELECT SUM(a.amount) as deposit_sum
FROM activity a
WHERE a.target_account_id = ?     -- 이 계좌로 들어온 송금
  AND a.owner_account_id = ?      -- 이 계좌 소유의 기록
  AND a.timestamp < ?;            -- 기준 시점 이전
```

#### 쿼리 분석
- **목적**: 특정 시점까지의 입금 총액 계산
- **이중 조건**: `targetAccountId = accountId AND ownerAccountId = accountId`
- **의미**: "내 계좌로 들어온 돈 중에서 내가 소유한 기록만"
- **NULL 처리**: 결과가 없으면 NULL 반환 (Money.of()에서 처리)

### 3. 출금 잔액 계산 쿼리

**파일**: `ActivityRepository.java:27`

```java
@Query("select sum(a.amount) from ActivityJpaEntity a " +
        "where a.sourceAccountId = :accountId " +
        "and a.ownerAccountId = :accountId " +
        "and a.timestamp < :until")
Long getWithdrawalBalanceUntil(
        @Param("accountId") Long accountId,
        @Param("until") LocalDateTime until);
```

#### 실행되는 SQL
```sql
SELECT SUM(a.amount) as withdrawal_sum
FROM activity a
WHERE a.source_account_id = ?     -- 이 계좌에서 나간 송금
  AND a.owner_account_id = ?      -- 이 계좌 소유의 기록  
  AND a.timestamp < ?;            -- 기준 시점 이전
```

#### 쿼리 분석
- **목적**: 특정 시점까지의 출금 총액 계산
- **이중 조건**: `sourceAccountId = accountId AND ownerAccountId = accountId`
- **의미**: "내 계좌에서 나간 돈 중에서 내가 소유한 기록만"

### 4. 기본 계좌 조회 (Spring Data JPA)

```java
// AccountRepository에서 상속받는 기본 메서드
Optional<AccountJpaEntity> findById(Long id);
```

#### 실행되는 SQL
```sql
SELECT a.id 
FROM account a 
WHERE a.id = ?;
```

## 복합 쿼리 패턴

### 1. 계좌 로딩 시 실행되는 전체 쿼리

**파일**: `AccountPersistenceAdapter.java:26`

```java
@Override
public Account loadAccount(AccountId accountId, LocalDateTime baselineDate) {
    // 1. 계좌 기본 정보 조회
    AccountJpaEntity account = accountRepository
        .findById(accountId.getValue())
        .orElseThrow(() -> new AccountNotFoundException(accountId));
    
    // 2. 활동 내역 조회  
    List<ActivityJpaEntity> activities = activityRepository
        .findByOwnerSince(accountId.getValue(), baselineDate);
    
    // 3. 출금 잔액 계산
    Long withdrawalBalance = activityRepository
        .getWithdrawalBalanceUntil(accountId.getValue(), baselineDate);
    
    // 4. 입금 잔액 계산
    Long depositBalance = activityRepository
        .getDepositBalanceUntil(accountId.getValue(), baselineDate);
    
    return accountMapper.mapToDomainEntity(
        account, activities, withdrawalBalance, depositBalance);
}
```

#### 실행 순서 및 쿼리
```sql
-- Query 1: 계좌 존재 확인
SELECT a.id FROM account a WHERE a.id = ?;

-- Query 2: 활동 내역 조회
SELECT a.id, a.timestamp, a.owner_account_id, a.source_account_id, 
       a.target_account_id, a.amount
FROM activity a 
WHERE a.owner_account_id = ? AND a.timestamp >= ?;

-- Query 3: 출금 잔액 계산
SELECT SUM(a.amount) FROM activity a
WHERE a.source_account_id = ? AND a.owner_account_id = ? AND a.timestamp < ?;

-- Query 4: 입금 잔액 계산  
SELECT SUM(a.amount) FROM activity a
WHERE a.target_account_id = ? AND a.owner_account_id = ? AND a.timestamp < ?;
```

### 2. 잔액 계산 로직

**파일**: `AccountMapper.java:16`

```java
Account mapToDomainEntity(
        AccountJpaEntity account,
        List<ActivityJpaEntity> activities,
        Long withdrawalBalance,
        Long depositBalance) {

    // 기준 잔액 = 입금 총액 - 출금 총액
    Money baselineBalance = Money.subtract(
            Money.of(depositBalance),   // NULL 안전성 Money.of() 처리
            Money.of(withdrawalBalance));

    return Account.withId(
            new AccountId(account.getId()),
            baselineBalance,
            mapToActivityWindow(activities));
}
```

## 성능 분석 및 최적화

### 1. 현재 성능 이슈

#### N+1 문제 분석
```java
// 여러 계좌 조회 시 발생할 수 있는 N+1 문제
for (Long accountId : accountIds) {
    Account account = loadAccount(new AccountId(accountId), baselineDate);
    // 각 계좌마다 4개의 쿼리 실행 → 1 + N×4 쿼리 문제
}
```

#### 해결 방안
```java
// 배치 로딩 최적화 (권장)
@Query("SELECT a FROM ActivityJpaEntity a " +
       "WHERE a.ownerAccountId IN :accountIds " +
       "AND a.timestamp >= :since")
List<ActivityJpaEntity> findByOwnersSince(
        @Param("accountIds") List<Long> accountIds,
        @Param("since") LocalDateTime since);
```

### 2. 쿼리 성능 최적화

#### 필수 인덱스 전략
```sql
-- 1순위: 복합 인덱스 (필터링 + 정렬)
CREATE INDEX idx_activity_owner_timestamp ON activity (owner_account_id, timestamp);

-- 2순위: 입금 계산 최적화
CREATE INDEX idx_activity_target_owner_timestamp ON activity (target_account_id, owner_account_id, timestamp);

-- 3순위: 출금 계산 최적화  
CREATE INDEX idx_activity_source_owner_timestamp ON activity (source_account_id, owner_account_id, timestamp);

-- 4순위: 시간 범위 쿼리
CREATE INDEX idx_activity_timestamp ON activity (timestamp);
```

#### 쿼리 튜닝 예시
```sql
-- AS-IS: 풀 테이블 스캔 가능성
SELECT SUM(a.amount) FROM activity a
WHERE a.target_account_id = 1 AND a.owner_account_id = 1 AND a.timestamp < '2023-12-31';

-- TO-BE: 인덱스 활용 (복합 인덱스 생성 후)
EXPLAIN PLAN:
Index Range Scan on idx_activity_target_owner_timestamp
```

### 3. 캐싱 전략

#### 잔액 캐싱 패턴
```java
@Service
@Transactional
public class CachedAccountService {
    
    @Cacheable(value = "account-balance", key = "#accountId")
    public Money getAccountBalance(AccountId accountId) {
        Account account = loadAccount(accountId, getBaselineDate());
        return account.calculateBalance();
    }
    
    @CacheEvict(value = "account-balance", key = "#accountId")  
    public void evictAccountBalance(AccountId accountId) {
        // 송금 완료 후 캐시 무효화
    }
}
```

#### Redis 기반 캐싱 (권장)
```yaml
# application-prod.yml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 300000  # 5분
```

## 복잡한 쿼리 패턴

### 1. 기간별 거래 내역 조회

```java
// 커스텀 쿼리 (향후 구현)
@Query("SELECT a FROM ActivityJpaEntity a " +
       "WHERE a.ownerAccountId = :accountId " +
       "AND a.timestamp BETWEEN :startDate AND :endDate " +
       "ORDER BY a.timestamp DESC")
Page<ActivityJpaEntity> findByAccountIdAndDateRange(
        @Param("accountId") Long accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable);
```

#### 실행 SQL
```sql
SELECT a.* FROM activity a
WHERE a.owner_account_id = ?
  AND a.timestamp BETWEEN ? AND ?
ORDER BY a.timestamp DESC
LIMIT ? OFFSET ?;  -- 페이징
```

### 2. 계좌별 월별 집계

```java
// 리포팅용 집계 쿼리 (향후 구현)
@Query("SELECT DATE_TRUNC('month', a.timestamp) as month, " +
       "SUM(CASE WHEN a.sourceAccountId = :accountId THEN a.amount ELSE 0 END) as withdrawals, " +
       "SUM(CASE WHEN a.targetAccountId = :accountId THEN a.amount ELSE 0 END) as deposits " +
       "FROM ActivityJpaEntity a " +
       "WHERE (a.sourceAccountId = :accountId OR a.targetAccountId = :accountId) " +
       "AND a.ownerAccountId = :accountId " +
       "GROUP BY DATE_TRUNC('month', a.timestamp) " +
       "ORDER BY month")
List<Object[]> getMonthlyAccountSummary(@Param("accountId") Long accountId);
```

### 3. 대용량 데이터 처리

#### 배치 조회 최적화
```java
// 스크롤 API 활용 (대용량 데이터)
@Query("SELECT a FROM ActivityJpaEntity a " +
       "WHERE a.timestamp >= :since " +
       "ORDER BY a.id")
Slice<ActivityJpaEntity> findActivitiesSince(
        @Param("since") LocalDateTime since, 
        Pageable pageable);
```

## 쿼리 모니터링

### 1. 실행 계획 분석

```sql
-- H2에서 실행 계획 확인
EXPLAIN PLAN FOR 
SELECT SUM(a.amount) FROM activity a
WHERE a.target_account_id = 1 AND a.owner_account_id = 1 AND a.timestamp < '2023-12-31';

-- 실행 계획 조회
SELECT * FROM INFORMATION_SCHEMA.PLAN_TABLE;
```

### 2. 쿼리 로깅 설정

**파일**: `application-local.yml`

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG          # 실행 SQL 로깅
    org.hibernate.type: TRACE         # 파라미터 값 로깅
    org.hibernate.stat: DEBUG         # 통계 정보 로깅
    
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true     # 통계 수집 활성화
        use_sql_comments: true       # SQL 주석 추가
        format_sql: true             # SQL 포맷팅
```

### 3. 성능 메트릭

```java
// JPA 통계 정보 수집
@Component
public class QueryMetricsCollector {
    
    @EventListener
    public void handleQueryExecution(QueryExecutionEvent event) {
        long executionTime = event.getExecutionTime();
        String sql = event.getSql();
        
        // Micrometer 메트릭 수집
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("jpa.query.execution.time")
                .tag("query.type", getQueryType(sql))
                .register(meterRegistry));
    }
}
```

## N+1 문제 해결 패턴

### 1. 배치 페칭

```java
// 즉시 로딩으로 N+1 해결 (주의: 메모리 사용량 증가)
@EntityGraph(attributePaths = {"activities"})
@Query("SELECT DISTINCT a FROM AccountJpaEntity a LEFT JOIN FETCH a.activities")
List<AccountJpaEntity> findAllWithActivities();
```

### 2. 서브쿼리 최적화

```java
// 서브쿼리를 JOIN으로 변경
@Query("SELECT a, " +
       "(SELECT SUM(act.amount) FROM ActivityJpaEntity act " +
        "WHERE act.targetAccountId = a.id AND act.ownerAccountId = a.id) as deposits, " +
       "(SELECT SUM(act.amount) FROM ActivityJpaEntity act " +  
        "WHERE act.sourceAccountId = a.id AND act.ownerAccountId = a.id) as withdrawals " +
       "FROM AccountJpaEntity a WHERE a.id IN :ids")
List<Object[]> findAccountsWithBalances(@Param("ids") List<Long> ids);
```

### 3. Projection 활용

```java
// 필요한 데이터만 조회하는 Projection
public interface AccountBalanceProjection {
    Long getId();
    BigDecimal getDepositBalance();
    BigDecimal getWithdrawalBalance();
}

@Query("SELECT a.id as id, " +
       "COALESCE(SUM(CASE WHEN act.targetAccountId = a.id THEN act.amount ELSE 0 END), 0) as depositBalance, " +
       "COALESCE(SUM(CASE WHEN act.sourceAccountId = a.id THEN act.amount ELSE 0 END), 0) as withdrawalBalance " +
       "FROM AccountJpaEntity a LEFT JOIN ActivityJpaEntity act ON " +
       "(act.targetAccountId = a.id OR act.sourceAccountId = a.id) AND act.ownerAccountId = a.id " +
       "GROUP BY a.id")
List<AccountBalanceProjection> findAccountBalances();
```

**현재 쿼리 패턴은 간단한 송금 도메인에 최적화되어 있으며, 프로덕션 환경에서는 인덱스 추가와 배치 처리 최적화가 필요합니다.**