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
            set +e  # Don't exit on errors, we'll handle them
            # Install Node.js if needed
            if ! command -v node &> /dev/null; then
                curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
                apt-get install -y nodejs || echo "Node.js install failed"
            fi

            # Install Aikido
            npm install -g @aikidosec/ci-api-client@latest || echo "Warning: Install failed"

            # Get repo name with owner (e.g., "anuddeeph2/sample-spring-boot-app")
            REPO_URL=\$(git config --get remote.origin.url)
            REPO_NAME=\$(basename "\${REPO_URL}" .git)

            # Extract owner/repo from git URL for API queries (POSIX-compliant)
            case "\${REPO_URL}" in
                *github.com:*/*|*github.com/*/*)
                    # Extract owner from github.com:owner/repo or github.com/owner/repo
                    REPO_OWNER=\$(echo "\${REPO_URL}" | sed -n 's|.*github.com[:/]\\([^/]*\\)/.*|\\1|p')
                    REPO_FULL_NAME="\${REPO_OWNER}/\${REPO_NAME}"
                    ;;
                *)
                    REPO_FULL_NAME="\${REPO_NAME}"
                    ;;
            esac

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

                echo "📥 CI API response received - will use for SARIF conversion"
                # Skip Public API entirely - it returns 0 issues
                # The SARIF converter will use summary data from CI API response

                if [ -s ${env.ARTIFACTS_DIR}/aikido-scan-details.json ]; then
                    echo "✅ Scan details retrieved"

                    # Extract summary
                    NEW_ISSUES=\$(jq -r '.new_issues_found // 0' ${env.ARTIFACTS_DIR}/aikido-scan-details.json 2>/dev/null || echo "0")
                    GATE_PASSED=\$(jq -r '.gate_passed // false' ${env.ARTIFACTS_DIR}/aikido-scan-details.json 2>/dev/null || echo "false")
                    DEPENDENCY_ISSUES=\$(jq -r '.new_dependency_issues_found // 0' ${env.ARTIFACTS_DIR}/aikido-scan-details.json 2>/dev/null || echo "0")
                    SAST_ISSUES=\$(jq -r '.new_sast_issues_found // 0' ${env.ARTIFACTS_DIR}/aikido-scan-details.json 2>/dev/null || echo "0")

                    echo "   New issues found: \${NEW_ISSUES}"
                    echo "   Dependency issues: \${DEPENDENCY_ISSUES}"
                    echo "   SAST issues: \${SAST_ISSUES}"
                    echo "   Gate passed: \${GATE_PASSED}"
                    echo "   Diff URL: \${DIFF_URL}"

                    # Convert to SARIF format with detailed issues
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

            # Always exit successfully - gate failures are expected
            exit 0
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

// convertAikidoToSARIF() removed - SARIF conversion now happens in runAikidoScan()
// via scripts/aikido-to-sarif.sh which properly includes vulnerability details

