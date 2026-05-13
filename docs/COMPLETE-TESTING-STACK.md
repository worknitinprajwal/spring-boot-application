# Complete Testing Stack - Integration Overview

## Testing Tools Deployed

Your Kubernetes cluster now has a complete testing stack integrated with CloudBees CI:

| Tool | Purpose | Type | Access |
|------|---------|------|--------|
| **OWASP ZAP** | Security scanning | DAST | http://zap.local |
| **Apache JMeter** | Performance testing | Load Testing | kubectl exec |
| **SoapUI** | API testing | Functional Testing | kubectl exec |
| **ArgoCD** | GitOps deployment | CD | http://argocd.local |

## Complete CI/CD Pipeline Example

Here's how to use all tools together in a comprehensive CloudBees CI pipeline:

```groovy
pipeline {
    agent any
    
    environment {
        APP_NAME = 'myapp'
        APP_NAMESPACE = 'default'
        APP_URL = "http://myapp.${APP_NAMESPACE}.svc.cluster.local:8080"
        
        // Tool endpoints
        ZAP_URL = 'http://zap.zap.svc.cluster.local:8080'
        JMETER_POD = sh(script: "kubectl get pod -n jmeter -l app=jmeter -o jsonpath='{.items[0].metadata.name}'", returnStdout: true).trim()
        SOAPUI_POD = sh(script: "kubectl get pod -n soapui -l app=soapui -o jsonpath='{.items[0].metadata.name}'", returnStdout: true).trim()
    }
    
    stages {
        stage('Build') {
            steps {
                echo 'Building application...'
                sh 'mvn clean package'
                sh 'docker build -t ${APP_NAME}:${BUILD_NUMBER} .'
            }
        }
        
        stage('Deploy to Test') {
            steps {
                echo 'Deploying to test environment...'
                sh """
                    kubectl set image deployment/${APP_NAME} \\
                        ${APP_NAME}=${APP_NAME}:${BUILD_NUMBER} \\
                        -n ${APP_NAMESPACE}
                """
                sh "kubectl wait --for=condition=ready pod -l app=${APP_NAME} -n ${APP_NAMESPACE} --timeout=120s"
                
                // Wait for app to be fully ready
                sleep(time: 10, unit: 'SECONDS')
            }
        }
        
        stage('API Functional Tests') {
            steps {
                echo 'Running SoapUI API tests...'
                script {
                    // Copy SoapUI project
                    sh "kubectl cp soapui-projects/functional-tests.xml soapui/${SOAPUI_POD}:/tmp/api-tests.xml"
                    
                    // Run tests
                    sh """
                        kubectl exec -n soapui ${SOAPUI_POD} -- \\
                            testrunner.sh -r -j -f /tmp/reports /tmp/api-tests.xml
                    """
                    
                    // Collect results
                    sh "kubectl exec -n soapui ${SOAPUI_POD} -- tar czf /tmp/reports.tar.gz -C /tmp reports"
                    sh "kubectl cp soapui/${SOAPUI_POD}:/tmp/reports.tar.gz ./soapui-reports.tar.gz"
                    sh "tar xzf soapui-reports.tar.gz"
                    
                    // Publish results
                    junit 'reports/TEST-*.xml'
                    archiveArtifacts artifacts: 'reports/**/*'
                }
            }
        }
        
        stage('Performance Tests') {
            steps {
                echo 'Running JMeter performance tests...'
                script {
                    // Copy JMeter test plan
                    sh "kubectl cp jmeter-tests/load-test.jmx jmeter/${JMETER_POD}:/tmp/load-test.jmx"
                    
                    // Run performance test
                    sh """
                        kubectl exec -n jmeter ${JMETER_POD} -- \\
                            jmeter -n \\
                            -t /tmp/load-test.jmx \\
                            -l /tmp/results.jtl \\
                            -e -o /tmp/report \\
                            -Jhost=${APP_NAME}.${APP_NAMESPACE}.svc.cluster.local \\
                            -Jport=8080 \\
                            -Jthreads=50 \\
                            -Jduration=60
                    """
                    
                    // Collect results
                    sh "kubectl cp jmeter/${JMETER_POD}:/tmp/results.jtl ./jmeter-results.jtl"
                    sh "kubectl exec -n jmeter ${JMETER_POD} -- tar czf /tmp/report.tar.gz -C /tmp report"
                    sh "kubectl cp jmeter/${JMETER_POD}:/tmp/report.tar.gz ./jmeter-report.tar.gz"
                    sh "tar xzf jmeter-report.tar.gz"
                    
                    // Publish reports
                    publishHTML([
                        reportDir: 'report',
                        reportFiles: 'index.html',
                        reportName: 'JMeter Performance Report'
                    ])
                    
                    // Check performance thresholds
                    def avgResponseTime = sh(
                        script: """
                            kubectl exec -n jmeter ${JMETER_POD} -- awk -F',' '
                                NR>1 {sum+=\$2; count++}
                                END {print int(sum/count)}
                            ' /tmp/results.jtl
                        """,
                        returnStdout: true
                    ).trim().toInteger()
                    
                    echo "Average Response Time: ${avgResponseTime}ms"
                    
                    if (avgResponseTime > 2000) {
                        unstable("Performance degradation: avg response time ${avgResponseTime}ms exceeds 2000ms threshold")
                    }
                }
            }
        }
        
        stage('Security Scan') {
            steps {
                echo 'Running OWASP ZAP security scan...'
                script {
                    // Spider the application
                    sh "curl '${ZAP_URL}/JSON/spider/action/scan/?url=${APP_URL}'"
                    
                    // Wait for spider to complete
                    def spiderComplete = false
                    while (!spiderComplete) {
                        def status = sh(
                            script: "curl -s '${ZAP_URL}/JSON/spider/view/status/'",
                            returnStdout: true
                        )
                        if (status.contains('"status":"100"')) {
                            spiderComplete = true
                        } else {
                            echo "Spider progress: ${status}"
                            sleep 5
                        }
                    }
                    
                    // Active scan
                    sh "curl '${ZAP_URL}/JSON/ascan/action/scan/?url=${APP_URL}'"
                    
                    // Wait for active scan
                    def scanComplete = false
                    while (!scanComplete) {
                        def status = sh(
                            script: "curl -s '${ZAP_URL}/JSON/ascan/view/status/'",
                            returnStdout: true
                        )
                        if (status.contains('"status":"100"')) {
                            scanComplete = true
                        } else {
                            echo "Active scan progress: ${status}"
                            sleep 10
                        }
                    }
                    
                    // Generate reports
                    sh "curl '${ZAP_URL}/OTHER/core/other/htmlreport/' > zap-security-report.html"
                    sh "curl '${ZAP_URL}/JSON/core/view/alerts/' > zap-alerts.json"
                    
                    archiveArtifacts artifacts: 'zap-*.html,zap-*.json'
                    
                    publishHTML([
                        reportDir: '.',
                        reportFiles: 'zap-security-report.html',
                        reportName: 'ZAP Security Report'
                    ])
                    
                    // Check security threshold
                    def highRisk = sh(
                        script: "curl -s '${ZAP_URL}/JSON/core/view/alerts/?risk=High' | grep -c '\"risk\":\"High\"' || true",
                        returnStdout: true
                    ).trim().toInteger()
                    
                    echo "High-risk vulnerabilities: ${highRisk}"
                    
                    if (highRisk > 0) {
                        error("Security gate failed: ${highRisk} high-risk vulnerabilities found!")
                    }
                }
            }
        }
        
        stage('Quality Gate') {
            steps {
                echo 'Checking quality gates...'
                script {
                    // Aggregate all test results
                    def apiTestsPassed = currentBuild.result != 'FAILURE'
                    def perfTestsPassed = currentBuild.result != 'UNSTABLE'
                    def securityPassed = currentBuild.result != 'FAILURE'
                    
                    if (!apiTestsPassed || !perfTestsPassed || !securityPassed) {
                        error('Quality gate failed - one or more tests did not meet criteria')
                    }
                    
                    echo '✅ All quality gates passed!'
                }
            }
        }
        
        stage('Deploy to Production') {
            when {
                branch 'main'
            }
            steps {
                input message: 'Deploy to production?', ok: 'Deploy'
                
                echo 'Deploying to production via ArgoCD...'
                sh """
                    # Update image tag in Git repository
                    git config user.email "ci@example.com"
                    git config user.name "CloudBees CI"
                    
                    # Update kustomization or helm values
                    sed -i 's|image:.*|image: ${APP_NAME}:${BUILD_NUMBER}|' k8s/production/deployment.yaml
                    
                    git add k8s/production/
                    git commit -m "Deploy ${APP_NAME}:${BUILD_NUMBER} to production"
                    git push origin main
                """
                
                echo 'ArgoCD will automatically sync the changes'
            }
        }
    }
    
    post {
        always {
            echo 'Cleaning up test artifacts...'
            sh """
                kubectl exec -n jmeter ${JMETER_POD} -- rm -rf /tmp/*.jmx /tmp/*.jtl /tmp/report /tmp/*.tar.gz || true
                kubectl exec -n soapui ${SOAPUI_POD} -- rm -rf /tmp/*.xml /tmp/reports /tmp/*.tar.gz || true
            """
        }
        success {
            echo '✅ Pipeline completed successfully!'
            echo 'All tests passed: Functional, Performance, and Security'
        }
        failure {
            echo '❌ Pipeline failed - check logs and reports for details'
        }
        unstable {
            echo '⚠️ Pipeline unstable - performance or quality issues detected'
        }
    }
}
```

