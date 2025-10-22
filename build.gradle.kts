import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    java
    id("com.google.devtools.ksp")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.hibernate.orm")
    id("org.graalvm.buildtools.native")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("kapt")
}
//직접 할당 고정값
// 선언과 동시에 값이 결정되는 `즉시 초기화`
// 컴파일러가 String 추론하므로 타입 x
//val releaseVer = "v0.0.1"

// property delegation 사용 - runtime시 프로퍼티에서 값을 가져옴
// `:` 타입을 명시적으로 선언 - 컴파일러가 타입 추론을 못하므로
// 외부 properties에서 값을 가져오는 delegation
val springBootVersion: String by project
val jpaVersion: String by project
val kotestVersion: String by project
val mockkVersion: String by project
val springMockKVersion: String by project
val mapstructVersion: String by project
val mapstructSpringVersion: String by project
val group: String by project
val releaseVer: String by project
val profile = System.getProperty("spring.profiles.active")
    ?: System.getenv("SPRING_PROFILES_ACTIVE")
    ?: "local"

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
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))

    // spring AP - Java + Kotlin 모두 지원
    // TODO: Kotlin 마이그레이션 완료 시 kapt만 사용
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    kapt("org.springframework.boot:spring-boot-configuration-processor")

    // spring starter
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // JPA
    implementation("jakarta.persistence:jakarta.persistence-api:$jpaVersion")

    // Kotlin - BOM에서 관리되는 버전 사용
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Test - Spring Boot BOM이 관리
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // BOM에서 관리하지 않는 라이브러리들만 버전 명시
    testImplementation("com.tngtech.archunit:archunit-junit5-engine:1.4.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.0.0")



    // MapStruct Core
    implementation("org.mapstruct:mapstruct:${mapstructVersion}")
    // TODO: Kotlin 마이그레이션 끝나고 향후 Lombok 제거 시 kapt만 남기고 정리할 것
    annotationProcessor("org.mapstruct:mapstruct-processor:${mapstructVersion}")
    testAnnotationProcessor("org.mapstruct:mapstruct-processor:${mapstructVersion}")
    kapt("org.mapstruct:mapstruct-processor:${mapstructVersion}")

    // MapStruct Spring Extensions
    implementation("org.mapstruct.extensions.spring:mapstruct-spring-annotations:${mapstructSpringVersion}")
    implementation("org.mapstruct.extensions.spring:mapstruct-spring-extensions:${mapstructSpringVersion}")
    kapt("org.mapstruct.extensions.spring:mapstruct-spring-extensions:${mapstructSpringVersion}")

    // MapStruct Test only
    testImplementation("org.mapstruct.extensions.spring:mapstruct-spring-test-extensions:${mapstructSpringVersion}")

    // TODO: kotlin 마이그레이션시 lombok, binding 전체 제거
    // Lombok(mapStruct 뒤에 와야 함)
    compileOnly("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // Lombok과 MapStruct 통합
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    testAnnotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

//    Kotlin 테스트 라이브러리
//    Kotest 테스트 프레임워크는 JVM, Android, 자바스크립트 및 네이티브 환경에서 지원됩니다.
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-framework-api-jvm:${kotestVersion}")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.ninja-squad:springmockk:$springMockKVersion")

    // dev only
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // DB - BOM에서 버전 관리
    // h2database
    // main DB :  (dev, demo, prod)
    runtimeOnly("com.h2database:h2")

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
        // local, dev mode
        // TODO: prod에서는 ERROR로 강화 고려

        arg("mapstruct.unmappedSourcePolicy", "WARN")
        arg("mapstruct.unmappedTargetPolicy", "WARN")
        arg("mapstruct.verbose", "true")
        arg("mapstruct.suppressGeneratorTimestamp", "true")
        arg("mapstruct.suppressGeneratorVersionInfoComment", "true")
        arg("mapstruct.defaultNullValuePropertyMappingStrategy", "SET_TO_NULL")
    }
}

hibernate {
    enhancement {
        // Hibernate 7.x에서 deprecated
        //  성능 최적화를 위해 비활성화
        enableAssociationManagement = false
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
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
        if (profile == "local" || profile == "dev") {
            // 상세 로그
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStackTraces = true
            showCauses = true
            showExceptions = true
            showStandardStreams = true
            println("🔍 테스트 상세 로그 활성화 (profile: $profile)")
        } else {
            // 간단한 로그 (demo, prod)
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
            showStackTraces = false
            println("📝 테스트 간단 로그 (profile: $profile)")
        }
    }
}
tasks.named("processTestAot").configure {
    enabled = false
}

kotlin {
    jvmToolchain(24)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")             //  JSR-305 애노테이션의 null 안정성 어노테이션을 엄격
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn") // 실험적 API등 API를 사용할 때 해당 옵트인 어노테이션 사용을 허용
        freeCompilerArgs.add("-Xjvm-default=all-compatibility") // Java 상호운용성 개선
        allWarningsAsErrors = true
        jvmTarget.set(JvmTarget.JVM_24)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

configurations.all {
    resolutionStrategy {
        // CVE-2025-48924 보안 취약점 해결
        force("org.apache.commons:commons-lang3:3.18.0")

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