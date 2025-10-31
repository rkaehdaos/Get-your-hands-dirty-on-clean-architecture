#!/bin/bash

# =============================================================================
# GitHub Actions 워크플로우 실행 기록 삭제 스크립트
# =============================================================================
# 설명: GitHub REST API를 사용하여 저장소의 모든 워크플로우 실행 기록을 삭제합니다.
# 사용법: ./delete-workflow-runs.sh [옵션]
#
# 필요한 환경변수:
#   GITHUB_TOKEN - GitHub Personal Access Token (repo, workflow 권한 필요)
#
# 옵션:
#   --dry-run    실제 삭제 없이 시뮬레이션만 실행
#   --yes        확인 없이 바로 삭제 실행
#   --help       도움말 출력
# =============================================================================

set -euo pipefail

# =============================================================================
# 설정 및 상수
# =============================================================================

# 색상 코드
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# API 설정
readonly API_BASE="https://api.github.com"
readonly PER_PAGE=100

# 저장소 정보 자동 감지
REPO_URL=$(git remote get-url origin)
if [[ "$REPO_URL" =~ git@([^:]+):([^/]+)/([^.]+)\.git$ ]]; then
    # SSH 형태: git@hostname:owner/repo.git
    readonly REPO_OWNER="${BASH_REMATCH[2]}"
    readonly REPO_NAME="${BASH_REMATCH[3]}"
