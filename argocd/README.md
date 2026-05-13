# ArgoCD Multi-Cluster Setup - Summary

## ✅ Cluster Registration Complete

ArgoCD on the `igs` cluster can now deploy to three clusters:

| Cluster | Server URL | Namespace | Auto-Sync | Branch |
|---------|-----------|-----------|-----------|--------|
| **dev-cluster** | https://172.18.0.2:6443 | dev | ✅ Yes | develop |
| **test-cluster** | https://172.18.0.8:6443 | test | ✅ Yes | main |
| **in-cluster (prod)** | https://kubernetes.default.svc | prod | ❌ Manual | main |

## Verify Cluster Registration

```bash
# List registered clusters
argocd cluster list --grpc-web

# Expected output:
# SERVER                          NAME          VERSION  STATUS
# https://172.18.0.2:6443         dev-cluster            Unknown
# https://172.18.0.8:6443         test-cluster           Unknown
# https://kubernetes.default.svc  in-cluster    1.35.0   Unknown
```

## ArgoCD Applications

Three Application manifests are ready to deploy:

### 1. Development Environment
**File:** `application-dev.yaml`
- **Target:** dev-cluster (kind-kind-dev)
- **Namespace:** dev
- **Branch:** develop
- **Auto-Sync:** ✅ Enabled
- **NodePort:** 30080

### 2. Test Environment
**File:** `application-test.yaml`
- **Target:** test-cluster (kind-kind-test)
- **Namespace:** test
- **Branch:** main
- **Auto-Sync:** ✅ Enabled
- **NodePort:** 30081

### 3. Production Environment
**File:** `application-prod.yaml`
- **Target:** in-cluster (igs cluster)
- **Namespace:** prod
- **Branch:** main
- **Auto-Sync:** ❌ Manual (requires approval)
- **Service:** ClusterIP

## Next Steps

### Step 1: Push Code to GitHub

```bash
cd /Users/analla/Downloads/cloudbees/IGS/sample-spring-app

# Initialize git repository
git init
git add .
git commit -m "Initial commit: Fitness Tracker with multi-cluster support"

# Create GitHub repository (via GitHub web UI or gh CLI)
gh repo create fitness-tracker --public --source=. --remote=origin

# Push code
git push -u origin main

# Create develop branch
git checkout -b develop
git push -u origin develop
```

### Step 2: Update ArgoCD Application Manifests

Update the `repoURL` in all three application files:

```bash
# Replace YOUR-USERNAME with your GitHub username
sed -i '' 's|YOUR-USERNAME|your-github-username|g' argocd/application-*.yaml
```

Or manually edit:
- [argocd/application-dev.yaml](application-dev.yaml) - line 12
- [argocd/application-test.yaml](application-test.yaml) - line 12
- [argocd/application-prod.yaml](application-prod.yaml) - line 12

### Step 3: Deploy ArgoCD Applications

```bash
# Apply all applications
kubectl apply -f argocd/application-dev.yaml
kubectl apply -f argocd/application-test.yaml
kubectl apply -f argocd/application-prod.yaml

# Or apply all at once
kubectl apply -f argocd/
```

### Step 4: Verify Deployment

```bash
# Check application status
argocd app list --grpc-web

# Check specific application
argocd app get fitness-tracker-dev --grpc-web

# Watch sync status
argocd app sync fitness-tracker-dev --grpc-web
watch argocd app get fitness-tracker-dev --grpc-web
```

### Step 5: Access Applications

Once deployed, access the applications:

```bash
# Get dev cluster IP
DEV_IP=$(docker inspect kind-dev-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')

# Get test cluster IP
TEST_IP=$(docker inspect kind-test-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')

# Test dev app
curl http://$DEV_IP:30080/actuator/health

# Test test app
curl http://$TEST_IP:30081/actuator/health

# Test prod app (from within igs cluster)
kubectl run curl-test --image=curlimages/curl --rm -it --restart=Never -- \
  curl http://fitness-tracker.prod.svc.cluster.local:8080/actuator/health
```

## How It Works

### Cross-Cluster Connectivity

