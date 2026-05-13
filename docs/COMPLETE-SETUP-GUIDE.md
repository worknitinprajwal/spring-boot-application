# Complete Setup Guide - Spring Boot App with CI/CD

## What We're Building

A complete CI/CD pipeline that:
1. ✅ Builds Spring Boot application
2. ✅ Creates Docker image and pushes to DockerHub
3. ✅ Updates Helm chart with new image tag
4. ✅ Deploys to selected environment (dev/test/prod) using ArgoCD
5. ✅ Runs comprehensive testing (SoapUI, JMeter, ZAP)
6. ✅ Publishes reports to CloudBees Unify

## Your Questions - Answered

### Q1: "How to restrict ArgoCD? Does it sync to both clusters if we add both contexts?"

**Answer**: NO, ArgoCD does NOT automatically sync to all clusters!

- Each ArgoCD **Application** targets **ONE specific cluster**
- Adding multiple clusters to ArgoCD = giving it PERMISSION
- Actual deployment = you create separate Applications for each cluster

Example:
```yaml
# Application 1 - ONLY deploys to dev cluster
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: fitness-tracker-dev
spec:
  destination:
    name: dev-cluster    # ONLY dev
    namespace: dev

# Application 2 - ONLY deploys to test cluster
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: fitness-tracker-test
spec:
  destination:
    name: test-cluster   # ONLY test
    namespace: test
```

### Q2: "How to integrate JMeter, SoapUI, ZAP as plugins in CloudBees CI?"

**Answer**: You don't need plugins! Use the deployed tools via kubectl:

```groovy
// SoapUI
sh "kubectl cp tests.xml soapui/\${POD}:/tmp/test.xml"
sh "kubectl exec -n soapui \${POD} -- testrunner.sh /tmp/test.xml"

// JMeter
sh "kubectl cp load.jmx jmeter/\${POD}:/tmp/load.jmx"
sh "kubectl exec -n jmeter \${POD} -- jmeter -n -t /tmp/load.jmx -l /tmp/results.jtl"

// ZAP
sh "curl 'http://zap.zap.svc.cluster.local:8080/JSON/spider/action/scan/?url=\${APP_URL}'"
```

**For Reports**:
- Use Jenkins `publishHTML()` for HTML reports
- Use `junit()` for XML test results
- Use `archiveArtifacts()` for all artifacts
- CloudBees Unify integration: Use CloudBees Analytics plugin

### Q3: "JMeter and SoapUI test deployed app - but how does ZAP monitor network traffic? Do we need to simulate traffic?"

**Answer**: ZAP has 2 modes:

**Mode 1: Active Scanning** (Recommended for CI/CD)
- ZAP automatically crawls your application (Spider)
- ZAP sends attack payloads to test for vulnerabilities
- **NO traffic simulation needed!**

```groovy
// ZAP does everything automatically
sh "curl '\${ZAP_URL}/JSON/spider/action/scan/?url=\${APP_URL}'"  // Discover endpoints
sh "curl '\${ZAP_URL}/JSON/ascan/action/scan/?url=\${APP_URL}'"   // Attack and test
```

**Mode 2: Passive Proxy** (For manual testing)
- ZAP sits between client and app
- You generate traffic (manually or via SoapUI)
- ZAP intercepts and analyzes
- **Requires traffic simulation**

```groovy
// Use SoapUI through ZAP proxy
sh """
    kubectl exec -n soapui \${POD} -- \\
        testrunner.sh -Dhttp.proxyHost=zap.zap.svc.cluster.local \\
                      -Dhttp.proxyPort=8080 /tmp/tests.xml
"""
```

**Recommendation**: Use Active Scanning (Mode 1) - it's fully automated!

## Step-by-Step Setup

### Step 1: Create Additional Kind Clusters

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

### Step 2: Install Ingress on New Clusters

```bash
# For kind-dev
kubectl config use-context kind-kind-dev
helm upgrade --install ingress-nginx ingress-nginx \\
  --repo https://kubernetes.github.io/ingress-nginx \\
  --namespace ingress-nginx --create-namespace \\
  --set controller.hostPort.enabled=true \\
  --set controller.service.type=NodePort

# For kind-test
kubectl config use-context kind-kind-test
helm upgrade --install ingress-nginx ingress-nginx \\
  --repo https://kubernetes.github.io/ingress-nginx \\
  --namespace ingress-nginx --create-namespace \\
  --set controller.hostPort.enabled=true \\
  --set controller.service.type=NodePort

# Back to main cluster
kubectl config use-context kind-igs
```

