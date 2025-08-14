# Quick Reference - BuckPal 프로젝트

## 자주 사용하는 명령어

### 개발 및 빌드
```bash
# 애플리케이션 실행
./gradlew bootRun

# 전체 빌드 (테스트 포함)
./gradlew build

# 테스트만 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "SendMoneyServiceTest"

# 특정 테스트 메서드 실행
./gradlew test --tests "SendMoneyServiceTest.givenValidAccount_whenSendMoney_thenSucceeds"

# 빌드 정리
./gradlew clean

# 실행 가능한 JAR 생성
./gradlew bootJar
```

### 네이티브 이미지 (GraalVM)
```bash
# 네이티브 이미지 컴파일
./gradlew nativeCompile

# 네이티브 실행 파일 실행
./gradlew nativeRun

# 네이티브 테스트 실행
./gradlew nativeTest
```

### 개발 도구
```bash
# Gradle 태스크 목록 확인
./gradlew tasks

# 의존성 확인
./gradlew dependencies

# 프로젝트 정보
./gradlew properties

# JavaDoc 생성
./gradlew javadoc
```

## 환경 변수

### 필수 환경 변수
- **JAVA_HOME**: `/Users/kai/.sdkman/candidates/java/24.0.1-graal`
  - JDK 24 (GraalVM) 필수

### 선택적 환경 변수
- **GRADLE_OPTS**: JVM 옵션 (gradle.properties에서 설정됨)
- **SPRING_PROFILES_ACTIVE**: 활성 프로파일 (`local`, `prod`)

## 주요 설정 파일 위치

### 애플리케이션 설정
```
src/main/resources/
├── application.yml           # 기본 설정
├── application-local.yml     # 로컬 개발 환경
├── application-prod.yml      # 운영 환경
└── banner.txt               # 시작 배너
```

### 테스트 설정
```
src/test/resources/
├── application.yml           # 테스트 환경 설정
└── dev/haja/buckpal/
    ├── SendMoneySystemTest.sql
    └── account/adapter/out/persistence/
        └── AccountPersistenceAdapterTest.sql
```

### 빌드 설정
```
├── build.gradle.kts         # 메인 빌드 스크립트
├── settings.gradle.kts      # 프로젝트 설정
├── gradle.properties        # Gradle 환경 설정
└── gradle/wrapper/          # Gradle Wrapper
```

## 포트 및 URL

### 개발 환경
- **애플리케이션**: http://localhost:8080
- **H2 콘솔**: http://localhost:8080/h2-console
- **Actuator**: http://localhost:8080/actuator

### API 엔드포인트
```
POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}
└── 계좌 간 송금 실행
```

## 중요한 디렉토리 구조

### 소스 코드
```
src/main/java/dev/haja/buckpal/
├── account/domain/          # 도메인 모델
├── account/application/     # 유스케이스 & 포트
├── account/adapter/         # 웹/영속성 어댑터
└── common/                  # 공통 어노테이션
```

### 테스트 코드
```
src/test/java/dev/haja/buckpal/
├── DependencyRuleTests.java     # 아키텍처 규칙
├── SendMoneySystemTest.java     # 시스템 테스트
├── account/domain/              # 도메인 테스트
├── account/adapter/             # 어댑터 테스트
└── archunit/                    # ArchUnit 커스텀 규칙
```

## 디버깅 및 문제 해결

### 로그 레벨 설정
`application.yml`에서 로깅 레벨 조정:
```yaml
logging:
  level:
    dev.haja.buckpal: DEBUG
    org.springframework.web: DEBUG
```

### 프로파일별 실행
```bash
# 로컬 프로파일로 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# 운영 프로파일로 실행  
./gradlew bootRun --args='--spring.profiles.active=prod'
```

### H2 데이터베이스 접속 정보
- **URL**: `jdbc:h2:mem:testdb`
- **사용자명**: `sa`
- **비밀번호**: (공백)

## 아키텍처 검증

### ArchUnit 테스트 실행
```bash
# 아키텍처 규칙 테스트만 실행
./gradlew test --tests "DependencyRuleTests"

# 헥사고날 아키텍처 검증
./gradlew test --tests "*Architecture*"
```

### 코드 품질 확인
```bash
# 컴파일러 경고 확인 (Kotlin 엄격 모드)
./gradlew compileKotlin

# Java 경고 확인
./gradlew compileJava
```

## Kotlin 마이그레이션 참고

### 현재 Kotlin 파일
- `src/main/kotlin/dev/haja/buckpal/sample/HelloWorld.kt`
- `src/main/kotlin/dev/haja/java2kotlin/Leg.kt`
- `src/main/kotlin/dev/haja/java2kotlin/Legs.kt`
- `src/test/kotlin/dev/haja/java2kotlin/LongestLegOverTestsKotlin.kt`

### 마이그레이션 우선순위
1. 값 객체 (Money, AccountId)
2. 도메인 엔티티 (Account, Activity)  
3. 애플리케이션 서비스

이 참조 가이드는 개발 과정에서 빠르게 필요한 정보를 찾을 수 있도록 구성되었습니다.