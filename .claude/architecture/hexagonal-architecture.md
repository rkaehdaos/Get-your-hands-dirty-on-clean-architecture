# 헥사고날 아키텍처 구조 분석

## 아키텍처 개요

BuckPal 프로젝트는 **포트 & 어댑터 패턴**(Ports & Adapters Pattern)을 구현한 헥사고날 아키텍처의 전형적인 예시입니다.

### 핵심 원칙

1. **도메인 중심성**: 비즈니스 로직이 중심에 위치
2. **의존성 역전**: 도메인이 외부 기술에 의존하지 않음
3. **포트를 통한 통신**: 명확한 인터페이스 정의
4. **어댑터를 통한 구현**: 외부 기술과의 연결점

## 계층별 상세 분석

### 1. 도메인 계층 (Domain Layer)
**위치**: `src/main/java/dev/haja/buckpal/account/domain/`

#### 핵심 구성요소

**Account.java** - 집합체 루트 (Aggregate Root)
```java
public class Account {
    private final AccountId id;
    private final Money baselineBalance;
    private ActivityWindow activityWindow;
    
    // 비즈니스 메서드들
    public boolean withdraw(Money money, AccountId targetAccountId)
    public boolean deposit(Money money, AccountId sourceAccountId)
    public Money calculateBalance()
}
```

**특징**:
- 송금 비즈니스 규칙이 도메인 엔티티 내부에 캡슐화
- 외부 의존성 없이 순수한 Java 객체
- 불변성과 방어적 복사 적용

**Money.java** - 값 객체 (Value Object)
```java
@Value
public class Money {
    @NonNull BigInteger amount;
    
    // 정적 메서드
    public static Money add(Money a, Money b)
    public static Money subtract(Money a, Money b)
    
    // 인스턴스 메서드
    public Money plus(Money money)
    public Money minus(Money money)
    public boolean isPositiveOrZero()
    public boolean isGreaterThanOrEqualTo(Money money)
}
```

**Activity.java & ActivityWindow.java** - 도메인 이벤트 모델링
- 송금 활동을 시간 기반으로 관리
- 특정 기간의 잔액 계산 로직 포함

### 2. 애플리케이션 계층 (Application Layer)
**위치**: `src/main/java/dev/haja/buckpal/account/application/`

#### 포트 구조 (Ports)

**인커밍 포트 (Incoming Ports)** - `port/in/`
```java
// 유스케이스 인터페이스
public interface SendMoneyUseCase {
    boolean sendMoney(SendMoneyCommand command);
}

// 조회 유스케이스
public interface GetAccountBalanceQuery {
    Money getAccountBalance(AccountId accountId);
}
```

**아웃고잉 포트 (Outgoing Ports)** - `port/out/`
```java
// 영속성 포트
public interface LoadAccountPort {
    Account loadAccount(AccountId accountId, LocalDateTime baselineDate);
}

public interface UpdateAccountStatePort {
    void updateActivities(Account account);
}

// 계좌 잠금 포트
public interface AccountLock {
    void lockAccount(AccountId accountId);
    void releaseAccount(AccountId accountId);
}
```

#### 애플리케이션 서비스
**SendMoneyService.java**
- 송금 유스케이스의 구체적 구현
- 트랜잭션 경계 관리 (`@Transactional`)
- 도메인 서비스와 포트들을 조율

### 3. 어댑터 계층 (Adapter Layer)
**위치**: `src/main/java/dev/haja/buckpal/account/adapter/`

#### 인커밍 어댑터 (Incoming Adapters) - `in/web/`

**SendMoneyController.java** - REST 어댑터
```java
@RestController
class SendMoneyController {
    private final SendMoneyUseCase sendMoneyUseCase;
    
    @PostMapping("/accounts/send")
    ResponseEntity<Void> sendMoney(@Valid @RequestBody SendMoneyReqDto dto) {
        // DTO → 도메인 Command 변환
        // 유스케이스 호출
        // HTTP 응답 생성
    }
}
```

#### 아웃고잉 어댑터 (Outgoing Adapters) - `out/persistence/`

**AccountPersistenceAdapter.java** - JPA 어댑터
```java
@PersistenceAdapter
class AccountPersistenceAdapter implements LoadAccountPort, UpdateAccountStatePort {
    private final AccountRepository accountRepository;
    private final ActivityRepository activityRepository;
    private final AccountMapper accountMapper;
    
    // JPA 엔티티 ↔ 도메인 모델 변환
    // 영속성 포트 구현
}
```

**AccountMapper.java** - 매핑 담당
- MapStruct 기반 자동 매핑
- JPA 엔티티 ↔ 도메인 모델 변환

## 의존성 방향

```
┌─────────────────────┐
│   Incoming Adapter  │ ──┐
│   (Web Controller)  │   │
└─────────────────────┘   │
                          │
┌─────────────────────┐   ▼
│  Application Layer  │ ◄─────► ┌─────────────────────┐
│   (Use Cases)       │         │   Domain Layer      │
└─────────────────────┘   ▲     │  (Business Logic)   │
                          │     └─────────────────────┘
┌─────────────────────┐   │
│  Outgoing Adapter   │ ──┘
│  (Persistence)      │
└─────────────────────┘
```

### 의존성 규칙 검증

**DependencyRuleTests.java**에서 ArchUnit을 사용한 자동 검증:
```java
@Test
void domainLayerDoesNotDependOnApplicationLayer() {
    noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("..application..");
}
```

## 헥사고날 아키텍처의 장점

### 1. 테스트 용이성
- 각 계층을 독립적으로 테스트 가능
- 모의 객체(Mock)를 통한 격리된 테스트
- 포트 인터페이스를 통한 테스트 더블 주입

### 2. 기술 독립성
- 데이터베이스 변경 시 도메인 코드 영향 없음
- 웹 프레임워크 교체 가능
- 외부 API 연동 변경 용이

### 3. 비즈니스 로직 보호
- 도메인 계층이 외부 변경에 격리됨
- 비즈니스 규칙의 일관성 유지
- 도메인 전문가와의 원활한 소통

### 4. 확장성
- 새로운 어댑터 추가 용이
- 다양한 인터페이스 지원 (REST, GraphQL, gRPC 등)
- 마이크로서비스 전환 시 경계 명확

## 구현 시 주의사항

### 1. 과도한 추상화 주의
- 단순한 CRUD에는 오버엔지니어링 가능
- 비즈니스 복잡도와 아키텍처 복잡도의 균형

### 2. 매핑 비용
- 계층 간 객체 변환 비용
- MapStruct 등 자동화 도구 활용 권장

### 3. 팀 학습 곡선
- 새로운 패턴에 대한 팀 학습 필요
- 일관된 구현 가이드라인 필요

이 아키텍처는 특히 복잡한 비즈니스 규칙을 가진 도메인에서 그 진가를 발휘하며, BuckPal 프로젝트는 이러한 패턴의 실제 적용 사례를 보여줍니다.