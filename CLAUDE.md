# CLAUDE.md

이 파일은 이 저장소에서 코드를 작업할 때 Claude Code(claude.ai/code)에 대한 지침을 제공합니다.

## Build and Development Commands

이것은 Gradle과 Kotlin DSL을 사용한 Spring Boot 애플리케이션입니다.

### Build Commands
- `./gradlew build` - Build the entire project
- `./gradlew assemble` - Build without running tests
- `./gradlew clean` - Clean build artifacts
- `./gradlew bootJar` - Create executable JAR

### Running the Application
- `./gradlew bootRun` - Run the Spring Boot application
- `./gradlew bootTestRun` - Run using test runtime classpath

### Testing
- `./gradlew test` - Run all tests (uses JUnit 5 Platform)
- `./gradlew nativeTest` - Run tests as native binary
- Tests run in parallel using all available processors
- Architecture tests validate hexagonal architecture compliance using ArchUnit

## Architecture Overview

This project implements **Hexagonal Architecture** (Clean Architecture) for a banking application called "BuckPal". The architecture enforces strict separation of concerns with the following layers:

### Core Structure
```
src/main/java/dev/haja/buckpal/
├── account/                    # Main business domain
│   ├── domain/                # Domain entities and business logic
│   │   ├── Account.java       # Core account entity with business rules
│   │   ├── Activity.java      # Account activity/transaction entity
│   │   ├── ActivityWindow.java # Collection of activities
│   │   └── Money.java         # Value object for monetary amounts
│   ├── application/           # Application services and ports
│   │   ├── port/
│   │   │   ├── in/           # Inbound ports (use cases)
│   │   │   └── out/          # Outbound ports (repositories)
│   │   └── service/          # Application services implementing use cases
│   └── adapter/              # External adapters
│       ├── in/web/          # Web controllers (REST APIs)
│       └── out/persistence/ # Database persistence adapters
└── common/                   # Shared infrastructure annotations
```

### Architectural Rules (Enforced by Tests)
- **Domain Layer**: Contains pure business logic, no dependencies on other layers
- **Application Layer**: Orchestrates business flows, depends only on domain
- **Adapter Layer**: Handles external communication (web, database)
- Ports define contracts between layers
- Dependency direction: Adapters → Application → Domain

### Key Design Patterns
- **Ports and Adapters**: Clear interface contracts between layers  
- **Factory Methods**: Domain entities use static factory methods (e.g., `Account.withId()`, `Account.withoutId()`)
- **Value Objects**: `Money`, `AccountId` are immutable value objects
- **Command Pattern**: Operations like `SendMoneyCommand` encapsulate requests
- **Repository Pattern**: Data access through port interfaces

## Technology Stack

### Core Technologies
- **Java 24** with **Kotlin 2.2** (mixed language project)
- **Spring Boot 3.5.4** (Web, JPA, Validation, Actuator)
- **Spring Data JPA** with Hibernate 6.6.22.Final
- **H2 Database** (runtime and testing)

### Development Tools
- **Lombok** - Reduces boilerplate code with Kotlin plugin support
- **MapStruct 1.6.3** - Type-safe bean mapping
- **Lombok-MapStruct binding** - Integration between Lombok and MapStruct
- **Kassava 2.1.0** - Kotlin data class utilities
- **ArchUnit 1.4.1** - Architecture testing framework
- **GraalVM Native Image** support

### Code Quality
- **Kotlin strict null safety** enabled (`-Xjsr305=strict`)
- **All warnings as errors** in Kotlin compilation
- **ArchUnit tests** enforce architectural boundaries
- **JPA entities** automatically opened for proxy creation

## Project Configuration

### Database
- Development: H2 in-memory database
- JPA DDL: `create-drop` (recreates schema on startup)
- Hibernate SQL logging enabled in debug mode

### Application Profiles
- Default active profile: `local`
- Application name: "Get-your-hands-dirty-on-clean-architecture"

## Testing Strategy

### Test Structure
- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test adapter integrations (e.g., `AccountPersistenceAdapterTest`)
- **System Tests**: End-to-end testing (`SendMoneySystemTest`) 
- **Architecture Tests**: Validate hexagonal architecture rules (`DependencyRuleTests`)

### Key Test Classes
- Architecture compliance verified in `DependencyRuleTests.java:24`
- System-level money transfer tested in `SendMoneySystemTest`
- Domain logic tested in `AccountTest`, `ActivityWindowTest`

## Important Implementation Notes

### Domain Model
- `Account` entities track balance through baseline + activity window calculation
- Business rules (e.g., withdrawal limits) are enforced in domain entities
- Money transfers require locking both source and target accounts
- All monetary operations use the `Money` value object for type safety

### Persistence Strategy  
- JPA entities (`AccountJpaEntity`, `ActivityJpaEntity`) separate from domain entities
- `AccountMapper` handles conversion between JPA and domain entities
- Account locking mechanism (`AccountLock`) prevents concurrent modification

### Error Handling
- `ThresholdExceededException` for transfer limit violations
- Domain validation through `SelfValidating` base class
- Proper error messages with account descriptions

The codebase demonstrates clean architecture principles with clear separation between business logic, application orchestration, and infrastructure concerns.