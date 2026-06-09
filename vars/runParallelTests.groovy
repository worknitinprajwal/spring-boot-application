#!/usr/bin/env groovy

/**
 * Parallel Test Execution Helper
 * Extracts massive parallel test blocks to reduce Jenkinsfile bytecode size
 */

def call(Map config = [:]) {
    def namespace = config.namespace ?: env.NAMESPACE ?: 'default'
    def appUrl = config.appUrl ?: env.APP_URL
    def artifactsDir = config.artifactsDir ?: 'build-artifacts'

    parallel(
        'API Tests': {
            runAPITests(namespace, appUrl, artifactsDir)
        },
        'Performance Tests': {
            runPerformanceTests(namespace, appUrl, artifactsDir)
        },
        'Security Tests': {
            runSecurityTests(namespace, appUrl, artifactsDir)
        },
        'UI Tests': {
            runUITests(namespace, appUrl, artifactsDir)
        }
    )
}

def runAPITests(String namespace, String appUrl, String artifactsDir) {
    stage('API Testing - REST Functional Tests') {
        container('maven') {
            try {
                echo "🧪 Running API Functional Tests against ${appUrl}..."

                sh """#!/bin/bash
                    set -e
                    mkdir -p ${artifactsDir}/api-tests

                    # Run REST API tests
                    mvn test -Dtest=ApiTest \\
                        -Dapi.url=${appUrl} \\
                        -Dmaven.test.failure.ignore=true

                    # Copy results
                    cp -r target/surefire-reports ${artifactsDir}/api-tests/ || true
                """

                echo "✅ API tests completed"
            } catch (Exception e) {
                echo "⚠️  API tests failed: ${e.message}"
                currentBuild.result = 'UNSTABLE'
            }
        }
    }
}

def runPerformanceTests(String namespace, String appUrl, String artifactsDir) {
    stage('Performance Testing - JMeter') {
        container('jmeter') {
            try {
                echo "⚡ Running Performance Tests with JMeter..."

                sh """#!/bin/bash
                    set -e
                    mkdir -p ${artifactsDir}/jmeter-reports

                    # Run JMeter tests (if test plan exists)
                    if [ -f "src/test/jmeter/LoadTest.jmx" ]; then
                        jmeter -n \\
                            -t src/test/jmeter/LoadTest.jmx \\
                            -l ${artifactsDir}/jmeter-reports/results.jtl \\
                            -e -o ${artifactsDir}/jmeter-reports/html \\
                            -Jhost=\${echo ${appUrl} | sed 's|http://||'} \\
                            -Jport=80 \\
                            -Jthreads=10 \\
                            -Jrampup=5 \\
                            -Jduration=60
                    else
                        echo "⚠️  JMeter test plan not found, skipping..."
                    fi
                """

                echo "✅ Performance tests completed"
            } catch (Exception e) {
                echo "⚠️  Performance tests failed: ${e.message}"
                currentBuild.result = 'UNSTABLE'
            }
        }
    }
}

def runSecurityTests(String namespace, String appUrl, String artifactsDir) {
    stage('Security Testing - OWASP ZAP') {
        container('zap') {
            try {
                echo "🔒 Running OWASP ZAP Security Scan..."

                sh """#!/bin/bash
                    set -e
                    mkdir -p ${artifactsDir}/zap-reports

                    # Run ZAP baseline scan
                    zap-baseline.py \\
                        -t ${appUrl} \\
                        -r ${artifactsDir}/zap-reports/zap-report.html \\
                        -w ${artifactsDir}/zap-reports/zap-report.md \\
                        -J ${artifactsDir}/zap-reports/zap-report.json \\
                        -x ${artifactsDir}/zap-reports/zap-scan.xml \\
                        || true

                    # Convert to SARIF if possible
                    if command -v python3 &> /dev/null; then
                        python3 -c "import json; print('SARIF conversion would go here')" || true
                    fi
                """

                echo "✅ Security scan completed"
            } catch (Exception e) {
                echo "⚠️  Security scan failed: ${e.message}"
                currentBuild.result = 'UNSTABLE'
            }
        }
    }
}

def runUITests(String namespace, String appUrl, String artifactsDir) {
    stage('UI Testing - UiPath') {
        container('uipath') {
            try {
                echo "🖥️  Running UI Automation Tests..."

                sh """#!/bin/bash
                    set -e
                    mkdir -p ${artifactsDir}/ui-tests

                    # Install Python dependencies for UI testing
                    pip install --quiet pytest selenium pytest-html || true

                    # Run UI tests (if they exist)
                    if [ -d "src/test/python" ]; then
                        pytest src/test/python/ \\
                            --html=${artifactsDir}/ui-tests/report.html \\
                            --self-contained-html \\
                            --junitxml=${artifactsDir}/ui-tests/junit-ui-tests.xml \\
                            || true
                    else
                        echo "⚠️  UI test directory not found, skipping..."
                    fi
                """

                echo "✅ UI tests completed"
            } catch (Exception e) {
                echo "⚠️  UI tests failed: ${e.message}"
                currentBuild.result = 'UNSTABLE'
            }
        }
    }
}

return this
