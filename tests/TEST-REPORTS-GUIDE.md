# Test Reports and Metrics Guide

This document explains where test reports and performance metrics are stored and how to access them for deeper analysis.

## Report Storage Locations

### Jenkins Build Artifacts
All test reports are archived as Jenkins build artifacts and available at:
```
<JENKINS_URL>/job/<JOB_NAME>/<BUILD_NUMBER>/artifact/build-artifacts/
```

### Individual Report Types

#### 1. JMeter Performance Reports
- **Location**: `build-artifacts/jmeter-reports/`
- **Main Report**: `html/index.html` (interactive HTML dashboard)
- **Raw Data**: `results.jtl` (CSV format with all request data)
- **Metrics Summary**: `metrics-summary.txt` (quick statistics)
- **Jenkins Link**: `<BUILD_URL>JMeter_20Performance_20Report/`

**What's Included:**
- Response time graphs (min, max, avg, percentiles)
- Throughput metrics (requests per second)
- Error rate analysis
- Request distribution charts
- Thread performance over time

**Deep Analysis:**
```bash
# Download results.jtl for analysis
curl -o results.jtl <JENKINS_URL>/artifact/build-artifacts/jmeter-reports/results.jtl

# Analyze with custom tools
# Results.jtl columns: timeStamp,elapsed,label,responseCode,success,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect
```

#### 2. OWASP ZAP Security Reports
- **Location**: `build-artifacts/zap-reports/`
- **HTML Report**: `baseline-report.html` (detailed findings)
- **XML Report**: `baseline-report.xml` (machine-readable)
- **JSON Report**: `baseline-report.json` (for automation)
- **Markdown Report**: `baseline-report.md` (text summary)
- **Metrics Summary**: `metrics-summary.txt` (alert counts by severity)
- **Jenkins Link**: `<BUILD_URL>OWASP_20ZAP_20Security_20Report/`

**What's Included:**
- Security vulnerabilities by risk level (High, Medium, Low, Informational)
- Detailed descriptions of each finding
- Recommendations for remediation
- Confidence ratings for each alert

**Deep Analysis:**
```bash
# Parse JSON for custom metrics
cat baseline-report.json | jq '.site[0].alerts[] | select(.riskdesc | contains("High"))'

# Export to CSV for spreadsheet analysis
cat baseline-report.json | jq -r '.site[0].alerts[] | [.riskdesc, .alert, .count, .solution] | @csv'
```

#### 3. SoapUI API Test Reports
- **Location**: `build-artifacts/soapui-reports/`
- **Test Results**: Individual test case results
- **Summary**: Overall pass/fail statistics

### 4. Consolidated Build Information
- **Location**: `build-artifacts/build-info.txt`
- **Contains**:
  - Build metadata (number, branch, commit)
  - Deployment information
  - Test execution timestamps
  - Consolidated metrics from all test stages

## Performance Metrics Details

### JMeter Load Test Configuration
```yaml
Thread Configuration:
  Users: 50 concurrent threads
  Ramp-up: 30 seconds
  Duration: 120 seconds (2 minutes)
  Think Time: 1 second between requests

Endpoints Tested:
  - GET /actuator/health (expected < 500ms)
  - GET /actuator/info (expected < 300ms)
  - GET /actuator/metrics (expected < 1000ms)

Assertions:
  - HTTP Status Code: 200
  - Response Time: Per-endpoint thresholds
  - Content Validation: Health status "UP"
```

### Key Performance Indicators (KPIs)

1. **Response Time Metrics**
   - Average Response Time
   - 50th Percentile (Median)
   - 90th Percentile
   - 95th Percentile
   - 99th Percentile
   - Min/Max Response Time

2. **Throughput Metrics**
   - Requests per Second
   - Total Request Count
   - Successful Requests
   - Failed Requests

3. **Error Analysis**
   - Error Rate (%)
   - Error Types Distribution
   - Failed Assertions

## Accessing Reports

### Via Jenkins UI
1. Navigate to build page: `<JENKINS_URL>/job/<JOB_NAME>/<BUILD_NUMBER>/`
2. Click "Build Artifacts" in left menu
3. Browse `build-artifacts/` directory
4. Click on HTML reports for interactive viewing

### Via Jenkins CLI
```bash
# Download all artifacts
jenkins-cli -s <JENKINS_URL> get-artifacts <JOB_NAME> <BUILD_NUMBER>

# Download specific report
curl -o jmeter-report.tar.gz <JENKINS_URL>/job/<JOB_NAME>/<BUILD_NUMBER>/artifact/build-artifacts/jmeter-reports/*zip*/jmeter-reports.zip
```

