package dev.haja.java2kotlin.archunit

import com.tngtech.archunit.core.domain.JavaClasses

/**
 * DSL 마커 어노테이션
 *
 * DSL 스코프를 제한하여 외부 스코프의 함수가 내부에서 호출되는 것을 방지합니다.
 * 예: adapters 블록 내에서 domain() 호출 방지
 */
@DslMarker
annotation class HexagonalDsl

/**
 * 헥사고날 아키텍처 DSL 메인 클래스
 *
 * 헥사고날 아키텍처의 의존성 규칙을 선언적으로 정의하고 검증합니다.
 *
 * ## 사용 예시
 * ```kotlin
 * hexagonalArchitecture("dev.haja.buckpal.account") {
 *     domain("domain")
 *
 *     adapters("adapter") {
 *         incoming("in.web")
 *         outgoing("out.persistence")
 *     }
 *
 *     application("application") {
 *         services("service")
 *         incomingPorts("port.in")
 *         outgoingPorts("port.out")
 *     }
 *
 *     configuration("configuration")
 * }.check(classes)
 * ```
 */
@HexagonalDsl
class HexagonalArchitecture private constructor(
    basePackage: String
) : ArchitectureElement(basePackage) {

    private var _adapters: Adapters? = null
    private var _applicationLayer: ApplicationLayer? = null
    private var _configurationPackage: String? = null
    private val _domainPackages = mutableSetOf<String>()

    /** 어댑터 계층 (읽기 전용) */
    val adapters: Adapters? get() = _adapters

    /** 애플리케이션 계층 (읽기 전용) */
    val applicationLayer: ApplicationLayer? get() = _applicationLayer

    /** 설정 패키지 (읽기 전용) */
    val configurationPackage: String? get() = _configurationPackage

    /** 도메인 패키지 목록 (읽기 전용) */
    val domainPackages: Set<String> get() = _domainPackages

    companion object {
        /**
         * 헥사고날 아키텍처 DSL 시작점 (정적 팩토리 메서드)
         */
        fun basePackage(basePackage: String): HexagonalArchitecture =
            HexagonalArchitecture(basePackage)
    }

    /**
     * 도메인 계층 설정
     */
    fun withDomainLayer(domainPackage: String): HexagonalArchitecture = apply {
        _domainPackages += domainPackage.toFullQualified()
    }

    /**
     * 어댑터 계층 설정
     */
    fun withAdaptersLayer(adaptersPackage: String): Adapters {
        return Adapters(this, adaptersPackage.toFullQualified()).also {
            _adapters = it
        }
    }

    /**
     * 애플리케이션 계층 설정
     */
    fun withApplicationLayer(applicationPackage: String): ApplicationLayer {
        return ApplicationLayer(applicationPackage.toFullQualified(), this).also {
            _applicationLayer = it
        }
    }

    /**
     * 설정 패키지 설정
     */
    fun withConfiguration(packageName: String): HexagonalArchitecture = apply {
        _configurationPackage = packageName.toFullQualified()
    }

    /**
     * 모든 아키텍처 규칙 검증 실행
     *
     * 검증 순서:
     * 1. 어댑터 계층 규칙
     * 2. 애플리케이션 계층 규칙
     * 3. 도메인 계층 규칙
     */
    fun check(classes: JavaClasses) {
        verifyAdaptersLayer(classes)
        verifyApplicationLayer(classes)
        verifyDomainLayer(classes)
    }

    private fun verifyAdaptersLayer(classes: JavaClasses) {
        val adapters = _adapters ?: return
        val configPackage = _configurationPackage

        adapters.verifyNoEmptyPackages()
        adapters.verifyNoCrossAdapterDependencies(classes)
        if (configPackage != null) {
            adapters.verifyNoDependencyOn(configPackage, classes)
        }
    }

    private fun verifyApplicationLayer(classes: JavaClasses) {
        val appLayer = _applicationLayer ?: return
        val adapters = _adapters
        val configPackage = _configurationPackage

        appLayer.verifyNoEmptyPackages()
        if (adapters != null) {
            appLayer.verifyNoDependencyOn(adapters.basePackage, classes)
        }
        if (configPackage != null) {
            appLayer.verifyNoDependencyOn(configPackage, classes)
        }
        appLayer.verifyPortsDoNotDependOnEachOther(classes)
    }

    private fun verifyDomainLayer(classes: JavaClasses) {
        val domainPackagesList = _domainPackages.toList()

        // 어댑터에 의존 금지
        _adapters?.let { adapters ->
            denyAnyDependency(domainPackagesList, listOf(adapters.basePackage), classes)
        }

        // 애플리케이션 계층에 의존 금지
        _applicationLayer?.let { appLayer ->
            denyAnyDependency(domainPackagesList, listOf(appLayer.basePackage), classes)
        }

        // 설정 패키지에 의존 금지
        _configurationPackage?.let { configPackage ->
            denyAnyDependency(domainPackagesList, listOf(configPackage), classes)
        }
    }
}

/**
 * 헥사고날 아키텍처 DSL 빌더 함수
 *
 * Kotlin DSL 스타일로 헥사고날 아키텍처를 정의할 수 있습니다.
 *
 * ## 사용 예시
 * ```kotlin
 * hexagonalArchitecture("dev.haja.buckpal.account") {
 *     domain("domain")
 *     adapters("adapter") {
 *         incoming("in.web")
 *         outgoing("out.persistence")
 *     }
 *     application("application") {
 *         services("service")
 *         incomingPorts("port.in")
 *         outgoingPorts("port.out")
 *     }
 *     configuration("configuration")
 * }.check(classes)
 * ```
 */
fun hexagonalArchitecture(
    basePackage: String,
    block: HexagonalArchitectureScope.() -> Unit
): HexagonalArchitecture {
    val scope = HexagonalArchitectureScope(basePackage)
    scope.block()
    return scope.build()
}

/**
 * 헥사고날 아키텍처 DSL 스코프
 *
 * DSL 블록 내에서 사용할 수 있는 함수들을 정의합니다.
 */
@HexagonalDsl
class HexagonalArchitectureScope(basePackage: String) {

    private val architecture = HexagonalArchitecture.basePackage(basePackage)

    /**
     * 도메인 계층 설정
     */
    fun domain(packageName: String) {
        architecture.withDomainLayer(packageName)
    }

    /**
     * 어댑터 계층 설정 (DSL 블록 방식)
     */
    fun adapters(packageName: String, block: Adapters.() -> Unit) {
        architecture.withAdaptersLayer(packageName).apply(block)
    }

    /**
     * 애플리케이션 계층 설정 (DSL 블록 방식)
     */
    fun application(packageName: String, block: ApplicationLayer.() -> Unit) {
        architecture.withApplicationLayer(packageName).apply(block)
    }

    /**
     * 설정 패키지 설정
     */
    fun configuration(packageName: String) {
        architecture.withConfiguration(packageName)
    }

    /**
     * 빌드된 HexagonalArchitecture 반환
     */
    internal fun build(): HexagonalArchitecture = architecture
}
