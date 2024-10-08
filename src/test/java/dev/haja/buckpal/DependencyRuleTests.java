package dev.haja.buckpal;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.haja.buckpal.archunit.HexagonalArchitecture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class DependencyRuleTests {
    @Test
    @DisplayName("도메인 계층은 애플리케이션 계층에 의존해서는 안된다.")
    void domainLayerDoesNotDependOnApplicationLayer() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat().resideInAPackage("..application..")
                .check(new ClassFileImporter().importPackages("dev.haja.buckpal"));
    }

    @Test
    @DisplayName("헥사고날 아키텍처 준수")
    void validateRegistrationContextArchitecture() {
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
                .check(new ClassFileImporter()
                        .importPackages("dev.haja.buckpal.."));
    }
}