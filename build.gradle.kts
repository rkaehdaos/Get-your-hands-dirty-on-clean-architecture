import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    java
    pmd
    alias(libs.plugins.ksp)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
    alias(libs.plugins.hibernate.orm)
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.kapt)
}
// 의존성/플러그인 버전은 Version Catalog(gradle/libs.versions.toml)에서 관리
// 언어 버전(java)도 카탈로그의 [versions]에서 가져옴
val javaVersion = libs.versions.java.get()

// 프로젝트 메타데이터(group은 gradle.properties의 group으로 자동 설정)
// releaseVer만 version 문자열 조합에 사용.
// Gradle 10에서 제거될 'by project' 위임 문법 대신 Provider API 사용
val releaseVer: String = providers.gradleProperty("releaseVer").get()

version =
    "$releaseVer-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}"
description = "Get-your-hands-dirty-on-clean-architecture"

configurations {
    compileOnly { extendsFrom(configurations.annotationProcessor.get()) }
    testCompileOnly { extendsFrom(configurations.testAnnotationProcessor.get()) }
}

repositories {
    mavenCentral()
}

dependencies {
    // spring
    implementation(platform(libs.spring.boot.dependencies))

    // Spring Boot Configuration Processor
    // NOTE: Java → Kotlin 마이그레이션 완료 후 annotationProcessor 제거, kapt만 유지
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    kapt("org.springframework.boot:spring-boot-configuration-processor")

    // spring starter
    // Spring Boot 4.0: spring-boot-starter-web → spring-boot-starter-webmvc
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // JPA
    implementation(libs.jakarta.persistence.api)

    // Kotlin - Spring Boot 4.0: Jackson 3 (tools.jackson)로 마이그레이션
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Test - Spring Boot 4.0: 모듈화된 테스트 스타터 사용
    // Spring Security를 사용하지 않으므로 security-test 제외
    // Spring Boot 4.1: classic 테스트 스타터에 grpc-test가 편입됨.
    // grpc-test의 spring.factories가 GrpcPortInfoApplicationContextInitializer를
    // 무조건 등록 → GrpcServerStartedEvent(spring-grpc) 미존재로 컨텍스트 로딩 실패.
    // gRPC를 사용하지 않으므로 grpc-test 제외
    testImplementation("org.springframework.boot:spring-boot-starter-test-classic") {
        exclude(group = "org.springframework.boot", module = "spring-boot-security-test")
        exclude(group = "org.springframework.boot", module = "spring-boot-grpc-test")
    }
    // Spring Boot 4.0: 슬라이스 테스트를 위한 개별 테스트 모듈
    // starter 대신 core 모듈 직접 사용 (Spring Security를 사용하지 않으므로)
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")   // @WebMvcTest
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test") // @DataJpaTest
    // Spring Boot 4.0: RestTemplateBuilder가 restclient 모듈로 분리됨
    // resttestclient가 restclient를 transitive 의존하지 않아 명시적 추가 필요
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.boot:spring-boot-resttestclient") // TestRestTemplate, @AutoConfigureTestRestTemplate
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // BOM에서 관리하지 않는 라이브러리들만 버전 명시
    testImplementation(libs.archunit.junit5.engine)
    testImplementation(libs.mockito.kotlin)

    // MapStruct Core
    implementation(libs.mapstruct)
    // NOTE: Java → Kotlin 마이그레이션 시 Lombok 제거 후 annotationProcessor 제거, kapt만 유지
    annotationProcessor(libs.mapstruct.processor)
    testAnnotationProcessor(libs.mapstruct.processor)
    kapt(libs.mapstruct.processor)

    // MapStruct Spring Extensions
    implementation(libs.mapstruct.spring.annotations)
    implementation(libs.mapstruct.spring.extensions)
    kapt(libs.mapstruct.spring.extensions)

    // MapStruct Test only
    testImplementation(libs.mapstruct.spring.test.extensions)

    // Lombok - Java → Kotlin 마이그레이션 시 전체 제거
    // NOTE: MapStruct와 함께 사용 시 Lombok이 먼저 처리되어야 함 (순서 중요)
    compileOnly("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // Lombok-MapStruct 통합 바인딩 - Java → Kotlin 마이그레이션 시 제거
    annotationProcessor(libs.lombok.mapstruct.binding)
    testAnnotationProcessor(libs.lombok.mapstruct.binding)

//    Kotlin 테스트 라이브러리
//    Kotest 테스트 프레임워크는 JVM, Android, 자바스크립트 및 네이티브 환경에서 지원됩니다.
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)

    // dev only
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // DB - BOM에서 버전 관리
    // H2: 개발 및 테스트 환경에서 사용
    developmentOnly("com.h2database:h2")
    testRuntimeOnly("com.h2database:h2")
    // PostgreSQL: 프로덕션 환경에서 사용
    runtimeOnly("org.postgresql:postgresql")

// testcontainers
/*
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:jdbc")
    testImplementation("org.testcontainers:mariadb")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:ollama")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:cassandra")
    testImplementation("org.testcontainers:selenium")
    testImplementation("org.testcontainers:vault")
    testImplementation("org.testcontainers:mockserver")
    testImplementation("org.testcontainers:nginx")
    testImplementation("org.testcontainers:consul")
    testImplementation("org.testcontainers:influxdb")
    testImplementation("org.testcontainers:activemq")
    testImplementation("org.testcontainers:grafana")
    testImplementation("org.testcontainers:docker-compose")
    testImplementation("org.testcontainers:ldap")
    testImplementation("org.testcontainers:jdbc-test")
    testImplementation("org.testcontainers:docs-examples")
    testImplementation("org.testcontainers:k6")

    testImplementation("org.testcontainers:r2dbc")
    testImplementation("org.testcontainers:rabbitmq")
*/

}

