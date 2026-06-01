#!/usr/bin/env groovy

/**
 * Test Reporter Helper Functions
 * Handles all test report archiving and HTML generation
 */

def call() {
    return this
}

def archiveAPITests() {
    archiveArtifacts artifacts: 'build-artifacts/api-tests/**/*', allowEmptyArchive: true

    // Create HTML version
    sh """
        if [ -f "\${ARTIFACTS_DIR}/api-tests/test-results.txt" ]; then
            cat > \${ARTIFACTS_DIR}/api-tests/index.html << 'HTMLEOF'
<!DOCTYPE html>
<html>
<head>
    <title>API Functional Test Results</title>
    <style>
        body { font-family: monospace; background: #1e1e1e; color: #d4d4d4; padding: 20px; }
        pre { background: #252526; padding: 20px; border-radius: 5px; overflow-x: auto; }
        .pass { color: #4ec9b0; }
        .fail { color: #f48771; }
        h1 { color: #569cd6; }
    </style>
</head>
<body>
    <h1>🧪 REST API Functional Test Results</h1>
    <p>Build: ${BUILD_NUMBER} | Branch: ${BRANCH_NAME}</p>
    <pre>
HTMLEOF
            cat \${ARTIFACTS_DIR}/api-tests/test-results.txt | sed 's/✅/<span class="pass">✅<\\/span>/g' | sed 's/❌/<span class="fail">❌<\\/span>/g' >> \${ARTIFACTS_DIR}/api-tests/index.html
            echo '</pre></body></html>' >> \${ARTIFACTS_DIR}/api-tests/index.html
        fi
    """

    // Skip publishHTML due to plugin compatibility issues
    // Reports are available in build artifacts
    echo "📄 API test reports archived to build-artifacts/api-tests/"
}

def archiveJMeterReports() {
    archiveArtifacts artifacts: 'build-artifacts/jmeter-reports/**/*', allowEmptyArchive: true
    archiveArtifacts artifacts: 'tests/jmeter/*.jmx', allowEmptyArchive: true

    // Skip publishHTML due to plugin compatibility issues
    // Reports are available in build artifacts
    echo "📄 JMeter reports archived to build-artifacts/jmeter-reports/html/"
}

def archiveZAPReports() {
    archiveArtifacts artifacts: 'build-artifacts/zap-reports/**/*', allowEmptyArchive: true

    // Skip publishHTML due to plugin compatibility issues
    // Reports are available in build artifacts
    echo "📄 ZAP reports archived to build-artifacts/zap-reports/"
}

def archiveUITests() {
    archiveArtifacts artifacts: 'build-artifacts/uipath-reports/**/*', allowEmptyArchive: true
    junit allowEmptyResults: true, testResults: 'build-artifacts/uipath-reports/uipath-junit.xml'

    // Skip publishHTML due to plugin compatibility issues
    // Reports are available in build artifacts
    echo "📄 UiPath reports archived to build-artifacts/uipath-reports/"
}

def createTestDashboard() {
    sh """
        cat > ${env.ARTIFACTS_DIR}/test-reports-index.html << 'INDEXEOF'
<!DOCTYPE html>
<html>
<head>
    <title>Test Reports - Build ${env.BUILD_NUMBER}</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 40px 20px;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
            background: white;
            border-radius: 12px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            overflow: hidden;
        }
        .header {
            background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
            color: white;
            padding: 40px;
            text-align: center;
        }
        .header h1 { font-size: 2.5em; margin-bottom: 10px; }
        .content { padding: 40px; }
        .section {
            margin-bottom: 40px;
            padding: 30px;
            background: #f8f9fa;
            border-radius: 8px;
            border-left: 5px solid #667eea;
        }
        .reports-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
        }
        .report-card {
            background: white;
            padding: 25px;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            transition: transform 0.2s;
        }
        .report-card:hover { transform: translateY(-5px); }
        .btn {
            display: inline-block;
            padding: 12px 24px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            text-decoration: none;
            border-radius: 6px;
            font-weight: 600;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📊 Test Reports Dashboard</h1>
            <p>Build #${env.BUILD_NUMBER} | Branch: ${env.BRANCH_NAME}</p>
        </div>
        <div class="content">
            <div class="section">
                <h2>🧪 Test Reports</h2>
                <div class="reports-grid">
                    <div class="report-card">
                        <h3>API Functional Tests</h3>
                        <a href="api-tests/index.html" class="btn">View Report</a>
                    </div>
                    <div class="report-card">
                        <h3>JMeter Performance</h3>
                        <a href="jmeter-reports/html/index.html" class="btn">View Report</a>
                    </div>
                    <div class="report-card">
                        <h3>OWASP ZAP Security</h3>
                        <a href="zap-reports/baseline-report.html" class="btn">View Report</a>
                    </div>
                    <div class="report-card">
                        <h3>UiPath UI Automation</h3>
                        <a href="uipath-reports/uipath-report.html" class="btn">View Report</a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</body>
</html>
INDEXEOF
    """

    // Skip publishHTML due to plugin compatibility issues
    // Dashboard is available in build artifacts at build-artifacts/test-reports-index.html
    echo "📄 Test dashboard created at build-artifacts/test-reports-index.html"
}

return this
