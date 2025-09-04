package dev.haja.java2kotlin.archunit

import com.tngtech.archunit.core.domain.JavaClasses

/**
 * 헥사고날 아키텍처의 어댑터 계층 검증 클래스 (Kotlin 버전)
 */
class Adapters internal constructor(
    private val parentContext: HexagonalArchitecture,
    basePackage: String
) : ArchitectureElement(basePackage) {

    private val incomingAdapterPackages = mutableListOf<String>()
    private val outgoingAdapterPackages = mutableListOf<String>()

    /**
     * 아웃고잉 어댑터 패키지 추가
     */
    fun outgoing(packageName: String): Adapters = apply {
        outgoingAdapterPackages.add(packageName.toFullQualifiedPackage())
    }

    /**
     * 인커밍 어댑터 패키지 추가
     */
    fun incoming(packageName: String): Adapters = apply {
        incomingAdapterPackages.add(packageName.toFullQualifiedPackage())
    }

    /**
     * 모든 어댑터 패키지 목록 반환
     */
    val allAdapterPackages: List<String>
        get() = buildList {
            addAll(incomingAdapterPackages)
            addAll(outgoingAdapterPackages)
        }

    /**
     * 부모 컨텍스트로 돌아가기
     */
    fun and(): HexagonalArchitecture = parentContext

    /**
     * 어댑터 간 서로 의존하지 않음을 검증
     */
    internal fun dontDependOnEachOther(classes: JavaClasses) {
        val allAdapters = allAdapterPackages
        for (adapter1 in allAdapters) {
            for (adapter2 in allAdapters) {
                if (adapter1 != adapter2) {
                    denyDependency(adapter1, adapter2, classes)
                }
            }
        }
    }

    /**
     * 특정 패키지에 의존하지 않음을 검증
     */
    internal fun doesNotDependOn(packageName: String, classes: JavaClasses) {
        denyDependency(this.basePackage, packageName, classes)
    }

    /**
     * 빈 패키지가 없음을 검증
     */
    internal fun doesNotContainEmptyPackages() {
        denyEmptyPackages(allAdapterPackages)
    }
}