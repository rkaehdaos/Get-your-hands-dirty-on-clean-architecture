package dev.haja.java2kotlin

import com.tngtech.archunit.core.importer.ClassFileImporter
import dev.haja.java2kotlin.archunit.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Kotlin으로 구현된 헥사고날 아키텍처 의존성 규칙 테스트
 */
class KotlinDependencyRuleTests {

    private val allClasses = ClassFileImporter().importPackages("dev.haja.buckpal")

    @Test
    @DisplayName("도메인 계층은 애플리케이션 계층에 의존해서는 안된다 (Kotlin DSL)")
    fun domainLayerDoesNotDependOnApplicationLayer() {
        allClasses.shouldNotDependOn("..domain..", "..application..")
    }

    @Test
    @DisplayName("헥사고날 아키텍처 준수 - 기존 방식 (Kotlin)")
    fun validateHexagonalArchitectureTraditional() {
        HexagonalArchitecture.basePackage("dev.haja.buckpal.account")
            .withDomainLayer("domain")
            .withAdaptersLayer("adapter")
                .incoming("in.web")
                .outgoing("out.persistence")
                .and()
            .withApplicationLayer("application")
                .services("service")
                .incomingPorts("port.in")
                .outgoingPorts("port.out")
                .and()
            .withConfiguration("configuration")
            .check(allClasses)
    }

    @Test
    @DisplayName("헥사고날 아키텍처 준수 - DSL 방식 (Kotlin)")
    fun validateHexagonalArchitectureWithDsl() {
        hexagonalArchitecture("dev.haja.buckpal.account") {
            domain("domain")
            
            adapters("adapter") {
                incoming("in.web")
                outgoing("out.persistence")
            }
            
            application("application") {
                services("service")
                incomingPorts("port.in")
                outgoingPorts("port.out")
            }
            
            configuration("configuration")
        }.check(allClasses)
    }

    @Test
    @DisplayName("헥사고날 아키텍처 준수 - 확장 함수 방식 (Kotlin)")
    fun validateHexagonalArchitectureWithExtensions() {
        "dev.haja.buckpal.account".verifyHexagonalArchitecture {
            domain("domain")
            
            adapters("adapter") {
                incoming("in.web")
                outgoing("out.persistence")
            }
            
            application("application") {
                services("service")
                incomingPorts("port.in")
                outgoingPorts("port.out")
            }
            
            configuration("configuration")
        }.check(allClasses)
    }

    @Test
    @DisplayName("간편한 아키텍처 검증 - 유틸리티 함수 방식 (Kotlin)")
    fun validateHexagonalArchitectureWithUtility() {
        verifyArchitecture("dev.haja.buckpal.account", allClasses) {
            domain("domain")
            
            adapters("adapter") {
                incoming("in.web")
                outgoing("out.persistence")
            }
            
            application("application") {
                services("service")
                incomingPorts("port.in")
                outgoingPorts("port.out")
            }
            
            configuration("configuration")
        }
    }

    @Test
    @DisplayName("타입 안전한 패키지 이름 테스트 (Kotlin)")
    fun testTypeSafePackageNames() {
        val basePackage = "dev.haja.buckpal.account".toPackageName()
        val domainPackage = basePackage + "domain"
        val adapterPackage = basePackage + "adapter"
        
        // 타입 안전성이 컴파일 타임에 검증됨
        println("Base package: $basePackage")
        println("Domain package: $domainPackage") 
        println("Adapter package: $adapterPackage")
    }

    @Test
    @DisplayName("여러 패키지 클래스 import 테스트 (Kotlin)")
    fun testMultiplePackageImport() {
        val packages = listOf(
            "dev.haja.buckpal.account.domain",
            "dev.haja.buckpal.account.application",
            "dev.haja.buckpal.account.adapter"
        )
        
        val classes = packages.importPackageClasses()
        
        // 기본적인 도메인 규칙 검증
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapter..")
            .check(classes)
    }
}