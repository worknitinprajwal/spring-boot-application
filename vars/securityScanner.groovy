#!/usr/bin/env groovy

/**
 * Security Scanner Helper Functions
 * Handles Aikido and ZAP security scans
 */

def call() {
    return this
}

def runAikidoScan() {
    try {
        withCredentials([string(credentialsId: 'AIKIDO_CLIENT_API_KEY', variable: 'AIKIDO_CLIENT_API_KEY')]) {
            sh """
            # Install Node.js if needed
            if ! command -v node &> /dev/null; then
                curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
                apt-get install -y nodejs || echo "Node.js install failed"
            fi

            # Install Aikido
            npm install -g @aikidosec/ci-api-client@latest || echo "Warning: Install failed"

            # Get repo name
            REPO_URL=\$(git config --get remote.origin.url)
            REPO_NAME=\$(basename "\${REPO_URL}" .git)

            # Run scan
            (aikido-api-client scan-release "\${REPO_NAME}" "${env.GIT_COMMIT}" \\
              --apikey "\${AIKIDO_CLIENT_API_KEY}" \\
              --fail-on-sast-scan \\
              --fail-on-secrets-scan \\
              --minimum-severity-level HIGH 2>&1 | tee ${env.ARTIFACTS_DIR}/aikido-scan-output.txt) || true

            # Extract scan details
            SCAN_ID=\$(grep "Aikido Security scan started" ${env.ARTIFACTS_DIR}/aikido-scan-output.txt | sed -n 's/.*id: \\([0-9]*\\).*/\\1/p')
            DIFF_URL=\$(grep "Diff url:" ${env.ARTIFACTS_DIR}/aikido-scan-output.txt | sed -n 's/.*Diff url: \\(.*\\)/\\1/p')

            # Fetch detailed results using the CI API endpoint
            if [ -n "\${SCAN_ID}" ]; then
                echo "📥 Fetching scan details for scan ID: \${SCAN_ID}..."

                # Install jq if needed
                apt-get update -qq > /dev/null 2>&1 && apt-get install -y -qq jq > /dev/null 2>&1 || true

                # Get detailed JSON results using CI integration API
                curl -s -H "X-AIK-API-SECRET: \${AIKIDO_CLIENT_API_KEY}" \\
                  "https://app.aikido.dev/api/integrations/continuous_integration/scan/repository?scan_id=\${SCAN_ID}" \\
                  > ${env.ARTIFACTS_DIR}/aikido-scan-details.json

                if [ -s ${env.ARTIFACTS_DIR}/aikido-scan-details.json ]; then
                    echo "✅ Scan details retrieved"

                    # Extract summary
                    NEW_ISSUES=\$(jq -r '.new_issues_found // 0' ${env.ARTIFACTS_DIR}/aikido-scan-details.json 2>/dev/null || echo "0")
                    GATE_PASSED=\$(jq -r '.gate_passed // false' ${env.ARTIFACTS_DIR}/aikido-scan-details.json 2>/dev/null || echo "false")

                    echo "   New issues found: \${NEW_ISSUES}"
                    echo "   Gate passed: \${GATE_PASSED}"
                    echo "   Diff URL: \${DIFF_URL}"

                    # Convert to SARIF format
                    echo ""
                    echo "📄 Converting to SARIF format..."
                    bash scripts/aikido-to-sarif.sh \\
                      ${env.ARTIFACTS_DIR}/aikido-scan-details.json \\
                      ${env.ARTIFACTS_DIR}/aikido-scan.sarif
                else
                    echo "⚠️  Could not retrieve scan details"
                fi
            fi

            # Generate summary
            echo "=== Aikido Security Scan ===" >> ${env.ARTIFACTS_DIR}/build-info.txt
            echo "Scan Date: \$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> ${env.ARTIFACTS_DIR}/build-info.txt
            echo "Scan ID: \${SCAN_ID:-N/A}" >> ${env.ARTIFACTS_DIR}/build-info.txt
            echo "Diff URL: \${DIFF_URL:-N/A}" >> ${env.ARTIFACTS_DIR}/build-info.txt
        """
        }
    } catch (Exception e) {
        echo "⚠️  Aikido scan failed: ${e.message}"
        echo "This is likely due to missing AIKIDO_CLIENT_API_KEY credential in Jenkins"
        echo "Creating placeholder scan file..."
        sh """
            mkdir -p ${env.ARTIFACTS_DIR}
            echo "Aikido scan skipped - credential not configured" > ${env.ARTIFACTS_DIR}/aikido-scan-output.txt
        """
    }
}

