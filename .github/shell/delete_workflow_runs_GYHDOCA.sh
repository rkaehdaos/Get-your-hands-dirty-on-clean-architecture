#!/bin/bash

# GitHub personal access token
TOKEN="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"
# Repository information
OWNER="rkaehdaos"
REPO="Get-your-hands-dirty-on-clean-architecture"

echo "🔍 Workflow 삭제 작업을 시작합니다..."

# 전체 workflow runs 개수 확인
echo "📋 전체 workflow 상태를 확인하는 중..."
initial_response=$(curl -s -w "%{http_code}" -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/$OWNER/$REPO/actions/runs?per_page=1")

initial_http_code="${initial_response: -3}"
initial_json_data="${initial_response%???}"

if [ "$initial_http_code" != "200" ]; then
  echo "❌ API 호출 실패! HTTP 상태 코드: $initial_http_code"
  echo "응답: $initial_json_data"
  exit 1
fi

total_count=$(echo "$initial_json_data" | jq -r '.total_count // 0')
echo "📊 전체 workflow runs: $total_count개"

if [ "$total_count" -eq 0 ]; then
  echo "✅ 삭제할 workflow가 없습니다."
  exit 0
fi

# 삭제 카운터 초기화
success_count=0
fail_count=0
page=1
per_page=100

echo "🗑️ 모든 workflow run 삭제를 시작합니다..."
echo "📄 페이지별로 처리합니다 (페이지당 최대 $per_page개)"

# 페이지네이션 루프
while true; do
  echo ""
  echo "📖 페이지 $page 처리 중..."

  # 현재 페이지의 workflow runs 가져오기
  api_response=$(curl -s -w "%{http_code}" -H "Authorization: token $TOKEN" \
    "https://api.github.com/repos/$OWNER/$REPO/actions/runs?per_page=$per_page&page=$page")

  http_code="${api_response: -3}"
  json_data="${api_response%???}"

  if [ "$http_code" != "200" ]; then
    echo "❌ 페이지 $page API 호출 실패! HTTP 상태 코드: $http_code"
    echo "응답: $json_data"
    break
  fi

  # workflow runs ID 추출
  workflow_runs=$(echo "$json_data" | jq -r '.workflow_runs[].id // empty')

  # 현재 페이지가 비어있으면 종료
  if [ -z "$workflow_runs" ]; then
    echo "📝 페이지 $page: workflow run이 없습니다. 처리를 완료합니다."
    break
  fi

  # 현재 페이지의 workflow run 개수 확인
  current_page_count=$(echo "$workflow_runs" | wc -l | tr -d ' ')
  echo "📄 페이지 $page: $current_page_count개의 workflow run 발견"

  # 각 workflow run 삭제
  page_success=0
  page_fail=0

  for run_id in $workflow_runs; do
    if [ -n "$run_id" ]; then
      echo "🔄 삭제 중: workflow run ID $run_id"

      delete_response=$(curl -s -w "%{http_code}" -X DELETE \
        -H "Authorization: token $TOKEN" \
        "https://api.github.com/repos/$OWNER/$REPO/actions/runs/$run_id")

      delete_http_code="${delete_response: -3}"
      delete_body="${delete_response%???}"

      case "$delete_http_code" in
        "204")
          echo "✅ 성공: $run_id 삭제됨"
          ((success_count++))
          ((page_success++))
          ;;
        "404")
          echo "⚠️ 건너뜀: $run_id (이미 삭제되었거나 존재하지 않음)"
          ((success_count++))
          ((page_success++))
          ;;
        *)
          echo "❌ 실패: $run_id (HTTP $delete_http_code)"
          if [ -n "$delete_body" ]; then
            echo "  응답: $delete_body"
          fi
          ((fail_count++))
          ((page_fail++))
          ;;
      esac

      # API 레이트 리미트 방지를 위한 짧은 대기
      sleep 0.1
    fi
  done

  echo "📄 페이지 $page 완료: 성공 $page_success개, 실패 $page_fail개"

  # 다음 페이지로
  ((page++))

  # 안전장치: 페이지 수가 너무 많으면 중단
  if [ $page -gt 100 ]; then
    echo "⚠️ 페이지 수가 100을 초과했습니다. 안전을 위해 중단합니다."
    break
  fi
done

echo ""
echo "🏁 최종 삭제 작업 완료!"
echo "✅ 전체 성공: $success_count개"
echo "❌ 전체 실패: $fail_count개"
echo "📊 총 처리: $((success_count + fail_count))개"
echo "📄 총 처리 페이지: $((page - 1))개"

# 남은 workflow 확인
final_response=$(curl -s -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/$OWNER/$REPO/actions/runs?per_page=1")
remaining_count=$(echo "$final_response" | jq -r '.total_count // 0')
echo "🔍 남은 workflow runs: $remaining_count개"

if [ $fail_count -gt 0 ]; then
  exit 1
elif [ $remaining_count -eq 0 ]; then
  echo "🎉 모든 workflow run이 성공적으로 삭제되었습니다!"
else
  echo "⚠️ $remaining_count개의 workflow run이 남아있습니다. 스크립트를 다시 실행해보세요."
fi