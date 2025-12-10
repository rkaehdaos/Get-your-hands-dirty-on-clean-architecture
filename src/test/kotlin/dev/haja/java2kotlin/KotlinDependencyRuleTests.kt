package dev.haja.java2kotlin

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import dev.haja.java2kotlin.archunit.hexagonalArchitecture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Kotlin DSL을 사용한 헥사고날 아키텍처 의존성 규칙 테스트
 *
 * @see dev.haja.java2kotlin.archunit.HexagonalArchitecture
 */
class KotlinDependencyRuleTests {

    private val allClasses = ClassFileImporter().importPackages("dev.haja.buckpal")

    @Test
    @DisplayName("도메인 계층은 애플리케이션 계층에 의존해서는 안된다")
    fun domainLayerShouldNotDependOnApplicationLayer() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..")
            .check(allClasses)
    }

    @Test
    @DisplayName("헥사고날 아키텍처 준수 (Kotlin DSL)")
    fun shouldComplyWithHexagonalArchitecture() {
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
}