def convertAikidoToSARIF() {
    sh """
        apt-get update -qq && apt-get install -y -qq jq > /dev/null 2>&1 || true

        if [ -f "build-artifacts/aikido-scan-details.json" ]; then
            SCAN_ID=\$(jq -r '.scan_id // "unknown"' build-artifacts/aikido-scan-details.json 2>/dev/null || echo 'unknown')

            cat > build-artifacts/aikido-scan.sarif << 'SARIF_EOF'
{
  "\$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
  "version": "2.1.0",
  "runs": [
    {
      "tool": {
        "driver": {
          "name": "Aikido Security",
          "informationUri": "https://aikido.dev",
          "semanticVersion": "1.0.0",
          "organization": "Aikido"
        }
      },
      "results": [],
      "properties": {
        "sourceFile": "aikido-scan-details.json",
        "scanId": "placeholder"
      },
      "columnKind": "utf16CodeUnits"
    }
  ]
}
SARIF_EOF

            # Replace placeholder with actual scan ID
            sed -i "s/\"placeholder\"/\"\${SCAN_ID}\"/" build-artifacts/aikido-scan.sarif
        else
            cat > build-artifacts/aikido-scan.sarif << 'SARIF_EOF'
{
  "\$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
  "version": "2.1.0",
  "runs": [
    {
      "tool": {
        "driver": {
          "name": "Aikido Security",
          "informationUri": "https://aikido.dev",
          "semanticVersion": "1.0.0"
        }
      },
      "results": [],
      "columnKind": "utf16CodeUnits"
    }
  ]
}
SARIF_EOF
        fi
    """

    archiveArtifacts artifacts: 'build-artifacts/aikido-scan*.{txt,json,sarif}', allowEmptyArchive: true
}

def publishAikidoToUnify() {
    try {
        def aikidoJson = "build-artifacts/aikido-scan-details.json"
        def aikidoSarif = "build-artifacts/aikido-scan.sarif"

        if (fileExists(aikidoJson)) {
            registerSecurityScan(
                artifacts: aikidoJson,
                format: 'json',
                scanner: 'Aikido',
                archive: false
            )
            echo "✅ Aikido scan registered (JSON)"
        } else if (fileExists(aikidoSarif)) {
            registerSecurityScan(
                artifacts: aikidoSarif,
                format: 'sarif',
                scanner: 'Aikido',
                archive: false
            )
            echo "✅ Aikido scan registered (SARIF)"
        }
    } catch (Exception e) {
        echo "⚠️  Failed to register Aikido scan: ${e.message}"
    }
}

def convertZAPToSARIF() {
    sh """
        if [ -f "build-artifacts/zap-reports/baseline-report.json" ]; then
            cat > build-artifacts/zap-reports/zap-scan.sarif << 'SARIF_EOF'
{
  "\\$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
  "version": "2.1.0",
  "runs": [
    {
      "tool": {
        "driver": {
          "name": "OWASP ZAP",
          "informationUri": "https://www.zaproxy.org/",
          "version": "2.14.0"
        }
      },
      "results": [],
      "properties": {
        "sourceFile": "baseline-report.json"
      }
    }
  ]
}
SARIF_EOF
        fi
    """

    archiveArtifacts artifacts: 'build-artifacts/zap-reports/zap-scan.sarif', allowEmptyArchive: true
}

def publishZAPToUnify() {
    try {
        // Try to register the ZAP JSON report directly (native format)
        if (fileExists('build-artifacts/zap-reports/baseline-report.json')) {
            registerSecurityScan(
                artifacts: 'build-artifacts/zap-reports/baseline-report.json',
                format: 'json',
                scanner: 'ZAP',
                archive: true
            )
            echo "✅ ZAP scan registered (JSON format)"
        } else if (fileExists('build-artifacts/zap-reports/zap-scan.sarif')) {
            registerSecurityScan(
                artifacts: 'build-artifacts/zap-reports/zap-scan.sarif',
                format: 'sarif',
                scanner: 'ZAP',
                archive: true
            )
            echo "✅ ZAP scan registered (SARIF format)"
        } else {
            echo "⚠️  No ZAP report files found"
        }
    } catch (Exception e) {
        echo "⚠️  Failed to register ZAP scan: ${e.message}"
    }
}

return this