## Testing Strategy Overview

### 1. **Functional Testing** (SoapUI)
- **When**: After deployment, before performance tests
- **Purpose**: Verify API functionality works correctly
- **Tests**:
  - REST endpoint availability
  - Request/response validation
  - CRUD operations
  - Error handling
  - Authentication/Authorization

### 2. **Performance Testing** (JMeter)
- **When**: After functional tests pass
- **Purpose**: Ensure application meets performance requirements
- **Tests**:
  - Load testing (expected traffic)
  - Stress testing (breaking point)
  - Endurance testing (memory leaks)
  - Spike testing (sudden traffic)

### 3. **Security Testing** (ZAP)
- **When**: After performance tests
- **Purpose**: Identify security vulnerabilities
- **Tests**:
  - OWASP Top 10 vulnerabilities
  - SQL injection, XSS, CSRF
  - Insecure configurations
  - Authentication issues

### 4. **Deployment** (ArgoCD)
- **When**: After all tests pass
- **Purpose**: GitOps-based continuous deployment
- **Process**:
  - Update Git repository
  - ArgoCD auto-syncs changes
  - Kubernetes deployment updated

## Test Execution Flow

```
┌─────────────┐
│   Build     │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Deploy    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  SoapUI     │ ← API Functional Tests
│  (REST/SOAP)│   - Validate endpoints
└──────┬──────┘   - Check responses
       │
       ▼
┌─────────────┐
│   JMeter    │ ← Performance Tests
│  (Load Test)│   - Response times
└──────┬──────┘   - Throughput
       │           - Error rates
       ▼
┌─────────────┐
│  OWASP ZAP  │ ← Security Scan
│  (Security) │   - Vulnerabilities
└──────┬──────┘   - OWASP Top 10
       │
       ▼
┌─────────────┐
│Quality Gate │ ← Pass/Fail Decision
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   ArgoCD    │ ← GitOps Deployment
│  (Deploy)   │   to Production
└─────────────┘
```

