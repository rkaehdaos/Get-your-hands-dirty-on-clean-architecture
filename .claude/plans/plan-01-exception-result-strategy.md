# Plan 1 — 예외 처리 전략 정비 + Result 타입 도입

> **근거**: [아키텍처 평가](../reviews/2026-07-12-java-architecture-review.md) §4(예외 처리 전략 부재), §5-3(deposit 죽은 코드)
> **우선순위**: 1 (사용자에게 500이 노출되는 현재진행형 결함)
> **실행 순서**: Plan 3 다음 — 자세한 순서는 문서 말미 "다른 Plan과의 의존 관계" 참조

---

## 1. 배경과 문제

### 1-1. 도메인/비즈니스 예외가 500으로 누수

`GlobalExceptionHandler`는 `MethodArgumentNotValidException` 하나만 처리한다. 그 결과:

| 예외 | 발생 지점 | 현재 응답 | 기대 응답 |
|---|---|---|---|
| `ThresholdExceededException` | `SendMoneyService.checkThreshold()` (`SendMoneyService.java:90-94`) | **500** | 422 |
| `EntityNotFoundException` (jakarta.persistence) | `AccountPersistenceAdapter.java:32` | **500** | 404 |
| `ConstraintViolationException` | `SendMoneyCommand` 자가 검증 (`SelfValidating.validateSelf()`) | **500** | 400 |

특히 `EntityNotFoundException`은 **JPA 예외가 어댑터 → 애플리케이션 → 웹까지 누수**되는 구조로, 아웃고잉 포트의 계약이 영속성 기술에 오염되어 있다.

### 1-2. 실패 사유 전달 불가

`SendMoneyUseCase.sendMoney()`가 `boolean`을 반환해 클라이언트는 400 응답만 받을 뿐 잔액 부족인지, 무엇이 문제인지 알 수 없다 (`SendMoneyController.java:26-30`).

### 1-3. `Account.deposit()`이 항상 true 반환 → 죽은 분기

`Account.java:116-125` — deposit은 실패 경로가 없는데 `boolean`을 반환한다. 이 때문에 `SendMoneyService.java:71-79`(`depositToTargetAccount`)의 실패 분기는 **도달 불가능한 죽은 코드**다.

## 2. 목표 / 비목표

### 목표

- 유스케이스 실패를 **`SendMoneyResult` 타입**으로 표현 (사용자 결정 사항)
- JPA 예외 누수 차단: 포트 계약 예외 `AccountNotFoundException` 도입 → 404 매핑
- `GlobalExceptionHandler`를 완결된 예외 → HTTP 매핑 테이블로 확장
- `Account.deposit()`을 `void`로 바꾸고 죽은 분기 제거
- `ThresholdExceededException` 삭제 (Result로 흡수)

### 비목표 (Non-Goals)

- `GetAccountBalanceService` 빈 등록/조회 API 신설 (평가 §5-1 — 별도 작업)
- 락 관련 구조 변경 (Plan 4 스코프)
- RFC 9457 `ProblemDetail` 전면 도입 (단순 `{code, message}` record로 시작, 추후 전환 가능)

## 3. 설계 결정

### 3-1. Result 타입 vs 예외 — 역할 분담

| 상황 | 표현 방식 | 이유 |
|---|---|---|
| 잔액 부족, 한도 초과 | `SendMoneyResult.failure(reason)` | 정상 흐름에서 **예상되는 비즈니스 결과**. 예외는 제어 흐름 도구가 아님 |
| 존재하지 않는 계좌 | `AccountNotFoundException` | 클라이언트가 잘못된 리소스를 참조한 것 — 유스케이스 관점에선 전제조건 위반 |
| 커맨드 검증 실패 | `ConstraintViolationException` (기존) | 입력 자체가 불량 — 유스케이스 진입 전 차단 |

**기각한 대안**: 계좌 없음도 Result에 넣는 방식(`SOURCE_ACCOUNT_NOT_FOUND` 등) — 실패 사유 enum이 "비즈니스 결과"와 "요청 오류"를 뒤섞게 되어 기각. 404와 422의 HTTP 의미 구분도 자연스럽게 유지된다.

### 3-2. Kotlin 마이그레이션 연계

Java record + enum 조합은 마이그레이션 시 다음 sealed class로 발전시킨다 (이 계획에서는 구현하지 않고 경로만 확보):

```kotlin
sealed interface SendMoneyResult {
    data object Success : SendMoneyResult
    data class Failure(val reason: FailureReason) : SendMoneyResult
}
```

## 4. 상세 변경 내역

### 4-1. 신규: `SendMoneyResult`

**파일**: `src/main/java/dev/haja/buckpal/account/application/port/in/SendMoneyResult.java`

