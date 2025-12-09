# AGENTS.md

이 문서는 CLAUDE.md를 바탕으로, ChatGPT/Copilot 등 모든 에이전트가 이 저장소에서 일할 때 따라야 할 지침을 정리합니다. 변경 전후로 CLAUDE.md와 함께 확인하며 지침을 준수하세요.

## AI 응답 원칙
- 항상 한국어로 응답
- 코드 예시는 Kotlin 우선, 불가피할 때만 Java 사용
- GitHub CLI 사용 시 `gh auth status`로 `rkaehdaos` 계정인지 확인 후 진행
- 저장소 아키텍처·테스트 규칙을 준수하며 변경 이유를 간단히 남길 것

## 개발 명령어
### 빌드 및 테스트
```bash
./gradlew build           # 테스트 포함 전체 빌드
./gradlew test            # 모든 테스트 실행
./gradlew bootRun         # Spring Boot 애플리케이션 실행
./gradlew bootJar         # 실행 가능한 JAR 생성
```

### 단일 테스트 실행
```bash
./gradlew test --tests "클래스명"           # 특정 테스트 클래스
./gradlew test --tests "클래스명.메서드명"   # 특정 테스트 메서드
```

### 네이티브 이미지 (GraalVM)
```bash
./gradlew nativeCompile   # 네이티브 이미지 컴파일
./gradlew nativeRun       # 네이티브 실행 파일 실행
./gradlew nativeTest      # 네이티브 바이너리로 테스트 실행
```

## 프로젝트 아키텍처
헥사고날 아키텍처 기반의 은행 계좌 송금 시스템(BuckPal).

```
src/main/java/dev/haja/buckpal/account/
├── domain/           # 비즈니스 엔티티 (Account, Money, Activity, ActivityWindow)
├── application/
│   ├── port/in/     # 인커밍 포트 (유스케이스 인터페이스)
│   ├── port/out/    # 아웃고잉 포트 (리포지토리 인터페이스)
│   └── service/     # 애플리케이션 서비스 (유스케이스 구현)
└── adapter/
    ├── in/web/      # REST 컨트롤러 (인커밍 어댑터)
    └── out/persistence/  # JPA 리포지토리 (아웃고잉 어댑터)
```

### 아키텍처 원칙
- 도메인 계층은 외부 의존성 없이 순수하게 유지
- 의존성 방향은 adapter → application → domain (안쪽으로만 흐름)
- ArchUnit 테스트(`DependencyRuleTests.java`)로 규칙을 강제하므로 위반 금지

## 테스트 표준
- `@DisplayName`에 한국어 설명 사용
- BDD 스타일: `given...when...then...`
- BDDMockito: `given().willReturn()` / `then().should()`
- 필요 시 Kotest/MockK를 활용해 가독성 유지

## Java → Kotlin 마이그레이션 우선순위
1. 값 객체(Money, AccountId)부터 Kotlin data class로 이전
2. 도메인 엔티티(Account, Activity) 순차 변환
3. 애플리케이션 서비스는 함수형 스타일을 선호
