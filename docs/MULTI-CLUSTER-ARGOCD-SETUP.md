
# Multi-Cluster ArgoCD Setup Guide

## Overview

This guide explains how to set up ArgoCD to manage deployments across multiple Kind clusters (dev, test, prod) with proper access control and separation.

## Your Cluster Setup

- **igs** (main): CloudBees CI, ArgoCD, Testing tools (ZAP, JMeter, SoapUI)
- **kind-dev**: Development environment
- **kind-test**: Test/QA environment
- **kind-prod**: Production environment (can be same as igs or separate)

## Key Concepts

### ArgoCD Application vs Context

**Important**: ArgoCD doesn't automatically sync to all clusters! You have control:

1. **One ArgoCD instance** (running on `igs` cluster)
2. **Multiple cluster contexts** registered with ArgoCD
3. **Separate Applications** for each environment
4. **RBAC policies** control who can deploy where

## Step 1: Create Additional Kind Clusters

```bash
# Create dev cluster
cat <<EOF | kind create cluster --name kind-dev --config=-
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
cat <<EOF | kind create cluster --name kind-test --config=-
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

# Verify clusters
kubectl config get-contexts
```

## Step 2: Install Ingress on New Clusters

```bash
# Install on kind-dev
kubectl config use-context kind-kind-dev

helm upgrade --install ingress-nginx ingress-nginx \\
  --repo https://kubernetes.github.io/ingress-nginx \\
  --namespace ingress-nginx --create-namespace \\
  --set controller.hostPort.enabled=true \\
  --set controller.service.type=NodePort

# Install on kind-test
kubectl config use-context kind-kind-test

helm upgrade --install ingress-nginx ingress-nginx \\
  --repo https://kubernetes.github.io/ingress-nginx \\
  --namespace ingress-nginx --create-namespace \\
  --set controller.hostPort.enabled=true \\
  --set controller.service.type=NodePort

# Switch back to main cluster
kubectl config use-context kind-igs
```

## Step 3: Register Clusters with ArgoCD

ArgoCD needs to know about other clusters to deploy to them.

```bash
# Get ArgoCD admin password
ARGOCD_PASSWORD=$(kubectl get secret argocd-initial-admin-secret -n argocd -o jsonpath='{.data.password}' | base64 -d)

# Login to ArgoCD CLI
argocd login argocd.local --username admin --password $ARGOCD_PASSWORD --insecure

# Add dev cluster
argocd cluster add kind-kind-dev --name dev-cluster

# Add test cluster
argocd cluster add kind-kind-test --name test-cluster

# List registered clusters
argocd cluster list

# Output should show:
# SERVER                          NAME           VERSION  STATUS      MESSAGE
# https://kubernetes.default.svc  in-cluster     1.28     Successful  
# https://kind-dev-address        dev-cluster    1.28     Successful
# https://kind-test-address       test-cluster   1.28     Successful
```

**What this does**:
- Creates a ServiceAccount in each cluster
- Gives ArgoCD permission to deploy
- ArgoCD can now manage resources on those clusters

## Step 4: Create Environment-Specific ArgoCD Applications

### Option A: Separate Git Branches

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
    targetRevision: develop  # dev branch
    path: sample-spring-app/k8s/helm-chart
    helm:
      valueFiles:
        - values.yaml
      parameters:
        - name: image.tag
          value: latest
  destination:
    server: https://dev-cluster-address  # Points to kind-dev
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
    targetRevision: main  # main/test branch
    path: sample-spring-app/k8s/helm-chart
    helm:
      valueFiles:
        - values.yaml
  destination:
    server: https://test-cluster-address  # Points to kind-test
    namespace: test
  syncPolicy:
    automated:
      prune: false  # Manual approval for test
      selfHeal: false
    syncOptions:
      - CreateNamespace=true
```

### Option B: Separate Directories

```
your-repo/
├── sample-spring-app/
│   ├── src/
│   ├── Dockerfile
│   └── k8s/
│       ├── helm-chart/
│       ├── overlays/
│       │   ├── dev/
│       │   │   └── values-dev.yaml
│       │   ├── test/
│       │   │   └── values-test.yaml
│       │   └── prod/
│       │       └── values-prod.yaml
```

```yaml
# Dev Application using overlay
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: fitness-tracker-dev
  namespace: argocd
spec:
  source:
    repoURL: https://github.com/your-username/your-repo.git
    targetRevision: main
    path: sample-spring-app/k8s/helm-chart
    helm:
      valueFiles:
        - ../../overlays/dev/values-dev.yaml
  destination:
    name: dev-cluster
    namespace: dev