## Quality Gates & Thresholds

Define clear criteria for each stage:

### API Tests (SoapUI)
- ✅ Pass: All assertions succeed, 0 failures
- ❌ Fail: Any assertion failure

### Performance Tests (JMeter)
- ✅ Pass: Avg response < 2000ms, Error rate < 1%
- ⚠️ Unstable: Avg response < 3000ms, Error rate < 5%
- ❌ Fail: Exceeds unstable thresholds

### Security Tests (ZAP)
- ✅ Pass: 0 high-risk vulnerabilities
- ⚠️ Unstable: 1-2 medium-risk issues
- ❌ Fail: Any high-risk vulnerability

## Quick Commands

### Get Pod Names
```bash
# JMeter
export JMETER_POD=$(kubectl get pod -n jmeter -l app=jmeter -o jsonpath='{.items[0].metadata.name}')

# SoapUI
export SOAPUI_POD=$(kubectl get pod -n soapui -l app=soapui -o jsonpath='{.items[0].metadata.name}')

# ZAP (API endpoint)
export ZAP_URL="http://zap.zap.svc.cluster.local:8080"
```

### Run Tests Manually
```bash
# SoapUI
kubectl exec -n soapui $SOAPUI_POD -- testrunner.sh /tmp/project.xml

# JMeter
kubectl exec -n jmeter $JMETER_POD -- jmeter -n -t /tmp/test.jmx -l /tmp/results.jtl

# ZAP
curl "http://zap.zap.svc.cluster.local:8080/JSON/spider/action/scan/?url=http://myapp"
```

## Documentation

- [ZAP Integration Guide](ZAP-INTEGRATION-GUIDE.md)
- [JMeter Integration Guide](JMETER-INTEGRATION-GUIDE.md)
- [SoapUI Integration Guide](SOAPUI-INTEGRATION-GUIDE.md)
- [Sample Pipeline](zap-integration-pipeline.groovy)

## Access URLs

- **CloudBees CI**: http://cloudbees-ci.local/cjoc (or ngrok URL)
- **ArgoCD**: http://argocd.local
- **ZAP Dashboard**: http://zap.local

## Next Steps

1. ✅ All tools deployed and running
2. Create test artifacts (JMX, SoapUI projects)
3. Create CloudBees CI pipeline using the example above
4. Configure quality gates and thresholds
5. Set up notifications (Slack, email) for failures
6. Schedule regular security scans
7. Monitor performance trends over time

## Summary

You now have a **complete testing stack** integrated with CloudBees CI:

- 🔒 **Security**: OWASP ZAP for vulnerability scanning
- ⚡ **Performance**: JMeter for load and stress testing
- ✅ **Functional**: SoapUI for API testing
- 🚀 **Deployment**: ArgoCD for GitOps CD

All tools work together in a unified CI/CD pipeline to ensure quality, performance, and security before production deployment!
