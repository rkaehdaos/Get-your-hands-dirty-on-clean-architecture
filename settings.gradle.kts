rootProject.name = "Get-your-hands-dirty-on-clean-architecture"

pluginManagement {
    // 버전 변수 정의
    val springBootVersion = providers.gradleProperty("springBootVersion").orNull ?: "3.5.5"
    val hibernatePluginVersion = providers.gradleProperty("hibernateVersion").orNull ?: "6.6.26.Final"
    val kotlinVersion = providers.gradleProperty("kotlinVersion").orNull ?: "2.2.20"
    val dependencyManageVer = providers.gradleProperty("dependencyManageVer").orNull ?: "1.1.7"

    repositories {}
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        kotlin("plugin.jpa") version kotlinVersion
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version dependencyManageVer
        id("org.hibernate.orm") version hibernatePluginVersion
        id("org.graalvm.buildtools.native") version "0.11.0"
    }
}