# Plan 4 — 락/트랜잭션 정합성 + 비관적 락 구현

> **근거**: [아키텍처 평가](../reviews/2026-07-12-java-architecture-review.md) §2(동시성·정합성 결함 전체)
> **우선순위**: 4 (구조적 개선 — NoOp 락이라 당장 터지진 않지만 근본 결함)
> **실행 순서**: **마지막** (Plan 1의 Result 시그니처, Plan 5의 도메인 시그니처 확정 후)
> **사용자 결정**: 구조 정비 + **비관적 락(SELECT FOR UPDATE) 실제 구현** (NoOp 유지·낙관적 락 대안은 기각)

---

## 1. 배경과 문제

`SendMoneyService`의 현재 흐름과 5가지 결함:

```
sendMoney()
  ├── checkThreshold
  ├── loadAccount(source), loadAccount(target)     ← (d) 락 없이 읽음 (TOCTOU)
  ├── lock(source) → withdraw → 실패 시 release
  ├── lock(target) → deposit
  ├── updateActivities ×2                          ← (a) 여기서 예외 나면 락 누수
  └── release(source), release(target)             ← (b) 커밋 전 해제
                                                     (c) source→target 고정 순서 → 데드락
                                                     (e) NoOpsAccountLock → 락이 아예 없음
```

- **(a) 예외 시 락 누수** (`SendMoneyService.java:46-59`): try/finally 부재
- **(b) 커밋 전 락 해제**: `@Transactional` 커밋은 메서드 반환 후인데 락은 반환 전에 풀림 → 미커밋 잔액을 읽는 레이스 윈도우
- **(c) 데드락**: A→B와 B→A 동시 송금 시 교착 (락 순서 미정렬)
- **(d) TOCTOU**: 락 획득 전에 계좌를 읽어 "잔액 확인 후 출금"의 원자성이 깨짐
- **(e) 초과 인출 가능**: `NoOpsAccountLock` + JPA 버전 관리 없음 + insert-only 저장 → 동시 요청 둘 다 잔액 검증 통과

## 2. 목표 / 비목표

### 목표

- DB 행 잠금(`SELECT ... FOR UPDATE`) 기반 `AccountLock` 구현체로 **초과 인출을 실제로 방지**
- 계좌 ID 정렬 → 전체 선(先)락 → 로드 순서로 재구성 (데드락 + TOCTOU 해결)
- try/finally로 포트 계약상 해제 보장
- 커밋 시점 락 해제 (DB 락의 자연스러운 성질로 해결)
- 동시성 통합 테스트로 초과 인출 방지를 검증
- `updateActivities` 배치화, `GetAccountBalanceService` 트랜잭션 경계 정리

### 비목표 (Non-Goals)

- 낙관적 락(`@Version`) — insert-only 저장 구조와 맞지 않아 기각(사용자 결정)
- 분산 락(Redis 등) — 단일 DB 스코프
- 송금 이벤트/사가 등 아키텍처 확장

## 3. 설계 결정

### 3-1. 비관적 락과 `AccountLock` 포트의 의미론

`lockAccount(accountId)` = **현재 트랜잭션 안에서 해당 계좌 행을 `PESSIMISTIC_WRITE`로 잠근다**.

핵심 통찰: DB 비관적 락은 **트랜잭션 커밋/롤백 시점에 자동 해제**된다. 따라서:

- `releaseAccount()`는 no-op이 된다 → **결함 (b)가 자동 해결**: 락이 커밋보다 먼저 풀릴 수 없음
- 예외 시에도 트랜잭션 롤백과 함께 해제 → **결함 (a)의 실질 위험 소멸** (try/finally는 포트 계약 방어용으로 유지)

**기각한 대안**: `releaseAccount()`에서 `EntityManager` 락 해제 시도 — JPA는 트랜잭션 중간 해제를 지원하지 않으며, 중간 해제는 오히려 (b)를 재도입한다.

