package dev.haja.java2kotlin.archunit

import com.tngtech.archunit.core.domain.JavaClasses

/**
 * 아키텍처 검증 결과를 나타내는 sealed class
 */
sealed class ValidationResult {
    /**
     * 검증 성공
     */
    data object Success : ValidationResult()

    /**
     * 검증 실패
     * @param violations 위반 사항 목록
     */
    data class Failure(val violations: List<String>) : ValidationResult() {
        constructor(vararg violations: String) : this(violations.toList())

        /**
         * 단일 위반 사항을 위한 편의 생성자
         */
        constructor(violation: String) : this(listOf(violation))

        /**
         * 다른 실패 결과와 결합
         */
        operator fun plus(other: Failure): Failure = Failure(violations + other.violations)
    }

    /**
     * 검증 성공 여부
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * 검증 실패 여부
     */
    val isFailure: Boolean get() = this is Failure

    /**
     * 실패 시 위반 사항 반환, 성공 시 빈 리스트
     */
    val allViolations: List<String> get() = (this as? Failure)?.violations.orEmpty()

    /**
     * 다른 결과와 결합 (둘 다 성공이어야 성공)
     */
    operator fun plus(other: ValidationResult): ValidationResult = when {
        this is Success && other is Success -> Success
        this is Failure && other is Failure -> this + other
        this is Failure -> this
        else -> other
    }

    /**
     * 결과를 조합하는 중위 함수
     */
    infix fun and(other: ValidationResult): ValidationResult = this + other

    /**
     * 성공 시 액션 실행
     */
    inline fun onSuccess(action: () -> Unit): ValidationResult {
        if (this is Success) action()
        return this
    }

    /**
     * 실패 시 액션 실행
     */
    inline fun onFailure(action: (List<String>) -> Unit): ValidationResult {
        if (this is Failure) action(violations)
        return this
    }

    companion object {
        /**
         * 성공 결과 생성
         */
        fun success(): ValidationResult = Success

        /**
         * 실패 결과 생성
         */
        fun failure(vararg violations: String): ValidationResult = Failure(*violations)

        /**
         * 실패 결과 생성
         */
        fun failure(violations: List<String>): ValidationResult = Failure(violations)

        /**
         * 여러 결과를 조합 (모두 성공이어야 성공)
         */
        fun combine(vararg results: ValidationResult): ValidationResult =
            results.fold(success()) { acc, result -> acc + result }

        /**
         * 리스트의 모든 결과를 조합
         */
        fun combineAll(results: List<ValidationResult>): ValidationResult =
            results.fold(success()) { acc, result -> acc + result }
    }
}

/**
 * 아키텍처 검증 규칙을 나타내는 인터페이스
 */
sealed interface ValidationRule {
    /**
     * 규칙을 검증하고 결과를 반환
     */
    fun validate(classes: JavaClasses): ValidationResult

    /**
     * 규칙들을 조합하는 연산자 (둘 다 통과해야 성공)
     */
    operator fun plus(other: ValidationRule): ValidationRule = CompositeRule(listOf(this, other))

    /**
     * 규칙들을 조합하는 중위 함수
     */
    infix fun and(other: ValidationRule): ValidationRule = this + other

    /**
     * 규칙 중 하나라도 통과하면 성공 (OR 조건)
     */
    infix fun or(other: ValidationRule): ValidationRule = AlternativeRule(listOf(this, other))
}

/**
 * 여러 규칙을 모두 만족해야 하는 복합 규칙 (AND 조건)
 */
internal data class CompositeRule(val rules: List<ValidationRule>) : ValidationRule {
    override fun validate(classes: JavaClasses): ValidationResult {
        return ValidationResult.combineAll(rules.map { it.validate(classes) })
    }

    override operator fun plus(other: ValidationRule): ValidationRule = when (other) {
        is CompositeRule -> CompositeRule(rules + other.rules)
        else -> CompositeRule(rules + other)
    }
}

/**
 * 규칙 중 하나라도 만족하면 되는 대안 규칙 (OR 조건)
 */
internal data class AlternativeRule(val rules: List<ValidationRule>) : ValidationRule {
    override fun validate(classes: JavaClasses): ValidationResult {
        val results = rules.map { it.validate(classes) }
        return if (results.any { it.isSuccess }) {
            ValidationResult.success()
        } else {
            ValidationResult.combineAll(results)
        }
    }

    override infix fun or(other: ValidationRule): ValidationRule = when (other) {
        is AlternativeRule -> AlternativeRule(rules + other.rules)
        else -> AlternativeRule(rules + other)
    }
}

/**
 * 의존성 금지 규칙
 */
data class DenyDependencyRule(
    val fromPackage: String,
    val toPackage: String,
    val description: String? = null
) : ValidationRule {

    override fun validate(classes: JavaClasses): ValidationResult {
        return try {
            // 기존 ArchitectureElement의 denyDependency 로직 재사용
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(fromPackage.matchAllClasses())
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(toPackage.matchAllClasses())
                .check(classes)
            ValidationResult.success()
        } catch (e: Exception) {
            val message = description ?: "$fromPackage should not depend on $toPackage"
            ValidationResult.failure(message + ": ${e.message}")
        }
    }
}

/**
 * 빈 패키지 금지 규칙
 */
data class DenyEmptyPackageRule(
    val packageName: String,
    val description: String? = null
) : ValidationRule {

    override fun validate(classes: JavaClasses): ValidationResult {
        return try {
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                .that()
                .resideInAPackage(packageName.matchAllClasses())
                .should(com.tngtech.archunit.lang.conditions.ArchConditions.containNumberOfElements(
                    com.tngtech.archunit.base.DescribedPredicate.greaterThanOrEqualTo(1)
                ))
                .check(com.tngtech.archunit.core.importer.ClassFileImporter().importPackages(packageName))
            ValidationResult.success()
        } catch (e: Exception) {
            val message = description ?: "Package $packageName should not be empty"
            ValidationResult.failure(message + ": ${e.message}")
        }
    }
}

/**
 * 확장 함수: 패키지명에 대한 매치 패턴 생성
 */
private fun String.matchAllClasses(): String = "$this.."