#!/bin/bash
# Convert Aikido scan details JSON to SARIF format with findings

AIKIDO_JSON="${1:-build-artifacts/aikido-scan-details.json}"
OUTPUT_SARIF="${2:-build-artifacts/aikido-scan.sarif}"

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
          "name": "Snyk",
          "informationUri": "https://snyk.io",
          "version": "1.0.0",
          "organization": "Snyk",
          "fullDescription": {
            "text": "Powered by Aikido Security (aikido.dev)"
          }
        }
      },
      "results": [
SARIF_START

# Create placeholder result entries based on issue counts
# This ensures CloudBees Unify has something to display
RESULT_COUNT=0

# Add dependency issue placeholder
if [ "$DEPENDENCY_ISSUES" -gt 0 ]; then
    [ $RESULT_COUNT -gt 0 ] && echo "        ," >> "${OUTPUT_SARIF}"
    cat >> "${OUTPUT_SARIF}" << EOF
        {
          "ruleId": "aikido-dependency-vulnerabilities",
          "level": "error",
          "message": {
            "text": "${DEPENDENCY_ISSUES} dependency vulnerabilities found. View details at ${DIFF_URL}"
          },
          "locations": [
            {
              "physicalLocation": {
                "artifactLocation": {
                  "uri": "pom.xml"
                }
              }
            }
          ],
          "properties": {
            "severity": "high",
            "issue_type": "dependency",
            "count": ${DEPENDENCY_ISSUES},
            "tags": ["security", "dependency", "aikido"]
          }
        }
EOF
    RESULT_COUNT=$((RESULT_COUNT + 1))
fi

# Add SAST issue placeholder
if [ "$SAST_ISSUES" -gt 0 ]; then
    [ $RESULT_COUNT -gt 0 ] && echo "        ," >> "${OUTPUT_SARIF}"
    cat >> "${OUTPUT_SARIF}" << EOF
        {
          "ruleId": "aikido-sast-issues",
          "level": "error",
          "message": {
            "text": "${SAST_ISSUES} SAST security issues found. View details at ${DIFF_URL}"
          },
          "locations": [
            {
              "physicalLocation": {
                "artifactLocation": {
                  "uri": "src/"
                }
              }
            }
          ],
          "properties": {
            "severity": "high",
            "issue_type": "sast",
            "count": ${SAST_ISSUES},
            "tags": ["security", "sast", "aikido"]
          }
        }
EOF
    RESULT_COUNT=$((RESULT_COUNT + 1))
fi

# If no issues, add empty placeholder
if [ $RESULT_COUNT -eq 0 ]; then
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
