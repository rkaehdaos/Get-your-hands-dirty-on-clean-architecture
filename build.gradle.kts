import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.hibernate.orm") version "6.5.3.Final"
    id("org.graalvm.buildtools.native") version "0.10.4"
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
}
group = "dev.haja"
var releaseVer = "v0.0.1"
version = "$releaseVer-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    sourceCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly { extendsFrom(configurations.annotationProcessor.get()) }
    testCompileOnly { extendsFrom(configurations.testAnnotationProcessor.get()) }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    gradlePluginPortal()
}

dependencies {
    // spring
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.tngtech.archunit:archunit-junit5-engine:1.3.0")

    // h2database
    runtimeOnly("com.h2database:h2")
    testImplementation("com.h2database:h2")

    // MapStruct
    implementation("org.mapstruct:mapstruct:1.6.3")
    testImplementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    testAnnotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.36")
    testCompileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.36")

    // Lombok과 MapStruct 통합
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    testAnnotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
}
hibernate {
    enhancement {
        enableAssociationManagement = true
    }
}

tasks.withType<JavaCompile> {
    // tasks.named("compileJava") 과 다름에 유의
    options.compilerArgs.add("-Xlint:deprecation")
//    options.compilerArgs.add("-Xlint:unchecked")
    options.encoding = "UTF-8"
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
    kotlinOptions {
        jvmTarget = "21"
    }
}

// null safety strict
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// 특정 어노테이션에 대해 자동으로 open 키워드 추가
allOpen {
    annotation("jakarta.persistence.Entity") // 엔티티는 프록시로 대체될 수 있어야 함
    annotation("jakarta.persistence.MappedSuperclass") //공통 부모, 상속 가능해야
    annotation("jakarta.persistence.Embeddable") // 값 타입
}
