package dev.haja.java2kotlin.archunit

import com.tngtech.archunit.core.domain.JavaClasses

/**
 * 컨텍스트 수신자를 활용한 고도화된 아키텍처 DSL
 */

// === DSL 마커 어노테이션 ===

@DslMarker
annotation class ArchitectureDsl

@DslMarker
annotation class LayerDsl

@DslMarker
annotation class RuleDsl

// === 컨텍스트 수신자를 활용한 메인 DSL ===

/**
 * 아키텍처 검증을 위한 최상위 DSL 함수
 */
@ArchitectureDsl
inline fun architecture(
    basePackage: String,
    crossinline block: context(ArchitectureContext) () -> Unit
): ArchitectureSpec {
    val context = ArchitectureContext(basePackage)
    context.block()
    return context.build()
}

/**
 * JavaClasses와 함께 아키텍처를 검증하는 DSL
 */
@ArchitectureDsl
inline fun verifyArchitecture(
    basePackage: String,
    classes: JavaClasses,
    crossinline block: context(ArchitectureContext) () -> Unit
): ValidationResult {
    val spec = architecture(basePackage, block)
    return spec.validate(classes)
}

// === 아키텍처 컨텍스트 ===

/**
 * 아키텍처 DSL의 컨텍스트 클래스
 */
@ArchitectureDsl
class ArchitectureContext(private val basePackage: String) {
    private val layers = mutableMapOf<String, LayerSpec>()
    private val customRules = mutableListOf<ValidationRule>()

    /**
     * 계층을 정의하는 DSL
     */
    @LayerDsl
    inline fun layer(
        name: String,
        crossinline config: context(LayerContext) LayerSpec.() -> Unit
    ): LayerSpec {
        val layerSpec = LayerSpec(name, "$basePackage.$name")
        val layerContext = LayerContext(this, layerSpec)
        layerSpec.config(layerContext)
        layers[name] = layerSpec
        return layerSpec
    }

    /**
     * 도메인 계층 정의
     */
    inline fun domain(
        packageName: String,
        crossinline config: context(LayerContext) LayerSpec.() -> Unit = {}
    ): LayerSpec = layer("domain") {
        config()
    }

    /**
     * 애플리케이션 계층 정의
     */
    inline fun application(
        packageName: String,
        crossinline config: context(LayerContext) LayerSpec.() -> Unit = {}
    ): LayerSpec = layer("application") {
        config()
    }

    /**
     * 어댑터 계층 정의
     */
    inline fun adapters(
        packageName: String,
        crossinline config: context(LayerContext) LayerSpec.() -> Unit = {}
    ): LayerSpec = layer("adapters") {
        config()
    }

    /**
     * 전역 규칙 추가
     */
    @RuleDsl
    inline fun rule(crossinline ruleBuilder: context(RuleContext) () -> ValidationRule) {
        val ruleContext = RuleContext(this)
        customRules.add(ruleContext.ruleBuilder())
    }

    /**
     * 계층 간 관계 정의
     */
    @RuleDsl
    inline fun layerRules(crossinline block: context(LayerRelationContext) () -> Unit) {
        val relationContext = LayerRelationContext(layers)
        relationContext.block()
    }

    /**
     * 빌드된 아키텍처 스펙 반환
     */
    internal fun build(): ArchitectureSpec = ArchitectureSpec(
        basePackage = basePackage,
        layers = layers.toMap(),
        customRules = customRules.toList()
    )
}

// === 계층 컨텍스트 ===

/**
 * 계층 정의를 위한 컨텍스트
 */
@LayerDsl
class LayerContext(
    private val architectureContext: ArchitectureContext,
    private val layerSpec: LayerSpec
) {
    /**
     * 하위 패키지 정의
     */
    fun subPackage(name: String): LayerSpec = layerSpec.apply {
        addSubPackage(name)
    }

    /**
     * 이 계층이 의존할 수 있는 계층 정의
     */
    infix fun canDependOn(layerNames: List<String>): LayerSpec = layerSpec.apply {
        allowedDependencies.addAll(layerNames)
    }

    infix fun canDependOn(layerName: String): LayerSpec = canDependOn(listOf(layerName))

    /**
     * 이 계층이 의존하면 안되는 계층 정의
     */
    infix fun cannotDependOn(layerNames: List<String>): LayerSpec = layerSpec.apply {
        forbiddenDependencies.addAll(layerNames)
    }

    infix fun cannotDependOn(layerName: String): LayerSpec = cannotDependOn(listOf(layerName))
}

// === 규칙 컨텍스트 ===

/**
 * 사용자 정의 규칙을 위한 컨텍스트
 */
@RuleDsl
class RuleContext(private val architectureContext: ArchitectureContext) {
    /**
     * 의존성 금지 규칙 생성
     */
    fun deny(fromPackage: String, toPackage: String): ValidationRule =
        DenyDependencyRule(fromPackage, toPackage)

