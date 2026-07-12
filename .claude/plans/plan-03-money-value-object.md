# Plan 3 — Money 값 객체 정비 (Quick Win)

> **근거**: [아키텍처 평가](../reviews/2026-07-12-java-architecture-review.md) §7(Money 값 객체 품질), §8-1(0원 송금 불일치), §8-2(null NPE 선행)
> **우선순위**: 3 (한 줄짜리 수정, 즉시 효과)
> **실행 순서**: **가장 먼저** (다른 계획과 충돌 없는 독립 quick win)

---

## 1. 배경과 문제

| 위치 | 문제 | 심각도 |
|---|---|---|
| `Money.java:11` | `public static Money ZERO` — **final이 아니어서 외부에서 `Money.ZERO = ...` 재할당 가능** | 높음 |
| `Money.java:31` | `isGreaterThan`이 `compareTo(...) >= 1` — `compareTo`의 계약은 "양수/음수/0"이므로 `> 0`이 올바른 코드. `BigInteger`가 우연히 ±1만 반환해 동작할 뿐 | 중간 |
| `Money.java:15-19` | 정적 `add`/`subtract`와 인스턴스 `plus`/`minus`가 완전 중복 | 낮음 |
| `SendMoneyCommand.java:23` | `isPositiveOrZero()` — **0원 송금 허용**. DTO(`SendMoneyReqDto`)는 `@Positive`로 0을 거부해 계층 간 검증 불일치. 커맨드가 최종 방어선인데 더 느슨함 | 중간 |
| `SendMoneyCommand.java:19-28` | `money.isPositiveOrZero()`가 `validateSelf()`(@NotNull 검증)보다 먼저 실행 — money가 null이면 검증 메시지 대신 맨 NPE | 중간 |
| 테스트 부재 | **`MoneyTest`가 없다.** 값 객체이자 Kotlin 마이그레이션 1순위 대상인데 안전망이 없음 | 중간 |
| 문서 불일치 | `.claude/CLAUDE.md`는 "Money: BigDecimal 래핑"이라 하지만 실제는 `BigInteger` | 낮음 |

## 2. 목표 / 비목표

### 목표

- `ZERO` 불변화, `isGreaterThan` 계약 준수 수정
- 연산 API를 인스턴스 메서드(`plus`/`minus`)로 단일화
- `SendMoneyCommand`의 0원 허용 제거 + 검증 순서 재배열
- `MoneyTest` 신설로 값 객체 안전망 확보 (마이그레이션 대비)
- 문서-코드 불일치 정정

### 비목표 (Non-Goals)

- **BigInteger → BigDecimal 전환**: 통화 스케일 도입은 Kotlin 마이그레이션(값 객체 1순위) 스코프에서 설계. 이번에 안전망(테스트)만 깔아둔다
- Kotlin `value class` 전환 자체 (CLAUDE.md 마이그레이션 계획의 별도 단계)
- `Money`에 통화(Currency) 개념 추가

## 3. 설계 결정

### 3-1. 정적 메서드 제거 방향

`add(a, b)`와 `a.plus(b)` 중 **인스턴스 메서드를 남긴다**. 이유:

- Kotlin 마이그레이션 시 `operator fun plus`로 자연스럽게 이어짐
- 메서드 레퍼런스도 인스턴스 방식이 그대로 동작: `reduce(Money.ZERO, Money::plus)` (`BiFunction<Money, Money, Money>`로 해석됨)

**기각한 대안**: 정적 유지 + 인스턴스 제거 — Kotlin 연산자 오버로딩과의 연속성이 없어 기각.

### 3-2. `SendMoneyCommand` 검증 순서

필드 대입 → `validateSelf()`(null은 `@NotNull` 메시지로) → 도메인 규칙(양수) 순으로 고정. Bean Validation이 잡을 수 있는 것은 Bean Validation에게, 도메인 규칙은 그 뒤에.

## 4. 상세 변경 내역

### 4-1. `Money.java`

```java
// Before
public static Money ZERO = Money.of(0L);
public static Money add(Money a, Money b) { return new Money(a.amount.add(b.amount)); }
public static Money subtract(Money a, Money b) { return new Money(a.amount.subtract(b.amount)); }
public boolean isGreaterThan(Money money) { return this.amount.compareTo(money.amount) >= 1; }

// After
public static final Money ZERO = Money.of(0L);
// add / subtract 삭제 (plus / minus로 단일화)
public boolean isGreaterThan(Money money) { return this.amount.compareTo(money.amount) > 0; }
```

### 4-2. 호출부 교체 (정적 → 인스턴스)

| 파일 | Before | After |
|---|---|---|
| `Account.java:67-71` (`calculateBalance`) | `Money.add(this.baselineBalance, this.activityWindow.calculateBalance(this.id))` | `this.baselineBalance.plus(this.activityWindow.calculateBalance(this.id))` |
| `Account.java:100-105` (`mayWithdraw`) | `Money.add(this.calculateBalance(), money.negate()).isPositiveOrZero()` | `this.calculateBalance().minus(money).isPositiveOrZero()` — negate+add보다 의도가 직접적 |
| `ActivityWindow.java:34-42` (`calculateBalance`) | `reduce(Money.ZERO, Money::add)` ×2, `Money.add(depositBalance, withdrawalBalance.negate())` | `reduce(Money.ZERO, Money::plus)` ×2, `depositBalance.minus(withdrawalBalance)` |
| `AccountMapper.java:22-24` | `Money.subtract(Money.of(depositBalance), Money.of(withdrawalBalance))` | `Money.of(depositBalance).minus(Money.of(withdrawalBalance))` |

### 4-3. `SendMoneyCommand.java`

