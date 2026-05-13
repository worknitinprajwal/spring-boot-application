# OWASP ZAP Integration with CloudBees CI

## Overview

OWASP ZAP (Zed Attack Proxy) is integrated into your Kubernetes cluster and can be used in CloudBees CI pipelines for automated security testing.

## ZAP Deployment Details

- **Namespace**: `zap`
- **Service**: `zap.zap.svc.cluster.local:8080`
- **Dashboard**: http://zap.local
- **Version**: 2.17.0
- **Mode**: Proxy/API Mode

## Integration Methods

### Method 1: Direct API Calls (Recommended)

Use curl commands in your Jenkins pipeline to interact with ZAP's REST API.

**Basic Pipeline Example:**

```groovy
pipeline {
    agent any
    
    environment {
        ZAP_URL = 'http://zap.zap.svc.cluster.local:8080'
        TARGET_URL = 'http://your-app-service:8080'
    }
    
    stages {
        stage('Security Scan') {
            steps {
                // Spider the target
                sh "curl '${ZAP_URL}/JSON/spider/action/scan/?url=${TARGET_URL}'"
                
                // Wait and get results
                sleep(time: 30, unit: 'SECONDS')
                sh "curl '${ZAP_URL}/JSON/core/view/alerts/' > zap-alerts.json"
                
                // Archive results
                archiveArtifacts artifacts: 'zap-alerts.json'
            }
        }
    }
}
```

### Method 2: Using ZAP Jenkins Plugin

Install the **OWASP ZAP Official Jenkins Plugin** from Jenkins Plugin Manager:

1. Go to CloudBees CI: http://cloudbees-ci.local/cjoc
2. Navigate to: Manage Jenkins → Plugin Manager
3. Search for: "OWASP Zed Attack Proxy"
4. Install the plugin

**Pipeline with ZAP Plugin:**

```groovy
pipeline {
    agent any
    
    stages {
        stage('ZAP Scan') {
            steps {
                zapAttack(
                    zapHost: 'zap.zap.svc.cluster.local',
                    zapPort: 8080,
                    zapApiKey: '',
                    targetURL: 'http://your-app:8080',
                    failBuild: true,
                    failHighAlerts: 5,
                    failMediumAlerts: 10
                )
            }
        }
    }
}
```

### Method 3: Using ZAP Docker in Pipeline Agents

Run ZAP as a sidecar container in your pipeline:

```groovy
pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    image: maven:3.8-openjdk-11
    command: ['cat']
    tty: true
  - name: zap
    image: zaproxy/zap-stable
    command:
    - sleep
    args:
    - infinity
'''
        }
    }
    
    stages {
        stage('Build') {
            steps {
                container('maven') {
                    sh 'mvn clean package'
                }
            }
        }
        
        stage('Security Scan') {
            steps {
                container('zap') {
                    sh '''
                        zap.sh -daemon -port 8090 -host 0.0.0.0 -config api.disablekey=true &
                        sleep 10
                        zap-cli quick-scan -s all http://your-app:8080
                        zap-cli report -o zap-report.html -f html
                    '''
                }
            }
        }
    }
}
```

## Common ZAP API Endpoints

All endpoints are accessed via: `http://zap.zap.svc.cluster.local:8080`

### Information
- **Get Version**: `/JSON/core/view/version/`
- **Get Alerts**: `/JSON/core/view/alerts/`
- **Get Alert Summary**: `/JSON/core/view/alertsSummary/`

### Scanning
- **Spider Scan**: `/JSON/spider/action/scan/?url=<target>`
- **Spider Status**: `/JSON/spider/view/status/`
- **Active Scan**: `/JSON/ascan/action/scan/?url=<target>`
- **Active Scan Status**: `/JSON/ascan/view/status/`

### Reporting
- **HTML Report**: `/OTHER/core/other/htmlreport/`
- **XML Report**: `/OTHER/core/other/xmlreport/`
- **JSON Report**: `/JSON/core/view/alerts/`

## Example: Complete Security Pipeline

