import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.hibernate.orm")
    id("org.graalvm.buildtools.native")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}
//직접 할당 고정값
// 선언과 동시에 값이 결정되는 `즉시 초기화`
// 컴파일러가 String 추론하므로 타입 x
val releaseVer = "v0.0.1"

// property delegation 사용 - runtime시 프로퍼티에서 값을 가져옴
// `:` 타입을 명시적으로 선언 - 컴파일러가 타입 추론을 못하므로
// 외부 properties에서 값을 가져오는 delegation
val jpaVersion: String by project
val kotestVersion: String by project
val mockkVersion: String by project

group = "dev.haja"
version =
    "$releaseVer-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}"
description = "new Smartwork"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
    sourceCompatibility = JavaVersion.VERSION_24
}

configurations {
    compileOnly { extendsFrom(configurations.annotationProcessor.get()) }
    testCompileOnly { extendsFrom(configurations.testAnnotationProcessor.get()) }
}

repositories {
    mavenCentral()
}

dependencies {
    // spring
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("jakarta.persistence:jakarta.persistence-api:$jpaVersion")

    // test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.tngtech.archunit:archunit-junit5-engine:1.4.1")

    // h2database
    runtimeOnly("com.h2database:h2")
    testImplementation("com.h2database:h2")

    // MapStruct
    implementation("org.mapstruct:mapstruct:1.6.3")
    testImplementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    testAnnotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // Lombok과 MapStruct 통합
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    testAnnotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Kotlin 테스트 라이브러리 : KotestVersion
//    Kotest 테스트 프레임워크는 JVM, Android, 자바스크립트 및 네이티브 환경에서 지원됩니다.

    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
//    testImplementation("com.ninja-squad:springmockk:4.0.2") // Spring과 MockK 통합

    // dev only
    developmentOnly("org.springframework.boot:spring-boot-devtools")

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

//  kotlin
tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_24)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

// null safety strict
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-opt-in=kotlin.RequiresOptIn"
        )
        allWarningsAsErrors = true
    }
}