```

## Step 5: Deploy Applications

```bash
# Apply ArgoCD applications
kubectl apply -f argocd-apps/dev-application.yaml
kubectl apply -f argocd-apps/test-application.yaml

# Check status
argocd app list
argocd app get fitness-tracker-dev
argocd app get fitness-tracker-test

# Sync manually (if not automated)
argocd app sync fitness-tracker-dev
argocd app sync fitness-tracker-test
```

## How ArgoCD Access Control Works

### 1. **Cluster-Level Isolation**

Each ArgoCD Application explicitly specifies:
- **Source**: Which Git repo/branch
- **Destination**: Which cluster and namespace

```yaml
destination:
  server: https://dev-cluster-address  # Specific cluster
  namespace: dev                        # Specific namespace
```

ArgoCD will ONLY deploy to that specific cluster/namespace combination.

### 2. **RBAC Policies**

Control who can deploy where:

```yaml
# argocd-rbac-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-rbac-cm
  namespace: argocd
data:
  policy.csv: |
    # Developers can only sync dev environment
    p, role:developer, applications, sync, */fitness-tracker-dev, allow
    p, role:developer, applications, get, */fitness-tracker-dev, allow
    
    # QA team can sync test environment
    p, role:qa, applications, sync, */fitness-tracker-test, allow
    p, role:qa, applications, get, */fitness-tracker-test, allow
    
    # Only ops team can sync prod
    p, role:ops, applications, *, */fitness-tracker-prod, allow
    
    # Assign users to roles
    g, developer@example.com, role:developer
    g, qa@example.com, role:qa
    g, ops@example.com, role:ops
```

Apply RBAC:
```bash
kubectl apply -f argocd-rbac-configmap.yaml
```

### 3. **Projects for Isolation**

Create separate ArgoCD Projects for each environment:

```yaml
# argocd-project-dev.yaml
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: dev-project
  namespace: argocd
spec:
  description: Development environment
  
  # Allowed source repos
  sourceRepos:
    - 'https://github.com/your-username/your-repo.git'
  
  # ONLY allowed to deploy to dev cluster
  destinations:
    - server: https://dev-cluster-address
      namespace: 'dev*'  # Only dev namespace
  
  # What resources can be deployed
  clusterResourceWhitelist:
    - group: '*'
      kind: '*'
  
  namespaceResourceWhitelist:
    - group: '*'
      kind: '*'
```

```yaml
# argocd-project-test.yaml
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: test-project
  namespace: argocd
spec:
  description: Test environment
  sourceRepos:
    - 'https://github.com/your-username/your-repo.git'
  destinations:
    - server: https://test-cluster-address
      namespace: 'test*'
```

Update applications to use projects:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: fitness-tracker-dev
spec:
  project: dev-project  # Links to dev-project
  # ... rest of config
```

## CloudBees CI Integration

### Update Jenkinsfile to Work with ArgoCD

```groovy
stage('Deploy via ArgoCD') {
    steps {
        script {
            def appName = "fitness-tracker-${params.DEPLOY_ENV}"
            
            // Update image tag in Git (ArgoCD watches this)
            sh """
                sed -i 's|tag:.*|tag: ${IMAGE_TAG}|' k8s/helm-chart/values.yaml
                git add k8s/helm-chart/values.yaml
                git commit -m "Update ${params.DEPLOY_ENV} to ${IMAGE_TAG}"
                git push origin main
            """
            
            // Trigger ArgoCD sync
            sh """
                argocd app sync ${appName} --async
                argocd app wait ${appName} --timeout 300
            """
            
            // Check deployment status
            def status = sh(
                script: "argocd app get ${appName} -o json | jq -r '.status.health.status'",
                returnStdout: true
            ).trim()
            
            if (status != 'Healthy') {
                error("Deployment failed: ${status}")
            }
        }
    }
}
```

### Environment-Specific Deployment

```groovy
parameters {
    choice(
        name: 'DEPLOY_ENV',
        choices: ['dev', 'test', 'prod'],
        description: 'Target environment'
    )
}

stage('Select Cluster') {
    steps {
        script {
            def clusterMapping = [
                'dev': 'kind-kind-dev',
                'test': 'kind-kind-test',
                'prod': 'kind-igs'
            ]
            
            env.TARGET_CLUSTER = clusterMapping[params.DEPLOY_ENV]
            env.ARGOCD_APP = "fitness-tracker-${params.DEPLOY_ENV}"
        }
    }
}
```

## Answers to Your Questions

### Q1: "How to restrict ArgoCD from syncing to all clusters?"

