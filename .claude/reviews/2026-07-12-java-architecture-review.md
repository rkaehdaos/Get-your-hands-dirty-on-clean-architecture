# BuckPal Java 소스 아키텍처·코드 품질 평가

> **평가일**: 2026-07-12
> **대상**: `src/main/java/dev/haja/buckpal/**` (Java 프로덕션 소스 21개) + Java 테스트 소스
> **제외**: Kotlin 소스 (`src/main/kotlin`, `src/test/kotlin`)
> **관점**: DDD, 헥사고날 아키텍처, 관심사 분리, 코드 품질

---

## 1. 총평

책(Get Your Hands Dirty on Clean Architecture)의 원형을 충실히 따르면서도 원본보다 개선된 부분이 많은, **잘 정돈된 헥사고날 아키텍처 구현**이다. 포트/어댑터 분리, 패키지 구조, package-private 가시성 활용, ArchUnit에 의한 규칙 강제까지 교과서적 골격은 탄탄하다.

다만 **동시성·트랜잭션 정합성, 예외 처리 전략, 애그리게잇 캡슐화**에서 실무 기준으로는 구멍이 있고, ArchUnit 테스트에 **실제 위반을 놓치는 사각지대**가 존재한다.

### 잘된 점

| 영역 | 내용 |
|---|---|
| 의존성 방향 | domain은 Lombok 외 외부 의존 없음. adapter → application → domain 방향 준수 |
| 가시성 제어 | `SendMoneyController`, `AccountPersistenceAdapter`, 리포지토리들이 package-private — 포트를 통해서만 접근 가능 |
| 포트 세분화 | `LoadAccountPort` / `UpdateAccountStatePort` / `AccountLock` 분리로 ISP 준수 |
| 설정 분리 시도 | 유스케이스별 설정 객체(`MoneyTransferProperties`)로 프레임워크 설정과 애플리케이션 설정 분리 |
| 입력 방어 | 커맨드 자가 검증(`SendMoneyCommand` + `SelfValidating`) |
| 테스트 피라미드 | 도메인 단위 → 서비스 단위(Mock) → 슬라이스(`@WebMvcTest`, `@DataJpaTest`) → 시스템 테스트 계층 구성. 테스트 데이터 빌더 팩토리 사용 |

---

## 2. 심각 — 동시성·정합성 결함 (`SendMoneyService`)

### 2-1. 예외 시 락 누수

`SendMoneyService.java:46-59` — `updateActivities()`나 `deposit()`에서 예외가 발생하면 락이 해제되지 않는다. try/finally가 없다.

### 2-2. 락 해제가 트랜잭션 커밋 전

클래스에 `@Transactional`이 걸려 있어 커밋은 `sendMoney()` 반환 **후**인데, 락은 반환 **전**에 풀린다. 락 해제 ~ 커밋 사이에 다른 트랜잭션이 아직 반영 안 된 잔액을 읽는 레이스 윈도우가 존재한다.

### 2-3. 데드락 가능성

항상 source → target 순서로 락을 잡으므로, A→B 송금과 B→A 송금이 동시에 일어나면 교착된다. 계좌 ID 정렬 후 획득이 정석.

### 2-4. 락 획득 전에 계좌를 읽음 (TOCTOU)

`sendMoney()`가 `loadAccount()`를 먼저 하고 락은 나중에 잡는다. 락으로 보호하려던 "잔액 확인 후 출금"의 원자성이 이미 깨져 있다.

### 2-5. 근본적으로 낙관적/비관적 락 부재

`NoOpsAccountLock` + JPA 버전 관리 없음 + insert-only 저장 구조라, 동시 요청 두 개가 모두 잔액 검증을 통과해 **초과 인출이 가능**하다. 학습 프로젝트의 의도된 단순화라면 `NoOpsAccountLock`에 한계를 주석으로 명시하고, 다음 단계로 `SELECT ... FOR UPDATE` 기반 어댑터 구현을 권장.

### 개선 골격

```java
// 락 정렬 + finally 보장
List<AccountId> lockOrder = Stream.of(sourceAccountId, targetAccountId)
        .sorted(comparing(AccountId::getValue)).toList();
lockOrder.forEach(accountLock::lockAccount);
try {
    // load → withdraw → deposit → update
} finally {
    lockOrder.forEach(accountLock::releaseAccount);
    // 커밋 후 해제가 필요하면 TransactionSynchronization 사용
}
```

