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