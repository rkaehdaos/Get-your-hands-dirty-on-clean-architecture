plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.hibernate.orm") version "6.5.3.Final"
    id("org.graalvm.buildtools.native") version "0.10.3"
}

group = "dev.haja"
version = "0.0.1-SNAPSHOT"

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
    // annotation
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    // spring boot starter
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // h2database
    runtimeOnly("com.h2database:h2")
    testImplementation("com.h2database:h2")

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