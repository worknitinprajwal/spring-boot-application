# Test Artifacts and Reports Guide

## Overview

Every build archives **ALL** test reports, HTML dashboards, JSON/XML/TXT files, logs, and configuration files. This guide shows you exactly what's archived and how to access it.

---

## 🎯 Quick Access: Test Reports Dashboard

**Best way to view all reports**: Click **"Test Reports Dashboard"** in Jenkins build sidebar

This gives you:
- ✅ Interactive cards for each report type
- ✅ Direct links to all HTML reports
- ✅ Links to raw artifacts (JSON, XML, TXT)
- ✅ Build information
- ✅ Documentation links

**URL**: `${BUILD_URL}Test_20Reports_20Dashboard/`

---

## 📊 Published HTML Reports

These reports are published and appear in the Jenkins build sidebar:

### 1. Test Reports Dashboard
**Link in sidebar**: "Test Reports Dashboard"  
**Direct URL**: `${BUILD_URL}Test_20Reports_20Dashboard/`  
**What it contains**: Beautiful UI with cards linking to all reports

### 2. API Functional Tests
**Link in sidebar**: "API Functional Tests"  
**Direct URL**: `${BUILD_URL}API_20Functional_20Tests/`  
**What it contains**: 
- Formatted test results with color-coded pass/fail
- Latency measurements per endpoint
- HTTP status validation results

### 3. JMeter Performance Report
**Link in sidebar**: "JMeter Performance Report"  
**Direct URL**: `${BUILD_URL}JMeter_20Performance_20Report/`  
**What it contains**:
- Interactive dashboard with graphs
- Response time charts
- Throughput over time
- Error analysis
- Request statistics

### 4. OWASP ZAP Security Report
**Link in sidebar**: "OWASP ZAP Security Report"  
**Direct URL**: `${BUILD_URL}OWASP_20ZAP_20Security_20Report/`  
**What it contains**:
- Security vulnerability findings
- Risk levels with descriptions
- Remediation recommendations
- Confidence ratings

---

## 📦 Archived Artifacts Structure

All artifacts are in: `${BUILD_URL}artifact/build-artifacts/`

```
build-artifacts/
├── test-reports-index.html          # Main dashboard
├── build-summary.html                # Build summary
├── build-info.txt                    # Consolidated metrics
├── metrics.json                      # Machine-readable metrics
│
├── api-tests/                        # API Functional Tests
│   ├── index.html                    # HTML formatted results
│   ├── test-results.txt              # Raw text results
│   ├── health-response.json          # /actuator/health response
│   ├── info-response.json            # /actuator/info response
│   └── metrics-response.json         # /actuator/metrics response
│
├── jmeter-reports/                   # JMeter Performance Tests
│   ├── html/                         # Interactive dashboard
│   │   ├── index.html                # Main report page
│   │   ├── content/                  # Graphs and charts
│   │   └── sbadmin2-1.0.7/          # Dashboard assets
│   ├── results.jtl                   # Raw test results (CSV)
│   ├── metrics-summary.txt           # Metrics analysis
│   └── jmeter.log                    # Execution log
│
├── zap-reports/                      # OWASP ZAP Security Scan
│   ├── baseline-report.html          # Interactive HTML report
│   ├── baseline-report.json          # JSON format
│   ├── baseline-report.xml           # XML format
│   ├── baseline-report.md            # Markdown format
│   └── metrics-summary.txt           # Findings summary
│
├── aikido-scan-output.txt            # Aikido security scan
├── aikido-scan-details.json          # Aikido scan details
├── unify-data.json                   # CloudBees Unify data
├── helm-values.yaml                  # Deployed Helm values
└── failure.log                       # Failure details (if failed)
```

---

## 📋 Accessing Specific Reports

### Via Jenkins UI

1. **Navigate to build**: Go to `${JENKINS_URL}/job/${JOB_NAME}/${BUILD_NUMBER}/`
2. **Sidebar links**:
   - Click "Test Reports Dashboard" for main index
   - Click "API Functional Tests" for API results
   - Click "JMeter Performance Report" for load test results
   - Click "OWASP ZAP Security Report" for security findings
3. **Build Artifacts**:
   - Click "Build Artifacts" in left sidebar
   - Browse `build-artifacts/` directory

### Via Direct URLs