---

## 3. 아키텍처 위반 — application 계층이 인프라 설정에 의존

### 3-1. `SendMoneyService` → `BuckPalConfigurationProperties` 직접 의존

`SendMoneyService.java:27`이 루트 패키지의 `BuckPalConfigurationProperties`(Spring Boot `@ConfigurationProperties`)를 직접 주입받는다. 같은 클래스가 `MoneyTransferProperties`(유스케이스별 설정, 올바른 패턴)도 쓰고 있어 **한 클래스 안에 두 방식이 공존**한다.

**개선**: `historyLookbackDays`도 `MoneyTransferProperties`처럼 애플리케이션 소유 객체로 옮기고, `BuckPalConfiguration`에서 변환해 주입.

### 3-2. ArchUnit 사각지대 — 이 위반을 못 잡는다

`DependencyRuleTests.java:43`의 `withConfiguration("configuration")`은 `dev.haja.buckpal.account.configuration`이라는 **존재하지 않는 패키지**를 가리킨다. `HexagonalArchitecture.check()`의 configuration 관련 규칙 2개(`adapters.doesNotDependOn`, `applicationLayer.doesNotDependOn`)가 공허하게(vacuously) 통과한다. 실제 설정 클래스는 루트 패키지(`dev.haja.buckpal`)에 있어 검사 범위 밖이다.

**개선**: 설정 클래스를 검사 가능한 패키지로 옮기거나, 루트 패키지 의존 금지 규칙을 별도로 추가. 아키텍처 테스트가 "거짓 안심"을 주는 현재 상태가 가장 위험하다.

---

## 4. 예외 처리 전략 부재 — 도메인 예외가 500으로 누수

`GlobalExceptionHandler`는 `MethodArgumentNotValidException`만 처리한다. 그 결과:

| 예외 | 발생 지점 | 현재 응답 | 기대 응답 |
|---|---|---|---|
| `ThresholdExceededException` | 송금 한도 초과 | **500** | 400/422 |
| `EntityNotFoundException` (jakarta.persistence) | 없는 계좌 (`AccountPersistenceAdapter.java:32`) | **500** | 404 |
| `ConstraintViolationException` | `SendMoneyCommand` 자가 검증 실패 | **500** | 400 |

추가 문제:

- `EntityNotFoundException`은 **JPA 예외가 어댑터 → 애플리케이션 → 웹까지 누수**되는 구조. 포트 계약에 `AccountNotFoundException` 같은 자체 예외를 정의해야 한다.
- `SendMoneyUseCase`가 `boolean`을 반환해 실패 사유(잔액 부족 등)를 전달할 방법이 없다. 결과 타입(enum/sealed) 또는 예외 기반으로 통일 권장.
- `GlobalExceptionHandler`의 `@ResponseStatus` + `ResponseEntity` 이중 지정 — `ResponseEntity`가 우선이므로 `@ResponseStatus`는 불필요.

---

## 5. 죽은 코드 / 미완성 유스케이스

### 5-1. `GetAccountBalanceService`가 빈으로 등록되지 않음

`@Component`도 `@Bean` 정의도 없고, 호출하는 인커밍 어댑터(컨트롤러)도 없다. **잔액 조회 유스케이스가 통째로 죽어 있다.**

### 5-2. `@PersistenceAdapter` 애너테이션 정의만 되고 미사용

`common/PersistenceAdapter.java`에 커스텀 스테레오타입이 정의돼 있으나, 정작 `AccountPersistenceAdapter`는 `@Component`를 쓴다. 쓰거나 지워야 한다.

### 5-3. `Account.deposit()`이 항상 true 반환

`Account.java:116-125` — 실패 경로가 없는데 `boolean`을 반환한다. 따라서 `SendMoneyService.java:71-79`의 deposit 실패 분기(락 해제 포함)는 **도달 불가능한 죽은 코드**다. 실패할 수 없다면 `void`로, 실패 규칙(계좌 동결 등)이 생길 예정이면 규칙을 구현해야 한다. 현재 상태는 API가 거짓말을 하고 있다.

