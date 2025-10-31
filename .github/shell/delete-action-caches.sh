#!/bin/bash

# =============================================================================
# GitHub Actions 캐시 삭제 스크립트
# =============================================================================
# 설명: GitHub REST API를 사용하여 저장소의 모든 Actions 캐시를 삭제합니다.
# 사용법: ./delete-action-caches.sh [옵션]
#
# 필요한 환경변수:
#   GITHUB_TOKEN - GitHub Personal Access Token (repo, actions:write 권한 필요)
#
# 옵션:
#   --dry-run          실제 삭제 없이 시뮬레이션만 실행
#   --yes              확인 없이 바로 삭제 실행
#   --key-pattern KEY  특정 패턴의 캐시키만 삭제 (예: node-modules-*)
#   --older-than DAYS  지정된 일수보다 오래된 캐시만 삭제
#   --help             도움말 출력
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
readonly CYAN='\033[0;36m'
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
KEY_PATTERN=""
OLDER_THAN_DAYS=""

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

log_cache() {
    echo -e "${CYAN}[CACHE]${NC} $1" >&2
}

show_help() {
    cat << EOF
GitHub Actions 캐시 삭제 스크립트

사용법: $0 [옵션]

옵션:
  --dry-run                실제 삭제 없이 시뮬레이션만 실행
  --yes                    확인 없이 바로 삭제 실행
  --key-pattern PATTERN    특정 패턴의 캐시키만 삭제 (예: 'node-modules-*')
  --older-than DAYS        지정된 일수보다 오래된 캐시만 삭제
  --help                   이 도움말을 출력

환경 변수:
  GITHUB_TOKEN    GitHub Personal Access Token (필수)
                  repo, actions:write 권한이 필요합니다.

예제:
  # 모든 캐시 시뮬레이션
  GITHUB_TOKEN=ghp_xxx $0 --dry-run

  # node-modules로 시작하는 캐시만 삭제
  GITHUB_TOKEN=ghp_xxx $0 --key-pattern 'node-modules-*'

  # 7일보다 오래된 캐시만 삭제
  GITHUB_TOKEN=ghp_xxx $0 --older-than 7

  # 확인 없이 모든 캐시 삭제
  GITHUB_TOKEN=ghp_xxx $0 --yes

저장소: $REPO_OWNER/$REPO_NAME
EOF
}

# 바이트를 사람이 읽기 쉬운 형태로 변환
human_readable_bytes() {
    local bytes=$1

    if [[ $bytes -lt 1024 ]]; then
        echo "${bytes}B"
    elif [[ $bytes -lt $((1024 * 1024)) ]]; then
        echo "$(( bytes / 1024 ))KB"
    elif [[ $bytes -lt $((1024 * 1024 * 1024)) ]]; then
        echo "$(( bytes / 1024 / 1024 ))MB"
    else
        echo "$(( bytes / 1024 / 1024 / 1024 ))GB"
    fi
}

# 날짜 비교 (지정된 일수보다 오래된지 확인)
is_older_than() {
    local created_at="$1"
    local days="$2"

    if [[ -z "$days" ]]; then
        return 1  # 날짜 필터가 없으면 false 반환
    fi

    local cache_date_ts
    cache_date_ts=$(date -d "$created_at" +%s 2>/dev/null || date -j -f "%Y-%m-%dT%H:%M:%SZ" "$created_at" +%s 2>/dev/null || echo "0")

    local cutoff_date_ts
    cutoff_date_ts=$(date -d "$days days ago" +%s 2>/dev/null || date -j -v-${days}d +%s 2>/dev/null || echo "0")

    [[ $cache_date_ts -lt $cutoff_date_ts ]]
}

# 캐시 키가 패턴과 일치하는지 확인
matches_pattern() {
    local cache_key="$1"
    local pattern="$2"

    if [[ -z "$pattern" ]]; then
        return 0  # 패턴이 없으면 모두 일치
    fi

    # 간단한 와일드카드 패턴 매칭
    case "$cache_key" in
        $pattern) return 0 ;;
        *) return 1 ;;
    esac
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

