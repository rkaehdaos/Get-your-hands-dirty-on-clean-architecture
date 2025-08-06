# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a toy project demonstrating **Hexagonal Architecture** (Ports & Adapters) and **Clean Code** principles using Spring Boot, Java 24, and Kotlin. The project implements a simple money transfer system ("BuckPal") showcasing proper domain-driven design and architectural boundaries.

## Common Commands

### Build & Test
```bash
# Full build with tests
./gradlew build

# Run tests only
./gradlew test

# Run a specific test class
./gradlew test --tests "SendMoneyServiceTest"

# Run tests with detailed output
./gradlew test --info

# Clean build
./gradlew clean build
```

### Development
```bash
# Run application locally
./gradlew bootRun

# Check for dependency updates
./gradlew dependencyUpdates
```

## Architecture Structure

### Hexagonal Architecture Layers

**Domain Layer** (`account/domain/`):
- Pure business logic with no external dependencies
- `Account`: Aggregate root with business rules (withdraw/deposit validation)
- `Money`: Value object for monetary calculations
- `Activity`: Entity representing money transfer activities
- `ActivityWindow`: Collection wrapper managing activity periods

**Application Layer** (`account/application/`):
- **Ports** (`port/in/`, `port/out/`): Interface definitions for inbound/outbound operations
- **Services** (`service/`): Use case implementations that orchestrate domain objects
- `SendMoneyService`: Main business process orchestration with transaction management

**Adapter Layer** (`account/adapter/`):
- **Inbound** (`in/web/`): REST controllers converting HTTP requests to domain commands
- **Outbound** (`out/persistence/`): JPA adapters implementing persistence ports
- `AccountPersistenceAdapter`: Implements both `LoadAccountPort` and `UpdateAccountStatePort`

### Key Architectural Patterns

1. **Dependency Inversion**: Application layer depends on port interfaces, not concrete adapters
2. **Command Pattern**: `SendMoneyCommand` encapsulates transfer requests
3. **Factory Methods**: Domain entities use static factory methods (`Account.withId()`, `Account.withoutId()`)
4. **Mapper Pattern**: `AccountMapper` handles domain ↔ JPA entity conversion

## Architecture Validation

The project uses **ArchUnit** to enforce architectural rules:
- `DependencyRuleTests`: Validates layer dependencies
- `HexagonalArchitecture`: Custom DSL for hexagonal architecture validation
- Run with: `./gradlew test --tests "*DependencyRuleTests*"`

## Technology Stack

- **Java 24** with **Kotlin 2.2** support
- **Spring Boot 3.5** with JPA/Hibernate
- **H2 Database** for development/testing
- **MapStruct** for mapping between layers
- **Lombok** for boilerplate reduction
- **ArchUnit** for architecture testing

## Development Notes

- Uses **strict null safety** in Kotlin (`allWarningsAsErrors = true`)
- **Parallel test execution** enabled for faster feedback
- **AOT compilation** configured for GraalVM native image support
- Database schema recreated on each run (`ddl-auto: create-drop`)

## Test Code Standards

When creating new test code:
- Each test method MUST include JavaDoc annotations
- JavaDoc MUST include `@author Kai Ahn` tag
- Follow existing test patterns and naming conventions

## Architecture Improvement Analysis

### 🔍 클린코드 & 헥사고날 아키텍처 개선 분석 (2025-08-07)

#### 📋 1. 도메인 레이어 개선점

**🔴 즉시 수정 필요:**
- `Account.java:90` - 도메인 로직에 System.out.println 제거 필요 (사이드 이펙트)
- `ActivityWindow.java:38,43` - 비즈니스 로직에서 로깅 코드 분리
- `Account.java:53` - JavaDoc 인코딩 오류 수정 ("ç한다" → "반환한다")

**🟡 개선 권장:**
- Money 클래스에 Comparable 인터페이스 구현
- ActivityWindow 방어적 복사 적용
- Value Object의 불변성 강화

#### 📋 2. 아키텍처 품질 강화 방향

**우선순위별 개선 로드맵:**

**1단계: 기본 품질 향상**
```kotlin
// Money 클래스 완전성 강화
data class Money(val amount: BigInteger) : Comparable<Money> {
    companion object {
        val ZERO = Money(BigInteger.ZERO)
        fun of(value: Long): Money = Money(BigInteger.valueOf(value))
    }
    
    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)
    
    fun plus(other: Money): Money = Money(amount.add(other.amount))
    fun minus(other: Money): Money = Money(amount.subtract(other.amount))
    fun negate(): Money = Money(amount.negate())
    
    fun isPositive(): Boolean = amount > BigInteger.ZERO
    fun isPositiveOrZero(): Boolean = amount >= BigInteger.ZERO
}
```

**2단계: 아키텍처 고도화**
```kotlin
// Result 타입 도입으로 에러 처리 개선
sealed class TransferResult {
    data object Success : TransferResult()
    data class InsufficientFunds(val availableBalance: Money) : TransferResult()
    data class AccountLocked(val accountId: AccountId) : TransferResult()
    data class ThresholdExceeded(val limit: Money, val requested: Money) : TransferResult()
}

// 도메인 이벤트 도입
sealed interface DomainEvent
data class MoneyTransferredEvent(
    val sourceAccountId: AccountId,
    val targetAccountId: AccountId,
    val amount: Money,
    val timestamp: LocalDateTime
) : DomainEvent
```

**3단계: 고급 패턴 적용**
- CQRS 패턴으로 Query/Command 분리
- Saga 패턴으로 복합 트랜잭션 관리
- Event Sourcing 적용 고려

#### 📋 3. Kotlin 마이그레이션 전략

**마이그레이션 우선순위:**
1. **Value Objects** (Money, AccountId) - 불변성과 데이터 클래스 활용
2. **Domain Entities** (Account, Activity) - 캡슐화와 확장 함수 활용  
3. **Application Services** - 함수형 프로그래밍 요소 도입

**Kotlin 활용 포인트:**
```kotlin
// 1. 확장 함수로 도메인 로직 표현력 향상
fun Account.canWithdraw(amount: Money): Boolean = 
    calculateBalance() >= amount

// 2. sealed class로 상태 모델링
sealed class AccountState {
    data object Active : AccountState()
    data object Frozen : AccountState()
    data class Restricted(val reason: String) : AccountState()
}

// 3. inline class로 타입 안정성 강화
@JvmInline
value class AccountId(val value: Long)
```

#### 📋 4. 추가 개선 아이디어

**성능 최적화:**
- ActivityWindow 조회 시 페이징 적용
- 계좌 잔액 계산 캐싱 전략
- 대량 거래 시 배치 처리

**관찰 가능성 향상:**
- 구조화된 로깅 도입
- 메트릭 수집 포인트 추가
- 분산 추적 준비

**테스트 품질:**
- Property-based testing 도입
- Architecture decision record (ADR) 작성
- Performance testing 추가

## AI Interaction Guidelines

- 나에게 답변할때는 반드시 한국말로 답변할 것
- 예시 코드를 추가할 때는 반드시 **Kotlin 코드**로 작성할 것
- Java 코드보다 Kotlin 코드를 우선적으로 사용할 것