```
┌─────────────────────────────────────────┐
│   igs Cluster (172.18.0.4)             │
│                                         │
│   ┌──────────────────────────────────┐ │
│   │  ArgoCD                          │ │
│   │  - Manages deployments           │ │
│   │  - Monitors Git repositories     │ │
│   │  - Syncs to multiple clusters    │ │
│   └──────────────────────────────────┘ │
│                                         │
│   ┌──────────────────────────────────┐ │
│   │  CloudBees CI                    │ │
│   │  - Builds Docker images          │ │
│   │  - Updates Helm charts           │ │
│   │  - Runs tests                    │ │
│   └──────────────────────────────────┘ │
└─────────────────────────────────────────┘
         │                    │
         │ Uses Kubernetes    │ Uses kubectl
         │ ServiceAccount     │ to access via
         │ tokens            │ Docker network IPs
         │                    │
         ▼                    ▼
┌──────────────────┐  ┌──────────────────┐
│ dev-cluster      │  │ test-cluster     │
│ 172.18.0.2:6443  │  │ 172.18.0.8:6443  │
│                  │  │                  │
│ App on NodePort  │  │ App on NodePort  │
│ 30080            │  │ 30081            │
└──────────────────┘  └──────────────────┘
```

### GitOps Workflow

```
Developer
   │
   │ 1. Push code to GitHub
   ▼
GitHub Repository
   │
   │ 2. ArgoCD detects changes
   ▼
ArgoCD (on igs cluster)
   │
   │ 3. Syncs Helm chart
   ├─────────────┬──────────────┐
   ▼             ▼              ▼
dev-cluster  test-cluster  igs-cluster
(auto-sync)  (auto-sync)   (manual)
```

### Testing Workflow

```
CloudBees CI
   │
   │ 1. Build Docker image
   │ 2. Push to DockerHub
   │ 3. Update Helm chart values
   │ 4. Push to Git
   ▼
ArgoCD detects change
   │
   │ Auto-sync to dev/test
   ▼
App deployed
   │
   │ CI gets app URL via Docker inspect
   ▼
Run Tests
   ├─ SoapUI (functional)
   ├─ JMeter (performance)
   └─ ZAP (security)
```

## Restricting ArgoCD Access

### Per-Environment Isolation

ArgoCD does NOT automatically sync to all clusters. Each `Application` explicitly defines:
- ONE destination cluster (via `server:` field)
- ONE namespace
- ONE Git branch

**Example:**
- `fitness-tracker-dev` → ONLY deploys to `https://172.18.0.2:6443` (dev-cluster)
- `fitness-tracker-test` → ONLY deploys to `https://172.18.0.8:6443` (test-cluster)
- `fitness-tracker-prod` → ONLY deploys to `https://kubernetes.default.svc` (igs/prod)

There is **NO cross-deployment** - each Application is isolated.

### Manual vs Auto-Sync

| Environment | Auto-Sync | Why |
|-------------|-----------|-----|
| dev | ✅ Enabled | Fast iteration, immediate feedback |
| test | ✅ Enabled | Automated integration testing |
| prod | ❌ Manual | Requires approval, controlled releases |

## Troubleshooting

### Application stuck in "OutOfSync"

```bash
# Force sync
argocd app sync fitness-tracker-dev --grpc-web --force

# Check sync status
argocd app get fitness-tracker-dev --grpc-web
```

### Application shows "Unknown" health

This is normal before first sync. After sync:
- ✅ **Healthy** - All resources are healthy
- 🔄 **Progressing** - Deployment in progress
- ❌ **Degraded** - Some resources are unhealthy

### Cannot reach cluster

```bash
# Verify cluster secret exists
kubectl get secret -n argocd cluster-dev-cluster -o yaml

# Check if ServiceAccount exists on target cluster
kubectl --context kind-kind-dev get sa argocd-manager -n kube-system

# Test connectivity from ArgoCD pod
kubectl exec -n argocd deployment/argocd-server -- curl -k https://172.18.0.2:6443/version
```

### Application deleted but resources remain

```bash
# Delete with cascade
argocd app delete fitness-tracker-dev --cascade --grpc-web

# Or manually clean up
kubectl --context kind-kind-dev delete namespace dev
```

## Best Practices

1. ✅ **Use branches for environments**
   - `develop` → dev environment
   - `main` → test/prod environments

2. ✅ **Use overlay values**
   - Keep base values in `values.yaml`
   - Override per-environment in `overlays/*/values-*.yaml`

3. ✅ **Enable auto-sync for non-prod**
   - Faster feedback for developers
   - Catches issues early

4. ✅ **Require manual approval for prod**
   - Controlled releases
   - Review changes before deployment

5. ✅ **Monitor sync status**
   - Set up ArgoCD notifications
   - Integrate with Slack/email

6. ✅ **Use health checks**
   - Define readiness/liveness probes
   - ArgoCD will mark app as healthy/unhealthy

## Summary

✅ **3 clusters registered** with ArgoCD  
✅ **3 Applications ready** to deploy  
✅ **Cross-cluster connectivity** working via Docker network  
✅ **GitOps workflow** configured  
✅ **Environment isolation** enforced  
✅ **Auto-sync vs manual sync** configured appropriately

**Next:** Push code to GitHub and apply ArgoCD Applications! 🚀
