#!/usr/bin/env groovy

/**
 * Test Executor Functions
 * Extracts massive test execution blocks
 */

def call() {
    return this
}

def runAPITests() {
    container('maven') {
        sh script: """
            set -e
            mkdir -p \${ARTIFACTS_DIR}/api-tests

            TEST_URL="${env.HEALTH_CHECK_URL}"

            echo "=== REST API Functional Testing ===" | tee \${ARTIFACTS_DIR}/api-tests/test-results.txt
            echo "Target: \${TEST_URL}" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt
            echo "Timestamp: \$(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt
            echo "" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt

            TESTS_PASSED=0
            TESTS_FAILED=0

            # Test 1: Health Endpoint
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt
            echo "TEST 1: Health Endpoint (/actuator/health)" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt

            START_TIME=\$(date +%s%3N)
            HTTP_CODE=\$(curl -s -o \${ARTIFACTS_DIR}/api-tests/health-response.json -w "%{http_code}" \\
                -H "Accept: application/json" --connect-timeout 5 --max-time 10 \${TEST_URL}/actuator/health)
            END_TIME=\$(date +%s%3N)
            LATENCY=\$((END_TIME - START_TIME))

            echo "  Response Code: \${HTTP_CODE}" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt
            echo "  Latency: \${LATENCY}ms" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt

            [ "\${HTTP_CODE}" = "200" ] && TESTS_PASSED=\$((TESTS_PASSED + 1)) || TESTS_FAILED=\$((TESTS_FAILED + 1))
            grep -q '"status":"UP"' \${ARTIFACTS_DIR}/api-tests/health-response.json && TESTS_PASSED=\$((TESTS_PASSED + 1)) || TESTS_FAILED=\$((TESTS_FAILED + 1))
            [ \${LATENCY} -lt 500 ] && TESTS_PASSED=\$((TESTS_PASSED + 1)) || TESTS_FAILED=\$((TESTS_FAILED + 1))

            # Test 2: Info Endpoint
            echo "" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt
            START_TIME=\$(date +%s%3N)
            HTTP_CODE=\$(curl -s -o \${ARTIFACTS_DIR}/api-tests/info-response.json -w "%{http_code}" \${TEST_URL}/actuator/info)
            END_TIME=\$(date +%s%3N)
            LATENCY=\$((END_TIME - START_TIME))
            [ "\${HTTP_CODE}" = "200" ] && TESTS_PASSED=\$((TESTS_PASSED + 1)) || TESTS_FAILED=\$((TESTS_FAILED + 1))

            # Test 3: Metrics Endpoint
            START_TIME=\$(date +%s%3N)
            HTTP_CODE=\$(curl -s -o \${ARTIFACTS_DIR}/api-tests/metrics-response.json -w "%{http_code}" \${TEST_URL}/actuator/metrics)
            END_TIME=\$(date +%s%3N)
            [ "\${HTTP_CODE}" = "200" ] && TESTS_PASSED=\$((TESTS_PASSED + 1)) || TESTS_FAILED=\$((TESTS_FAILED + 1))

            echo "" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt
            echo "SUMMARY: Passed: \${TESTS_PASSED}, Failed: \${TESTS_FAILED}" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a \${ARTIFACTS_DIR}/api-tests/test-results.txt

            [ \${TESTS_FAILED} -gt 0 ] && exit 1 || exit 0
        """, returnStdout: false
    }
}

