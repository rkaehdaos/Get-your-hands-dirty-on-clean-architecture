# Repository Guidelines

## Project Structure & Module Organization
- `src/main/kotlin/dev/haja/...` contains the primary Kotlin code; `buckpal` models the clean architecture layering and `java2kotlin` holds migration exercises.
- `src/main/java` stores remaining Java adapters; prefer new implementations in Kotlin.
- `src/main/resources` manages Spring configuration and database setup; `docs/` captures architecture notes; Gradle outputs live under `build/`.
- Mirror production packages in `src/test/kotlin` or `src/test/java` and keep shared fixtures in `src/test/resources/dev`.

## Build, Test, and Development Commands
- `./gradlew clean build` runs compilation, annotation processors (KSP/KAPT), and the full verification suite.
- `./gradlew test` executes JUnit Platform tests, including Kotest specs and Spring Boot slices.
- `./gradlew bootRun` starts the application with the default H2 profile for local exploration.
- `./gradlew kaptKotlin` regenerates MapStruct stubs when touching mapper DTOs; otherwise rely on the build task.

## Coding Style & Naming Conventions
- Follow a Kotlin-first approach with four-space indentation, trailing commas, and constructor injection via Spring annotations.
- Respect the `dev.haja` package prefix; separate domain, application, and adapter code inside each feature module.
- Use PascalCase for types, camelCase for members, and describe tests with `should...` phrasing or Kotest spec styles.
- Keep configuration constants in dedicated `config` packages and expose ports/adapters through clear interfaces.

## Testing Guidelines
- Favor JUnit5 for Spring integration tests and Kotest + MockK for domain behaviour; legacy Java tests may continue with Mockito.
- Co-locate tests with their production packages; name classes `<Subject>Test` or adopt Kotest `DescribeSpec`/`BehaviorSpec`.
- Maintain coverage on domain aggregates and update ArchUnit rules when introducing new module boundaries.
- Run `./gradlew test` before pushing; place reusable SQL or JSON fixtures in `src/test/resources/dev`.

## Commit & Pull Request Guidelines
- Write imperative, present-tense commit messages and reference related issues using the `(#NN)` suffix as seen in `git log`.
- Keep pull requests focused, include a summary of architectural impact, and attach `./gradlew test` results or relevant screenshots.
- Call out configuration or dependency changes explicitly and request reviews for boundary adjustments across modules or layers.
