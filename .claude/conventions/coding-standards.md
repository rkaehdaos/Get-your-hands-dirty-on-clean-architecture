# 코딩 표준 (Coding Standards)

BuckPal 프로젝트의 코딩 컨벤션과 표준을 정의합니다.

## 1. 네이밍 컨벤션

### 1.1 Java 클래스 및 인터페이스

```java
// 클래스명: PascalCase
public class Account {
    // 필드명: camelCase
    private final AccountId id;
    private final Money baselineBalance;
    
    // 메서드명: camelCase, 동사로 시작
    public boolean withdraw(Money money, AccountId targetAccountId) {
        return mayWithdraw(money);
    }
    
    // private 메서드: camelCase, 목적을 명확히
    private boolean mayWithdraw(Money money) {
        // ...
    }
}

// 인터페이스명: 목적에 따라 명명
public interface SendMoneyUseCase {
    boolean sendMoney(SendMoneyCommand command);
}

// 포트 인터페이스: 역할 + Port 접미사
public interface LoadAccountPort {
    Account loadAccount(AccountId accountId, LocalDateTime baselineDate);
}
```

### 1.2 Kotlin 클래스 및 프로퍼티

```kotlin
// data class: PascalCase
data class Leg(
    val description: String,           // 프로퍼티: camelCase
    val plannedStart: ZonedDateTime,
    val plannedEnd: ZonedDateTime
) {
    // computed property: camelCase
    val plannedDuration: Duration get() = Duration.between(plannedStart, plannedEnd)
}

// 확장 함수: camelCase, 목적을 명확히
fun List<Leg>.longestLegOver(duration: Duration): Leg? {
    return this
        .filter { it.plannedDuration > duration }
        .maxByOrNull { it.plannedDuration }
}
```

### 1.3 패키지명

```
dev.haja.buckpal.account
├── domain                    // 도메인 엔티티
├── application              // 애플리케이션 서비스
│   ├── port.in             // 인커밍 포트 (UseCase)
│   ├── port.out            // 아웃고잉 포트 (Repository 인터페이스)
│   └── service             // 비즈니스 로직 구현
└── adapter                 // 어댑터
    ├── in.web              // REST 컨트롤러
    └── out.persistence     // JPA 구현체
```

### 1.4 테스트 클래스

```java
// 테스트 클래스: 대상클래스명 + Test 접미사
class AccountTest {
    
    // 테스트 메서드: 목적을 명확히 설명
    @Test
    void withdrawalSucceedsTest() {
        // given, when, then
    }
    
    @Test 
    void withdrawalFailure() {
        // given, when, then
    }
}
```

```kotlin
// Kotlin 테스트: 백틱을 사용하여 읽기 쉽게
class LongestLegOverTestsKotlin {
    
    @Test
    fun `is_absent_when_no_legs`() {
        assertNull(emptyList<Leg>().longestLegOver(Duration.ZERO))
    }
    
    @Test
    fun `is_longest_leg_when_one_match`() {
        assertEquals("one day", legs.longestLegOver(oneDay.minusMillis(1))!!.description)
    }
}
```

## 2. 코드 포맷팅 규칙

### 2.1 들여쓰기와 공백

```java
// 4칸 공백 들여쓰기 (Java)
public class Account {
    private final AccountId id;
    
    public boolean withdraw(Money money, AccountId targetAccountId) {
        if (!mayWithdraw(money)) return false;
        
        Activity withdrawal = new Activity(
                this.id,
                this.id,
                targetAccountId,
                LocalDateTime.now(),
                money);
        return true;
    }
}
```

```kotlin
// 4칸 공백 들여쓰기 (Kotlin)
data class Leg(
    val description: String,
    val plannedStart: ZonedDateTime,
    val plannedEnd: ZonedDateTime
) {
    val plannedDuration: Duration get() = Duration.between(plannedStart, plannedEnd)
}
```

### 2.2 줄 길이 제한

- **최대 120자**: 가독성을 위해 한 줄당 120자 제한
- **메서드 체이닝**: 각 체이닝을 새 줄에 작성

```kotlin
// 좋은 예
return this
    .filter { it.plannedDuration > duration }
    .maxByOrNull { it.plannedDuration }

// 나쁜 예
return this.filter { it.plannedDuration > duration }.maxByOrNull { it.plannedDuration }
```

### 2.3 중괄호 스타일

```java
// Java: K&R 스타일
public boolean withdraw(Money money, AccountId targetAccountId) {
    if (!mayWithdraw(money)) {
        return false;
    }
    // ...
}
```

```kotlin
// Kotlin: 함수형 스타일 선호
fun longestLegOver(duration: Duration): Leg? {
    return this
        .filter { it.plannedDuration > duration }
        .maxByOrNull { it.plannedDuration }
}
```

## 3. 주석 작성 규칙