---

## 6. 도메인 모델 캡슐화 약점 (DDD 관점)

### 6-1. 애그리게잇 불변식 우회 가능

`account.getActivityWindow().addActivity(...)`로 잔액 검증 없이 활동 추가가 가능하다. `ActivityWindow.addActivity`의 접근을 좁히거나, `Account`가 활동 목록의 읽기 전용 뷰만 노출해야 한다.

### 6-2. 방어적 복사 비일관

`ActivityWindow.java:19-25` — varargs 생성자는 복사하는데 `List` 생성자는 참조를 그대로 보관한다. 외부에서 원본 리스트를 수정하면 내부 상태가 오염된다.

### 6-3. `LocalDateTime.now()`가 도메인 안에

`Account.java:88,121` — 시계 의존이 도메인에 박혀 테스트 제어가 안 된다. 타임스탬프를 파라미터로 받거나 `Clock`을 주입할 것. `SendMoneyService.java:37`, `GetAccountBalanceService.java:18`의 `now()`도 동일.

### 6-4. `Account.withoutId()` 계좌에서 `withdraw()` 호출 시 NPE

`this.id`가 null인 채 `Activity`의 `@NonNull ownerAccountId`에 들어가 의미 없는 NPE가 발생한다. 명시적 가드 필요.

### 6-5. 자기 자신에게 송금 가능

`SendMoneyCommand`에 source ≠ target 검증이 없다. 현재는 잔액 변화 0인 활동 2건이 쌓일 뿐이지만, 실제 락 구현이 들어오면 같은 계좌 이중 락 → 데드락이 된다.

### 6-6. `Activity` 불변식 미검증

withdraw 시 owner == source, deposit 시 owner == target이어야 하는데 `Activity` 생성자는 아무 조합이나 허용한다.

---

## 7. `Money` 값 객체 품질

| 위치 | 문제 | 심각도 |
|---|---|---|
| `Money.java:11` | `public static Money ZERO`가 **final이 아님** — 외부에서 재할당 가능 | 높음 |
| `Money.java:31` | `isGreaterThan`이 `compareTo(...) >= 1` — `compareTo` 계약은 "양수/음수/0"이므로 `> 0`이 올바름 | 중간 |
| 전체 | 정적 `add`/`subtract`와 인스턴스 `plus`/`minus` 중복 — 하나로 통일 | 낮음 |
| 전체 | `BigInteger` 기반이라 소수점 표현 불가. **CLAUDE.md에는 "BigDecimal 래핑"으로 문서화되어 있어 문서-코드 불일치** | 중간 |

`Money`는 Kotlin 마이그레이션 1순위 대상이므로, 마이그레이션 시 정리 권장:

```kotlin
@JvmInline
value class Money(val amount: BigDecimal) : Comparable<Money> {
    operator fun plus(other: Money) = Money(amount + other.amount)
    operator fun minus(other: Money) = Money(amount - other.amount)
    operator fun unaryMinus() = Money(-amount)
    override fun compareTo(other: Money) = amount.compareTo(other.amount)
    val isPositive get() = amount.signum() > 0
    companion object { val ZERO = Money(BigDecimal.ZERO) }
}
```

---

## 8. 검증 로직의 불일치와 비용

### 8-1. 0원 송금 허용 불일치

- DTO(`SendMoneyReqDto`): `@Positive` — 0 거부
- 커맨드(`SendMoneyCommand.java:23`): `isPositiveOrZero()` — **0 허용**

웹이 아닌 다른 어댑터가 유스케이스를 호출하면 0원 송금이 통과한다. 커맨드가 최종 방어선이므로 `isPositive()`로 일치시켜야 한다.

### 8-2. null money 시 NPE 선행

`SendMoneyCommand` 생성자에서 `money.isPositiveOrZero()`가 `validateSelf()`(`@NotNull` 검증)보다 먼저 실행되어, money가 null이면 검증 메시지 대신 맨 NPE가 발생한다.

### 8-3. `SelfValidating` 비용

`SelfValidating.java:10-14` — **인스턴스 생성마다 `ValidatorFactory`를 새로 생성**한다. 비용이 큰 객체이므로 `private static final Validator`로 공유해야 한다.