### Via REST API
```bash
# List all artifacts
curl -u user:token <JENKINS_URL>/job/<JOB_NAME>/<BUILD_NUMBER>/api/json?tree=artifacts[*]

# Download specific file
curl -u user:token -o build-info.txt <JENKINS_URL>/job/<JOB_NAME>/<BUILD_NUMBER>/artifact/build-artifacts/build-info.txt
```

## Metrics Storage and Retention

### Current Configuration
```groovy
buildDiscarder(logRotator(
    numToKeepStr: '10',           // Keep last 10 builds
    artifactNumToKeepStr: '5',    // Keep artifacts for last 5 builds
    daysToKeepStr: '30'           // Keep builds for 30 days
))
```

### Artifact Retention
- **Last 5 builds**: Full artifacts including all reports
- **Builds 6-10**: Build logs only (artifacts deleted)
- **After 30 days**: All build data deleted

## Integration with External Tools

### Export to InfluxDB/Grafana (Future Enhancement)
```groovy
// Add to Jenkinsfile for time-series metrics storage
BackendListener: InfluxDB HTTP Sender
  - Metrics: Response times, throughput, errors
  - Visualization: Grafana dashboards
  - Retention: Long-term trend analysis
```

### Export to ELK Stack (Future Enhancement)
```bash
# Send JMeter results to Elasticsearch
curl -X POST "localhost:9200/jmeter-results/_doc" \
  -H 'Content-Type: application/json' \
  -d @build-artifacts/jmeter-reports/results.jtl
```

### Export to CloudBees Unify
Already implemented in pipeline:
- Test metrics published to CloudBees Unify dashboard
- Historical trend tracking
- Cross-project comparison

## Production-Level Monitoring

### Real-time Metrics
For production deployments, consider:
1. **APM Tools**: New Relic, Datadog, AppDynamics
2. **Prometheus + Grafana**: For Kubernetes metrics
3. **ELK Stack**: For log aggregation and analysis

### Continuous Performance Monitoring
```yaml
Alerts:
  - Response time > threshold
  - Error rate > 5%
  - Throughput drops > 20%
  
Dashboards:
  - Real-time performance metrics
  - Historical trend analysis
  - Comparison with baseline
```

## Report Analysis Examples

### Calculate Performance Regression
```bash
# Compare current build with baseline
CURRENT=$(awk -F',' 'NR>1 {sum+=$2; count++} END {print sum/count}' current-results.jtl)
BASELINE=$(awk -F',' 'NR>1 {sum+=$2; count++} END {print sum/count}' baseline-results.jtl)

REGRESSION=$(echo "scale=2; ($CURRENT - $BASELINE) / $BASELINE * 100" | bc)
echo "Performance change: ${REGRESSION}%"
```

### Extract Security Vulnerabilities by Severity
```bash
# High severity issues
jq '.site[0].alerts[] | select(.riskdesc | startswith("High"))' baseline-report.json

# Count by risk level
jq -r '.site[0].alerts[] | .riskdesc' baseline-report.json | sort | uniq -c
```

### Generate Custom Performance Report
```python
import csv
import statistics

with open('results.jtl', 'r') as f:
    reader = csv.DictReader(f)
    response_times = [int(row['elapsed']) for row in reader]

print(f"Mean: {statistics.mean(response_times)}ms")
print(f"Median: {statistics.median(response_times)}ms")
print(f"95th Percentile: {statistics.quantiles(response_times, n=20)[18]}ms")
print(f"Std Dev: {statistics.stdev(response_times)}ms")
```

## Troubleshooting

### Missing Reports
1. Check Jenkins console output for errors
2. Verify `build-artifacts/` directory was created
3. Check container permissions (all containers run as different users)
4. Verify test files exist in repository

### Incomplete Metrics
1. Check if tests ran to completion (non-blocking failures)
2. Verify network connectivity to application URL
3. Check application health before tests
4. Review JMeter/ZAP logs for errors

### Access Issues
1. Verify Jenkins artifact permissions
2. Check build retention policy
3. Confirm authentication credentials
4. Use direct artifact URLs if UI fails

## Contact
For questions about test reports or metrics analysis:
- Check Jenkins console logs
- Review pipeline configuration in `Jenkinsfile.kubernetes`
- Consult CloudBees documentation
