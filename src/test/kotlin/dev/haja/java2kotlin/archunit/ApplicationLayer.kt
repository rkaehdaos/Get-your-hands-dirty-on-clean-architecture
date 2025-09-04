package dev.haja.java2kotlin.archunit

import com.tngtech.archunit.core.domain.JavaClasses

/**
 * 헥사고날 아키텍처의 애플리케이션 계층 검증 클래스 (Kotlin 버전)
 */
class ApplicationLayer internal constructor(
    basePackage: String,
    private val parentContext: HexagonalArchitecture
) : ArchitectureElement(basePackage) {

    private val incomingPortsPackages = mutableListOf<String>()
    private val outgoingPortsPackages = mutableListOf<String>()
    private val servicePackages = mutableListOf<String>()

    /**
     * 인커밍 포트 패키지 추가
     */
    fun incomingPorts(packageName: String): ApplicationLayer = apply {
        incomingPortsPackages.add(packageName.toFullQualifiedPackage())
    }

    /**
     * 아웃고잉 포트 패키지 추가
     */
    fun outgoingPorts(packageName: String): ApplicationLayer = apply {
        outgoingPortsPackages.add(packageName.toFullQualifiedPackage())
    }

    /**
     * 서비스 패키지 추가
     */
    fun services(packageName: String): ApplicationLayer = apply {
        servicePackages.add(packageName.toFullQualifiedPackage())
    }

    /**
     * 부모 컨텍스트로 돌아가기
     */
    fun and(): HexagonalArchitecture = parentContext

    /**
     * 특정 패키지에 의존하지 않음을 검증
     */
    internal fun doesNotDependOn(packageName: String, classes: JavaClasses) {
        denyDependency(this.basePackage, packageName, classes)
    }

    /**
     * 인커밍 포트와 아웃고잉 포트가 서로 의존하지 않음을 검증
     */
    internal fun incomingAndOutgoingPortsDoNotDependOnEachOther(classes: JavaClasses) {
        denyAnyDependency(incomingPortsPackages, outgoingPortsPackages, classes)
        denyAnyDependency(outgoingPortsPackages, incomingPortsPackages, classes)
    }

    /**
     * 모든 패키지 목록 반환
     */
    private val allPackages: List<String>
        get() = buildList {
            addAll(incomingPortsPackages)
            addAll(outgoingPortsPackages)  
            addAll(servicePackages)
        }

    /**
     * 빈 패키지가 없음을 검증
     */
    internal fun doesNotContainEmptyPackages() {
        denyEmptyPackages(allPackages)
    }
}