    /**
     * 빈 패키지 금지 규칙 생성
     */
    fun requireNonEmpty(packageName: String): ValidationRule =
        DenyEmptyPackageRule(packageName)

    /**
     * 여러 규칙을 결합
     */
    fun allOf(vararg rules: ValidationRule): ValidationRule =
        ValidationResult.combineAll(rules.toList().map { rule ->
            ValidationRule { classes -> rule.validate(classes) }
        }).let { result ->
            ValidationRule { _ -> result }
        }
}

// === 계층 간 관계 컨텍스트 ===

/**
 * 계층 간 관계를 정의하기 위한 컨텍스트
 */
@RuleDsl
class LayerRelationContext(private val layers: Map<String, LayerSpec>) {
    /**
     * 특정 계층 참조
     */
    val domain: LayerSpec? get() = layers["domain"]
    val application: LayerSpec? get() = layers["application"]
    val adapters: LayerSpec? get() = layers["adapters"]

    /**
     * 계층 독립성 보장
     */
    infix fun LayerSpec.independentFrom(other: LayerSpec): ValidationRule =
        MutualIndependenceRule(listOf(this.packageName, other.packageName))

    /**
     * 계층 의존성 허용
     */
    infix fun LayerSpec.mayDependOn(other: LayerSpec): LayerSpec = this.apply {
        allowedDependencies.add(other.name)
    }

    /**
     * 계층 의존성 금지
     */
    infix fun LayerSpec.mustNotDependOn(other: LayerSpec): LayerSpec = this.apply {
        forbiddenDependencies.add(other.name)
    }
}

// === 계층 스펙 ===

/**
 * 계층의 사양을 나타내는 클래스
 */
data class LayerSpec(
    val name: String,
    val packageName: String,
    val subPackages: MutableList<String> = mutableListOf(),
    val allowedDependencies: MutableList<String> = mutableListOf(),
    val forbiddenDependencies: MutableList<String> = mutableListOf()
) {
    fun addSubPackage(name: String) {
        subPackages.add(name)
    }

    fun getAllPackages(): List<String> = listOf(packageName) + subPackages.map { "$packageName.$it" }
}

// === 아키텍처 스펙 ===

/**
 * 전체 아키텍처의 사양을 나타내는 클래스
 */
data class ArchitectureSpec(
    val basePackage: String,
    val layers: Map<String, LayerSpec>,
    val customRules: List<ValidationRule>
) {
    /**
     * 아키텍처 검증 실행
     */
    fun validate(classes: JavaClasses): ValidationResult {
        val layerRules = generateLayerRules()
        val allRules = layerRules + customRules
        return ValidationResult.combineAll(allRules.map { it.validate(classes) })
    }

    private fun generateLayerRules(): List<ValidationRule> {
        return layers.values.flatMap { layer ->
            buildList {
                // 금지된 의존성들에 대한 규칙
                layer.forbiddenDependencies.forEach { forbiddenLayer ->
                    layers[forbiddenLayer]?.let { targetLayer ->
                        add(DenyDependencyRule(layer.packageName, targetLayer.packageName))
                    }
                }

                // 빈 패키지 금지 규칙
                layer.getAllPackages().forEach { packageName ->
                    add(DenyEmptyPackageRule(packageName))
                }
            }
        }
    }
}

// === 편의 확장 함수들 ===

/**
 * 문자열에서 계층 스펙으로의 변환
 */
fun String.asLayer(): LayerSpec = LayerSpec(this, this)

/**
 * 계층들의 리스트를 위한 확장
 */
fun List<LayerSpec>.shouldBeIndependent(): ValidationRule =
    MutualIndependenceRule(this.map { it.packageName })

/**
 * inline ValidationRule 생성을 위한 SAM 변환
 */
fun ValidationRule(validate: (JavaClasses) -> ValidationResult): ValidationRule =
    object : ValidationRule {
        override fun validate(classes: JavaClasses): ValidationResult = validate(classes)
    }

// === 사용 예시 주석 ===
/*
사용 예시:

```kotlin
verifyArchitecture("com.example.app", classes) {
    domain("domain") {
        cannotDependOn("adapters")
        cannotDependOn("application")
    }

    application("application") {
        canDependOn("domain")
        cannotDependOn("adapters")

        subPackage("port.in")
        subPackage("port.out")
        subPackage("service")
    }

    adapters("adapters") {
        canDependOn(listOf("domain", "application"))

        subPackage("in.web")
        subPackage("out.persistence")
    }

    layerRules {
        adapters?.let { adapters ->
            adapters.subPackages.forEach { subPkg ->
                "$${adapters.packageName}.$subPkg".asLayer() independentFrom
                    adapters.subPackages.filter { it != subPkg }
                        .map { "$${adapters.packageName}.$it".asLayer() }
                        .first()
            }
        }
    }

    rule {
        deny("..domain..", "..infrastructure..")
    }
}
```
*/