```bash
# Test Reports Dashboard
${BUILD_URL}Test_20Reports_20Dashboard/

# API Functional Tests
${BUILD_URL}API_20Functional_20Tests/
${BUILD_URL}artifact/build-artifacts/api-tests/test-results.txt

# JMeter Performance
${BUILD_URL}JMeter_20Performance_20Report/
${BUILD_URL}artifact/build-artifacts/jmeter-reports/metrics-summary.txt
${BUILD_URL}artifact/build-artifacts/jmeter-reports/results.jtl

# ZAP Security
${BUILD_URL}OWASP_20ZAP_20Security_20Report/
${BUILD_URL}artifact/build-artifacts/zap-reports/metrics-summary.txt
${BUILD_URL}artifact/build-artifacts/zap-reports/baseline-report.json

# Consolidated
${BUILD_URL}artifact/build-artifacts/build-info.txt
${BUILD_URL}artifact/build-artifacts/metrics.json
```

### Via CLI

```bash
# Download all artifacts as ZIP
curl -u user:token ${BUILD_URL}artifact/build-artifacts/*zip*/build-artifacts.zip

# Download specific file
curl -u user:token -o test-results.txt \
  ${BUILD_URL}artifact/build-artifacts/api-tests/test-results.txt

# Download JMeter results for analysis
curl -u user:token -o results.jtl \
  ${BUILD_URL}artifact/build-artifacts/jmeter-reports/results.jtl

# Download ZAP JSON for parsing
curl -u user:token -o zap-report.json \
  ${BUILD_URL}artifact/build-artifacts/zap-reports/baseline-report.json
```

---

## 🔍 What Each Report Contains

### API Functional Tests

**HTML Report** (`api-tests/index.html`):
- Color-coded test results
- Pass/fail indicators for each assertion
- Latency measurements
- HTTP status codes

**Text Report** (`api-tests/test-results.txt`):
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST 1: Health Endpoint (/actuator/health)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Response Code: 200
  Latency: 145ms
  ✅ HTTP Status: PASS
  ✅ Health Status: PASS (status=UP)
  ✅ Performance SLA: PASS (145ms < 500ms)
```

**Response Files** (JSON):
- Actual API responses captured
- Useful for debugging failures
- Can validate data structure

---

### JMeter Performance Report

**HTML Dashboard** (`jmeter-reports/html/index.html`):
- **Dashboard**: Overview with key metrics
- **Charts**: Response times over time, throughput
- **Statistics**: Min/max/avg/percentiles per request
- **Errors**: Failed requests with details
- **Top 5**: Slowest requests

**Metrics Summary** (`jmeter-reports/metrics-summary.txt`):
```
PERFORMANCE METRICS SUMMARY
Request Statistics:
  Total Requests: 3456
  Successful: 3452 (99.88%)
Response Time (ms):
  Average: 234.56
  ~95th Percentile: 567
  ~99th Percentile: 892
Throughput:
  Requests/sec: 28.80
```

**Raw Results** (`results.jtl`):
- CSV format with all request data
- Columns: timestamp, elapsed, label, responseCode, success, bytes, etc.
- Can import into Excel or analysis tools

---

### OWASP ZAP Security Report

**HTML Report** (`baseline-report.html`):
- Interactive report with collapsible sections
- Color-coded by risk level (High/Medium/Low)
- Detailed descriptions
- Remediation steps
- CWE/WASC references

**Metrics Summary** (`metrics-summary.txt`):
```
SECURITY SCAN RESULTS
Alert Summary by Severity:
  ❌ High: 0
  ⚠️  Medium: 2
  ℹ️  Low: 5

Detailed Findings:
[Medium Risk] X-Content-Type-Options Header Missing
  Description: ...
  Solution: Set X-Content-Type-Options to 'nosniff'
  Count: 3 instances
```

**JSON Report** (`baseline-report.json`):
- Machine-readable format
- Can parse with `jq` or scripts
- Includes all alert details

---

## 📈 Metrics Files

### build-info.txt

Consolidated text summary of entire build:

```
Build: 98
Branch: main
Timestamp: 2026-05-15T12:35:22Z
Commit: abc1234
Image: anuddeeph2/fitness-tracker:main-98

=== REST API Functional Testing ===
Target URL: http://172.18.0.7:31397
Tests Passed: 9
Tests Failed: 0

=== JMeter Performance Testing ===
Success Rate: 99.88%
Avg Response Time: 234.56ms
Failed Requests: 4

