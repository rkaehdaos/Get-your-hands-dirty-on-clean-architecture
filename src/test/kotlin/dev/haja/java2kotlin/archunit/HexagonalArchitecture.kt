package dev.haja.java2kotlin.archunit

import com.tngtech.archunit.core.domain.JavaClasses

/**
 * 헥사고날 아키텍처 DSL 메인 클래스 (Kotlin 버전)
 * 
 * 사용 예시:
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
class HexagonalArchitecture private constructor(basePackage: String) : ArchitectureElement(basePackage) {

    private var adapters: Adapters? = null
    private var applicationLayer: ApplicationLayer? = null
    private var configurationPackage: String? = null
    private val domainPackages = mutableListOf<String>()

    companion object {
        /**
         * 헥사고날 아키텍처 DSL 시작점
         */
        fun basePackage(basePackage: String): HexagonalArchitecture {
            return HexagonalArchitecture(basePackage)
        }
    }

    /**
     * 어댑터 계층 설정
     */
    fun withAdaptersLayer(adaptersPackage: String): Adapters {
        val adapters = Adapters(this, adaptersPackage.toFullQualifiedPackage())
        this.adapters = adapters
        return adapters
    }

    /**
     * 도메인 계층 설정
     */
    fun withDomainLayer(domainPackage: String): HexagonalArchitecture = apply {
        domainPackages.add(domainPackage.toFullQualifiedPackage())
    }

    /**
     * 애플리케이션 계층 설정
     */
    fun withApplicationLayer(applicationPackage: String): ApplicationLayer {
        val applicationLayer = ApplicationLayer(applicationPackage.toFullQualifiedPackage(), this)
        this.applicationLayer = applicationLayer
        return applicationLayer
    }

    /**
     * 설정 패키지 설정
     */
    fun withConfiguration(packageName: String): HexagonalArchitecture = apply {
        configurationPackage = packageName.toFullQualifiedPackage()
    }

    /**
     * 도메인이 어댑터에 의존하지 않음을 검증
     */
    private fun domainDoesNotDependOnAdapters(classes: JavaClasses) {
        val adaptersPackage = adapters?.basePackage ?: return
        denyAnyDependency(domainPackages, listOf(adaptersPackage), classes)
    }

    /**
     * 모든 아키텍처 규칙 검증 실행
     */
    fun check(classes: JavaClasses) {
        adapters?.let { adapters ->
            adapters.doesNotContainEmptyPackages()
            adapters.dontDependOnEachOther(classes)
            configurationPackage?.let { configPackage ->
                adapters.doesNotDependOn(configPackage, classes)
            }
        }

        applicationLayer?.let { appLayer ->
            appLayer.doesNotContainEmptyPackages()
            adapters?.let { adapters ->
                appLayer.doesNotDependOn(adapters.basePackage, classes)
            }
            configurationPackage?.let { configPackage ->
                appLayer.doesNotDependOn(configPackage, classes)
            }
            appLayer.incomingAndOutgoingPortsDoNotDependOnEachOther(classes)
        }

        domainDoesNotDependOnAdapters(classes)
    }
}

/**
 * DSL 빌더 함수 - 더 Kotlin다운 사용법 제공
 */
fun hexagonalArchitecture(
    basePackage: String,
    block: HexagonalArchitectureBuilder.() -> Unit
): HexagonalArchitecture {
    val builder = HexagonalArchitectureBuilder(basePackage)
    builder.block()
    return builder.build()
}

/**
 * 헥사고날 아키텍처 DSL 빌더
 */
class HexagonalArchitectureBuilder(private val basePackage: String) {
    private var architecture = HexagonalArchitecture.basePackage(basePackage)

    /**
     * 도메인 계층 설정
     */
    fun domain(packageName: String) {
        architecture = architecture.withDomainLayer(packageName)
    }

    /**
     * 어댑터 계층 설정 (DSL 블록 방식)
     */
    fun adapters(packageName: String, block: Adapters.() -> Unit) {
        val adapters = architecture.withAdaptersLayer(packageName)
        adapters.block()
    }

    /**
     * 애플리케이션 계층 설정 (DSL 블록 방식)
     */
    fun application(packageName: String, block: ApplicationLayer.() -> Unit) {
        val applicationLayer = architecture.withApplicationLayer(packageName)
        applicationLayer.block()
    }

    /**
     * 설정 패키지 설정
     */
    fun configuration(packageName: String) {
        architecture = architecture.withConfiguration(packageName)
    }

    /**
     * 빌드된 HexagonalArchitecture 반환
     */
    fun build(): HexagonalArchitecture = architecture
}