### Step 3: Register Clusters with ArgoCD

```bash
# Get ArgoCD password
kubectl config use-context kind-igs
ARGOCD_PWD=$(kubectl get secret argocd-initial-admin-secret -n argocd -o jsonpath='{.data.password}' | base64 -d)
echo "ArgoCD Password: $ARGOCD_PWD"

# Login to ArgoCD
argocd login argocd.local --username admin --password $ARGOCD_PWD --insecure

# Add clusters
argocd cluster add kind-kind-dev --name dev-cluster
argocd cluster add kind-kind-test --name test-cluster

# List clusters
argocd cluster list
```

### Step 4: Create ArgoCD Applications

```bash
# Create dev application
kubectl apply -f - <<EOF
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
  destination:
    name: dev-cluster
    namespace: dev
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
EOF

# Create test application
kubectl apply -f - <<EOF
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
  destination:
    name: test-cluster
    namespace: test
  syncPolicy:
    automated:
      prune: false
      selfHeal: false
    syncOptions:
      - CreateNamespace=true
EOF

# Check status
argocd app list
```

### Step 5: Configure Jenkins Credentials

In CloudBees CI (http://cloudbees-ci.local/cjoc):

1. **DockerHub Credentials**:
   - Go to: Manage Jenkins → Credentials → System → Global credentials
   - Add → Username with password
   - ID: `dockerhub-credentials`
   - Username: your DockerHub username
   - Password: your DockerHub access token

2. **GitHub Credentials**:
   - Add → Username with password
   - ID: `github-credentials`
   - Username: your GitHub username
   - Password: your GitHub personal access token

### Step 6: Update Application Files

1. **Update Jenkinsfile**:
```groovy
// Line 23
DOCKER_REPO = 'your-dockerhub-username'  // CHANGE THIS

// Line 32
UNIFY_ORG = 'your-cloudbees-org'  // CHANGE THIS

// Line 99 - Update Git push URL
git push https://\${GIT_USER}:\${GIT_PASS}@github.com/your-username/your-repo.git HEAD:main
```

2. **Update Helm values.yaml**:
```yaml
image:
  repository: your-dockerhub-username/fitness-tracker  # CHANGE THIS
  tag: latest
```

3. **Push to GitHub**:
```bash
cd /Users/analla/Downloads/cloudbees/IGS/sample-spring-app

git init
git add .
git commit -m "Initial commit - Fitness Tracker with CI/CD"
git branch -M main
git remote add origin https://github.com/your-username/your-repo.git
git push -u origin main
```

### Step 7: Create Jenkins Pipeline

In CloudBees CI:

1. New Item → Pipeline
2. Name: `fitness-tracker-pipeline`
3. Pipeline → Definition: Pipeline script from SCM
4. SCM: Git
5. Repository URL: https://github.com/your-username/your-repo.git
6. Script Path: `sample-spring-app/Jenkinsfile`
7. Save

### Step 8: Run the Pipeline!

1. Click "Build with Parameters"
2. Select:
   - **DEPLOY_ENV**: dev (start with dev)
   - **RUN_SECURITY_SCAN**: ✅ Yes
   - **RUN_PERFORMANCE_TEST**: ✅ Yes
3. Click "Build"

## Complete Workflow

```
Developer pushes code to GitHub
         ↓
CloudBees CI detects change (webhook/polling)
         ↓
Jenkins Pipeline starts:
  1. Checkout code from Git
  2. Build with Maven
  3. Run unit tests
  4. Build Docker image
  5. Push image to DockerHub
  6. Update Helm chart values.yaml with new image tag
  7. Commit & push Helm changes to Git
         ↓
User selects environment (dev/test/prod)
         ↓
ArgoCD detects Git change (Helm chart update)
         ↓
ArgoCD syncs to SELECTED cluster only
  - dev → kind-kind-dev cluster
  - test → kind-kind-test cluster
  - prod → kind-igs cluster
         ↓
Application deployed
         ↓
SoapUI functional tests run
  - Test all API endpoints
  - Validate responses
  - Generate JUnit XML reports
         ↓
JMeter performance tests run (if enabled)
  - Simulate 10-50 users
  - Measure response times
  - Generate HTML reports
         ↓
OWASP ZAP security scan runs (if enabled)
  - Spider application (discover endpoints)
  - Active scan (test vulnerabilities)
  - Generate security report
  - NO traffic simulation needed!
         ↓
All reports published:
  - Jenkins HTML reports
  - JUnit test results
  - Archived artifacts
  - CloudBees Unify metrics
         ↓
✅ Pipeline complete!
```

## Verification

### Check Deployments

```bash
# Dev cluster
kubectl get all -n dev --context kind-kind-dev

# Test cluster
kubectl get all -n test --context kind-kind-test

# ArgoCD applications
argocd app list
argocd app get fitness-tracker-dev
argocd app get fitness-tracker-test
```

### Access Applications

```bash
# Dev
kubectl port-forward -n dev svc/fitness-tracker 8080:8080 --context kind-kind-dev
curl http://localhost:8080/actuator/health

# Test
kubectl port-forward -n test svc/fitness-tracker 8080:8080 --context kind-kind-test
curl http://localhost:8080/actuator/health
```

### View Reports

In Jenkins pipeline run:
- "SoapUI Test Results" (JUnit)
- "JMeter Performance Report" (HTML)
- "ZAP Security Report" (HTML)
- "Archived Artifacts" (all files)

## Key Points Summary

### ArgoCD Multi-Cluster

✅ Each Application = ONE cluster  
✅ Add clusters to ArgoCD = give permission  
✅ Create separate Applications for each environment  
✅ RBAC controls who can deploy where  
✅ Jenkins parameter selects which environment  

### Testing Tools Integration

✅ **SoapUI**: Functional API testing
- Tests: Endpoints, responses, CRUD operations
- When: After deployment
- Method: kubectl exec to run testrunner.sh
- Reports: JUnit XML

✅ **JMeter**: Performance testing
- Tests: Load, stress, response times
- When: After functional tests pass
- Method: kubectl exec to run jmeter
- Reports: HTML dashboard

✅ **ZAP**: Security scanning
- Tests: OWASP Top 10, vulnerabilities
- When: After performance tests
- Method: REST API calls (Active Scanning)
- **NO traffic simulation needed!**
- Reports: HTML + JSON

### CloudBees Unify Integration

- Use `publishHTML()` for reports
- Use `junit()` for test results
- Use `archiveArtifacts()` for files
- CloudBees Analytics plugin for metrics
- Custom API calls for advanced integration

## Troubleshooting

### "Pod not found" errors

```bash
# Check if testing tools are running
kubectl get pods -n jmeter
kubectl get pods -n soapui
kubectl get pods -n zap

# If not running, deploy them
cd /Users/analla/Downloads/cloudbees/IGS
./deploy-all-apps.sh
```

### ArgoCD not syncing

```bash
# Check Application status
argocd app get fitness-tracker-dev

# Manual sync
argocd app sync fitness-tracker-dev

# View logs
kubectl logs -n argocd deployment/argocd-application-controller
```

### Docker push fails

- Check DockerHub credentials in Jenkins
- Verify Docker daemon is running
- Test manual login: `docker login`

### Git push fails in pipeline

- Check GitHub credentials in Jenkins
- Ensure token has `repo` permissions
- Test manual push from command line

## Next Steps

1. ✅ Setup complete - all components running
2. 🔄 Run first pipeline build
3. 📊 Review test reports
4. 🔒 Configure RBAC for ArgoCD
5. 📈 Set up CloudBees Unify dashboards
6. 🔔 Configure notifications (Slack/email)
7. 🚀 Add more applications

## Files Reference

- `sample-spring-app/` - Complete application source
- `sample-spring-app/Jenkinsfile` - Complete CI/CD pipeline
- `sample-spring-app/k8s/helm-chart/` - Kubernetes Helm chart
- `MULTI-CLUSTER-ARGOCD-SETUP.md` - Detailed ArgoCD setup
- `ZAP-INTEGRATION-GUIDE.md` - ZAP security testing
- `JMETER-INTEGRATION-GUIDE.md` - JMeter performance testing
- `SOAPUI-INTEGRATION-GUIDE.md` - SoapUI API testing
- `COMPLETE-TESTING-STACK.md` - Testing overview

---

**You now have a complete CI/CD pipeline with comprehensive testing!** 🎉