### 3-2. 락 획득 순서

`sourceAccountId`, `targetAccountId`를 **`AccountId.value` 오름차순 정렬 후 순서대로 락** → 모든 트랜잭션이 같은 순서로 잠그므로 순환 대기가 불가능 (결함 (c) 해결). 락 획득을 `loadAccount()`보다 **앞으로** 이동 (결함 (d) 해결) — 락 이후 읽은 잔액은 커밋까지 다른 송금이 건드릴 수 없다.

주의: Plan 5의 자기 송금 차단(source ≠ target)이 선행되어야 같은 ID 이중 락 문제가 원천 차단된다.

### 3-3. `NoOpsAccountLock` 처리

**삭제**하고 `PessimisticAccountLock`으로 대체. 동일 타입 빈이 2개면 주입 충돌이 나므로 공존 불가. "NoOp을 프로파일로 남기는" 대안은 학습 프로젝트에서 혼란만 더해 기각.

## 4. 상세 변경 내역

### 4-1. `AccountRepository` — 잠금 조회 메서드 추가

**파일**: `account/adapter/out/persistence/AccountRepository.java`

```java
interface AccountRepository extends JpaRepository<AccountJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select a from AccountJpaEntity a where a.id = :id")
    Optional<AccountJpaEntity> findWithLockById(@Param("id") Long id);
}
```

- H2·PostgreSQL 모두 `SELECT ... FOR UPDATE`로 번역됨
- `lock.timeout` 3초: 데드락은 정렬로 예방되지만, 장기 트랜잭션 대비 안전장치. (H2는 힌트를 무시할 수 있음 — 문서화만, 동작 의존 금지)

### 4-2. 신규: `PessimisticAccountLock` / 삭제: `NoOpsAccountLock`

**파일**: `account/adapter/out/persistence/PessimisticAccountLock.java`

```java
package dev.haja.buckpal.account.adapter.out.persistence;

import dev.haja.buckpal.account.application.port.out.AccountLock;
import dev.haja.buckpal.account.application.port.out.AccountNotFoundException;
import dev.haja.buckpal.account.domain.Account.AccountId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DB 행 잠금(SELECT ... FOR UPDATE) 기반 계좌 락.
 * 락은 현재 트랜잭션의 커밋/롤백 시점에 DB가 자동 해제하므로
 * releaseAccount는 no-op이다. 반드시 활성 트랜잭션 안에서 호출해야 한다.
 */
@Component
@RequiredArgsConstructor
class PessimisticAccountLock implements AccountLock {

    private final AccountRepository accountRepository;

    @Override
    public void lockAccount(AccountId accountId) {
        accountRepository.findWithLockById(accountId.getValue())
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Override
    public void releaseAccount(AccountId accountId) {
        // DB 비관적 락은 트랜잭션 종료 시 자동 해제 — 의도적 no-op
    }
}
```

`AccountNotFoundException`은 Plan 1에서 도입된 것을 재사용 (없는 계좌를 잠그려는 시도 = 404 경로).

`NoOpsAccountLock.java` **삭제**.

### 4-3. `SendMoneyService` 재구성

Plan 1(Result)·Plan 5(Clock, 자기 송금 차단) 반영 후 기준:

