#!/bin/bash

# GitHub personal access token
TOKEN=""
# Repository information
OWNER="rkaehdaos"
REPO="Get-your-hands-dirty-on-clean-architecture"

# Get all cache keys
CACHE_KEYS=$(curl -s -H "Authorization: token $TOKEN" "https://api.github.com/repos/$OWNER/$REPO/actions/caches" | jq -r '.actions_caches[].key')


# Delete all caches
for KEY in $CACHE_KEYS; do
  curl -X DELETE -H "Authorization: token $TOKEN" "https://api.github.com/repos/$OWNER/$REPO/actions/caches?key=$KEY"
done

echo "All cache runs deleted."