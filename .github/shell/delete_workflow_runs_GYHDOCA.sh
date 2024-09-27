#!/bin/bash

# GitHub personal access token
TOKEN="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"
# Repository information
OWNER="thebusinesson"
REPO="Get-your-hands-dirty-on-clean-architecture"

# Get all workflow runs
workflow_runs=$(curl -s -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/$OWNER/$REPO/actions/runs" | jq -r '.workflow_runs[].id')

# Delete each workflow run
for run_id in $workflow_runs; do
  echo "Deleting workflow run ID: $run_id"
  curl -s -X DELETE -H "Authorization: token $TOKEN" \
    "https://api.github.com/repos/$OWNER/$REPO/actions/runs/$run_id"
done

echo "All workflow runs deleted."