### 8-4. 설정 검증 시점 오류

`SendMoneyService.java:33-36`의 `historyLookbackDays` 검증이 **요청마다** 실행된다. 설정 값 검증은 부팅 시점(`BuckPalConfigurationProperties` 컴팩트 생성자나 `@Validated`)에서 한 번 하는 것이 맞다.

---

## 9. 설정 클래스의 어색함

### 9-1. 기본값 출처가 두 군데

`MoneyTransferProperties.java:12`의 필드 기본값 `Money.of(1_000_000L)`은 `BuckPalConfiguration`이 항상 `@AllArgsConstructor`로 덮어쓰므로 **죽은 값**이고, 실제 기본값은 record 쪽 `Long.MAX_VALUE`다. 또한 `@Data`라 가변인데 설정 객체는 불변이어야 한다.

### 9-2. record + 수동 getter 중복

`BuckPalConfigurationProperties`는 record인데 `getTransferThreshold()`/`getAccount()` getter를 수동 중복 정의했다. record 접근자(`transferThreshold()`)를 쓰고 기본값은 `@DefaultValue`로 처리하면 코드 절반이 사라진다.

---

## 10. 영속성 어댑터 마이너

- JPA 엔티티에 `@Data`는 안티패턴(불필요한 setter 전면 개방, toString/equals 함정). `@Getter` + 필요한 곳만 개방 권장.
- 테이블명 비일관: `@Table(name = "account")` vs `@Table(name = "Activity")`.
- `updateActivities`의 루프 개별 `save()` → `saveAll()`로 배치화 가능.
- `orZero()` 대신 리포지토리 반환 타입을 `Optional<Long>`으로.
- `AccountMapper`는 수동 매퍼 — 프로젝트에 MapStruct가 있으므로 활용 여지 (단, Kotlin 마이그레이션 계획과 조율 필요).

---

## 11. 테스트 개선점

| 항목 | 내용 |
|---|---|
| 테스트 부재 | **`Money` 단위 테스트 없음** (값 객체·마이그레이션 1순위인데). `GetAccountBalanceService` 테스트 없음 |
| 무의미한 테스트 | `SendMoneyServiceTest.java:47-51` `sampleTest`(`assertThat(true).isTrue()`) — 제거 대상 |
| 중복 헬퍼 | `createInvalidBuckPalConfiguration`과 `createBuckPalConfigurationWithCustomDays`는 구현이 완전히 동일 |
| 응답 타입 오류 | `SendMoneySystemTest.java:91-92` — 응답 바디 없는 API를 `SendMoneyReqDto.class`로 파싱. `Void.class`가 맞음 |
| 표준 미준수 | CLAUDE.md 테스트 표준(한국어 `@DisplayName`, given-when-then)이 `AccountTest`, `SendMoneyControllerTest`, `AccountPersistenceAdapterTest`에 미적용 |
| 도메인 mock | `SendMoneyServiceTest`가 도메인 객체 `Account`까지 mock — 서비스의 오케스트레이션 순서에 강결합(책 원본의 알려진 약점). 실제 `Account`를 쓰면 리팩터링 내성 향상 |
| 격리 | 시스템 테스트에 `@Sql`만 있고 정리(cleanup) 전략 없음 — 테스트 간 오염 가능 |

---

## 12. 우선순위 제안

| 순위 | 항목 | 근거 |
|---|---|---|
| 1 | 예외 처리 전략 정비 (§4, §5-3) | 사용자에게 500이 노출되는 현재진행형 결함 |
| 2 | ArchUnit 사각지대 수정 + 설정 의존 정리 (§3) | 아키텍처 테스트가 거짓 안심을 주는 상태 |
| 3 | `Money.ZERO` final, 0원 송금 불일치 (§7, §8-1) | 한 줄짜리 수정, 즉시 효과 |
| 4 | 락/트랜잭션 정합성 (§2) | 구조적 개선 필요. NoOp 락이라 당장 터지진 않음 |
| 5 | 도메인 캡슐화 + Clock 주입 (§6) | Kotlin 마이그레이션 시 설계에 함께 반영 권장 |

---

*평가 도구: Claude Code (수동 정독 기반 리뷰). 대상 커밋: dd6dfac 이후 main 브랜치.*