```java
@Override
public SendMoneyResult sendMoney(SendMoneyCommand command) {
    if (command.getMoney().isGreaterThan(moneyTransferProperties.maximumTransferThreshold()))
        return SendMoneyResult.failure(FailureReason.THRESHOLD_EXCEEDED);

    // 1. 데드락 방지: ID 오름차순으로 두 계좌 모두 선(先)락
    List<AccountId> lockOrder = Stream.of(command.getSourceAccountId(), command.getTargetAccountId())
            .sorted(Comparator.comparing(AccountId::getValue))
            .toList();
    lockOrder.forEach(accountLock::lockAccount);
    try {
        // 2. 락 이후 로드 → 읽은 잔액은 커밋까지 불변 (TOCTOU 해결)
        LocalDateTime baselineDate = LocalDateTime.now(clock)
                .minusDays(moneyTransferProperties.historyLookbackDays());
        Account sourceAccount = loadAccountPort.loadAccount(command.getSourceAccountId(), baselineDate);
        Account targetAccount = loadAccountPort.loadAccount(command.getTargetAccountId(), baselineDate);

        // 3. 도메인 로직
        if (!sourceAccount.withdraw(command.getMoney(), command.getTargetAccountId(), LocalDateTime.now(clock)))
            return SendMoneyResult.failure(FailureReason.INSUFFICIENT_BALANCE);
        targetAccount.deposit(command.getMoney(), command.getSourceAccountId(), LocalDateTime.now(clock));

        // 4. 영속화 — 예외 시 트랜잭션 롤백과 함께 락 자동 해제
        updateAccountStatePort.updateActivities(sourceAccount);
        updateAccountStatePort.updateActivities(targetAccount);
        return SendMoneyResult.success();
    } finally {
        // DB 락 구현에선 no-op이지만, 포트 계약상 다른 구현(예: 외부 락 서비스) 대비 보장
        lockOrder.forEach(accountLock::releaseAccount);
    }
}
```

삭제되는 기존 private 메서드: `executeMoneyTransfer`, `withdrawFromSourceAccount`, `depositToTargetAccount`, `releaseLock` — 락 시나리오가 단순해져 헬퍼 분해가 오히려 흐름을 가렸던 문제도 해소. `getAccountId(...)` 헬퍼는 락을 커맨드의 ID로 걸게 되면서 불필요(로드 전에 ID를 이미 앎).

### 4-4. `AccountPersistenceAdapter.updateActivities` 배치화

```java
// Before: 루프 내 개별 save()
// After
@Override
public void updateActivities(Account account) {
    List<ActivityJpaEntity> newActivities = account.getActivityWindow().getActivities().stream()
            .filter(activity -> activity.getId() == null)
            .map(accountMapper::mapToJpaEntity)
            .toList();
    activityRepository.saveAll(newActivities);
}
```

### 4-5. `GetAccountBalanceService` 트랜잭션 경계

클래스에 `@Transactional(readOnly = true)` 부여 (빈 등록 문제 자체는 비목표 — 평가 §5-1 별도 작업이나, 등록될 때를 대비한 경계 정리).

## 5. 테스트 계획

### 5-1. 신규: `SendMoneyConcurrencyTest` (통합)

**파일**: `src/test/java/dev/haja/buckpal/SendMoneyConcurrencyTest.java`
**시나리오 — 초과 인출 방지 (결함 (e) 검증)**:

```
@SpringBootTest + @Sql(잔액 1000인 계좌 1, 계좌 2·3 시드)
given: 계좌1 잔액 1000
when:  계좌1→2로 700원, 계좌1→3으로 700원을
       CountDownLatch로 동시 시작 (ExecutorService 2스레드, 각자 UseCase 빈 호출)
then:  정확히 1건 success, 1건 INSUFFICIENT_BALANCE
       최종 계좌1 잔액 == 300 (음수 아님)
```

구현 메모:

- 각 스레드가 `SendMoneyUseCase` 빈을 직접 호출 (`@Transactional` 프록시가 스레드별 트랜잭션 생성)
- `@DisplayName` 한국어: "동시 송금 시 초과 인출이 발생하지 않는다"
- Mockito 불사용이므로 `@DisabledInNativeImage` 불필요 — 단, H2 파일/메모리 모드에서 FOR UPDATE 동작 확인
- **회귀 확인**: `PessimisticAccountLock`을 일시적으로 no-op으로 바꾸면 이 테스트가 실패(둘 다 성공, 잔액 -400)하는지 확인 후 복원 — 테스트가 실제로 무는지 검증

### 5-2. 데드락 회귀 테스트 (선택적, 권장)

