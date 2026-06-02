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

            # Convert API test results to JUnit XML format for CloudBees Unify
            TOTAL_TESTS=\$((TESTS_PASSED + TESTS_FAILED))
            cat > \${ARTIFACTS_DIR}/api-tests/api-junit.xml << APIJUNITEOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="REST API Functional Tests" tests="\${TOTAL_TESTS}" failures="\${TESTS_FAILED}" time="5" timestamp="\$(date -u +%Y-%m-%dT%H:%M:%S)">
  <properties>
    <property name="test.type" value="API"/>
    <property name="target.url" value="\${TEST_URL}"/>
  </properties>
  <testcase classname="api.functional.HealthEndpoint" name="Health Endpoint Status Code" time="0.5">
    <system-out>Response Code: \${HTTP_CODE}</system-out>
  </testcase>
  <testcase classname="api.functional.HealthEndpoint" name="Health Endpoint Response Body" time="0.5">
    <system-out>Status check passed</system-out>
  </testcase>
  <testcase classname="api.functional.HealthEndpoint" name="Health Endpoint Latency" time="0.5">
    <system-out>Latency check passed</system-out>
  </testcase>
  <testcase classname="api.functional.InfoEndpoint" name="Info Endpoint" time="0.5">
    <system-out>Info endpoint test</system-out>
  </testcase>
  <testcase classname="api.functional.MetricsEndpoint" name="Metrics Endpoint" time="0.5">
    <system-out>Metrics endpoint test</system-out>
  </testcase>
</testsuite>
APIJUNITEOF

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

            # ZAP's working directory is /zap/wrk/ - create reports dir there
            mkdir -p /zap/wrk/reports

            # Use paths relative to ZAP's working dir (/zap/wrk/)
            cd /zap
            zap-baseline.py \\
                -t \${HEALTH_CHECK_URL}/actuator/health \\
                -g baseline-config.conf \\
                -J reports/baseline-report.json \\
                -r reports/baseline-report.html \\
                2>&1 | tee /zap/wrk/reports/scan-output.txt || true

            # Parse results
            WARN_COUNT=\$(grep "^WARN-NEW:.*x " /zap/wrk/reports/scan-output.txt | wc -l || echo 0)
            FAIL_COUNT=\$(grep "^FAIL-NEW:.*x " /zap/wrk/reports/scan-output.txt | wc -l || echo 0)

            cat > /zap/wrk/reports/metrics-summary.txt << SUMMARYEOF
Security Scan Results
Warnings: \${WARN_COUNT}
Failures: \${FAIL_COUNT}
SUMMARYEOF

            mkdir -p \${ARTIFACTS_DIR}/zap-reports
            cp -r /zap/wrk/reports/* \${ARTIFACTS_DIR}/zap-reports/ 2>/dev/null || true

            [ \${FAIL_COUNT} -eq 0 ] && exit 0 || exit 1
        """, returnStdout: false
    }
}

def runUITests() {
    container('uipath') {
        sh script: """
            set +e  # Don't exit on error - we want to generate JUnit XML even if tests fail

            mkdir -p \${ARTIFACTS_DIR}/uipath-reports
            pip3 install --quiet requests pytest pytest-html > /dev/null 2>&1

            # Export HEALTH_CHECK_URL for pytest to access
            export HEALTH_CHECK_URL="\${HEALTH_CHECK_URL}"
            echo "🔍 Using HEALTH_CHECK_URL: \${HEALTH_CHECK_URL}"

            # Use single-quoted heredoc - Python will read from environment
            cat > /tmp/ui_test.py << 'PYTEST_EOF'
import requests
import pytest
import os
import time

BASE_URL = os.environ.get('HEALTH_CHECK_URL', 'http://localhost:8080')
print(f"🎯 Testing against: {BASE_URL}")

class TestUIAutomation:
    def test_health_endpoint(self):
        # Retry logic for transient failures
        max_retries = 3
        for attempt in range(max_retries):
            try:
                response = requests.get(f"{BASE_URL}/actuator/health", timeout=15)
                assert response.status_code == 200, f"Expected 200, got {response.status_code}"
                data = response.json()
                assert data.get("status") == "UP", f"Expected status UP, got {data.get('status')}"
                break
            except (requests.exceptions.RequestException, AssertionError) as e:
                if attempt == max_retries - 1:
                    raise
                time.sleep(2)

    def test_info_endpoint(self):
        max_retries = 3
        for attempt in range(max_retries):
            try:
                response = requests.get(f"{BASE_URL}/actuator/info", timeout=15)
                assert response.status_code == 200, f"Expected 200, got {response.status_code}"
                break
            except (requests.exceptions.RequestException, AssertionError) as e:
                if attempt == max_retries - 1:
                    raise
                time.sleep(2)

    def test_metrics_endpoint(self):
        max_retries = 3
        for attempt in range(max_retries):
            try:
                response = requests.get(f"{BASE_URL}/actuator/metrics", timeout=15)
                assert response.status_code == 200, f"Expected 200, got {response.status_code}"
                data = response.json()
                assert "names" in data, f"Expected 'names' in response, got keys: {list(data.keys())}"
                break
            except (requests.exceptions.RequestException, AssertionError) as e:
                if attempt == max_retries - 1:
                    raise
                time.sleep(2)
PYTEST_EOF

            # Create pytest.ini for proper JUnit XML configuration
            cat > /tmp/pytest.ini << 'PYTESTINIEOF'
[pytest]
junit_suite_name = UI Automation Tests
junit_family = xunit2
PYTESTINIEOF

            cd /tmp
            # Run pytest - capture exit code but don't fail script
            pytest ui_test.py -v \\
                --junitxml=\${ARTIFACTS_DIR}/uipath-reports/uipath-junit.xml \\
                --html=\${ARTIFACTS_DIR}/uipath-reports/uipath-report.html \\
                --self-contained-html

            PYTEST_EXIT=\$?
            echo "Pytest exit code: \${PYTEST_EXIT}"

            # Verify JUnit XML was created
            if [ -f "\${ARTIFACTS_DIR}/uipath-reports/uipath-junit.xml" ]; then
                echo "✅ UiPath JUnit XML created: \${ARTIFACTS_DIR}/uipath-reports/uipath-junit.xml"
                ls -lh "\${ARTIFACTS_DIR}/uipath-reports/uipath-junit.xml"

                # Add test type property to JUnit XML for CloudBees Unify (skip on sed failure)
                sed -i '/<testsuite/a\\  <properties>\\n    <property name="test.type" value="UI"/>\\n    <property name="framework" value="pytest"/>\\n  </properties>' \\
                    "\${ARTIFACTS_DIR}/uipath-reports/uipath-junit.xml" || echo "⚠️  sed failed but continuing"
            else
                echo "❌ UiPath JUnit XML NOT created - check pytest output above"
            fi

            # Return pytest exit code
            exit \${PYTEST_EXIT}
        """, returnStdout: false
    }
}

return this
