# Plan 5 — 도메인 캡슐화 강화 + Clock 주입

> **근거**: [아키텍처 평가](../reviews/2026-07-12-java-architecture-review.md) §6(도메인 모델 캡슐화 약점 전체)
> **우선순위**: 5 (Kotlin 마이그레이션 시 설계에 함께 반영 권장)
> **실행 순서**: Plan 2 다음, Plan 4 이전 (Plan 4가 이 계획의 도메인 시그니처에 의존)

---

## 1. 배경과 문제

| # | 문제 | 위치 |
|---|---|---|
| §6-1 | `account.getActivityWindow().addActivity(...)`로 **잔액 검증 없이 활동 추가 가능** — 애그리게잇 불변식 우회 | `ActivityWindow.java:45-47`, `Account.java:15`(@Getter) |
| §6-2 | `ActivityWindow`의 List 생성자는 참조를 그대로 보관, varargs 생성자는 복사 — **방어적 복사 비일관**. 외부 리스트 수정 시 내부 상태 오염 | `ActivityWindow.java:19-25` |
| §6-3 | `LocalDateTime.now()`가 도메인(`Account.withdraw/deposit`)과 서비스에 직접 박혀 있어 **시간 제어 불가** — 테스트에서 타임스탬프 검증 불가능 | `Account.java:88,121`, `SendMoneyService.java:37`, `GetAccountBalanceService.java:18` |
| §6-4 | `Account.withoutId()` 계좌에서 `withdraw()` 호출 시 `Activity`의 `@NonNull ownerAccountId`에 null이 들어가 **맥락 없는 NPE** | `Account.java:84-89` |
| §6-5 | `SendMoneyCommand`에 source ≠ target 검증이 없어 **자기 자신에게 송금 가능** — 무의미한 활동 2건 생성, 실제 락 도입 시(Plan 4) 같은 계좌 이중 락 위험 | `SendMoneyCommand.java` |
| §6-6 | `Activity`가 owner/source/target의 관계 불변식(출금: owner==source, 입금: owner==target)을 강제하지 않음 | `Activity.java:49-61` |

## 2. 목표 / 비목표

### 목표

- 애그리게잇 루트(`Account`)를 통해서만 활동이 추가되도록 캡슐화
- `ActivityWindow` 생성자 방어적 복사 일관화
- 도메인에서 시계 의존 제거 — 타임스탬프를 파라미터로 수령, 서비스가 주입된 `Clock`으로 생성
- `withoutId` 계좌 조작 시 명확한 실패 메시지
- 자기 송금 차단
- `Activity` 관계 불변식을 팩토리 메서드로 표현

### 비목표 (Non-Goals)

- `Account`/`ActivityWindow`의 Kotlin 전환 (마이그레이션 2순위 — 이 계획은 Java에서 설계를 바로잡아 전환을 쉽게 만드는 선행 작업)
- 락/저장 흐름 변경 (Plan 4)
- `ActivityWindow`의 기간 무결성(윈도우 밖 timestamp 활동 거부) — 과설계 판단, 보류

## 3. 설계 결정

### 3-1. `addActivity` 접근 제어: package-private

`Account`와 `ActivityWindow`는 같은 `domain` 패키지이므로 `addActivity`를 **package-private**으로 좁히면 도메인 밖(어댑터·서비스·테스트의 우회 호출)에서는 차단되고 `Account`는 그대로 쓸 수 있다.

- 프로덕션 호출부 확인 결과: `Account.withdraw/deposit`만 호출 — 외부 사용 없음 (`AccountPersistenceAdapter`는 `getActivities()` 읽기만)
- **기각한 대안 1**: `ActivityWindow`를 `Account` 내부로 흡수 — 책의 구조에서 너무 멀어짐
- **기각한 대안 2**: getter 제거 — 영속성 어댑터가 활동 목록을 읽어야 하므로 읽기 노출은 유지

### 3-2. 시간 주입 방식: "도메인은 시간을 받는다"

**선택**: `withdraw`/`deposit`에 `LocalDateTime timestamp` 파라미터 추가. `Clock` 빈은 애플리케이션 계층(서비스)이 주입받는다.

- 도메인 객체에 `Clock` 필드를 넣는 대안은 기각 — 도메인 엔티티가 인프라 개념을 들고 다니게 되고, 매퍼가 재구성할 때마다 Clock을 끼워야 함
- "시각 결정"은 유스케이스의 책임(언제 일어난 거래로 기록할지), "그 시각으로 활동 생성"은 도메인의 책임 — 관심사가 자연스럽게 갈림
- 테스트는 `Clock.fixed()`로 결정론적 타임스탬프 검증 가능