```java
package dev.haja.buckpal.account.application.port.in;

import java.util.Objects;

/**
 * 송금 유스케이스의 결과.
 * 성공 시 failureReason은 null, 실패 시 사유를 담는다.
 */
public record SendMoneyResult(boolean success, FailureReason failureReason) {

    public enum FailureReason {
        /** 출금 계좌 잔액 부족 */
        INSUFFICIENT_BALANCE,
        /** 최대 송금 한도 초과 */
        THRESHOLD_EXCEEDED
    }

    public static SendMoneyResult success() {
        return new SendMoneyResult(true, null);
    }

    public static SendMoneyResult failure(FailureReason reason) {
        return new SendMoneyResult(false, Objects.requireNonNull(reason));
    }
}
```

### 4-2. `SendMoneyUseCase` 시그니처 변경

```java
// Before
boolean sendMoney(SendMoneyCommand command);
// After
SendMoneyResult sendMoney(SendMoneyCommand command);
```

### 4-3. 신규: `AccountNotFoundException`

**파일**: `src/main/java/dev/haja/buckpal/account/application/port/out/AccountNotFoundException.java`

포트 계약의 일부이므로 `port/out`에 둔다 (어댑터가 던지고, 애플리케이션·웹이 인지).

```java
package dev.haja.buckpal.account.application.port.out;

import dev.haja.buckpal.account.domain.Account.AccountId;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(AccountId accountId) {
        super("계좌를 찾을 수 없습니다: " + accountId.getValue());
    }
}
```

`LoadAccountPort`의 Javadoc에 "계좌가 없으면 `AccountNotFoundException`을 던진다"를 계약으로 명시한다.

### 4-4. `AccountPersistenceAdapter` — JPA 예외 누수 제거

`AccountPersistenceAdapter.java:30-32`:

```java
// Before
AccountJpaEntity account = accountRepository.findById(accountId.getValue())
        .orElseThrow(EntityNotFoundException::new);
// After
AccountJpaEntity account = accountRepository.findById(accountId.getValue())
        .orElseThrow(() -> new AccountNotFoundException(accountId));
```

`jakarta.persistence.EntityNotFoundException` import 삭제.

### 4-5. `Account.deposit()` → void

`Account.java:116-125`:

```java
// Before
public boolean deposit(Money money, AccountId sourceAccountId) { ... return true; }
// After
public void deposit(Money money, AccountId sourceAccountId) {
    Activity deposit = new Activity(this.id, sourceAccountId, this.id,
            LocalDateTime.now(), money);
    this.activityWindow.addActivity(deposit);
}
```

Javadoc의 "성공 여부 반환" 문구도 함께 수정. (`LocalDateTime.now()` 제거는 Plan 5 스코프 — 여기서는 시그니처만.)

### 4-6. `SendMoneyService` 재구성

- `ThresholdExceededException` 삭제 → `checkThreshold`가 실패 시 `SendMoneyResult` 반환하도록 인라인화
- deposit 실패 분기(`depositToTargetAccount`, `SendMoneyService.java:71-79`) 삭제
- withdraw 실패 → `failure(INSUFFICIENT_BALANCE)`

```java
@Override
public SendMoneyResult sendMoney(SendMoneyCommand command) {
    if (command.getMoney().isGreaterThan(moneyTransferProperties.getMaximumTransferThreshold()))
        return SendMoneyResult.failure(FailureReason.THRESHOLD_EXCEEDED);

    // ... (계좌 로드 — Plan 4에서 락 순서와 함께 재배치 예정)

    accountLock.lockAccount(sourceAccountId);
    if (!sourceAccount.withdraw(command.getMoney(), targetAccountId)) {
        accountLock.releaseAccount(sourceAccountId);
        return SendMoneyResult.failure(FailureReason.INSUFFICIENT_BALANCE);
    }
    accountLock.lockAccount(targetAccountId);
    targetAccount.deposit(command.getMoney(), sourceAccountId);   // void — 실패 분기 없음

    updateAccountStates(sourceAccount, targetAccount);
    releaseLock(sourceAccountId, targetAccountId);
    return SendMoneyResult.success();
}
```

**파일 삭제**: `account/application/service/ThresholdExceededException.java`

### 4-7. `SendMoneyController` — Result → HTTP 매핑

**신규**: `SendMoneyFailureResDto` (adapter/in/web, record):

```java
record SendMoneyFailureResDto(String code, String message) {}
```

컨트롤러 (`SendMoneyController.java:21-31`):

```java
@PostMapping(path = "/accounts/send")
ResponseEntity<?> sendMoney(@Valid @RequestBody SendMoneyReqDto dto) {
    SendMoneyCommand command = new SendMoneyCommand(
            new AccountId(dto.sourceAccountId()),
            new AccountId(dto.targetAccountId()),
            Money.of(dto.amount()));
    SendMoneyResult result = sendMoneyUseCase.sendMoney(command);
    if (result.success()) return ResponseEntity.ok().build();
    return ResponseEntity.unprocessableEntity()
            .body(new SendMoneyFailureResDto(
                    result.failureReason().name(),
                    failureMessage(result.failureReason())));
}
```

`failureReason` → 한국어 메시지 매핑은 컨트롤러 private 메서드 또는 enum 내 `message` 필드로 (enum에 두면 웹 계층 밖에서도 재사용 가능 — enum 필드 방식 권장).