계좌1→2와 계좌2→1 송금을 동시 실행 → 둘 다 (성공 또는 잔액부족으로) **완료**되고 타임아웃/교착이 없는지 검증. 정렬 락의 효과 확인.

### 5-3. 기존 테스트 수정

| 테스트 | 수정 내용 |
|---|---|
| `SendMoneyServiceTest` | 락 검증 순서 변경: `lockAccount`가 **로드 전에, 정렬 순서로 2회** 호출됨을 검증. `releaseAccount`는 finally에서 2회. 출금 실패 케이스: 락 2개 모두 획득 후 실패 반환 — 기존 "소스만 잠김" 단언을 새 의미론("정렬 순서로 둘 다 잠김")으로 갱신 |
| `AccountPersistenceAdapterTest` | `updateActivities` 배치화 후에도 통과 확인. `findWithLockById` 케이스 추가(존재/부재) |
| `SendMoneySystemTest` | 무수정 통과 예상 (E2E 회귀 검증 역할) |

## 6. 작업 순서와 커밋 분할

1. **커밋 1 — 락 어댑터**: `findWithLockById` 추가, `PessimisticAccountLock` 신규, `NoOpsAccountLock` 삭제, 어댑터 테스트
2. **커밋 2 — 서비스 재구성**: 정렬 선락 + try/finally + 로드 순서 이동, `SendMoneyServiceTest` 갱신
3. **커밋 3 — 동시성 검증**: `SendMoneyConcurrencyTest` 신설 (+ 데드락 회귀 테스트), `updateActivities` 배치화, `GetAccountBalanceService` readOnly
4. 각 커밋 후 `./gradlew test`, 마지막에 `./gradlew check`

## 7. 리스크와 롤백

- **성능**: 두 계좌 행이 트랜잭션 내내 잠김 → 같은 계좌에 대한 송금이 직렬화된다. 은행 도메인에서 의도된 트레이드오프이며, 락 범위가 계좌 2행으로 최소화되어 있음
- **락 타임아웃**: H2가 `lock.timeout` 힌트를 무시할 수 있음 — 테스트에서 교착 대신 무한 대기가 생기면 테스트 레벨 타임아웃(`assertTimeoutPreemptively` 또는 `Future.get(timeout)`)으로 방어
- **의미론 변경**: "출금 실패 시 소스만 잠금"이라는 기존(책 원본) 의미론이 "둘 다 선락"으로 바뀜 — `SendMoneyServiceTest`의 기존 단언과 충돌하므로 테스트 갱신이 필수라는 점을 커밋 메시지에 명시
- **네이티브 이미지**: 동시성 테스트는 Mockito 미사용이라 네이티브 테스트에 포함됨 — `nativeTest`에서도 통과해야 함 (스레드/latch는 문제없음)
- 롤백: 커밋 1~3 순차 revert. DB 스키마 변경 없음(잠금은 쿼리 수준)

## 8. 완료 기준 (DoD)

- [ ] `./gradlew check` 통과
- [ ] `NoOpsAccountLock.java` 삭제, `PessimisticAccountLock`이 유일한 `AccountLock` 빈
- [ ] `SendMoneyConcurrencyTest` 통과 + no-op으로 바꾸면 실패함을 확인한 기록
- [ ] `SendMoneyService`에 try/finally 존재, 락 획득이 로드보다 앞
- [ ] A→B / B→A 동시 송금이 교착 없이 완료

## 9. 다른 Plan과의 의존 관계

- **선행 (필수)**: Plan 1 — `SendMoneyResult` 시그니처. Plan 5 — `withdraw/deposit`의 timestamp 파라미터·`Clock` 주입, 자기 송금 차단(같은 ID 이중 락 방지)
- **선행 (권장)**: Plan 2 — `moneyTransferProperties.historyLookbackDays()` 사용
- 전체 5개 계획 중 **마지막**에 실행: `Plan 3 → 1 → 2 → 5 → 4`