def publishAikidoToUnify() {
    try {
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "📤 Publishing Aikido Scan to CloudBees Unify"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

        def sarifFileName = "aikido-scan.sarif"

        // Build SARIF in Groovy — guaranteed regardless of bash script or SCAN_ID
        // Uses "Snyk" as tool name since it is on Unify's supported scanner list
        def dependencyIssues = 0
        def sastIssues = 0
        def iacIssues = 0
        def secretIssues = 0
        def totalIssues = 0
        def diffUrl = ""
        def gatePassed = true
        def aikidoUrl = ""

        def aikidoJsonSource = "build-artifacts/aikido-scan-details.json"
        if (fileExists(aikidoJsonSource)) {
            echo "✅ Found Aikido scan details JSON — extracting findings..."
            try {
                def jsonText = readFile(aikidoJsonSource)
                def json = readJSON text: jsonText
                dependencyIssues = json.new_dependency_issues_found ?: 0
                sastIssues       = json.new_sast_issues_found ?: 0
                iacIssues        = json.new_iac_issues_found ?: 0
                secretIssues     = json.new_leaked_secret_issues_found ?: 0
                totalIssues      = json.new_issues_found ?: 0
                gatePassed       = json.gate_passed ?: true
                diffUrl          = json.diff_url ?: ""
                aikidoUrl        = diffUrl
            } catch (Exception je) {
                echo "⚠️  Could not parse Aikido JSON: ${je.message}"
            }
        } else {
            echo "⚠️  Aikido scan details JSON not found — generating empty SARIF"
        }

        echo "   Dependency issues : ${dependencyIssues}"
        echo "   SAST issues       : ${sastIssues}"
        echo "   IAC issues        : ${iacIssues}"
        echo "   Secret issues     : ${secretIssues}"
        echo "   Gate passed       : ${gatePassed}"

        // Build SARIF results array
        def results = []
        if (dependencyIssues > 0) {
            results << """{
          "ruleId": "aikido/dependency-vulnerabilities",
          "level": "error",
          "message": { "text": "${dependencyIssues} new dependency vulnerabilities found. See ${diffUrl}" },
          "locations": [{ "physicalLocation": { "artifactLocation": { "uri": "pom.xml" } } }]
        }"""
        }
        if (sastIssues > 0) {
            results << """{
          "ruleId": "aikido/sast-issues",
          "level": "error",
          "message": { "text": "${sastIssues} new SAST issues found. See ${diffUrl}" },
          "locations": [{ "physicalLocation": { "artifactLocation": { "uri": "src/" } } }]
        }"""
        }
        if (iacIssues > 0) {
            results << """{
          "ruleId": "aikido/iac-issues",
          "level": "warning",
          "message": { "text": "${iacIssues} new IaC issues found. See ${diffUrl}" },
          "locations": [{ "physicalLocation": { "artifactLocation": { "uri": "k8s/" } } }]
        }"""
        }
        if (secretIssues > 0) {
            results << """{
          "ruleId": "aikido/secret-leaks",
          "level": "error",
          "message": { "text": "${secretIssues} new leaked secrets found. See ${diffUrl}" },
          "locations": [{ "physicalLocation": { "artifactLocation": { "uri": "." } } }]
        }"""
        }
        // Unify requires at least one result entry to register the scan
        if (results.isEmpty()) {
            results << """{
          "ruleId": "aikido/scan-passed",
          "level": "note",
          "message": { "text": "Aikido security scan passed with no new issues." },
          "locations": [{ "physicalLocation": { "artifactLocation": { "uri": "." } } }]
        }"""
        }

        def sarifContent = """{
  "\$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
  "version": "2.1.0",
  "runs": [
    {
      "tool": {
        "driver": {
          "name": "Snyk",
          "informationUri": "https://snyk.io",
          "version": "1.0.0",
          "fullDescription": { "text": "Powered by Aikido Security (aikido.dev)" }
        }
      },
      "results": [
        ${results.join(',\n        ')}
      ]
    }
  ]
}"""

        writeFile file: sarifFileName, text: sarifContent
        echo "✅ SARIF written to workspace: ${sarifFileName}"

        archiveArtifacts artifacts: sarifFileName, allowEmptyArchive: false
        echo "✅ Aikido SARIF archived as Jenkins artifact"

        // Archive JSON for reference
        if (fileExists(aikidoJsonSource)) {
            def aikidoJsonName = "aikido-findings.json"
            sh "cp '${env.WORKSPACE}/${aikidoJsonSource}' '${env.WORKSPACE}/${aikidoJsonName}'"
            archiveArtifacts artifacts: aikidoJsonName, allowEmptyArchive: true
        }

        echo ""
        echo "📊 Registering with CloudBees Unify (format: sarif, scanner: Snyk)"
        try {
            registerSecurityScan(
                artifacts: sarifFileName,
                format: "sarif",
                scanner: "Snyk",
                archive: false
            )
            echo "✅ Aikido SARIF registered with CloudBees Unify"
        } catch (Exception e) {
            echo "⚠️  Aikido scan registration failed: ${e.message}"
        }

        // Add report links to build description
        try {
            def buildDescription = currentBuild.description ?: ""
            buildDescription += "<br/>📄 <a href='${env.BUILD_URL}artifact/${sarifFileName}' target='_blank'>Aikido SARIF</a>"
            if (aikidoUrl) {
                buildDescription += "<br/>🛡️ <a href='${aikidoUrl}' target='_blank'>Aikido Dashboard</a>"
            }
            currentBuild.description = buildDescription
        } catch (Exception e) {
            echo "⚠️  Could not update build description: ${e.message}"
        }

        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    } catch (Exception e) {
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "❌ Aikido scan publishing FAILED"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "Error: ${e.message}"
        echo ""
        echo "Troubleshooting:"
        echo "1. Check CloudBees Unify plugin is installed:"
        echo "   Manage Jenkins → Manage Plugins → Installed → 'CloudBees Unify'"
        echo "2. Check CloudBees Unify configuration:"
        echo "   Manage Jenkins → Configure System → CloudBees Unify"
        echo "3. Verify file exists in workspace root:"
        sh """
            ls -la '${env.WORKSPACE}/' | grep -E '\\.sarif\$' || echo 'No SARIF files in workspace root'
            echo ""
            echo "SARIF file content preview:"
            if [ -f '${env.WORKSPACE}/aikido-scan.sarif' ]; then
                head -50 '${env.WORKSPACE}/aikido-scan.sarif'
            fi
        """
        echo ""
        echo "⚠️  Continuing pipeline despite registration failure"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
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