```groovy
pipeline {
    agent any
    
    environment {
        ZAP_HOST = 'zap.zap.svc.cluster.local:8080'
        APP_URL = 'http://myapp.default.svc.cluster.local'
    }
    
    stages {
        stage('Deploy App') {
            steps {
                sh 'kubectl apply -f k8s/deployment.yaml'
                sh 'kubectl wait --for=condition=ready pod -l app=myapp --timeout=60s'
            }
        }
        
        stage('ZAP Spider') {
            steps {
                script {
                    sh "curl 'http://${ZAP_HOST}/JSON/spider/action/scan/?url=${APP_URL}'"
                    
                    def spiderComplete = false
                    while (!spiderComplete) {
                        def status = sh(
                            script: "curl -s 'http://${ZAP_HOST}/JSON/spider/view/status/'",
                            returnStdout: true
                        )
                        
                        if (status.contains('"status":"100"')) {
                            spiderComplete = true
                        } else {
                            echo "Spider in progress..."
                            sleep 5
                        }
                    }
                    echo "Spider completed"
                }
            }
        }
        
        stage('ZAP Active Scan') {
            steps {
                script {
                    sh "curl 'http://${ZAP_HOST}/JSON/ascan/action/scan/?url=${APP_URL}'"
                    
                    def scanComplete = false
                    while (!scanComplete) {
                        def status = sh(
                            script: "curl -s 'http://${ZAP_HOST}/JSON/ascan/view/status/'",
                            returnStdout: true
                        )
                        
                        if (status.contains('"status":"100"')) {
                            scanComplete = true
                        } else {
                            echo "Active scan in progress..."
                            sleep 10
                        }
                    }
                    echo "Active scan completed"
                }
            }
        }
        
        stage('Generate Reports') {
            steps {
                sh "curl 'http://${ZAP_HOST}/OTHER/core/other/htmlreport/' > zap-report.html"
                sh "curl 'http://${ZAP_HOST}/JSON/core/view/alerts/' > zap-alerts.json"
                
                archiveArtifacts artifacts: 'zap-*.html,zap-*.json'
                
                publishHTML([
                    reportDir: '.',
                    reportFiles: 'zap-report.html',
                    reportName: 'ZAP Security Report'
                ])
            }
        }
        
        stage('Quality Gate') {
            steps {
                script {
                    def alerts = readJSON file: 'zap-alerts.json'
                    def highRisk = alerts.alerts.findAll { it.risk == 'High' }.size()
                    
                    echo "High-risk vulnerabilities found: ${highRisk}"
                    
                    if (highRisk > 0) {
                        error("Security gate failed: ${highRisk} high-risk vulnerabilities detected!")
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo 'Cleaning up...'
        }
    }
}
```

## Best Practices

1. **Run ZAP scans on every pull request**
   - Catch vulnerabilities early in the development cycle

2. **Set security thresholds**
   - Fail builds with high-risk vulnerabilities
   - Warn on medium-risk issues

3. **Use passive scanning for fast feedback**
   - Passive scans are quick and don't attack the application
   - Use active scans for thorough testing before release

4. **Configure scan policies**
   - Customize which tests ZAP runs
   - Exclude false positives

5. **Integrate with quality gates**
   - Use SonarQube/quality gates along with ZAP
   - Combine SAST + DAST for comprehensive security

## Testing ZAP from Command Line

```bash
# Check ZAP is running
kubectl exec -n zap deployment/zap -- curl http://127.0.0.1:8080/JSON/core/view/version/

# Spider a website
kubectl exec -n zap deployment/zap -- curl 'http://127.0.0.1:8080/JSON/spider/action/scan/?url=http://example.com'

# Get scan results
kubectl exec -n zap deployment/zap -- curl http://127.0.0.1:8080/JSON/core/view/alerts/

# Generate report
kubectl exec -n zap deployment/zap -- curl 'http://127.0.0.1:8080/OTHER/core/other/htmlreport/' > zap-report.html
```

## Accessing ZAP Dashboard

- **URL**: http://zap.local
- **Purpose**: View ZAP documentation and usage instructions
- **API**: Use kubectl commands to interact with ZAP

## Next Steps

1. Install ZAP Jenkins plugin (optional)
2. Create a sample pipeline using the examples above
3. Configure security thresholds for your project
4. Integrate ZAP scans into your CI/CD workflow
5. Set up automated reports and notifications

## Additional Resources

- ZAP API Documentation: https://www.zaproxy.org/docs/api/
- ZAP Jenkins Plugin: https://plugins.jenkins.io/zap/
- OWASP ZAP User Guide: https://www.zaproxy.org/docs/