# 캐시 목록 가져오기
get_caches() {
    local page="${1:-1}"

    log_info "캐시 목록 조회 중... (페이지 $page)"

    local caches_json
    caches_json=$(github_api "GET" "repos/$REPO_OWNER/$REPO_NAME/actions/caches?per_page=$PER_PAGE&page=$page")

    if [[ $? -ne 0 ]]; then
        log_error "캐시 목록 조회 실패"
        exit 1
    fi

    echo "$caches_json"
}

# 캐시 삭제
delete_cache() {
    local cache_id="$1"

    if [[ "$DRY_RUN" == "true" ]]; then
        return 0
    fi

    local response_with_code
    response_with_code=$(curl -s -w "\n%{http_code}" \
        -X "DELETE" \
        -H "Accept: application/vnd.github.v3+json" \
        -H "Authorization: token $GITHUB_TOKEN" \
        -H "User-Agent: github-actions-cleanup-script" \
        "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/actions/caches/$cache_id" 2>&1)

    local http_code=$(echo "$response_with_code" | tail -n1)
    local response_body=$(echo "$response_with_code" | sed '$d')

    if [[ "$http_code" -eq 204 ]]; then
        return 0  # 성공
    elif [[ "$http_code" -eq 403 ]]; then
        log_warn "캐시 $cache_id 삭제 실패: 권한 부족 (HTTP 403)"
        return 1
    elif [[ "$http_code" -eq 404 ]]; then
        log_warn "캐시 $cache_id: 이미 삭제되었거나 존재하지 않음 (HTTP 404)"
        return 0  # 이미 삭제된 것으로 간주
    else
        log_warn "캐시 $cache_id 삭제 실패 (HTTP $http_code): $response_body"
        return 1
    fi
}

# 캐시 정보 표시
display_cache_info() {
    local cache_json="$1"
    local cache_id cache_key cache_size_bytes cache_created_at cache_last_accessed

    cache_id=$(echo "$cache_json" | jq -r '.id')
    cache_key=$(echo "$cache_json" | jq -r '.key')
    cache_size_bytes=$(echo "$cache_json" | jq -r '.size_in_bytes // 0')
    cache_created_at=$(echo "$cache_json" | jq -r '.created_at')
    cache_last_accessed=$(echo "$cache_json" | jq -r '.last_accessed_at // .created_at')

    local cache_size_human
    cache_size_human=$(human_readable_bytes "$cache_size_bytes")

    local created_date last_accessed_date
    created_date=$(date -d "$cache_created_at" "+%Y-%m-%d %H:%M" 2>/dev/null || date -j -f "%Y-%m-%dT%H:%M:%SZ" "$cache_created_at" "+%Y-%m-%d %H:%M" 2>/dev/null || echo "$cache_created_at")
    last_accessed_date=$(date -d "$cache_last_accessed" "+%Y-%m-%d %H:%M" 2>/dev/null || date -j -f "%Y-%m-%dT%H:%M:%SZ" "$cache_last_accessed" "+%Y-%m-%d %H:%M" 2>/dev/null || echo "$cache_last_accessed")

    log_cache "ID: $cache_id | Key: $cache_key"
    log_cache "  크기: $cache_size_human | 생성: $created_date | 접근: $last_accessed_date"
}

