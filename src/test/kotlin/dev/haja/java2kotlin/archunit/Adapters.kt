package dev.haja.java2kotlin.archunit

import com.tngtech.archunit.core.domain.JavaClasses

/**
 * 헥사고날 아키텍처의 어댑터 계층 검증 클래스
 *
 * DSL 마커를 통해 스코프를 제한하고, 불변 리스트로 상태를 관리합니다.
 *
 * @property parentContext 부모 HexagonalArchitecture 컨텍스트
 * @property basePackage 어댑터 계층의 기본 패키지
 */
@HexagonalDsl
class Adapters internal constructor(
    private val parentContext: HexagonalArchitecture,
    basePackage: String
) : ArchitectureElement(basePackage) {

    private val _incomingPackages = mutableListOf<String>()
    private val _outgoingPackages = mutableListOf<String>()

    /** 인커밍 어댑터 패키지 목록 (읽기 전용) */
    val incomingPackages: List<String> get() = _incomingPackages

    /** 아웃고잉 어댑터 패키지 목록 (읽기 전용) */
    val outgoingPackages: List<String> get() = _outgoingPackages

    /** 모든 어댑터 패키지 목록 */
    val allPackages: List<String>
        get() = _incomingPackages + _outgoingPackages

    /**
     * 인커밍 어댑터 패키지 추가 (웹, CLI 등 외부 → 내부)
     */
    fun incoming(packageName: String): Adapters = apply {
        _incomingPackages += packageName.toFullQualified()
    }

    /**
     * 아웃고잉 어댑터 패키지 추가 (내부 → 외부: DB, 메시지큐 등)
     */
    fun outgoing(packageName: String): Adapters = apply {
        _outgoingPackages += packageName.toFullQualified()
    }

    /**
     * 부모 컨텍스트로 복귀 (체이닝용)
     */
    fun and(): HexagonalArchitecture = parentContext

    /**
     * 어댑터 간 상호 의존 금지 검증
     *
     * 각 어댑터는 독립적이어야 하며, 다른 어댑터에 의존하면 안 됩니다.
     */
    internal fun verifyNoCrossAdapterDependencies(classes: JavaClasses) {
        val adapters = allPackages
        adapters.forEach { adapter1 ->
            adapters.filter { it != adapter1 }
                .forEach { adapter2 -> denyDependency(adapter1, adapter2, classes) }
        }
    }

    /**
     * 특정 패키지에 대한 의존 금지 검증
     */
    internal fun verifyNoDependencyOn(packageName: String, classes: JavaClasses) {
        denyDependency(basePackage, packageName, classes)
    }

    /**
     * 빈 패키지 없음 검증
     */
    internal fun verifyNoEmptyPackages() {
        denyEmptyPackages(allPackages)
    }
}