=== OWASP ZAP Security Scan ===
High Severity: 0
Medium Severity: 2
Low Severity: 5
```

### metrics.json

Machine-readable build metadata:

```json
{
  "build": {
    "number": "98",
    "branch": "main",
    "image": "anuddeeph2/fitness-tracker:main-98",
    "timestamp": "2026-05-15T12:35:22Z"
  },
  "storage": {
    "location": "Jenkins Artifacts",
    "reports": {
      "api_tests": "build-artifacts/api-tests/test-results.txt",
      "jmeter": "build-artifacts/jmeter-reports/html/index.html",
      "zap": "build-artifacts/zap-reports/baseline-report.html"
    }
  }
}
```

---

## 📚 Documentation Files

Also archived for reference:

```
tests/COMPREHENSIVE-TESTING-GUIDE.md  # Full testing documentation
tests/TESTING-SUMMARY.md              # Quick summary
tests/TEST-REPORTS-GUIDE.md           # Original reports guide
tests/**/*.jmx                        # JMeter test plans
tests/**/*.xml                        # SoapUI configs
```

Access via: `${BUILD_URL}artifact/tests/COMPREHENSIVE-TESTING-GUIDE.md`

---

## 🎨 Viewing HTML Reports

### Best Experience

1. **Click sidebar links** in Jenkins:
   - Uses Jenkins' HTML publisher
   - Properly rendered with CSS/JS
   - Interactive elements work

2. **Download and open locally**:
   ```bash
   curl -u user:token ${BUILD_URL}artifact/build-artifacts/*zip*/build-artifacts.zip
   unzip build-artifacts.zip
   open build-artifacts/test-reports-index.html
   ```

### Avoid

❌ Don't use "raw" artifact URLs - CSS/JS may not load correctly

---

## 📊 Analyzing Results

### Trend Analysis

Compare metrics across builds:

```bash
# Get last 10 builds' metrics
for i in {1..10}; do
  BUILD=$((BUILD_NUMBER - i))
  curl -u user:token \
    ${JENKINS_URL}/job/${JOB_NAME}/${BUILD}/artifact/build-artifacts/metrics.json \
    >> metrics-history.json
done
```

### Parse JMeter Results

```python
import csv

with open('results.jtl', 'r') as f:
    reader = csv.DictReader(f)
    for row in reader:
        print(f"Request: {row['label']}, Time: {row['elapsed']}ms")
```

### Parse ZAP Results

```bash
# Count high severity issues
jq '[.site[0].alerts[] | select(.riskdesc | startswith("High"))] | length' \
  baseline-report.json

# List all medium+ risks
jq -r '.site[0].alerts[] | select(.riskdesc | startswith("High") or startswith("Medium")) | 
  .alert' baseline-report.json
```

---

## 🔧 Customizing Archived Artifacts

To add more files to archive, edit `Jenkinsfile.kubernetes`:

```groovy
archiveArtifacts artifacts: 'path/to/file/**/*', allowEmptyArchive: true
```

Current archiving happens in:
- Line ~730: API tests
- Line ~925: JMeter reports + test plan
- Line ~1060: ZAP reports
- Line ~1440: All build artifacts + documentation

---

## 💾 Retention Policy

From `Jenkinsfile.kubernetes`:

```groovy
buildDiscarder(logRotator(
    numToKeepStr: '10',           // Keep last 10 builds
    artifactNumToKeepStr: '5',    // Keep artifacts for last 5 builds
    daysToKeepStr: '30'           // Keep builds for 30 days
))
```

**What this means**:
- Last 5 builds: **Full artifacts available**
- Builds 6-10: **Build logs only** (artifacts deleted)
- After 30 days: **All build data deleted**

**Important**: Download critical reports before they're purged!

---

## ❓ Troubleshooting

### Can't find a report?

1. Check it was generated: Look at Jenkins console output
2. Check the archive step succeeded: Should see "Archiving artifacts"
3. Try direct artifact URL: `${BUILD_URL}artifact/build-artifacts/`

### HTML report not rendering?

- Use sidebar links (not raw artifact URLs)
- Or download and open locally

### Old builds missing artifacts?

- Check retention policy (only last 5 builds have artifacts)
- Older builds were purged

---

## 📞 Quick Reference

| Report Type | Best Access Method | Direct Artifact Path |
|------------|-------------------|---------------------|
| **All Reports** | Click "Test Reports Dashboard" | `build-artifacts/test-reports-index.html` |
| **API Tests** | Click "API Functional Tests" | `build-artifacts/api-tests/test-results.txt` |
| **Performance** | Click "JMeter Performance Report" | `build-artifacts/jmeter-reports/html/index.html` |
| **Security** | Click "OWASP ZAP Security Report" | `build-artifacts/zap-reports/baseline-report.html` |
| **Summary** | Download | `build-artifacts/build-info.txt` |

**Bottom line**: Everything is archived. Start with "Test Reports Dashboard" in the sidebar for the best experience.
