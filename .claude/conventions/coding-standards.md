# 코딩 표준 및 컨벤션

## 전체 프로젝트 규칙

### 1. 언어별 우선순위
- **Kotlin 우선**: 새로운 코드는 Kotlin으로 작성
- **Java 레거시**: 기존 Java 코드는 점진적으로 마이그레이션
- **마이그레이션 순서**: 값 객체 → 도메인 엔티티 → 애플리케이션 서비스

### 2. 아키텍처 원칙
- **헥사고날 아키텍처** 준수
- **의존성 역전 원칙** 적용
- **도메인 중심 설계** 패턴 따르기

## Java 코딩 표준

### 1. 어노테이션 사용 규칙

#### Lombok 사용법
```java
// 도메인 엔티티
@Getter @ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Account {
    // 생성자 접근 제한으로 팩토리 메서드 강제
}

// 값 객체
@Value  // 불변 객체 생성
public static class AccountId {
    Long value;
}
```

#### Spring 어노테이션
```java
// 어댑터 식별
@RestController          // 웹 어댑터
@PersistenceAdapter     // 영속성 어댑터 (커스텀)
@WebAdapter             // 웹 어댑터 (커스텀)

// 서비스 레이어
@Component              // 애플리케이션 서비스
@Transactional          // 트랜잭션 경계
```

### 2. 네이밍 컨벤션

#### 클래스명
```java
// 도메인
Account.java           // 집합체 루트
Money.java            // 값 객체
Activity.java         // 도메인 엔티티
ActivityWindow.java   // 도메인 서비스

// 애플리케이션
SendMoneyUseCase.java      // 유스케이스 인터페이스
SendMoneyService.java      // 유스케이스 구현체
LoadAccountPort.java       // 아웃고잉 포트
GetAccountBalanceQuery.java // 인커밍 포트 (조회)

// 어댑터
SendMoneyController.java        // 웹 어댑터
AccountPersistenceAdapter.java  // 영속성 어댑터
AccountJpaEntity.java          // JPA 엔티티
```

#### 메서드명
```java
// 비즈니스 메서드 - 동사로 시작
withdraw()
deposit()
calculateBalance()

// 쿼리 메서드 - get/is/has 접두어
getId()
isPositive()
hasActivities()

// 팩토리 메서드 - with/of/from
withId()
of()
mapToDomainEntity()
```

### 3. 패키지 구조 규칙
```
dev.haja.buckpal
├── {domain}/           # 바운디드 컨텍스트
│   ├── domain/        # 도메인 계층
│   ├── application/   # 애플리케이션 계층
│   │   ├── port/
│   │   │   ├── in/    # 인커밍 포트
│   │   │   └── out/   # 아웃고잉 포트
│   │   └── service/   # 애플리케이션 서비스
│   └── adapter/       # 어댑터 계층
│       ├── in/
│       │   └── web/   # 웹 어댑터
│       └── out/
│           └── persistence/  # 영속성 어댑터
└── common/            # 공통 요소
```

## Kotlin 코딩 표준

### 1. 언어 기능 활용

#### 값 객체 (Value Classes)
```kotlin
@JvmInline
value class AccountId(val value: Long)

@JvmInline  
value class Money(val amount: BigInteger) {
    companion object {
        val ZERO = Money(BigInteger.ZERO)
        fun of(value: Long) = Money(BigInteger.valueOf(value))
    }
    
    operator fun plus(other: Money) = Money(amount + other.amount)
    operator fun minus(other: Money) = Money(amount - other.amount)
}
```

#### 데이터 클래스
```kotlin
data class Activity(
    val id: ActivityId? = null,
    val ownerAccountId: AccountId,
    val sourceAccountId: AccountId,
    val targetAccountId: AccountId,
    val timestamp: LocalDateTime,
    val money: Money
)
```

#### Sealed Classes/Interfaces
```kotlin
sealed class TransferResult {
    data object Success : TransferResult()
    data class InsufficientFunds(val availableBalance: Money) : TransferResult()
    data class ThresholdExceeded(val limit: Money, val requested: Money) : TransferResult()
}
```

### 2. 함수형 프로그래밍

#### 확장 함수
```kotlin
// 도메인 로직을 확장 함수로 표현
fun Account.canWithdraw(amount: Money): Boolean = 
    calculateBalance() >= amount

fun List<Activity>.calculateBalance(accountId: AccountId): Money =
    filter { it.targetAccountId == accountId }
        .sumOf { it.money.amount }
        .let { deposits ->
            val withdrawals = filter { it.sourceAccountId == accountId }
                .sumOf { it.money.amount }
            Money(deposits - withdrawals)
        }
```

