#!/bin/bash
# Convert Aikido scan details JSON to SARIF format with detailed findings

AIKIDO_JSON="${1:-build-artifacts/aikido-scan-details.json}"
OUTPUT_SARIF="${2:-build-artifacts/aikido-scan.sarif}"
ISSUES_JSON="build-artifacts/aikido-issues-export.json"

if [ ! -f "${AIKIDO_JSON}" ]; then
    echo "Error: Aikido scan details not found at ${AIKIDO_JSON}"
    exit 1
fi

# Extract key data from scan summary
SCAN_PASSED=$(jq -r '.gate_passed // false' "${AIKIDO_JSON}")
NEW_ISSUES=$(jq -r '.new_issues_found // 0' "${AIKIDO_JSON}")
DEPENDENCY_ISSUES=$(jq -r '.new_dependency_issues_found // 0' "${AIKIDO_JSON}")
SAST_ISSUES=$(jq -r '.new_sast_issues_found // 0' "${AIKIDO_JSON}")
IAC_ISSUES=$(jq -r '.new_iac_issues_found // 0' "${AIKIDO_JSON}")
SECRET_ISSUES=$(jq -r '.new_leaked_secret_issues_found // 0' "${AIKIDO_JSON}")
DIFF_URL=$(jq -r '.diff_url // ""' "${AIKIDO_JSON}")
MESSAGE=$(jq -r '.outcome.short_human_readable_message // "Aikido scan completed"' "${AIKIDO_JSON}")

# Start SARIF structure
cat > "${OUTPUT_SARIF}" << 'SARIF_START'
{
  "$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
  "version": "2.1.0",
  "runs": [
    {
      "tool": {
        "driver": {
          "name": "Aikido Security",
          "informationUri": "https://aikido.dev",
          "version": "1.0.0",
          "organization": "Aikido"
        }
      },
      "results": [
SARIF_START

# Add issue details if available
if [ -f "${ISSUES_JSON}" ] && [ -s "${ISSUES_JSON}" ]; then
    # Convert Aikido issues to SARIF results format (first 20 for brevity)
    jq -r '.issues[:20] | to_entries | .[] |
      "        {\n" +
      "          \"ruleId\": \"aikido-\(.value.issue_type // "unknown")\",\n" +
      "          \"level\": \"\(if .value.severity == "critical" then "error" elif .value.severity == "high" then "error" elif .value.severity == "medium" then "warning" else "note" end)\",\n" +
      "          \"message\": {\n" +
      "            \"text\": \"\(.value.title // "Security issue detected")\"\n" +
      "          },\n" +
      "          \"properties\": {\n" +
      "            \"severity\": \"\(.value.severity // "unknown")\",\n" +
      "            \"issue_type\": \"\(.value.issue_type // "unknown")\"\n" +
      "          }\n" +
      "        }\(if .key < 19 then "," else "" end)"' "${ISSUES_JSON}" >> "${OUTPUT_SARIF}" 2>/dev/null || echo "        {}" >> "${OUTPUT_SARIF}"
else
    # No detailed issues, add placeholder
    echo "        {}" >> "${OUTPUT_SARIF}"
fi

# Close SARIF structure
cat >> "${OUTPUT_SARIF}" << EOF
      ],
      "properties": {
        "gated": ${SCAN_PASSED},
        "totalIssues": ${NEW_ISSUES},
        "dependencyIssues": ${DEPENDENCY_ISSUES},
        "sastIssues": ${SAST_ISSUES},
        "iacIssues": ${IAC_ISSUES},
        "secretIssues": ${SECRET_ISSUES},
        "diffUrl": "${DIFF_URL}",
        "summary": "${MESSAGE}"
      },
      "columnKind": "utf16CodeUnits"
    }
  ]
}
EOF

echo "✅ Created SARIF at ${OUTPUT_SARIF}"
echo "   Total issues: ${NEW_ISSUES}"
echo "   Dependency: ${DEPENDENCY_ISSUES}, SAST: ${SAST_ISSUES}"
echo "   Gate passed: ${SCAN_PASSED}"
echo "   Details: ${DIFF_URL}"
