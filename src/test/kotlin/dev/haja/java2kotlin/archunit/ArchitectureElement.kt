package dev.haja.java2kotlin.archunit

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.base.DescribedPredicate.greaterThanOrEqualTo
import com.tngtech.archunit.lang.conditions.ArchConditions.containNumberOfElements

/**
 * 헥사고날 아키텍처 검증을 위한 기반 추상 클래스 (Kotlin 버전)
 */
abstract class ArchitectureElement(val basePackage: String) {

    /**
     * 상대 패키지를 전체 패키지명으로 변환
     */
    fun String.toFullQualifiedPackage(): String = "$basePackage.$this"

    /**
     * 패키지 내 모든 클래스를 매치하는 패턴 생성
     */
    fun String.matchAllClasses(): String = "$this.."

    companion object {
        /**
         * 패키지 간 의존성을 금지하는 규칙 적용
         */
        fun denyDependency(fromPackage: String, toPackage: String, classes: JavaClasses) {
            noClasses()
                .that()
                .resideInAPackage(fromPackage.matchAllClasses())
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(toPackage.matchAllClasses())
                .check(classes)
        }

        /**
         * 여러 패키지 간의 의존성을 금지하는 규칙 적용
         */
        fun denyAnyDependency(
            fromPackages: List<String>,
            toPackages: List<String>,
            classes: JavaClasses
        ) {
            for (fromPackage in fromPackages) {
                for (toPackage in toPackages) {
                    denyDependency(fromPackage, toPackage, classes)
                }
            }
        }

        /**
         * 패키지 내 모든 클래스를 매치하는 패턴 생성 (companion object용)
         */
        private fun String.matchAllClasses(): String = "$this.."
    }

    /**
     * 빈 패키지를 금지하는 규칙 적용
     */
    fun denyEmptyPackage(packageName: String) {
        classes()
            .that()
            .resideInAPackage(packageName.matchAllClasses())
            .should(containNumberOfElements(greaterThanOrEqualTo(1)))
            .check(ClassFileImporter().importPackages(packageName))
    }

    /**
     * 여러 패키지가 빈 패키지가 아님을 검증
     */
    fun denyEmptyPackages(packages: List<String>) {
        packages.forEach(::denyEmptyPackage)
    }
}