### 3.1 Javadoc 스타일

```java
/**
 * 일정 금액을 보유하고 있는 계정.
 * {@link Account} 에는 최신 계정 활동 윈도우가 포함된다.
 * 계정의 총 잔액은 윈도우에서 첫 활동 이전에 유효했던 기준 잔액과 활동 값의 합이다.
 *
 * @see Account
 */
@Getter @ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Account {

    /**
     * 출금: 이 계좌에서 일정 금액을 출금하려고 시도
     *
     * @param money           출금 금액
     * @param targetAccountId 대상 계좌 id
     * @return 출금 성공 여부
     */
    public boolean withdraw(Money money, AccountId targetAccountId) {
        // 비즈니스 규칙을 도메인 엔티티 안에 넣었다.
        if (!mayWithdraw(money)) return false;
        // ...
    }
}
```

### 3.2 인라인 주석

```java
// 좋은 예: Why를 설명
if (!mayWithdraw(money)) return false; // 비즈니스 규칙 검증

// 나쁜 예: What을 중복 설명
if (!mayWithdraw(money)) return false; // 출금 가능 여부 확인하고 false 반환
```

### 3.3 TODO 주석

```kotlin
//TODO: gradle 9.0 지원 버전 나올시 처리할 것
id("org.hibernate.orm") version "6.6.22.Final"
```

## 4. 언어별 컨벤션

### 4.1 Java 컨벤션

```java
// Lombok 어노테이션 순서
@Getter @ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Account {
    
    // 필드 순서: final -> non-final
    private final AccountId id;
    private final Money baselineBalance;
    private ActivityWindow activityWindow;
    
    // 메서드 순서: public -> private
    public static Account withId(...) { }
    public Money calculateBalance() { }
    private boolean mayWithdraw(Money money) { }
}

// Value Object 패턴
@Value
public static class AccountId {
    Long value;
}
```

### 4.2 Kotlin 컨벤션

```kotlin
// data class 프로퍼티 정렬
data class Leg(
    val description: String,
    val plannedStart: ZonedDateTime,
    val plannedEnd: ZonedDateTime
) {
    // computed property는 class body에
    val plannedDuration: Duration get() = Duration.between(plannedStart, plannedEnd)
}

// null safety 활용
fun List<Leg>.longestLegOver(duration: Duration): Leg? {
    return this
        .filter { it.plannedDuration > duration }
        .maxByOrNull { it.plannedDuration }
}
```

## 5. 어노테이션 컨벤션

### 5.1 Spring 어노테이션

```java
@WebAdapter
@RestController
@RequiredArgsConstructor
@Validated
public class SendMoneyController {
    
    @PostMapping("/accounts/send/{sourceAccountId}/{targetAccountId}/{amount}")
    public void sendMoney(
            @PathVariable("sourceAccountId") Long sourceAccountId,
            @PathVariable("targetAccountId") Long targetAccountId,
            @PathVariable("amount") Long amount) {
        // ...
    }
}
```

### 5.2 테스트 어노테이션

```java
class AccountTest {
    
    @BeforeEach
    void setUp() {
        // 테스트 데이터 준비
    }
    
    @Test
    @DisplayName("계좌 출금이 성공하는 경우")
    void withdrawalSucceedsTest() {
        // given, when, then
    }
}
```

## 6. 빌드 설정 컨벤션

### 6.1 Gradle 스타일

```kotlin
// build.gradle.kts: 변수명 camelCase
var releaseVer = "v0.0.1"
version = "$releaseVer-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}"

// 의존성 그룹화
dependencies {
    // spring
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5-engine:1.4.1")
    
    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
```

## 7. 파일 구조 컨벤션

### 7.1 패키지별 파일 배치

```
src/main/java/dev/haja/buckpal/
├── account/
│   ├── domain/              # 도메인 엔티티
│   │   ├── Account.java
│   │   ├── Activity.java
│   │   └── Money.java
│   ├── application/         # 애플리케이션 레이어
│   │   ├── port/in/        # 유스케이스 인터페이스
│   │   ├── port/out/       # 리포지토리 인터페이스
│   │   └── service/        # 비즈니스 로직 구현
│   └── adapter/            # 어댑터
│       ├── in/web/         # REST 컨트롤러
│       └── out/persistence/ # JPA 구현체
└── common/                 # 공통 유틸리티
```

### 7.2 테스트 파일 배치

```
src/test/java/dev/haja/buckpal/
├── account/
│   ├── domain/             # 도메인 테스트
│   │   ├── AccountTest.java
│   │   └── ActivityWindowTest.java
│   ├── application/service/ # 서비스 테스트
│   └── adapter/            # 어댑터 테스트
└── common/                 # 테스트 데이터 빌더
    ├── AccountTestData.java
    └── ActivityTestData.java
```