### 4-8. `GlobalExceptionHandler` 확장

`common/GlobalExceptionHandler.java`:

- **기존 핸들러**: `@ResponseStatus` + `ResponseEntity` 이중 지정 제거 (`ResponseEntity`만 유지)
- **추가 핸들러**:

```java
@ExceptionHandler(AccountNotFoundException.class)
public ResponseEntity<Map<String, String>> handleAccountNotFound(AccountNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("code", "ACCOUNT_NOT_FOUND", "message", ex.getMessage()));
}

@ExceptionHandler(ConstraintViolationException.class)
public ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException ex) {
    Map<String, String> errors = ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                    v -> v.getPropertyPath().toString(),
                    ConstraintViolation::getMessage,
                    (a, b) -> a));
    return ResponseEntity.badRequest().body(errors);
}
```

주의: `common` 패키지가 `account.application.port.out`의 예외를 참조하게 되므로, ArchUnit 규칙(`DependencyRuleTests`)에 걸리지 않는지 확인 — 현재 규칙은 `account` 패키지 내부만 검사하므로 통과하지만, 어댑터 계층 공통 관심사이므로 위치는 `common` 유지가 적절하다.

## 5. 테스트 계획

CLAUDE.md 표준 준수: 한국어 `@DisplayName`, `given...when...then` 명명, BDDMockito.

| 테스트 | 변경/신규 | 내용 |
|---|---|---|
| `SendMoneyServiceTest` | 수정 | `assertThat(success).isTrue()` → `assertThat(result.success()).isTrue()`. 출금 실패 케이스에 `failureReason() == INSUFFICIENT_BALANCE` 단언 추가. **신규**: 한도 초과 시 `THRESHOLD_EXCEEDED` 반환 검증(현재는 예외 테스트 없음 — 신설) |
| `SendMoneyControllerTest` | 수정+신규 | 기존 성공/실패 케이스의 mock 반환값을 Result로 교체. 실패 응답 422 + `$.code` 존재 검증. **신규**: UseCase가 `AccountNotFoundException` 던질 때 404 검증 |
| `SendMoneySystemTest` | 수정 | 응답 검증은 그대로(200). 컴파일 영향만 확인 |
| `AccountTest` | 수정 | `depositSuccessTest`에서 boolean 단언 제거, 활동 추가·잔액 검증만 유지 |
| `AccountPersistenceAdapterTest` | 신규 케이스 | 없는 계좌 로드 시 `AccountNotFoundException` 발생 검증 |
| `GlobalExceptionHandler` | 신규(슬라이스) | `SendMoneyControllerTest`에 통합 — 별도 클래스 불필요 |

## 6. 작업 순서와 커밋 분할

1. **커밋 1 — 포트 계약**: `SendMoneyResult` 신규, `SendMoneyUseCase` 시그니처 변경, `AccountNotFoundException` 신규 + `LoadAccountPort` Javadoc
2. **커밋 2 — 도메인/서비스**: `Account.deposit()` void화, `SendMoneyService` 재구성, `ThresholdExceededException` 삭제, `SendMoneyServiceTest`·`AccountTest` 수정
3. **커밋 3 — 어댑터**: `AccountPersistenceAdapter` 예외 교체, `SendMoneyController` Result 매핑 + `SendMoneyFailureResDto`, `GlobalExceptionHandler` 확장, 관련 테스트
4. 각 커밋마다 `./gradlew test` 통과 확인

## 7. 리스크와 롤백

- **API 계약 변경**: 실패 응답이 400(빈 바디) → 422(`{code, message}`)로 바뀐다. 이 프로젝트는 학습용이라 외부 소비자가 없지만, README/시스템 테스트에 새 계약을 반영해야 한다.
- **Plan 4와의 충돌**: `SendMoneyService`를 두 계획이 모두 수정한다. 반드시 이 계획을 먼저 완료하고 Plan 4를 시작한다.
- 롤백: 커밋 단위 revert로 충분 (DB 스키마 변경 없음).

## 8. 완료 기준 (DoD)

- [ ] `./gradlew check` 통과 (테스트 + PMD + detekt)
- [ ] `ThresholdExceededException.java` 삭제됨, `EntityNotFoundException` import가 프로덕션 소스에 없음
- [ ] 없는 계좌로 송금 요청 시 404 + 메시지 응답 (수동 확인: `./gradlew bootRun` 후 curl)
- [ ] 잔액 부족 송금 시 422 + `INSUFFICIENT_BALANCE` 응답
- [ ] `Account.deposit()` 반환 타입 void, `SendMoneyService`에 도달 불가능 분기 없음

## 9. 다른 Plan과의 의존 관계

- **선행**: Plan 3 (quick win — `SendMoneyCommand` 검증 순서 수정이 이 계획의 커맨드 생성 흐름과 겹치므로 먼저 정리)
- **후행**: Plan 4 (Result 시그니처 확정 후 서비스 락 구조 재편), Plan 2 (서비스 생성자 파라미터 변경이 겹치지만 순서 무관 — 권장 순서상 뒤)