```java
// Before (19-28행)
public SendMoneyCommand(AccountId sourceAccountId, AccountId targetAccountId, Money money) {
    this.sourceAccountId = sourceAccountId;
    this.targetAccountId = targetAccountId;
    this.money = money;
    if (!money.isPositiveOrZero()) {                    // ← null이면 NPE, 0원 허용
        throw new IllegalArgumentException(
            "The money amount must be greater than or equal to zero");
    }
    validateSelf();
}

// After
public SendMoneyCommand(AccountId sourceAccountId, AccountId targetAccountId, Money money) {
    this.sourceAccountId = sourceAccountId;
    this.targetAccountId = targetAccountId;
    this.money = money;
    validateSelf();                                     // @NotNull 검증 선행 → null이면 ConstraintViolationException
    if (!money.isPositive()) {                          // 0원 차단 — DTO의 @Positive와 일치
        throw new IllegalArgumentException("송금액은 0보다 커야 합니다");
    }
}
```

### 4-4. 문서 정정

`.claude/CLAUDE.md`의 "Money: 금액 값 객체 - BigDecimal 래핑" → "BigInteger 래핑 (Kotlin 마이그레이션 시 BigDecimal 전환 예정)".

## 5. 테스트 계획

### 5-1. 신규 `MoneyTest`

**파일**: `src/test/java/dev/haja/buckpal/account/domain/MoneyTest.java` — 한국어 `@DisplayName`, given-when-then 명명.

| 케이스 | 검증 |
|---|---|
| `ZERO` 상수 | `Money.ZERO.equals(Money.of(0L))`, `isPositiveOrZero() == true`, `isPositive() == false` |
| `plus` / `minus` | `Money.of(3).plus(Money.of(4)) == Money.of(7)`, `minus`로 음수 결과 생성 가능 |
| `negate` | `Money.of(5).negate() == Money.of(-5)`, 이중 negate 항등 |
| 부호 판별 4종 | 양수/0/음수 각각에 대해 `isPositive`/`isPositiveOrZero`/`isNegative`/`isNegativeOrZero` 진리표 검증 |
| **`isGreaterThan` 경계값** | `Money.of(5).isGreaterThan(Money.of(5)) == false` ← **버그 수정 검증 핵심**, `of(6).isGreaterThan(of(5)) == true` |
| `isGreaterThanOrEqualTo` | 같은 값 true, 작은 값 false |
| 불변성 | `plus` 호출 후 원본 인스턴스 값 불변 |
| `Long` 경계 | `Money.of(Long.MAX_VALUE).plus(Money.of(1))` — BigInteger라 오버플로 없이 동작함을 문서화하는 테스트 |

### 5-2. `SendMoneyCommand` 검증 테스트 (신규 케이스)

기존 테스트 클래스가 없으므로 `SendMoneyCommandTest` 신설:

- 0원 송금 → `IllegalArgumentException`
- 음수 송금 → `IllegalArgumentException`
- `money == null` → **`ConstraintViolationException`** (NPE가 아님 — 순서 재배열 검증)
- `sourceAccountId == null` → `ConstraintViolationException`
- 정상 생성 → 예외 없음

### 5-3. 기존 테스트 영향

- `AccountTest`, `ActivityWindowTest`: 호출부 교체는 내부 구현이라 무영향 — 그대로 통과해야 함(회귀 검증 역할)
- `SendMoneyControllerTest`의 음수 금액 케이스: DTO 검증에서 걸러지므로 무영향

## 6. 작업 순서와 커밋 분할

단일 커밋으로 충분한 규모지만, 리뷰 편의상 2개 권장:

1. **커밋 1 — Money 정비**: `ZERO` final, `isGreaterThan` 수정, add/subtract 제거 + 호출부 4곳 교체, `MoneyTest` 신설
2. **커밋 2 — 커맨드 검증**: `SendMoneyCommand` 순서 재배열 + `isPositive()`, `SendMoneyCommandTest` 신설, CLAUDE.md 정정

## 7. 리스크와 롤백

- **행동 변화 1**: 0원 송금이 커맨드 레벨에서 거부됨 — 기존에도 웹 경로는 DTO가 막고 있었으므로 실사용 영향 없음. 유스케이스를 직접 호출하는 테스트 중 0원을 쓰는 곳 없음(확인됨: 300L/500L 사용)
- **행동 변화 2**: money null 시 NPE → `ConstraintViolationException`. Plan 1의 `GlobalExceptionHandler` 확장(400 매핑)과 맞물리면 완결. Plan 1 이전에는 여전히 500이나, 예외 타입이 더 정확해지는 방향이므로 문제없음
- `isGreaterThan` 수정은 `BigInteger.compareTo`가 ±1만 반환하는 현재 구현에서는 **동작 동일** — 순수 계약 준수 수정, 회귀 위험 없음
- 롤백: 단순 revert

## 8. 완료 기준 (DoD)

- [ ] `./gradlew check` 통과
- [ ] `grep -n "Money.add\|Money.subtract" src/main/java` 결과 0건
- [ ] `Money.ZERO`가 `public static final`
- [ ] `MoneyTest`·`SendMoneyCommandTest` 존재, 경계값 케이스 포함
- [ ] `.claude/CLAUDE.md`의 Money 설명이 실제 구현과 일치

## 9. 다른 Plan과의 의존 관계

- **선행 없음** — 전체 계획 중 가장 먼저 실행 (독립 quick win)
- **후행**: Plan 1 (`SendMoneyCommand` 생성 흐름과 `GlobalExceptionHandler` 매핑이 이 계획의 결과 위에 쌓임)
- Kotlin 마이그레이션 시 이 계획의 `MoneyTest`가 전환 안전망이 된다 — 마이그레이션 커밋에서 테스트 무수정 통과가 목표
