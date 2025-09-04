package dev.haja.java2kotlin.archunit

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * ArchUnit을 위한 Kotlin 확장 함수들
 */

/**
 * JavaClasses에 대한 의존성 검증 확장 함수
 */
fun JavaClasses.shouldNotDependOn(from: String, to: String) {
    noClasses()
        .that().resideInAPackage(from)
        .should().dependOnClassesThat().resideInAnyPackage(to)
        .check(this)
}

/**
 * 패키지 문자열을 위한 확장 함수들
 */
fun String.importPackageClasses(): JavaClasses = ClassFileImporter().importPackages(this)

/**
 * 여러 패키지를 한번에 import하는 확장 함수
 */
fun List<String>.importPackageClasses(): JavaClasses {
    return ClassFileImporter().importPackages(*this.toTypedArray())
}

/**
 * 아키텍처 검증을 위한 DSL 확장 함수
 */
fun String.verifyHexagonalArchitecture(
    block: HexagonalArchitectureBuilder.() -> Unit
): HexagonalArchitecture {
    return hexagonalArchitecture(this, block)
}

/**
 * 간편한 아키텍처 검증 함수
 */
fun verifyArchitecture(
    basePackage: String,
    classes: JavaClasses,
    block: HexagonalArchitectureBuilder.() -> Unit
) {
    hexagonalArchitecture(basePackage, block).check(classes)
}

/**
 * 타입 안전한 패키지 이름 래퍼
 */
@JvmInline
value class PackageName(val value: String) {
    override fun toString(): String = value
    
    operator fun plus(other: String): PackageName = PackageName("$value.$other")
    operator fun plus(other: PackageName): PackageName = PackageName("$value.${other.value}")
}

/**
 * 패키지 이름 생성을 위한 확장 함수
 */
fun String.toPackageName(): PackageName = PackageName(this)

/**
 * 아키텍처 계층을 나타내는 sealed class
 */
sealed class ArchitectureLayerType(val packageName: PackageName) {
    class Domain(packageName: PackageName) : ArchitectureLayerType(packageName)
    class Application(packageName: PackageName) : ArchitectureLayerType(packageName)
    class Adapters(packageName: PackageName) : ArchitectureLayerType(packageName)
    class Configuration(packageName: PackageName) : ArchitectureLayerType(packageName)
}