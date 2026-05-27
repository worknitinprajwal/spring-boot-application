#!/bin/bash
set -e

# Test Aikido API endpoints to understand the data structure
# This script will be run in the Jenkins pipeline with proper credentials

AIKIDO_REPO_ID="2052306"

echo "=========================================="
echo "Testing Aikido API Endpoints"
echo "=========================================="
echo ""

# Test 1: Get repository details
echo "1. Testing GET /api/v1/repositories/${AIKIDO_REPO_ID}"
echo "   Response:"
curl -s -H "X-AIK-API-SECRET: ${AIKIDO_CLIENT_API_KEY}" \
  "https://app.aikido.dev/api/v1/repositories/${AIKIDO_REPO_ID}" | jq '.' || echo "   ERROR: Failed to get repository details"
echo ""

# Test 2: List issues for this repository
echo "2. Testing GET /api/v1/issues?repository_id=${AIKIDO_REPO_ID}"
echo "   Response:"
curl -s -H "X-AIK-API-SECRET: ${AIKIDO_CLIENT_API_KEY}" \
  "https://app.aikido.dev/api/v1/issues?repository_id=${AIKIDO_REPO_ID}&limit=10" | jq '.' > /tmp/aikido-issues.json
cat /tmp/aikido-issues.json
echo ""

# Test 3: Count issues
ISSUE_COUNT=$(jq '.items | length' /tmp/aikido-issues.json 2>/dev/null || echo "0")
echo "3. Total issues found: ${ISSUE_COUNT}"
echo ""

# Test 4: SARIF export
echo "4. Testing SARIF export"
curl -s -H "X-AIK-API-SECRET: ${AIKIDO_CLIENT_API_KEY}" \
  "https://app.aikido.dev/api/v1/issues/export/sarif?repository_id=${AIKIDO_REPO_ID}" > /tmp/aikido-sarif.json

SARIF_SIZE=$(wc -c < /tmp/aikido-sarif.json)
echo "   SARIF file size: ${SARIF_SIZE} bytes"

if [ -s /tmp/aikido-sarif.json ]; then
    echo "   First 20 lines of SARIF:"
    head -20 /tmp/aikido-sarif.json | jq '.' || cat /tmp/aikido-sarif.json | head -20

    # Check if SARIF has results
    SARIF_RESULTS=$(jq '.runs[0].results | length' /tmp/aikido-sarif.json 2>/dev/null || echo "0")
    echo "   Results in SARIF: ${SARIF_RESULTS}"
else
    echo "   ERROR: SARIF file is empty"
fi
echo ""

echo "=========================================="
echo "Summary:"
echo "- Repository ID: ${AIKIDO_REPO_ID}"
echo "- Issues found: ${ISSUE_COUNT}"
echo "- SARIF results: ${SARIF_RESULTS:-0}"
echo "=========================================="
