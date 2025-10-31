rootProject.name = "Get-your-hands-dirty-on-clean-architecture"

pluginManagement {
    // 버전 변수 정의
    val springBootVersion = providers.gradleProperty("springBootVersion").get()
    val hibernateVersion = providers.gradleProperty("hibernateVersion").get()
    val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
    val kspVersion = providers.gradleProperty("kspVersion").get()
    val dependencyManageVer = providers.gradleProperty("dependencyManageVer").get()
    val nativeBuildVersion = providers.gradleProperty("nativeBuildVersion").get()


    // ✅ 플러그인 저장소 지정 (필수!)
    repositories {
        gradlePluginPortal()  // Gradle 공식 플러그인 포털
        mavenCentral()        // Maven Central
        maven { url = uri("https://repo.spring.io/milestone") }  // Spring 마일스톤 (필요시)
    }
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        kotlin("plugin.jpa") version kotlinVersion
        kotlin("kapt") version kotlinVersion
//        id("com.google.devtools.ksp") version "${kotlinVersion}-${kspVersion}"
        id("com.google.devtools.ksp") version kspVersion
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version dependencyManageVer
        id("org.hibernate.orm") version hibernateVersion
        id("org.graalvm.buildtools.native") version nativeBuildVersion
    }
}