#### 고차 함수 활용
```kotlin
fun processTransfer(
    command: SendMoneyCommand,
    validator: (SendMoneyCommand) -> Boolean,
    executor: (SendMoneyCommand) -> Boolean
): TransferResult {
    return when {
        !validator(command) -> TransferResult.InsufficientFunds(Money.ZERO)
        executor(command) -> TransferResult.Success
        else -> TransferResult.Error("Unknown error")
    }
}
```

## 테스트 코딩 표준

### 1. 테스트 구조

#### 기본 패턴 (AAA)
```java
@Test
@DisplayName("충분한 잔액이 있을 때 출금이 성공한다")
void givenSufficientBalance_whenWithdraw_thenSucceeds() {
    // given (준비)
    AccountId accountId = new AccountId(1L);
    Account account = givenAccountWithBalance(accountId, 1000L);
    Money withdrawalAmount = Money.of(500L);
    
    // when (실행)  
    boolean success = account.withdraw(withdrawalAmount, new AccountId(2L));
    
    // then (검증)
    assertThat(success).isTrue();
    assertThat(account.calculateBalance()).isEqualTo(Money.of(500L));
}
```

#### BDD 스타일 네이밍
```java
// 메서드명 패턴
given{상황}_when{행동}_then{결과}

// DisplayName은 한국어로
@DisplayName("계좌 이체가 성공적으로 처리되는 경우")
@DisplayName("잔액 부족 시 출금이 실패하는 경우")
@DisplayName("송금 한도를 초과했을 때 예외가 발생하는 경우")
```

### 2. Mock 사용 규칙

#### BDDMockito 스타일
```java
// given
given(loadAccountPort.loadAccount(eq(sourceAccountId), any(LocalDateTime.class)))
    .willReturn(sourceAccount);

// when  
boolean result = sendMoneyService.sendMoney(command);

// then
then(updateAccountStatePort).should().updateActivities(sourceAccount);
then(updateAccountStatePort).should().updateActivities(targetAccount);
```

### 3. 테스트 데이터 팩토리

#### AccountTestData 패턴
```java
public class AccountTestData {
    public static Account defaultAccount() {
        return Account.withId(
            new AccountId(42L),
            Money.of(999L),
            defaultActivityWindow()
        );
    }
    
    public static Account emptyAccount() {
        return Account.withId(
            new AccountId(7L), 
            Money.of(0L),
            emptyActivityWindow()
        );
    }
}
```

## 설정 및 도구

### 1. Kotlin 컴파일러 설정
```kotlin
// build.gradle.kts
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn")
        allWarningsAsErrors = true  // 모든 경고를 에러로 처리
    }
}
```

### 2. 코드 품질 도구

#### ArchUnit 규칙
```java
@Test
void domainLayerDoesNotDependOnApplicationLayer() {
    noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("..application..")
        .check(importedClasses);
}
```

#### 헥사고날 아키텍처 검증
```java
HexagonalArchitecture.basePackage("dev.haja.buckpal.account")
    .withDomainLayer("domain")
    .withAdaptersLayer("adapter")
        .incoming("in.web")
        .outgoing("out.persistence")
    .and()
    .withApplicationLayer("application")
        .services("service")
        .incomingPorts("port.in")
        .outgoingPorts("port.out")
    .and()
    .check(importedClasses);
```

## 문서화 규칙

### 1. JavaDoc/KDoc
```java
/**
 * 계좌 간 송금을 처리하는 유스케이스
 * 
 * @param command 송금 명령 객체
 * @return 송금 성공 여부
 * @throws ThresholdExceededException 송금 한도 초과 시
 */
boolean sendMoney(SendMoneyCommand command);
```

### 2. README 및 문서
- 한국어 작성 우선
- 아키텍처 결정 기록 (ADR) 작성
- 코드 예시는 Kotlin으로 제공

## 커밋 메시지 규칙

### 형식
```
<type>(<scope>): <subject>

<body>

<footer>
```

### 예시
```
feat(domain): Account 엔티티에 송금 한도 검증 로직 추가

- MoneyTransferProperties를 통한 설정값 주입
- ThresholdExceededException 예외 처리 추가
- 관련 테스트 케이스 작성

Closes #123
```

이러한 코딩 표준은 팀 전체의 일관성을 유지하고, 헥사고날 아키텍처의 원칙을 코드 레벨에서 실현하기 위해 수립되었습니다.