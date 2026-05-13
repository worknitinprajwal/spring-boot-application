# Complete CloudBees CI/CD Guide
## Spring Boot Application with Multi-Cluster Kubernetes, Testing Stack & GitOps

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Quick Start](#quick-start)
4. [Multi-Cluster Setup](#multi-cluster-setup)
5. [Cross-Cluster Connectivity](#cross-cluster-connectivity)
6. [ArgoCD Multi-Cluster Setup](#argocd-multi-cluster-setup)
7. [Pipeline Flow](#pipeline-flow)
8. [Testing Tools Integration](#testing-tools-integration)
   - [OWASP ZAP - Security Testing](#owasp-zap-security-testing)
   - [Apache JMeter - Performance Testing](#apache-jmeter-performance-testing)
   - [SoapUI - API Testing](#soapui-api-testing)
9. [Complete Testing Stack](#complete-testing-stack)
10. [Troubleshooting](#troubleshooting)
11. [Best Practices](#best-practices)

---

## Overview

This guide provides complete documentation for building a production-ready CI/CD pipeline with:

- ✅ **Spring Boot Application** - REST API with Kubernetes deployment
- ✅ **Multi-Cluster Kubernetes** - Deploy to dev/test/prod clusters
- ✅ **CloudBees CI** - Complete CI/CD pipeline with testing
- ✅ **ArgoCD** - GitOps-based continuous deployment
- ✅ **Comprehensive Testing** - Security (ZAP), Performance (JMeter), Functional (SoapUI)
- ✅ **Cross-Cluster Testing** - Test apps on different clusters
- ✅ **Reports & Metrics** - Published to CloudBees Unify

### What We're Building

A complete CI/CD pipeline that:
1. Builds Spring Boot application with Maven
2. Creates Docker image and pushes to DockerHub
3. Updates Helm chart with new image tag (GitOps)
4. Deploys to selected environment (dev/test/prod) on different clusters
5. Runs comprehensive testing (SoapUI, JMeter, ZAP)
6. Publishes reports to CloudBees Unify

---

## Architecture

### Cluster Setup

```
┌────────────────────────────────────────────────┐
│   Cluster: igs (Main/Control Plane)           │
│   IP: 172.18.0.2                               │
│                                                │
│  ┌──────────────┐  ┌────────────────┐        │
│  │ CloudBees CI │  │    ArgoCD      │        │
│  └──────────────┘  └────────────────┘        │
│                                                │
│  ┌─────┐ ┌────────┐ ┌────────┐               │
│  │ ZAP │ │ JMeter │ │ SoapUI │               │
│  └─────┘ └────────┘ └────────┘               │
└────────────────────────────────────────────────┘
           │ │ │
           │ │ │  Cross-Cluster Communication
           │ │ │  (NodePort + Docker Network)
           ▼ ▼ ▼
┌────────────────────┐  ┌────────────────────┐
│  Cluster: kind-dev │  │ Cluster: kind-test │
│  IP: 172.18.0.3    │  │ IP: 172.18.0.4     │
│                    │  │                    │
│  ┌──────────────┐ │  │  ┌──────────────┐ │
│  │ Spring Boot  │ │  │  │ Spring Boot  │ │
│  │ NodePort:30080│ │  │ │ NodePort:30081│ │
│  └──────────────┘ │  │  └──────────────┘ │
└────────────────────┘  └────────────────────┘
```

### Complete Workflow

```
Developer pushes code
         ↓
CloudBees CI Pipeline:
  1. Checkout & Build (Maven)
  2. Docker Build & Push to DockerHub
  3. Update Helm Chart (values.yaml)
  4. Commit & Push to Git
         ↓
User Selects Environment (dev/test/prod)
         ↓
ArgoCD Detects Git Change
         ↓
ArgoCD Deploys to Selected Cluster
  - dev → kind-kind-dev cluster
  - test → kind-kind-test cluster
  - prod → igs cluster
         ↓
Verify Deployment Success
  - Check pods running
  - Test health endpoint
  - Verify connectivity
         ↓
Run Tests (ONLY if deployment successful):
  - SoapUI (Functional API tests)
  - JMeter (Performance tests)
  - ZAP (Security scan - Active mode, automated)
         ↓
Publish Reports
  - Jenkins HTML reports
  - JUnit test results
  - CloudBees Unify metrics
         ↓
✅ Pipeline Complete!
```

---

## Quick Start

### Prerequisites

- Docker Desktop with Kind
- kubectl
- helm
- argocd CLI
- Access to CloudBees CI
- DockerHub account
- GitHub account

### 1. Create Additional Kind Clusters

```bash
# Create dev cluster
kind create cluster --name kind-dev --config - <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  extraPortMappings:
  - containerPort: 80
    hostPort: 8081
  - containerPort: 443
    hostPort: 8443
- role: worker
EOF

# Create test cluster
kind create cluster --name kind-test --config - <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  extraPortMappings:
  - containerPort: 80
    hostPort: 8082
  - containerPort: 443
    hostPort: 8444
- role: worker
EOF

# Verify
kubectl config get-contexts
```

### 2. Install Ingress Controllers

```bash
# Install on kind-dev
kubectl config use-context kind-kind-dev
helm upgrade --install ingress-nginx ingress-nginx \
  --repo https://kubernetes.github.io/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.hostPort.enabled=true \
  --set controller.service.type=NodePort

# Install on kind-test
kubectl config use-context kind-kind-test
helm upgrade --install ingress-nginx ingress-nginx \
  --repo https://kubernetes.github.io/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.hostPort.enabled=true \
  --set controller.service.type=NodePort

# Back to main cluster
kubectl config use-context kind-igs
```

### 3. Register Clusters with ArgoCD

```bash
# Get ArgoCD password
ARGOCD_PWD=$(kubectl get secret argocd-initial-admin-secret -n argocd -o jsonpath='{.data.password}' | base64 -d)

# Login
argocd login argocd.local --username admin --password $ARGOCD_PWD --insecure

# Register clusters
argocd cluster add kind-kind-dev --name dev-cluster
argocd cluster add kind-kind-test --name test-cluster

# Verify
argocd cluster list
```

### 4. Configure Jenkins Credentials

In CloudBees CI (http://cloudbees-ci.local/cjoc):

**DockerHub Credentials:**
- Manage Jenkins → Credentials → Add Credentials
- ID: `dockerhub-credentials`
- Username: your-dockerhub-username
- Password: your-dockerhub-token

**GitHub Credentials:**
- ID: `github-credentials`
- Username: your-github-username
- Password: your-github-token

### 5. Update Application Files

```bash
cd /Users/analla/Downloads/cloudbees/IGS/sample-spring-app

# Update Jenkinsfile
sed -i 's/your-dockerhub-username/ACTUAL_USERNAME/' Jenkinsfile-MultiCluster

# Update Helm values
sed -i 's/your-dockerhub-username/ACTUAL_USERNAME/' k8s/helm-chart/values.yaml

# Push to GitHub
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/your-username/your-repo.git
git push -u origin main
```

### 6. Create Jenkins Pipeline

1. New Item → Pipeline
2. Name: `fitness-tracker-pipeline`
3. Pipeline → Definition: Pipeline script from SCM
4. SCM: Git
5. Repository URL: your repo URL
6. Script Path: `sample-spring-app/Jenkinsfile-MultiCluster`
7. Save

### 7. Run the Pipeline

1. Click "Build with Parameters"
2. Select environment: dev/test/prod
3. Enable tests (Security/Performance)
4. Click "Build"

---

## Multi-Cluster Setup

### Why Multi-Cluster?

- **Isolation**: Dev/test/prod environments completely separated
- **Resource Management**: Different resource allocations per environment
- **Security**: Prod isolated from dev/test
- **Realistic Testing**: Test in environment similar to production

### Cluster Roles

| Cluster | Role | Contains | NodePort |
|---------|------|----------|----------|
| **igs** | Control Plane | CloudBees CI, ArgoCD, Testing Tools | N/A |
| **kind-dev** | Development | Spring Boot app (dev) | 30080 |
| **kind-test** | QA/Testing | Spring Boot app (test) | 30081 |
| **kind-igs** | Production | Spring Boot app (prod) | ClusterIP |

---

## Cross-Cluster Connectivity

### The Challenge

```
Question: ZAP, ArgoCD, CloudBees are on cluster "igs",
          Spring Boot deploys to "kind-dev" and "kind-test".
          How will connectivity work?
```

### The Solution: NodePort + Docker Network

**Kind clusters share the Docker network!** They can communicate directly.

### How It Works

#### 1. Deploy App with NodePort

```yaml
# Dev cluster service (values-dev.yaml)
service:
  type: NodePort
  nodePort: 30080  # Fixed port for dev

# Test cluster service (values-test.yaml)
service:
  type: NodePort
  nodePort: 30081  # Fixed port for test
```

#### 2. Get Cluster IPs

```bash
# Get dev cluster IP (it's a Docker container!)
DEV_IP=$(docker inspect kind-dev-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
# Result: 172.18.0.3

# Get test cluster IP
TEST_IP=$(docker inspect kind-test-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
# Result: 172.18.0.4
```

#### 3. Access from Testing Tools

```bash
# From igs cluster, ZAP/JMeter/SoapUI can access:
# Dev app: http://172.18.0.3:30080
# Test app: http://172.18.0.4:30081

# Test connectivity
kubectl run test --image=curlimages/curl --rm -it --context kind-igs -- \
  curl http://172.18.0.3:30080/actuator/health
```

#### 4. Jenkins Pipeline Handles It Automatically

```groovy
stage('Get Application URL') {
    steps {
        script {
            switch(params.DEPLOY_ENV) {
                case 'dev':
                    def devIp = sh(script: "docker inspect kind-dev-control-plane ...", returnStdout: true).trim()
                    env.APP_URL = "http://${devIp}:30080"
                    break
                case 'test':
                    def testIp = sh(script: "docker inspect kind-test-control-plane ...", returnStdout: true).trim()
                    env.APP_URL = "http://${testIp}:30081"
                    break
            }
            
            // Testing tools use APP_URL to reach the app
            echo "Testing application at: ${env.APP_URL}"
        }
    }
}
```

### Network Diagram

```
┌────────────────────────────────────────────────┐
│        Docker Network (172.18.0.0/16)          │
│                                                │
│  igs cluster (172.18.0.2)                     │
│  ├─ ZAP, JMeter, SoapUI                       │
│  │  Can access:                               │
│  │  - http://172.18.0.3:30080 (dev app)       │
│  │  - http://172.18.0.4:30081 (test app)      │
│                                                │
│  kind-dev (172.18.0.3)                        │
│  └─ Spring Boot on NodePort 30080             │
│                                                │
│  kind-test (172.18.0.4)                       │
│  └─ Spring Boot on NodePort 30081             │
└────────────────────────────────────────────────┘
```

### Why This Works

1. **Kind clusters = Docker containers**
2. **Docker containers share network**
3. **NodePort exposes service on node IP**
4. **Node IP = Docker container IP**
5. **Accessible from any container on same network!**

---

## ArgoCD Multi-Cluster Setup

### Key Concepts

**Important**: ArgoCD does NOT automatically sync to all clusters!

- **One ArgoCD instance** (running on `igs` cluster)
- **Multiple cluster contexts** registered with ArgoCD
- **Separate Applications** for each environment
- **RBAC policies** control who can deploy where

### How ArgoCD Connectivity Works

```bash
# When you run:
argocd cluster add kind-kind-dev --name dev-cluster

# ArgoCD:
1. Creates a ServiceAccount in kind-dev cluster
2. Gets credentials (token, certificate)
3. Stores in Secret on igs cluster
4. Uses Kubernetes API to deploy

# ArgoCD can now deploy to kind-dev from igs!
```

**No special network setup needed** - it's built-in Kubernetes!

### Create Environment-Specific Applications

```yaml
# argocd-apps/dev-application.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: fitness-tracker-dev
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/your-username/your-repo.git
    targetRevision: main
    path: sample-spring-app/k8s/helm-chart
    helm:
      valueFiles:
        - ../../overlays/dev/values-dev.yaml
  destination:
    name: dev-cluster  # ONLY deploys to dev cluster
    namespace: dev
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

```yaml
# argocd-apps/test-application.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: fitness-tracker-test
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/your-username/your-repo.git
    targetRevision: main
    path: sample-spring-app/k8s/helm-chart
    helm:
      valueFiles:
        - ../../overlays/test/values-test.yaml
  destination:
    name: test-cluster  # ONLY deploys to test cluster
    namespace: test
  syncPolicy:
    automated:
      prune: false
      selfHeal: false
    syncOptions:
      - CreateNamespace=true
```

### Deploy Applications

```bash
kubectl apply -f argocd-apps/dev-application.yaml
kubectl apply -f argocd-apps/test-application.yaml

# Check status
argocd app list
argocd app get fitness-tracker-dev
```

### Access Control (RBAC)

```yaml
# argocd-rbac-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-rbac-cm
  namespace: argocd
data:
  policy.csv: |
    # Developers can only sync dev
    p, role:developer, applications, sync, */fitness-tracker-dev, allow
    
    # QA team can sync test
    p, role:qa, applications, sync, */fitness-tracker-test, allow
    
    # Only ops can sync prod
    p, role:ops, applications, *, */fitness-tracker-prod, allow
```

---

## Pipeline Flow

### Visual Flow Diagram

```
┌──────────────────┐
│  1. Checkout     │
└────────┬─────────┘
         ▼
┌──────────────────┐
│  2. Build &      │
│  Unit Tests      │
└────────┬─────────┘
         ▼
┌──────────────────┐
│  3. Docker       │
│  Build & Push    │
└────────┬─────────┘
         ▼
┌──────────────────┐
│  4. Update Helm  │
│  Chart & Push    │
└────────┬─────────┘
         ▼
┌──────────────────────────────────┐
│  5. User Selects Environment     │
│  (dev / test / prod)             │
└────────┬─────────────────────────┘
         ▼
┌──────────────────────────────────┐
│  6. Deploy to Selected Cluster   │
│  helm upgrade --install --wait   │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│  7. Verify Deployment Success ✅ │
│  - Check pods running            │
│  - Test health endpoint          │
│  IF FAILS → ❌ STOP PIPELINE     │
└────────┬─────────────────────────┘
         │ ✅ Success
         ▼
┌──────────────────────────────────┐
│  8. Get App URL for Testing      │
│  - Get cluster IP                │
│  - Build URL: http://IP:NodePort │
└────────┬─────────────────────────┘
         ▼
┌──────────────────────────────────┐
│  9. Verify Connectivity ✅       │
│  - Test from igs cluster         │
│  IF FAILS → ❌ STOP PIPELINE     │
└────────┬─────────────────────────┘
         │ ✅ Verified
         ├─────────────┬─────────────┐
         ▼             ▼             ▼
┌────────────┐ ┌────────────┐ ┌────────────┐
│ 10a.SoapUI │ │ 10b.JMeter │ │ 10c. ZAP   │
│ API Tests  │ │ Perf Tests │ │ Security   │
└────────┬───┘ └──────┬─────┘ └──────┬─────┘
         └─────────┬──────────────────┘
                   ▼
┌──────────────────────────────────┐
│  11. Publish Reports             │
└──────────────────────────────────┘
```

### Key Gates

**Gate 1: Build Success**
- If build fails → Pipeline STOPS

**Gate 2: Deployment Success**
- If `helm upgrade --install --wait` fails → Pipeline STOPS

**Gate 3: Deployment Verification**
- If pods not Running → Pipeline STOPS
- If replicas not ready → Pipeline STOPS
- If health check fails → Pipeline STOPS

**Gate 4: Connectivity Verification**
- If testing tools can't reach app → Pipeline STOPS

**Gate 5: Testing Phase**
- **Only runs if ALL previous gates passed!**

### Answer to Key Question

> "Once the app is deployed successfully, then new workflow should start to perform testing?"

**YES! That's exactly how it works!**

The pipeline has explicit verification gates:
1. Deploy stage uses `--wait` (waits for deployment)
2. Verify stage checks pod status and health
3. Get URL stage verifies connectivity
4. **Only then** do testing stages run

If deployment fails at any point → Testing stages are SKIPPED! ✅

---

## Testing Tools Integration

### Overview

| Tool | Purpose | When | Traffic Simulation? |
|------|---------|------|---------------------|
| **SoapUI** | Functional API testing | After deployment | No - direct API calls |
| **JMeter** | Performance testing | After functional tests | No - generates load |
| **ZAP** | Security scanning | After performance tests | No - Active scanning |

---

## OWASP ZAP Security Testing

### What is ZAP?

OWASP ZAP (Zed Attack Proxy) finds security vulnerabilities in web applications.

**Detects:**
- SQL Injection
- Cross-Site Scripting (XSS)
- CSRF attacks
- Insecure headers
- Authentication issues
- SSL/TLS problems

### Deployment Details

- **Namespace**: `zap`
- **Service**: `zap.zap.svc.cluster.local:8080`
- **Dashboard**: http://zap.local
- **Version**: 2.17.0

### How ZAP Works (Active Scanning)

**Question**: "How does ZAP monitor traffic? Need to simulate it?"

**Answer**: NO traffic simulation needed!

ZAP Active Scanning:
1. **Spider**: Crawls your app, discovers all endpoints automatically
2. **Active Scan**: Sends attack payloads to test for vulnerabilities
3. **Reports**: Generates vulnerability report

```groovy
stage('ZAP Security Scan') {
    steps {
        // Spider discovers all endpoints
        sh "curl '${ZAP_URL}/JSON/spider/action/scan/?url=${APP_URL}'"
        
        // Wait for spider
        waitUntil {
            def status = sh(script: "curl -s '${ZAP_URL}/JSON/spider/view/status/'", returnStdout: true)
            return status.contains('"status":"100"')
        }
        
        // Active scan tests for vulnerabilities
        sh "curl '${ZAP_URL}/JSON/ascan/action/scan/?url=${APP_URL}'"
        
        // Wait for scan
        waitUntil {
            def status = sh(script: "curl -s '${ZAP_URL}/JSON/ascan/view/status/'", returnStdout: true)
            return status.contains('"status":"100"')
        }
        
        // Get results
        sh "curl '${ZAP_URL}/JSON/core/view/alerts/' > zap-alerts.json"
        sh "curl '${ZAP_URL}/OTHER/core/other/htmlreport/' > zap-report.html"
        
        publishHTML([
            reportDir: '.',
            reportFiles: 'zap-report.html',
            reportName: 'ZAP Security Report'
        ])
    }
}
```

### Common ZAP API Endpoints

```bash
# Base URL
ZAP_URL="http://zap.zap.svc.cluster.local:8080"

# Spider scan
curl "${ZAP_URL}/JSON/spider/action/scan/?url=http://myapp"

# Spider status
curl "${ZAP_URL}/JSON/spider/view/status/"

# Active scan
curl "${ZAP_URL}/JSON/ascan/action/scan/?url=http://myapp"

# Active scan status
curl "${ZAP_URL}/JSON/ascan/view/status/"

# Get alerts
curl "${ZAP_URL}/JSON/core/view/alerts/"

# HTML report
curl "${ZAP_URL}/OTHER/core/other/htmlreport/" > report.html
```

### Security Quality Gates

```groovy
stage('Security Quality Gate') {
    steps {
        script {
            def highRisk = sh(
                script: "curl -s '${ZAP_URL}/JSON/core/view/alerts/?risk=High' | grep -c '\"risk\":\"High\"' || echo '0'",
                returnStdout: true
            ).trim().toInteger()
            
            if (highRisk > 0) {
                error("❌ ${highRisk} high-risk vulnerabilities found!")
            }
        }
    }
}
```

---

## Apache JMeter Performance Testing

### What is JMeter?

Apache JMeter is a performance and load testing tool.

**Tests:**
- Load testing (simulate multiple users)
- Stress testing (find breaking point)
- Endurance testing (long-running tests)
- API, web apps, databases

**Metrics:**
- Response times
- Throughput (requests/second)
- Error rates
- Latency percentiles (p95, p99)

### Deployment Details

- **Namespace**: `jmeter`
- **Service**: `jmeter.jmeter.svc.cluster.local:8080`
- **Storage**: NFS 5Gi

### Pipeline Integration

```groovy
stage('JMeter Performance Test') {
    steps {
        script {
            def jmeterPod = sh(
                script: "kubectl get pod -n jmeter -l app=jmeter -o jsonpath='{.items[0].metadata.name}'",
                returnStdout: true
            ).trim()
            
            // Create test plan
            writeFile file: 'load-test.jmx', text: """<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan>
    </TestPlan>
    <hashTree>
      <ThreadGroup>
        <intProp name="ThreadGroup.num_threads">50</intProp>
        <intProp name="ThreadGroup.ramp_time">10</intProp>
        <longProp name="ThreadGroup.duration">60</longProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy>
          <stringProp name="HTTPSampler.domain">\${__P(host)}</stringProp>
          <stringProp name="HTTPSampler.port">\${__P(port)}</stringProp>
          <stringProp name="HTTPSampler.path">/actuator/health</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>"""
            
            // Upload and run
            sh "kubectl cp load-test.jmx jmeter/${jmeterPod}:/tmp/test.jmx"
            sh """
                kubectl exec -n jmeter ${jmeterPod} -- \\
                    jmeter -n -t /tmp/test.jmx \\
                    -l /tmp/results.jtl \\
                    -e -o /tmp/report \\
                    -Jhost=${env.APP_HOST} \\
                    -Jport=${env.APP_PORT}
            """
            
            // Collect results
            sh "kubectl cp jmeter/${jmeterPod}:/tmp/results.jtl ./jmeter-results.jtl"
            sh "kubectl exec -n jmeter ${jmeterPod} -- tar czf /tmp/report.tar.gz -C /tmp report"
            sh "kubectl cp jmeter/${jmeterPod}:/tmp/report.tar.gz ./jmeter-report.tar.gz"
            sh "tar xzf jmeter-report.tar.gz"
            
            publishHTML([
                reportDir: 'report',
                reportFiles: 'index.html',
                reportName: 'JMeter Performance Report'
            ])
        }
    }
}
```

### JMeter CLI Options

```bash
jmeter -n -t <test.jmx> -l <results.jtl> [options]

Options:
  -n                    Non-GUI mode
  -t <file>            Test plan file
  -l <file>            Results file
  -e                   Generate report
  -o <folder>          Report output folder
  -J<prop>=<value>     Set JMeter property
```

### Performance Quality Gates

```groovy
stage('Performance Quality Gate') {
    steps {
        script {
            def avgResponseTime = sh(
                script: """
                    kubectl exec -n jmeter ${jmeterPod} -- awk -F',' '
                        NR>1 {sum+=\$2; count++}
                        END {print int(sum/count)}
                    ' /tmp/results.jtl
                """,
                returnStdout: true
            ).trim().toInteger()
            
            echo "Average Response Time: ${avgResponseTime}ms"
            
            if (avgResponseTime > 2000) {
                unstable("⚠️ Performance degraded: ${avgResponseTime}ms > 2000ms")
            }
        }
    }
}
```

---

## SoapUI API Testing

### What is SoapUI?

SoapUI is a comprehensive API testing tool for REST and SOAP APIs.

**Features:**
- Functional testing
- Request/response validation
- JSON/XML validation
- Data-driven testing
- JUnit-style reports

### Deployment Details

- **Namespace**: `soapui`
- **Service**: `soapui.soapui.svc.cluster.local:8080`

### Pipeline Integration

```groovy
stage('SoapUI API Tests') {
    steps {
        script {
            def soapuiPod = sh(
                script: "kubectl get pod -n soapui -l app=soapui -o jsonpath='{.items[0].metadata.name}'",
                returnStdout: true
            ).trim()
            
            // Create test project
            writeFile file: 'api-tests.xml', text: """<?xml version="1.0" encoding="UTF-8"?>
<con:soapui-project name="API Tests" xmlns:con="http://eviware.com/soapui/config">
  <con:interface name="REST API" type="rest">
    <con:resource name="Health" path="/actuator/health">
      <con:method name="GET">
        <con:request name="Request">
          <con:endpoint>${env.APP_URL}</con:endpoint>
        </con:request>
      </con:method>
    </con:resource>
  </con:interface>
  <con:testSuite name="Tests">
    <con:testCase name="Health Check">
      <con:testStep type="restrequest" name="Check">
        <con:config>
          <restRequest>
            <method>GET</method>
            <endpoint>${env.APP_URL}</endpoint>
            <resource>/actuator/health</resource>
          </restRequest>
          <assertion type="Valid HTTP Status Codes">
            <configuration><codes>200</codes></configuration>
          </assertion>
        </config>
      </con:testStep>
    </con:testCase>
  </con:testSuite>
</con:soapui-project>"""
            
            // Upload and run
            sh "kubectl cp api-tests.xml soapui/${soapuiPod}:/tmp/tests.xml"
            sh """
                kubectl exec -n soapui ${soapuiPod} -- \\
                    testrunner.sh -r -j -f /tmp/reports /tmp/tests.xml
            """
            
            // Collect results
            sh "kubectl exec -n soapui ${soapuiPod} -- tar czf /tmp/reports.tar.gz -C /tmp reports"
            sh "kubectl cp soapui/${soapuiPod}:/tmp/reports.tar.gz ./soapui-reports.tar.gz"
            sh "tar xzf soapui-reports.tar.gz"
            
            junit allowEmptyResults: true, testResults: 'reports/TEST-*.xml'
        }
    }
}
```

### SoapUI CLI Options

```bash
testrunner.sh [options] <project.xml>

Options:
  -s <suite>       Test suite to run
  -c <case>        Test case to run
  -f <folder>      Output folder
  -r               Print reports
  -j               JUnit-style reports
  -e <endpoint>    Override endpoint
```

---

## Complete Testing Stack

### Testing Strategy

```
┌─────────────┐
│   Build     │
└──────┬──────┘
       ▼
┌─────────────┐
│   Deploy    │ ← helm upgrade --install --wait
└──────┬──────┘
       ▼
┌─────────────┐
│  SoapUI     │ ← API Functional Tests
│  REST/SOAP  │   - Validate endpoints
└──────┬──────┘   - Check responses
       ▼
┌─────────────┐
│   JMeter    │ ← Performance Tests
│  Load Test  │   - Response times
└──────┬──────┘   - Throughput
       ▼
┌─────────────┐
│  OWASP ZAP  │ ← Security Scan
│  Security   │   - Vulnerabilities
└──────┬──────┘   - OWASP Top 10
       ▼
┌─────────────┐
│Quality Gate │ ← Pass/Fail Decision
└──────┬──────┘
       ▼
┌─────────────┐
│   ArgoCD    │ ← GitOps Deployment
│  Deploy Prod│   (if all tests pass)
└─────────────┘
```

### Quality Gates & Thresholds

**API Tests (SoapUI):**
- ✅ Pass: All assertions succeed, 0 failures
- ❌ Fail: Any assertion failure

**Performance Tests (JMeter):**
- ✅ Pass: Avg response < 2000ms, Error rate < 1%
- ⚠️ Unstable: Avg response < 3000ms, Error rate < 5%
- ❌ Fail: Exceeds unstable thresholds

**Security Tests (ZAP):**
- ✅ Pass: 0 high-risk vulnerabilities
- ⚠️ Unstable: 1-2 medium-risk issues
- ❌ Fail: Any high-risk vulnerability

### Quick Commands

```bash
# Get pod names
export JMETER_POD=$(kubectl get pod -n jmeter -l app=jmeter -o jsonpath='{.items[0].metadata.name}')
export SOAPUI_POD=$(kubectl get pod -n soapui -l app=soapui -o jsonpath='{.items[0].metadata.name}')
export ZAP_URL="http://zap.zap.svc.cluster.local:8080"

# Run tests manually
kubectl exec -n soapui $SOAPUI_POD -- testrunner.sh /tmp/project.xml
kubectl exec -n jmeter $JMETER_POD -- jmeter -n -t /tmp/test.jmx -l /tmp/results.jtl
curl "${ZAP_URL}/JSON/spider/action/scan/?url=http://myapp"
```

---

## Troubleshooting

### Deployment Issues

**Pods not starting:**
```bash
kubectl get pods -n <namespace> --context <cluster>
kubectl describe pod <pod-name> -n <namespace> --context <cluster>
kubectl logs <pod-name> -n <namespace> --context <cluster>
```

**Helm deployment fails:**
```bash
helm list -n <namespace> --kube-context <cluster>
helm history <release> -n <namespace> --kube-context <cluster>
helm rollback <release> <revision> -n <namespace> --kube-context <cluster>
```

### Connectivity Issues

**Test cluster IP:**
```bash
# Get cluster IP
DEV_IP=$(docker inspect kind-dev-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
echo $DEV_IP

# Test from igs cluster
kubectl run test --image=curlimages/curl --rm -it --context kind-igs -- \
  curl http://$DEV_IP:30080/actuator/health
```

**Check NodePort service:**
```bash
kubectl get svc -n <namespace> --context <cluster>
kubectl describe svc <service-name> -n <namespace> --context <cluster>
```

### Testing Tool Issues

**SoapUI pod not found:**
```bash
kubectl get pods -n soapui
kubectl logs -n soapui deployment/soapui
```

**JMeter test fails:**
```bash
kubectl logs -n jmeter deployment/jmeter
kubectl exec -n jmeter deployment/jmeter -- ls -la /tmp/
```

**ZAP not responding:**
```bash
kubectl get pods -n zap
kubectl logs -n zap deployment/zap
curl http://zap.zap.svc.cluster.local:8080/JSON/core/view/version/
```

### ArgoCD Issues

**Application not syncing:**
```bash
argocd app list
argocd app get <app-name>
argocd app sync <app-name>
kubectl logs -n argocd deployment/argocd-application-controller
```

**Cluster not registered:**
```bash
argocd cluster list
argocd cluster add <context-name> --name <cluster-name>
```

---

## Best Practices

### Pipeline Design

1. **Fail Fast**: Use explicit gates, stop on first failure
2. **Parallel Testing**: Run independent tests in parallel
3. **Resource Cleanup**: Always clean up temporary resources
4. **Retry Logic**: Retry transient failures (networking, etc.)
5. **Timeout**: Set reasonable timeouts for all stages

### Testing Strategy

1. **Pyramid Approach**: Many unit tests, fewer integration tests, few E2E tests
2. **Test in Production-Like**: Test environment should mirror production
3. **Automate Everything**: All tests should run automatically
4. **Quality Gates**: Set clear pass/fail criteria
5. **Fast Feedback**: Keep test execution time reasonable

### Security

1. **Secrets Management**: Use Jenkins credentials, never hardcode
2. **RBAC**: Restrict who can deploy where (ArgoCD Projects)
3. **Image Scanning**: Scan Docker images for vulnerabilities
4. **Regular Scans**: Run ZAP scans on every PR
5. **Security First**: Fail build on high-risk vulnerabilities

### GitOps

1. **Single Source of Truth**: All config in Git
2. **Declarative**: Describe desired state, not steps
3. **Version Controlled**: Track all changes
4. **Automated Sync**: ArgoCD auto-deploys from Git
5. **Easy Rollback**: Git revert = instant rollback

### Monitoring

1. **Health Checks**: Implement proper health/readiness probes
2. **Logging**: Centralized logging for all services
3. **Metrics**: Expose Prometheus metrics
4. **Alerts**: Alert on failures, anomalies
5. **Dashboards**: CloudBees Unify dashboards for CI/CD metrics

---

## Summary

You now have a **complete, production-ready CI/CD pipeline**:

✅ **Multi-Cluster Kubernetes** - Deploy to dev/test/prod on different clusters  
✅ **Cross-Cluster Testing** - Test apps across clusters via NodePort  
✅ **CloudBees CI** - Complete pipeline with all testing integrated  
✅ **ArgoCD** - GitOps-based continuous deployment  
✅ **Comprehensive Testing**:
  - 🔒 **Security**: OWASP ZAP (automated, no traffic simulation)
  - ⚡ **Performance**: JMeter (load & stress testing)
  - ✅ **Functional**: SoapUI (API testing)  
✅ **Quality Gates** - Fail fast on issues  
✅ **Reports & Metrics** - Published to CloudBees Unify  

### Key Takeaways

1. **ArgoCD** does NOT sync to all clusters - each Application targets ONE cluster
2. **Cross-cluster connectivity** works via NodePort + Docker network (simple!)
3. **Testing only runs** if deployment succeeds (explicit verification gates)
4. **ZAP Active Scanning** requires NO traffic simulation (fully automated)
5. **Jenkins parameters** let you select which environment to deploy to

### Next Steps

1. ✅ Setup complete - all components running
2. Run first pipeline build
3. Review test reports
4. Configure RBAC for ArgoCD
5. Set up CloudBees Unify dashboards
6. Configure notifications (Slack/email)
7. Add more applications

---

**Complete CI/CD pipeline with comprehensive testing - ready for production!** 🎉
