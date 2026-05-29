# GEMINI.md

Gemini CLI 에이전트용 프로젝트 컨텍스트입니다.
이 저장소의 표준 작업 지침은 루트 `CLAUDE.md`와 `.claude/CLAUDE.md`에 일원화되어 있습니다.
중복 유지보수를 피하기 위해 GEMINI.md는 import와 핵심 요약만 둡니다.

@CLAUDE.md
@.claude/CLAUDE.md

## 핵심 요약 (import 미지원 환경 fallback)

- **응답 언어**: 한국어 기본
- **코드 예시**: Kotlin 우선 (Java는 레거시 / 마이그레이션 대상)
- **아키텍처**: 헥사고날 — 의존성은 항상 안쪽으로(`Adapter → Application → Domain`), `domain`은 외부 의존성 없음, 외부 통신은 In/Out Port 경유. ArchUnit(`DependencyRuleTests`)으로 규칙 강제
- **테스트**: JUnit 5 · Kotest · MockK, BDD `given-when-then`, `@DisplayName`은 한국어로 작성
- **빌드/실행**: `./gradlew build` · `test` · `bootRun` (네이티브: `nativeCompile`)

> 디렉토리 구조, 전체 명령어 목록, Java→Kotlin 마이그레이션 우선순위, 테스트 세부 표준 등 상세 내용은 위 `CLAUDE.md` 문서를 따릅니다.