### 3-3. `Activity` 불변식: 팩토리 메서드

public 생성자 대신 의도가 드러나는 팩토리 2개를 제공해 owner/source/target 관계를 타입 수준에서 고정:

```java
Activity.withdrawal(ownerAccountId, targetAccountId, timestamp, money)  // owner == source
Activity.deposit(ownerAccountId, sourceAccountId, timestamp, money)     // owner == target
```

기존 id 포함 전체 필드 생성자는 매퍼(재구성)용으로 유지.

## 4. 상세 변경 내역

### 4-1. `ActivityWindow.java`

```java
// 생성자 — 방어적 복사 (§6-2)
public ActivityWindow(@NonNull List<Activity> activities) {
    this.activities = new ArrayList<>(activities);
}

// 접근 축소 (§6-1): public → package-private
void addActivity(Activity activity) {
    this.activities.add(activity);
}
```

### 4-2. `Account.java`

```java
// 가드 (§6-4) + 시간 파라미터 (§6-3) + 팩토리 사용 (§6-6)
public boolean withdraw(Money money, AccountId targetAccountId, LocalDateTime timestamp) {
    AccountId ownerId = requirePersistedId();
    if (!mayWithdraw(money)) return false;
    this.activityWindow.addActivity(
            Activity.withdrawal(ownerId, targetAccountId, timestamp, money));
    return true;
}

public void deposit(Money money, AccountId sourceAccountId, LocalDateTime timestamp) {
    AccountId ownerId = requirePersistedId();
    this.activityWindow.addActivity(
            Activity.deposit(ownerId, sourceAccountId, timestamp, money));
}

private AccountId requirePersistedId() {
    return getId().orElseThrow(() -> new IllegalStateException(
            "영속화되지 않은 계좌(id 없음)에서는 입출금할 수 없습니다"));
}
```

`deposit`의 반환 타입 void는 Plan 1에서 선행 적용됨 — 이 계획에서는 파라미터만 추가.
`import java.time.LocalDateTime`은 유지(파라미터 타입), **`LocalDateTime.now()` 호출은 도메인에서 완전 제거**.

### 4-3. `Activity.java` — 팩토리 추가

```java
public static Activity withdrawal(
        @NonNull Account.AccountId ownerAccountId,
        @NonNull Account.AccountId targetAccountId,
        @NonNull LocalDateTime timestamp,
        @NonNull Money money) {
    return new Activity(ownerAccountId, ownerAccountId, targetAccountId, timestamp, money);
}

public static Activity deposit(
        @NonNull Account.AccountId ownerAccountId,
        @NonNull Account.AccountId sourceAccountId,
        @NonNull LocalDateTime timestamp,
        @NonNull Money money) {
    return new Activity(ownerAccountId, sourceAccountId, ownerAccountId, timestamp, money);
}
```

id 없는 기존 public 생성자(`Activity.java:49-61`)는 **private으로 축소** (팩토리 경유 강제). id 포함 전체 생성자는 `AccountMapper` 재구성용으로 public 유지.

### 4-4. `Clock` 빈과 서비스 주입

**`BuckPalConfiguration`** (Plan 2 이후 `configuration` 패키지):

```java
@Bean
public Clock clock() {
    return Clock.systemDefaultZone();
}
```

**`SendMoneyService`**: `private final Clock clock;` 추가, `LocalDateTime.now()` → `LocalDateTime.now(clock)` (baselineDate 계산과 withdraw/deposit 타임스탬프 전달 — Plan 4의 재구성 코드에 반영됨).

**`GetAccountBalanceService`**: 동일하게 `Clock` 주입, `loadAccount(accountId, LocalDateTime.now(clock))`.

### 4-5. `SendMoneyCommand` — 자기 송금 차단 (§6-5)

Plan 3에서 재배열된 생성자에 검증 추가:

```java
validateSelf();
if (sourceAccountId.equals(targetAccountId)) {
    throw new IllegalArgumentException("출금 계좌와 입금 계좌는 달라야 합니다");
}
if (!money.isPositive()) { ... }
```

DTO(`SendMoneyReqDto`) 레벨 교차 필드 검증은 비목표 — 커맨드가 최종 방어선이며, 응답은 Plan 1의 `ConstraintViolationException`/`IllegalArgumentException` 핸들링 경로를 따른다. (`IllegalArgumentException` → 400 매핑 핸들러가 없다면 Plan 1의 `GlobalExceptionHandler`에 추가.)

### 4-6. 호출부 영향 정리