def runJMeterTests() {
    container('jmeter') {
        sh script: """
            set -e

            if [ ! -f "tests/jmeter/fitness-tracker-load-test.jmx" ]; then
                echo "❌ JMeter test plan not found!"
                exit 1
            fi

            TARGET_HOST=\$(echo "\${HEALTH_CHECK_URL}" | sed -E 's|https?://([^:/]+).*|\\1|')
            TARGET_PORT=\$(echo "\${HEALTH_CHECK_URL}" | sed -E 's|https?://[^:]+:([0-9]+).*|\\1|')
            [ "\${TARGET_PORT}" = "\${APP_URL}" ] && TARGET_PORT=80

            jmeter -n \\
                -t tests/jmeter/fitness-tracker-load-test.jmx \\
                -JTARGET_HOST=\${TARGET_HOST} \\
                -JTARGET_PORT=\${TARGET_PORT} \\
                -JTHREADS=50 \\
                -JRAMP_UP=30 \\
                -JDURATION=120 \\
                -l \${ARTIFACTS_DIR}/jmeter-reports/results.jtl \\
                -j \${ARTIFACTS_DIR}/jmeter-reports/jmeter.log \\
                -e -o \${ARTIFACTS_DIR}/jmeter-reports/html

            # Analyze results
            if [ -f "\${ARTIFACTS_DIR}/jmeter-reports/results.jtl" ]; then
                TRUE_SUCCESS=\$(grep -c ',true,' \${ARTIFACTS_DIR}/jmeter-reports/results.jtl || echo 0)
                TRUE_FAILURE=\$(grep -c ',false,' \${ARTIFACTS_DIR}/jmeter-reports/results.jtl || echo 0)
                TOTAL=\$((TRUE_SUCCESS + TRUE_FAILURE))

                if [ \$TOTAL -gt 0 ]; then
                    SUCCESS_RATE=\$(echo "scale=2; (\$TRUE_SUCCESS * 100) / \$TOTAL" | bc)
                    echo "Success Rate: \${SUCCESS_RATE}%" | tee \${ARTIFACTS_DIR}/jmeter-reports/metrics-summary.txt

                    # SLA check: 65% success rate minimum
                    if [ "\$(echo "\${SUCCESS_RATE} >= 65" | bc)" = "1" ]; then
                        echo "✅ Performance SLA met"
                        exit 0
                    else
                        echo "❌ Performance SLA failed"
                        exit 1
                    fi
                fi
            fi
        """, returnStdout: false
    }
}

def runZAPTests() {
    container('zap') {
        sh script: """
            set +e

            cd /zap
            mkdir -p reports

            zap-baseline.py \\
                -t \${HEALTH_CHECK_URL}/actuator/health \\
                -g baseline-config.conf \\
                -J reports/baseline-report.json \\
                -r reports/baseline-report.html \\
                2>&1 | tee reports/scan-output.txt || true

            # Parse results
            WARN_COUNT=\$(grep "^WARN-NEW:.*x " reports/scan-output.txt | wc -l || echo 0)
            FAIL_COUNT=\$(grep "^FAIL-NEW:.*x " reports/scan-output.txt | wc -l || echo 0)

            cat > reports/metrics-summary.txt << SUMMARYEOF
Security Scan Results
Warnings: \${WARN_COUNT}
Failures: \${FAIL_COUNT}
SUMMARYEOF

            mkdir -p \${ARTIFACTS_DIR}/zap-reports
            cp -r reports/* \${ARTIFACTS_DIR}/zap-reports/ 2>/dev/null || true

            [ \${FAIL_COUNT} -eq 0 ] && exit 0 || exit 1
        """, returnStdout: false
    }
}

def runUITests() {
    container('uipath') {
        sh script: """
            set -e

            mkdir -p \${ARTIFACTS_DIR}/uipath-reports
            pip3 install --quiet requests pytest pytest-html > /dev/null 2>&1

            # Use unquoted heredoc to allow variable expansion
            cat > /tmp/ui_test.py << PYTEST_EOF
import requests
import pytest
import os

BASE_URL = "\${HEALTH_CHECK_URL}"

class TestUIAutomation:
    def test_health_endpoint(self):
        response = requests.get(f"{BASE_URL}/actuator/health", timeout=10)
        assert response.status_code == 200
        assert response.json()["status"] == "UP"

    def test_info_endpoint(self):
        response = requests.get(f"{BASE_URL}/actuator/info", timeout=10)
        assert response.status_code == 200

    def test_metrics_endpoint(self):
        response = requests.get(f"{BASE_URL}/actuator/metrics", timeout=10)
        assert response.status_code == 200
        assert "names" in response.json()
PYTEST_EOF

            cd /tmp
            pytest ui_test.py -v \\
                --junitxml=\${ARTIFACTS_DIR}/uipath-reports/uipath-junit.xml \\
                --html=\${ARTIFACTS_DIR}/uipath-reports/uipath-report.html \\
                --self-contained-html
        """, returnStdout: false
    }
}

return this
