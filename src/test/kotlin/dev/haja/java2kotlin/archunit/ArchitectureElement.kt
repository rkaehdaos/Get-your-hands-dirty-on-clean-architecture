package dev.haja.java2kotlin.archunit

import com.tngtech.archunit.base.DescribedPredicate.greaterThanOrEqualTo
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.conditions.ArchConditions.containNumberOfElements
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * 헥사고날 아키텍처 검증을 위한 기반 클래스
 *
 * Kotlin 특징:
 * - 확장 프로퍼티/함수를 통한 가독성 향상
 * - 고차 함수를 활용한 의존성 검증
 * - 불변 컬렉션 기본 사용
 */
abstract class ArchitectureElement(val basePackage: String) {

    /**
     * 상대 패키지를 전체 패키지명으로 변환
     */
    protected fun String.toFullQualified(): String = "$basePackage.$this"

    /**
     * 패키지 내 모든 클래스를 매치하는 패턴 (..suffix)
     */
    protected val String.allClasses: String get() = "$this.."

    /**
     * 두 패키지 간 의존성 금지 규칙 적용
     */
    protected fun denyDependency(from: String, to: String, classes: JavaClasses) {
        noClasses()
            .that().resideInAPackage(from.allClasses)
            .should().dependOnClassesThat().resideInAnyPackage(to.allClasses)
            .check(classes)
    }

    /**
     * 여러 패키지 간 의존성을 금지 (cartesian product)
     *
     * 기존 Java 중첩 for 루프를 함수형으로 개선
     */
    protected fun denyAnyDependency(
        fromPackages: List<String>,
        toPackages: List<String>,
        classes: JavaClasses
    ) {
        fromPackages.forEach { from ->
            toPackages.forEach { to ->
                denyDependency(from, to, classes)
            }
        }
    }

    /**
     * 빈 패키지 금지 규칙 적용
     */
    protected fun denyEmptyPackage(packageName: String) {
        classes()
            .that().resideInAPackage(packageName.allClasses)
            .should(containNumberOfElements(greaterThanOrEqualTo(1)))
            .check(ClassFileImporter().importPackages(packageName))
    }

    /**
     * 여러 패키지가 비어있지 않음을 검증
     */
    protected fun denyEmptyPackages(packages: List<String>) {
        packages.forEach(::denyEmptyPackage)
    }
}