elif [[ "$REPO_URL" =~ https://([^/]+)/([^/]+)/([^.]+)\.git$ ]]; then
    # HTTPS 형태: https://hostname/owner/repo.git
    readonly REPO_OWNER="${BASH_REMATCH[2]}"
    readonly REPO_NAME="${BASH_REMATCH[3]}"
else
    # 기본 파싱 (기존 방식)
    REPO_INFO=$(echo "$REPO_URL" | sed 's/.*github\.com[:/]\([^.]*\)\.git/\1/' | sed 's/.*github\.com[:/]\([^.]*\)/\1/')
    readonly REPO_OWNER=$(echo "$REPO_INFO" | cut -d'/' -f1)
    readonly REPO_NAME=$(echo "$REPO_INFO" | cut -d'/' -f2)
fi

# 옵션 변수
DRY_RUN=false
SKIP_CONFIRM=false

# =============================================================================
# 유틸리티 함수
# =============================================================================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" >&2
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" >&2
}

log_warn() {
    echo -e "${YELLOW}[WARNING]${NC} $1" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

show_help() {
    cat << EOF
GitHub Actions 워크플로우 실행 기록 삭제 스크립트

사용법: $0 [옵션]

옵션:
  --dry-run    실제 삭제 없이 시뮬레이션만 실행
  --yes        확인 없이 바로 삭제 실행
  --help       이 도움말을 출력

환경 변수:
  GITHUB_TOKEN    GitHub Personal Access Token (필수)
                  repo, workflow 권한이 필요합니다.

예제:
  # 시뮬레이션 모드로 실행
  GITHUB_TOKEN=ghp_xxx $0 --dry-run

  # 확인 없이 바로 삭제
  GITHUB_TOKEN=ghp_xxx $0 --yes

저장소: $REPO_OWNER/$REPO_NAME
EOF
}

# GitHub API 호출 함수
github_api() {
    local method="$1"
    local endpoint="$2"
    local data="${3:-}"

    local curl_args=(
        -s
        -X "$method"
        -H "Accept: application/vnd.github.v3+json"
        -H "Authorization: token $GITHUB_TOKEN"
        -H "User-Agent: github-actions-cleanup-script"
    )

    if [[ -n "$data" ]]; then
        curl_args+=(-d "$data")
    fi

    curl "${curl_args[@]}" "$API_BASE/$endpoint"
}

# 진행률 바 표시 함수
show_progress() {
    local current=$1
    local total=$2
    local width=50
    local percentage=$((current * 100 / total))
    local filled=$((current * width / total))
    local empty=$((width - filled))

    printf "\r["
    printf "%*s" $filled | tr ' ' '='
    printf "%*s" $empty | tr ' ' '-'
    printf "] %d/%d (%d%%)" $current $total $percentage
}

# =============================================================================
# 메인 함수들
# =============================================================================

# 환경 변수 확인
check_prerequisites() {
    log_info "환경 확인 중..."

    if [[ -z "${GITHUB_TOKEN:-}" ]]; then
        log_error "GITHUB_TOKEN 환경변수가 설정되지 않았습니다."
        log_error "GitHub Personal Access Token을 설정해주세요."
        log_error "예: export GITHUB_TOKEN=ghp_xxxxxxxxxxxxx"
        exit 1
    fi

    if ! command -v curl >/dev/null 2>&1; then
        log_error "curl이 설치되지 않았습니다."
        exit 1
    fi

    if ! command -v jq >/dev/null 2>&1; then
        log_error "jq가 설치되지 않았습니다."
        log_error "macOS: brew install jq"
        log_error "Ubuntu: sudo apt-get install jq"
        exit 1
    fi

    # API 접근 테스트
    if ! github_api "GET" "repos/$REPO_OWNER/$REPO_NAME" >/dev/null; then
        log_error "GitHub API 접근에 실패했습니다."
        log_error "토큰 권한과 저장소 접근 권한을 확인해주세요."
        exit 1
    fi

    log_success "환경 확인 완료"
}

# 워크플로우 목록 가져오기
get_workflows() {
    log_info "워크플로우 목록 조회 중..."

    local workflows_json
    workflows_json=$(github_api "GET" "repos/$REPO_OWNER/$REPO_NAME/actions/workflows")

    if [[ $? -ne 0 ]]; then
        log_error "워크플로우 목록 조회 실패"
        exit 1
    fi

    local count_value
    count_value=$(echo "$workflows_json" | jq -r '.total_count // 0' | sed 's/[^0-9]//g')

    if [[ "$count_value" -eq 0 ]]; then
        log_warn "워크플로우가 없습니다."
        exit 0
    fi

    log_info "총 ${count_value}개의 워크플로우를 발견했습니다."
    echo "$workflows_json" | jq -r '.workflows[] | "\(.id)|\(.name)|\(.path)"'
}

# 워크플로우 실행 기록 가져오기
get_workflow_runs() {
    local workflow_id="$1"
    local page="${2:-1}"

    github_api "GET" "repos/$REPO_OWNER/$REPO_NAME/actions/workflows/$workflow_id/runs?per_page=$PER_PAGE&page=$page"
}

# 워크플로우 실행 기록 삭제
delete_workflow_run() {
    local run_id="$1"

    if [[ "$DRY_RUN" == "true" ]]; then
        echo "DRY-RUN: 실행 기록 $run_id 삭제 시뮬레이션"
        return 0
    fi

    local response_with_code
    response_with_code=$(curl -s -w "\n%{http_code}" \
        -X "DELETE" \
        -H "Accept: application/vnd.github.v3+json" \
        -H "Authorization: token $GITHUB_TOKEN" \
        -H "User-Agent: github-actions-cleanup-script" \
        "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/actions/runs/$run_id" 2>&1)

    local http_code=$(echo "$response_with_code" | tail -n1)
    local response_body=$(echo "$response_with_code" | sed '$d')

    if [[ "$http_code" -eq 204 ]]; then
        return 0  # 성공
    elif [[ "$http_code" -eq 403 ]]; then
        log_warn "실행 기록 $run_id 삭제 실패: 권한 부족 (HTTP 403)"
        return 1
    elif [[ "$http_code" -eq 404 ]]; then
        log_warn "실행 기록 $run_id: 이미 삭제되었거나 존재하지 않음 (HTTP 404)"
        return 0  # 이미 삭제된 것으로 간주
    else
        log_warn "실행 기록 $run_id 삭제 실패 (HTTP $http_code): $response_body"
        return 1
    fi
}

# 단일 워크플로우의 모든 실행 기록 삭제
delete_workflow_runs() {
    local workflow_id="$1"
    local workflow_name="$2"
    local workflow_path="$3"

    log_info "워크플로우 '$workflow_name' ($workflow_path) 처리 중..."

    local total_runs=0
    local deleted_runs=0
    local failed_runs=0

    # 총 실행 수 계산
    local first_page_json
    first_page_json=$(get_workflow_runs "$workflow_id" 1)
    local run_count_value
    run_count_value=$(echo "$first_page_json" | jq -r '.total_count // 0' | sed 's/[^0-9]//g')

    if [[ "$run_count_value" -eq 0 ]]; then
        log_info "  실행 기록이 없습니다."
        return 0
    fi

    log_info "  총 ${run_count_value}개의 실행 기록 발견"

    # 모든 페이지 처리
    local page=1
    local processed=0

    while true; do
        local runs_json
        runs_json=$(get_workflow_runs "$workflow_id" "$page")

        local runs_on_page
        runs_on_page=$(echo "$runs_json" | jq -r '.workflow_runs | length')

        if [[ "$runs_on_page" -eq 0 ]]; then
            break
        fi

        # 현재 페이지의 실행 기록들 처리
        while IFS= read -r run_id; do
            if [[ -n "$run_id" && "$run_id" != "null" ]]; then
                if delete_workflow_run "$run_id"; then
                    ((deleted_runs++))
                else
                    ((failed_runs++))
                fi
                ((processed++))
                show_progress "$processed" "$run_count_value"
            fi
        done < <(echo "$runs_json" | jq -r '.workflow_runs[].id')

        ((page++))
    done

    echo # 진행률 바 다음 줄로

    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "  시뮬레이션: ${run_count_value}개 실행 기록 삭제 예정"
    else
        log_success "  완료: ${deleted_runs}개 삭제, ${failed_runs}개 실패"
    fi
}

# 메인 실행 함수
main() {
    local total_deleted=0
    local total_failed=0
    local workflow_count=0

    log_info "=== GitHub Actions 워크플로우 실행 기록 삭제 ==="
    log_info "저장소: $REPO_OWNER/$REPO_NAME"

    if [[ "$DRY_RUN" == "true" ]]; then
        log_warn "DRY-RUN 모드: 실제 삭제하지 않고 시뮬레이션만 실행합니다."
    fi

    check_prerequisites

    # 사용자 확인
    if [[ "$SKIP_CONFIRM" == "false" && "$DRY_RUN" == "false" ]]; then
        echo
        log_warn "경고: 이 작업은 모든 워크플로우 실행 기록을 영구적으로 삭제합니다."
        log_warn "저장소: $REPO_OWNER/$REPO_NAME"
        read -p "계속하시겠습니까? [y/N]: " -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "작업이 취소되었습니다."
            exit 0
        fi
    fi

    # 워크플로우 목록 가져오기
    local workflows
    workflows=$(get_workflows)

    if [[ -z "$workflows" ]]; then
        log_warn "워크플로우가 없습니다."
        exit 0
    fi

    # 각 워크플로우 처리
    while IFS='|' read -r workflow_id workflow_name workflow_path; do
        ((workflow_count++))
        delete_workflow_runs "$workflow_id" "$workflow_name" "$workflow_path"
        echo
    done <<< "$workflows"

    # 최종 결과 출력
    echo
    log_info "=== 작업 완료 ==="
    log_info "처리된 워크플로우: ${workflow_count}개"

    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "시뮬레이션 완료"
        log_info "--dry-run 모드였으므로 실제로는 삭제되지 않았습니다."
    else
        log_success "모든 워크플로우 실행 기록 삭제 완료"
    fi
}

# =============================================================================
# 스크립트 진입점
# =============================================================================

# 명령행 인수 처리
while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --yes)
            SKIP_CONFIRM=true
            shift
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            log_error "알 수 없는 옵션: $1"
            show_help
            exit 1
            ;;
    esac
done

# 메인 함수 실행
main "$@"