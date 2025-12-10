package dev.haja.java2kotlin.archunit

import com.tngtech.archunit.core.domain.JavaClasses

/**
 * 헥사고날 아키텍처의 애플리케이션 계층 검증 클래스
 *
 * 포트(인커밍/아웃고잉)와 서비스 패키지를 관리하고,
 * 계층 간 의존성 규칙을 검증합니다.
 *
 * @property parentContext 부모 HexagonalArchitecture 컨텍스트
 * @property basePackage 애플리케이션 계층의 기본 패키지
 */
@HexagonalDsl
class ApplicationLayer internal constructor(
    basePackage: String,
    private val parentContext: HexagonalArchitecture
) : ArchitectureElement(basePackage) {

    private val _incomingPortsPackages = mutableListOf<String>()
    private val _outgoingPortsPackages = mutableListOf<String>()
    private val _servicePackages = mutableListOf<String>()

    /** 인커밍 포트 패키지 목록 (읽기 전용) */
    val incomingPortsPackages: List<String> get() = _incomingPortsPackages

    /** 아웃고잉 포트 패키지 목록 (읽기 전용) */
    val outgoingPortsPackages: List<String> get() = _outgoingPortsPackages

    /** 서비스 패키지 목록 (읽기 전용) */
    val servicePackages: List<String> get() = _servicePackages

    /** 모든 패키지 목록 */
    val allPackages: List<String>
        get() = _incomingPortsPackages + _outgoingPortsPackages + _servicePackages

    /**
     * 인커밍 포트 패키지 추가 (유스케이스 인터페이스)
     */
    fun incomingPorts(packageName: String): ApplicationLayer = apply {
        _incomingPortsPackages += packageName.toFullQualified()
    }

    /**
     * 아웃고잉 포트 패키지 추가 (영속성 인터페이스)
     */
    fun outgoingPorts(packageName: String): ApplicationLayer = apply {
        _outgoingPortsPackages += packageName.toFullQualified()
    }

    /**
     * 서비스 패키지 추가 (유스케이스 구현)
     */
    fun services(packageName: String): ApplicationLayer = apply {
        _servicePackages += packageName.toFullQualified()
    }

    /**
     * 부모 컨텍스트로 복귀 (체이닝용)
     */
    fun and(): HexagonalArchitecture = parentContext

    /**
     * 특정 패키지에 대한 의존 금지 검증
     */
    internal fun verifyNoDependencyOn(packageName: String, classes: JavaClasses) {
        denyDependency(basePackage, packageName, classes)
    }

    /**
     * 인커밍 포트와 아웃고잉 포트 간 상호 의존 금지 검증
     *
     * 포트들은 서로 독립적이어야 합니다.
     */
    internal fun verifyPortsDoNotDependOnEachOther(classes: JavaClasses) {
        // 인커밍 → 아웃고잉 의존 금지
        denyAnyDependency(_incomingPortsPackages, _outgoingPortsPackages, classes)
        // 아웃고잉 → 인커밍 의존 금지
        denyAnyDependency(_outgoingPortsPackages, _incomingPortsPackages, classes)
    }

    /**
     * 빈 패키지 없음 검증
     */
    internal fun verifyNoEmptyPackages() {
        denyEmptyPackages(allPackages)
    }
}