**Answer**: ArgoCD does NOT sync to all clusters automatically. Each Application explicitly defines ONE destination cluster.

```yaml
# This app ONLY deploys to dev-cluster
destination:
  name: dev-cluster
  namespace: dev
```

To deploy to multiple clusters, you create **separate Applications**, one per cluster.

### Q2: "I should have an option where to deploy in CI"

**Answer**: Yes! The Jenkinsfile uses parameters:

```groovy
parameters {
    choice(name: 'DEPLOY_ENV', choices: ['dev', 'test', 'prod'])
}
```

When you run the pipeline, you SELECT which environment. The pipeline then:
1. Builds the image
2. Updates the Helm chart for that environment
3. Triggers the ArgoCD Application for that environment ONLY

### Q3: "How does ZAP monitor network traffic on deployed app?"

**Answer**: 

**ZAP works in 2 modes**:

#### Mode 1: Passive Proxy (requires traffic simulation)
- ZAP sits between client and application
- You MUST generate traffic (manually or via tests)
- ZAP intercepts and analyzes

```groovy
stage('Generate Traffic for ZAP') {
    steps {
        // Use SoapUI or curl to generate traffic through ZAP proxy
        sh """
            # Configure SoapUI to use ZAP as proxy
            kubectl exec -n soapui \${SOAPUI_POD} -- \\
                testrunner.sh -Dhttp.proxyHost=zap.zap.svc.cluster.local \\
                -Dhttp.proxyPort=8080 /tmp/api-tests.xml
        """
        
        // Or use curl through ZAP
        sh """
            for i in {1..50}; do
                curl -x http://zap.zap.svc.cluster.local:8080 http://\${APP_URL}/api/workouts
                sleep 1
            done
        """
    }
}
```

#### Mode 2: Active Scanning (ZAP generates traffic)
- ZAP actively crawls (spider) your application
- ZAP sends attack payloads
- No manual traffic needed!

```groovy
stage('ZAP Active Scan') {
    steps {
        // Spider discovers all endpoints
        sh "curl '\${ZAP_URL}/JSON/spider/action/scan/?url=http://\${APP_URL}'"
        
        // Active scan tests for vulnerabilities
        sh "curl '\${ZAP_URL}/JSON/ascan/action/scan/?url=http://\${APP_URL}'"
    }
}
```

**Recommended**: Use Active Scanning (Mode 2) in CI/CD - it's automated and doesn't require traffic simulation.

## Complete Workflow

```
1. Developer pushes code
   ↓
2. CloudBees CI pipeline triggers
   ↓
3. Build & Test (Maven)
   ↓
4. Build Docker image
   ↓
5. Push to DockerHub
   ↓
6. Update Helm chart (values.yaml) with new image tag
   ↓
7. Commit & push Helm changes to Git
   ↓
8. User selects environment (dev/test/prod)
   ↓
9. ArgoCD detects Git change
   ↓
10. ArgoCD deploys to SELECTED cluster only
   ↓
11. Run SoapUI tests (functional)
   ↓
12. Run JMeter tests (performance) - OPTIONAL
   ↓
13. Run ZAP scan (security) - Active mode, no traffic needed
   ↓
14. Publish reports to CloudBees Unify
```

## Testing the Setup

```bash
# 1. Build and push image manually
cd sample-spring-app
docker build -t your-dockerhub-username/fitness-tracker:test .
docker push your-dockerhub-username/fitness-tracker:test

# 2. Update Helm chart
cd k8s/helm-chart
sed -i 's|tag:.*|tag: test|' values.yaml
git add values.yaml
git commit -m "Test deployment"
git push

# 3. Watch ArgoCD sync
argocd app list
argocd app sync fitness-tracker-dev
kubectl get pods -n dev --context kind-kind-dev

# 4. Verify deployment
kubectl get all -n dev --context kind-kind-dev
```

## Summary

✅ **Multi-cluster**: ArgoCD manages 3 clusters (dev/test/prod)  
✅ **Isolation**: Each Application targets ONE cluster  
✅ **RBAC**: Control who can deploy where  
✅ **CI Selection**: Jenkins parameter chooses environment  
✅ **Testing**: SoapUI (functional), JMeter (performance), ZAP (security)  
✅ **ZAP Mode**: Use Active Scanning (automated, no traffic simulation needed)  
✅ **GitOps**: Helm chart updates trigger ArgoCD sync  

## Next Steps

1. Create the additional Kind clusters
2. Register them with ArgoCD
3. Create environment-specific Applications
4. Set up RBAC policies
5. Configure Jenkins credentials (DockerHub, GitHub)
6. Run the pipeline!
