rootProject.name = "Get-your-hands-dirty-on-clean-architecture"

pluginManagement {
    // 플러그인 버전은 Version Catalog(gradle/libs.versions.toml)에서 관리
    // build.gradle.kts의 plugins 블록에서 alias(libs.plugins.xxx)로 적용

    // ✅ 플러그인 저장소 지정 (필수!)
    repositories {
        gradlePluginPortal()  // Gradle 공식 플러그인 포털
        mavenCentral()        // Maven Central
        maven { url = uri("https://repo.spring.io/milestone") }  // Spring 마일스톤 (필요시)
    }
}