# 메인 실행 함수
main() {
    local total_caches=0
    local deleted_caches=0
    local failed_caches=0
    local skipped_caches=0
    local total_size_bytes=0
    local deleted_size_bytes=0

    log_info "=== GitHub Actions 캐시 삭제 ==="
    log_info "저장소: $REPO_OWNER/$REPO_NAME"

    if [[ -n "$KEY_PATTERN" ]]; then
        log_info "키 패턴 필터: $KEY_PATTERN"
    fi

    if [[ -n "$OLDER_THAN_DAYS" ]]; then
        log_info "날짜 필터: ${OLDER_THAN_DAYS}일보다 오래된 캐시만"
    fi

    if [[ "$DRY_RUN" == "true" ]]; then
        log_warn "DRY-RUN 모드: 실제 삭제하지 않고 시뮬레이션만 실행합니다."
    fi

    check_prerequisites

    # 첫 번째 페이지로 총 캐시 수 확인
    local first_page_json
    first_page_json=$(get_caches 1)
    cache_count_value=$(echo "$first_page_json" | jq -r '.total_count // 0' | sed 's/[^0-9]//g')

    if [[ "$cache_count_value" -eq 0 ]]; then
        log_warn "캐시가 없습니다."
        exit 0
    fi

    log_info "총 ${cache_count_value}개의 캐시를 발견했습니다."
    echo

    # 사용자 확인
    if [[ "$SKIP_CONFIRM" == "false" && "$DRY_RUN" == "false" ]]; then
        log_warn "경고: 이 작업은 선택된 캐시를 영구적으로 삭제합니다."
        log_warn "저장소: $REPO_OWNER/$REPO_NAME"
        if [[ -n "$KEY_PATTERN" ]]; then
            log_warn "키 패턴: $KEY_PATTERN"
        fi
        if [[ -n "$OLDER_THAN_DAYS" ]]; then
            log_warn "날짜 필터: ${OLDER_THAN_DAYS}일보다 오래된 캐시"
        fi
        read -p "계속하시겠습니까? [y/N]: " -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "작업이 취소되었습니다."
            exit 0
        fi
    fi

    # 모든 페이지의 캐시 처리
    local page=1
    local processed=0

    while true; do
        local caches_json
        if [[ $page -eq 1 ]]; then
            caches_json="$first_page_json"
        else
            caches_json=$(get_caches "$page")
        fi

        local caches_on_page
        caches_on_page=$(echo "$caches_json" | jq -r '.actions_caches | length')

        if [[ "$caches_on_page" -eq 0 ]]; then
            break
        fi

        # 현재 페이지의 캐시들 처리
        while IFS= read -r cache_line; do
            if [[ -n "$cache_line" && "$cache_line" != "null" ]]; then
                local cache_json="$cache_line"
                local cache_id cache_key cache_size_bytes cache_created_at

                cache_id=$(echo "$cache_json" | jq -r '.id')
                cache_key=$(echo "$cache_json" | jq -r '.key')
                cache_size_bytes=$(echo "$cache_json" | jq -r '.size_in_bytes // 0')
                cache_created_at=$(echo "$cache_json" | jq -r '.created_at')

                total_size_bytes=$((total_size_bytes + cache_size_bytes))

                # 필터 조건 확인
                local should_delete=true

                if ! matches_pattern "$cache_key" "$KEY_PATTERN"; then
                    should_delete=false
                fi

                if [[ "$should_delete" == "true" && -n "$OLDER_THAN_DAYS" ]]; then
                    if ! is_older_than "$cache_created_at" "$OLDER_THAN_DAYS"; then
                        should_delete=false
                    fi
                fi

                if [[ "$should_delete" == "true" ]]; then
                    if [[ "$DRY_RUN" == "false" ]]; then
                        display_cache_info "$cache_json"
                    fi

                    if delete_cache "$cache_id"; then
                        ((deleted_caches++))
                        deleted_size_bytes=$((deleted_size_bytes + cache_size_bytes))
                    else
                        ((failed_caches++))
                    fi
                else
                    ((skipped_caches++))
                fi

                ((processed++))
                show_progress "$processed" "$cache_count_value"
            fi
        done < <(echo "$caches_json" | jq -c '.actions_caches[]')

        ((page++))
    done

    echo # 진행률 바 다음 줄로
    echo

    # 최종 결과 출력
    log_info "=== 작업 완료 ==="
    log_info "전체 캐시: ${cache_count_value}개 ($(human_readable_bytes $total_size_bytes))"

    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "삭제 예정: ${deleted_caches}개 ($(human_readable_bytes $deleted_size_bytes))"
        log_info "건너뛰기: ${skipped_caches}개"
        log_info "시뮬레이션 완료 - 실제로는 삭제되지 않았습니다."
    else
        log_success "삭제 완료: ${deleted_caches}개 ($(human_readable_bytes ${deleted_size_bytes}))"
        if [[ ${failed_caches} -gt 0 ]]; then
            log_warn "삭제 실패: ${failed_caches}개"
        fi
        if [[ ${skipped_caches} -gt 0 ]]; then
            log_info "필터로 제외: ${skipped_caches}개"
        fi

        if [[ ${deleted_size_bytes} -gt 0 ]]; then
            log_success "절약된 스토리지: $(human_readable_bytes $deleted_size_bytes)"
        fi
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
        --key-pattern)
            KEY_PATTERN="$2"
            shift 2
            ;;
        --older-than)
            OLDER_THAN_DAYS="$2"
            shift 2
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