| 파일 | 영향 |
|---|---|
| `SendMoneyService` | withdraw/deposit 호출에 `LocalDateTime.now(clock)` 전달 |
| `AccountMapper` | 무영향 (id 포함 생성자 사용) |
| `AccountTest` | 시그니처 변경 반영 + 신규 케이스 |
| `ActivityTestData` (테스트 팩토리) | 빌더가 id 포함 생성자를 쓰는지 확인 — private화된 생성자를 쓰고 있으면 팩토리/전체 생성자로 교체 |
| Kotlin 학습 모듈 | `src/*/kotlin`은 buckpal 도메인을 참조하지 않음 — 무영향 (스코프 밖) |

## 5. 테스트 계획

한국어 `@DisplayName`, given-when-then 명명 (기존 `AccountTest`는 이 기회에 표준 준수로 정비).

| 테스트 | 변경/신규 | 내용 |
|---|---|---|
| `AccountTest` | 수정+신규 | 기존 케이스에 고정 timestamp 전달. **신규**: ① `withoutId` 계좌 withdraw/deposit → `IllegalStateException` + 메시지 검증 ② 전달한 timestamp가 생성된 `Activity`에 그대로 기록되는지 검증 (시계 주입의 효용 증명) |
| `ActivityWindowTest` | 신규 케이스 | **방어적 복사**: 생성자에 넘긴 리스트를 밖에서 `add()` 해도 `getActivities()` 크기 불변 |
| `ActivityTest` | 신규 | `withdrawal()` 팩토리: owner==source, `deposit()` 팩토리: owner==target 검증 |
| `SendMoneyCommandTest` | 신규 케이스 | source == target → `IllegalArgumentException` (Plan 3의 테스트 클래스에 추가) |
| `SendMoneyServiceTest` | 수정 | `Clock.fixed(...)`를 서비스 생성자에 주입, withdraw/deposit mock 시그니처에 timestamp matcher(`any(LocalDateTime.class)`) 추가 |
| 컴파일 검증 | — | `addActivity` package-private화 후 도메인 밖에서 호출하는 코드가 없음을 컴파일로 확인 |

## 6. 작업 순서와 커밋 분할

1. **커밋 1 — 캡슐화**: `ActivityWindow` 방어적 복사 + `addActivity` 축소, `Activity` 팩토리 + 생성자 private화, 테스트
2. **커밋 2 — 시간 주입**: `Clock` 빈, `Account` 시그니처 변경(가드 포함), 서비스 2곳 `now(clock)` 전환, 테스트 갱신
3. **커밋 3 — 자기 송금 차단**: `SendMoneyCommand` 검증 + 테스트 (+ 필요시 핸들러 추가)

## 7. 리스크와 롤백

- **공개 API(도메인 메서드) 시그니처 변경**: `withdraw(Money, AccountId)` → 3-파라미터. 도메인을 직접 쓰는 곳이 서비스·테스트뿐이라 파급 작음(컴파일러가 전수 색출)
- **`Activity` 생성자 private화**: 테스트 팩토리(`ActivityTestData`)가 어떤 생성자를 쓰는지에 따라 수정 필요 — 실행 시점에 확인
- `addActivity` package-private화는 **도메인 패키지 내 테스트**(`src/test/java/dev/haja/buckpal/account/domain/`)에서는 여전히 호출 가능 — 테스트 편의는 유지됨
- Plan 4가 이 시그니처를 전제하므로, 이 계획을 건너뛰고 Plan 4를 실행하면 코드 스케치가 맞지 않음 — 순서 엄수
- 롤백: 커밋 단위 revert (스키마·설정 무관)

## 8. 완료 기준 (DoD)

- [ ] `./gradlew check` 통과
- [ ] `grep -rn "LocalDateTime.now()" src/main/java/dev/haja/buckpal/account/domain/` 결과 0건
- [ ] `grep -rn "LocalDateTime.now()" src/main/java/` 결과 0건 (모두 `now(clock)`)
- [ ] `ActivityWindow.addActivity`가 package-private
- [ ] 자기 송금 요청이 400으로 거부됨 (수동 확인)
- [ ] `withoutId` 계좌 입출금 시 한국어 메시지의 `IllegalStateException`

## 9. 다른 Plan과의 의존 관계

- **선행**: Plan 1 (`deposit` void화), Plan 3 (`SendMoneyCommand` 생성자 재배열), Plan 2 (`BuckPalConfiguration` 위치 확정 — Clock 빈 추가 위치)
- **후행**: Plan 4 (이 계획의 `withdraw/deposit(…, timestamp)` 시그니처와 자기 송금 차단을 전제로 서비스 재구성)
- **Kotlin 마이그레이션 연계**: 이 계획 완료 후 `Account`/`ActivityWindow`는 가변 최소화·시계 무의존 상태가 되어 data class/불변 컬렉션 전환이 수월해진다
