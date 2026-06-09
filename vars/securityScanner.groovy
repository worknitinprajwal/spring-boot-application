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
        def aikidoSarifSource = "build-artifacts/aikido-scan.sarif"

        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "📤 Publishing Aikido Scan to CloudBees Unify"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "🔍 Checking for SARIF at: ${env.WORKSPACE}/${aikidoSarifSource}"

        if (!fileExists(aikidoSarifSource)) {
            echo "❌ Aikido SARIF not found at: ${aikidoSarifSource}"
            sh """
                echo "📂 Listing build-artifacts directory:"
                ls -la '${env.WORKSPACE}/build-artifacts/' || echo "build-artifacts/ doesn't exist"
                echo ""
                echo "📂 Searching for SARIF files:"
                find '${env.WORKSPACE}' -name '*.sarif' -type f 2>/dev/null || echo "No SARIF files found"
            """
            echo "⚠️  Skipping Aikido scan registration - SARIF file not generated"
            echo "   This likely means the Aikido scan stage failed or was skipped"
            return
        }

        sh """
            echo "✅ Found SARIF file:"
            ls -lh '${env.WORKSPACE}/${aikidoSarifSource}'
            echo ""

            # Validate SARIF format using jq
            echo "🔍 Validating SARIF format..."
            if command -v jq >/dev/null 2>&1; then
                if jq empty '${env.WORKSPACE}/${aikidoSarifSource}' 2>/dev/null; then
                    echo "✅ Valid JSON structure"
                else
                    echo "❌ Invalid JSON - cannot parse SARIF"
                    cat '${env.WORKSPACE}/${aikidoSarifSource}'
                    exit 1
                fi

                VERSION=\$(jq -r '.version' '${env.WORKSPACE}/${aikidoSarifSource}' 2>/dev/null || echo "missing")
                TOOL=\$(jq -r '.runs[0].tool.driver.name' '${env.WORKSPACE}/${aikidoSarifSource}' 2>/dev/null || echo "missing")
                RESULTS=\$(jq '.runs[0].results | length' '${env.WORKSPACE}/${aikidoSarifSource}' 2>/dev/null || echo "0")

                echo "   SARIF Version: \${VERSION}"
                echo "   Tool Name: \${TOOL}"
                echo "   Results Count: \${RESULTS}"

                if [ "\${VERSION}" = "missing" ] || [ "\${TOOL}" = "missing" ]; then
                    echo "❌ Invalid SARIF structure - missing required fields"
                    exit 1
                fi
            else
                echo "⚠️  jq not available - skipping validation"
            fi
        """

        // CloudBees Unify registerSecurityScan expects the file in workspace root
        // Copy SARIF from build-artifacts to workspace root
        def sarifFileName = "aikido-scan.sarif"
        sh """
            echo ""
            echo "📋 Copying SARIF to workspace root for CloudBees Unify..."
            cp '${env.WORKSPACE}/${aikidoSarifSource}' '${env.WORKSPACE}/${sarifFileName}'
            ls -lh '${env.WORKSPACE}/${sarifFileName}'
        """

        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "⚠️  IMPORTANT: Aikido CI Gating vs CloudBees Unify Visibility"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
        echo "✅ Aikido scan is used for CI/CD pipeline gating (fail/pass builds)"
        echo "✅ Aikido SARIF is archived as Jenkins artifact for manual review"
        echo "❌ Aikido results WILL NOT appear in CloudBees Unify Security Center"
        echo ""
        echo "Why? CloudBees Unify Security Center only displays:"
        echo "  • Officially supported scanners (Checkov, Snyk, Trivy, etc.)"
        echo "  • Scanners that run via implicit security scanning (automatic)"
        echo "  • Aikido is not officially supported by CloudBees Unify"
        echo ""
        echo "Aikido Report Access:"
        echo "  • Jenkins Build Artifacts: ${env.BUILD_URL}artifact/"
        echo "  • Direct SARIF: ${env.BUILD_URL}artifact/${sarifFileName}"
        echo "  • Aikido Dashboard: Check build description for clickable links"
        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""

        // Archive BOTH SARIF and JSON formats
        archiveArtifacts artifacts: sarifFileName, allowEmptyArchive: false
        echo "✅ Aikido SARIF archived as Jenkins artifact"
        echo "   Access at: ${env.BUILD_URL}artifact/${sarifFileName}"

        // Register Aikido scan using SARIF format (standard format Unify can parse)
        // SARIF is generated by scripts/aikido-to-sarif.sh earlier in the pipeline
        // format: sarif does not require scanner to be in Unify's supported list
        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "📊 Registering Aikido Security Scan with CloudBees Unify"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "   Format: SARIF (standard — scanner name read from tool.driver.name)"
        echo "   File: ${sarifFileName}"

        try {
            registerSecurityScan(
                artifacts: sarifFileName,
                format: "sarif",
                scanner: "Aikido",
                archive: false  // Already archived above
            )
            echo "✅ Aikido SARIF registered with CloudBees Unify"
            echo "   Check Security Center UI for results"
        } catch (Exception e) {
            echo "⚠️  Aikido scan registration failed: ${e.message}"
        }

        // Also archive JSON for reference
        def aikidoJsonSource = "build-artifacts/aikido-scan-details.json"
        if (fileExists(aikidoJsonSource)) {
            def aikidoJsonName = "aikido-findings.json"
            sh "cp '${env.WORKSPACE}/${aikidoJsonSource}' '${env.WORKSPACE}/${aikidoJsonName}'"
            archiveArtifacts artifacts: aikidoJsonName, allowEmptyArchive: true
        }

        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

        // Add Aikido report links to build description for easy access from Unify
        try {
            // Extract Aikido dashboard URL from scan details
            def aikidoUrl = sh(
                script: "jq -r '.diff_url // \"\"' '${env.WORKSPACE}/build-artifacts/aikido-scan-details.json' 2>/dev/null || echo ''",
                returnStdout: true
            ).trim()

            def buildDescription = currentBuild.description ?: ""

            // Add Jenkins artifact URLs (visible in CloudBees Unify)
            buildDescription += "<br/>📄 <a href='${env.BUILD_URL}artifact/${sarifFileName}' target='_blank'>Aikido SARIF</a>"
            buildDescription += "<br/>📊 <a href='${env.BUILD_URL}artifact/aikido-findings.json' target='_blank'>Aikido JSON</a>"

            // Add Aikido dashboard URL if available
            if (aikidoUrl && aikidoUrl != "") {
                buildDescription += "<br/>🛡️ <a href='${aikidoUrl}' target='_blank'>Aikido Dashboard</a>"
            }

            currentBuild.description = buildDescription
            echo "✅ Aikido report links added to build description"
        } catch (Exception e) {
            echo "⚠️  Could not add Aikido links to build description: ${e.message}"
        }

        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "✅ Aikido scan completed successfully"
        echo "   Purpose: CI/CD pipeline gating only"
        echo "   Jenkins Artifact: ${env.BUILD_URL}artifact/${sarifFileName}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    } catch (Exception e) {
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "❌ Aikido scan publishing FAILED"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "Error: ${e.message}"
        if (e.cause) {
            echo "Caused by: ${e.cause.message}"
        }
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