kapt {
    keepJavacAnnotationProcessors = true  // Java AP 병행 실행 (Lombok 처리용)
    correctErrorTypes = true              // 타입 에러 정확도 향상
    arguments {
        arg("mapstruct.defaultComponentModel", "spring")
        arg("mapstruct.defaultInjectionStrategy", "constructor")
        // NOTE: 운영 환경에서는 unmappedSourcePolicy, unmappedTargetPolicy를 ERROR로 강화 권장
        arg("mapstruct.unmappedSourcePolicy", "WARN")
        arg("mapstruct.unmappedTargetPolicy", "WARN")
        arg("mapstruct.verbose", "true")
        arg("mapstruct.suppressGeneratorTimestamp", "true")
        arg("mapstruct.suppressGeneratorVersionInfoComment", "true")
        arg("mapstruct.defaultNullValuePropertyMappingStrategy", "SET_TO_NULL")
    }
}

// Hibernate 7.x에서 association management enhancement가 deprecated 되었고,
// 기본값이 비활성화(false)이므로 별도 설정 없이 기본값 사용 (deprecated 경고 제거)

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// PMD 정적 분석: 커스텀 룰셋만 적용 (NcssCount 메서드 15줄 제한 등)
// Kotlin 파일은 PMD 대상이 아님 (Java 소스만 분석)
pmd {
    toolVersion = libs.versions.pmd.get()
    ruleSetFiles = files(".github/pmd/ruleset.xml")
    ruleSets = listOf()          // 내장 기본 룰셋 비활성화, 커스텀 룰셋만 사용
    isConsoleOutput = true
    isIgnoreFailures = false     // 위반 시 빌드 실패
}

// Spring Boot AOT가 추가하는 aot/aotTest 소스셋은 생성 코드이므로 PMD 분석 제외
// (compileAotJava의 -Xlint:none 처리와 동일한 취지)
tasks.matching { it.name == "pmdAot" || it.name == "pmdAotTest" }.configureEach {
    enabled = false
}

// JAR 태스크: 중복 파일 처리 전략 (KAPT + annotationProcessor 병행 시)
tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Jar>("bootJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// 일반 Java 컴파일에서는 경고 활성화
tasks.named("compileJava", JavaCompile::class) {
    options.compilerArgs.add("-Xlint:unchecked")
}

// AOT 컴파일 태스크에서는 생성된 코드의 경고 완전 제거
tasks.named("compileAotJava", JavaCompile::class) {
    options.compilerArgs.addAll(listOf(
        "-Xlint:none"  // 모든 경고 완전 제거
    ))
}

tasks.withType<Test> {
    // Test 유형의 모든 테스트 task 공통 configure 용
}
tasks.named<Test>("test") {
    useJUnitPlatform()
    maxParallelForks = Runtime.getRuntime().availableProcessors()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
tasks.named("processTestAot").configure {
    enabled = false
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")             //  JSR-305 애노테이션의 null 안정성 어노테이션을 엄격
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn") // 실험적 API등 API를 사용할 때 해당 옵트인 어노테이션 사용을 허용
        allWarningsAsErrors = true
        jvmTarget.set(JvmTarget.fromTarget(javaVersion))
        // languageVersion / apiVersion 미지정 → 플러그인(Kotlin Version Catalog) 기본값 자동 추종.
        // 애플리케이션이라 apiVersion 고정이 불필요하고, 컴파일러 버전이 곧 단일 언어 표준.
        // 명시하면 컴파일러 버전을 미러링만 하면서 업그레이드마다 lockstep bump가 필요하고,
        // 누락 시 allWarningsAsErrors와 맞물려 deprecated-language-version 경고가 빌드 실패로 이어짐.
    }
}

configurations.all {
    resolutionStrategy {
        // 캐시
        // prod: 하루에 한 번만 체크
//        cacheDynamicVersionsFor(24, TimeUnit.HOURS)
//        cacheChangingModulesFor(24, TimeUnit.HOURS)

        // dev 서버용: 10분마다 새 버전 체크 (너무 자주 체크하면 빌드 느림)
//        cacheDynamicVersionsFor(10, TimeUnit.MINUTES)
        // SNAPSHOT은 5분마다 체크
//        cacheChangingModulesFor(5, TimeUnit.MINUTES)
    }
}
