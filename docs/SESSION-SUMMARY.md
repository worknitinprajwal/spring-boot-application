# Complete Session Summary - CloudBees CI & GitOps Setup

## Overview

This document summarizes the complete setup of a CI/CD pipeline using:
- **CloudBees CI** (Jenkins) for building Docker images
- **ArgoCD** for GitOps deployments
- **Kind** Kubernetes clusters (3 clusters: igs, kind-dev, kind-test)
- **Spring Boot** application (fitness-tracker)
- **Ngrok** for external access

---

## Infrastructure Setup

### Kubernetes Clusters

Three Kind clusters were set up with Docker network connectivity:

| Cluster | Purpose | Context | API Server |
|---------|---------|---------|------------|
| **igs** | Production + CI/CD tools | `kind-igs` | `127.0.0.1:60453` |
| **kind-dev** | Development environment | `kind-dev` | `127.0.0.1:61738` |
| **kind-test** | Test environment | `kind-test` | `127.0.0.1:61772` |

**Network**: All clusters share Docker network `kind` for cross-cluster communication.

**IP Addresses**:
- igs control plane: `172.18.0.3`
- kind-dev control plane: `172.18.0.2`
- kind-test control plane: `172.18.0.8`

---

## CloudBees CI Setup

### Installation

CloudBees CI installed in `igs` cluster via Helm:

```bash
helm repo add cloudbees https://charts.cloudbees.com/public/cloudbees
helm install cloudbees-ci cloudbees/cloudbees-core \
  --namespace cloudbees-ci \
  --create-namespace \
  --set OperationsCenter.HostName=cloudbees-ci.local
```

### Components Deployed

1. **CJOC (Operations Center)** - `cjoc-0` pod
   - Manages controllers
   - Central authentication
   - License management

2. **Managed Controller** - `fitness-tracker-controller-0`
   - Runs CI/CD pipelines
   - Isolated Jenkins instance
   - Kubernetes-based agents

3. **Ingress Controller** (Nginx)
   - Routes traffic to CJOC and controllers
   - Path-based routing (`/cjoc/`, `/fitness-tracker-controller/`)

---

## Ngrok Configuration (External Access)

### Problem Solved

CloudBees CI was only accessible via `cloudbees-ci.local` (local DNS). We needed external access via ngrok.

### Solution Implemented

**Step 1: Port-Forward Setup**
```bash
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 8080:80 &
```

**Step 2: Ngrok Tunnel**
```bash
ngrok http 8080
```

**Generated URL**: `https://backspin-feline-pesticide.ngrok-free.dev`

**Step 3: Updated ALL CloudBees Resources**

Changed these Kubernetes resources to use ngrok URL instead of `cloudbees-ci.local`:

1. **ConfigMap** `cjoc-configure-jenkins-groovy` - CJOC Jenkins URL
2. **StatefulSet** `cjoc` - CloudBees networking hostname
3. **Ingress** `cjoc` - Wildcard host (accepts any hostname)
4. **Ingress** `fitness-tracker-controller` - Wildcard host
5. **Ingress** `managed-master-hibernation-monitor` - Wildcard host

**Result**: CloudBees CI now fully accessible via ngrok URL with working OAuth authentication.

**Access URLs**:
- CJOC: `https://backspin-feline-pesticide.ngrok-free.dev/cjoc/`
- Controller: `https://backspin-feline-pesticide.ngrok-free.dev/fitness-tracker-controller/`

See: [CLOUDBEES-NGROK-CONFIGURATION.md](CLOUDBEES-NGROK-CONFIGURATION.md)

---

## ArgoCD Setup

### Installation

ArgoCD installed in `igs` cluster:

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

### Cluster Registration

Registered all 3 Kind clusters with ArgoCD:

```bash
# Dev cluster
argocd cluster add kind-dev --name dev-cluster --server https://172.18.0.2:6443

# Test cluster  
argocd cluster add kind-test --name test-cluster --server https://172.18.0.8:6443

# Prod cluster (in-cluster)
Uses default 'https://kubernetes.default.svc'
```

### ArgoCD Applications Created

Three applications for GitOps deployments:

| Application | Environment | Branch | Target Cluster | Auto-Sync | Helm Values |
|-------------|-------------|--------|----------------|-----------|-------------|
| **fitness-tracker-dev** | Development | `develop` | dev-cluster (kind-dev) | ✅ Yes | `values.yaml` + `values-dev.yaml` |
| **fitness-tracker-test** | Test | `main` | test-cluster (kind-test) | ✅ Yes | `values.yaml` + `values-test.yaml` |
| **fitness-tracker-prod** | Production | `main` | in-cluster (igs) | ❌ Manual | `values.yaml` + `values-prod.yaml` |

**Deployment Files**:
- [argocd/application-dev.yaml](../argocd/application-dev.yaml)
- [argocd/application-test.yaml](../argocd/application-test.yaml)
- [argocd/application-prod.yaml](../argocd/application-prod.yaml)

---

## Helm Chart Configuration

### Base Chart

Location: `k8s/helm-chart/`

**Key files**:
- `Chart.yaml` - Chart metadata
- `values.yaml` - Base configuration
- `templates/` - Kubernetes manifests

### Environment Overlays (Kustomize-style)

Location: `k8s/overlays/{env}/`

**Environment-specific configurations**:

#### Development (`values-dev.yaml`)
```yaml
replicaCount: 1
service:
  type: NodePort
  nodePort: 30080
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "dev"
```

#### Test (`values-test.yaml`)
```yaml
replicaCount: 2
service:
  type: NodePort
  nodePort: 30081
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "test"
```

#### Production (`values-prod.yaml`)
```yaml
replicaCount: 3
service:
  type: ClusterIP  # Same cluster as testing tools
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
```

---

## Jenkins Pipeline (Jenkinsfile)

### Purpose

Automates the entire CI/CD workflow:
1. ✅ Build Spring Boot application
2. ✅ Run unit tests
3. ✅ Build Docker image
4. ✅ Push to DockerHub
5. ✅ Update Helm chart with new image tag
6. ✅ Commit changes to Git
7. ✅ Trigger ArgoCD sync
8. ✅ Archive build artifacts
9. ✅ Publish metrics to CloudBees Unify

### Pipeline Stages

| Stage | Description | Tools |
|-------|-------------|-------|
| **Setup** | Create artifact directories | Bash |
| **Checkout** | Clone Git repository | SCM |
| **Build & Test** | Maven package + JUnit tests | Maven 3.9 + JDK 17 |
| **Docker Build & Push** | Build and push Docker image | Docker |
| **Update Helm Chart** | Update `values.yaml` with new image tag | sed |
| **Trigger ArgoCD Sync** | Sync ArgoCD application | ArgoCD CLI |
| **Collect Build Artifacts** | Archive JARs, reports, HTML summary | Jenkins archiveArtifacts |
| **Publish to CloudBees Unify** | Send metrics to CloudBees Platform | CloudBees Analytics |

### Artifact Storage

**During Build** (workspace):
```
${WORKSPACE}/
├── build-artifacts/
│   ├── build-info.txt
│   ├── *.jar
│   ├── helm-values.yaml
│   ├── build-summary.html
│   └── unify-data.json
└── test-reports/
    └── unit-tests/*.xml
```

**After Build** (Jenkins):
- Permanently archived via `archiveArtifacts`
- Accessible from Jenkins UI
- Retention: 5 builds, 30 days max

### Branch Strategy

| Branch | ArgoCD App | Target Environment | Deployment |
|--------|------------|-------------------|------------|
| **develop** | `fitness-tracker-dev` | Development (kind-dev) | Auto-sync |
| **main** | `fitness-tracker-test` | Test (kind-test) | Auto-sync |
| **main** | `fitness-tracker-prod` | Production (igs) | Manual |

---

## Multibranch Pipeline Setup

### Configuration

**Pipeline Name**: `fitness-tracker-spring-boot-app`

**Branch Sources**:
- **Type**: GitHub
- **Repository**: `https://github.com/anuddeeph2/sample-spring-boot-app.git`
- **Credentials**: `github-credentials`
- **Discover branches**: All branches
- **Discover PRs**: Merge with base branch

**Build Configuration**:
- **Mode**: Jenkinsfile from SCM
- **Script Path**: `Jenkinsfile`

**Scan Triggers**:
- Scan every 1 minute
- GitHub webhooks configured

### Branch Discovery

Pipeline automatically discovered:
- ✅ `main` branch
- ✅ `develop` branch

Each branch gets its own pipeline job.

---

## Docker Image Strategy

### Image Naming

**Format**: `anuddeeph2/fitness-tracker:{branch}-{buildNumber}`

**Examples**:
- `anuddeeph2/fitness-tracker:main-5`
- `anuddeeph2/fitness-tracker:develop-3`

### Tags Created

For each build:
1. **Specific tag**: `{branch}-{buildNumber}` (e.g., `main-5`)
2. **Latest tag**: `{branch}-latest` (e.g., `main-latest`)

**DockerHub Repository**: `docker.io/anuddeeph2/fitness-tracker`

---

## GitOps Workflow

### Complete Flow

```
Developer pushes code to GitHub
         ↓
GitHub webhook triggers Jenkins
         ↓
Jenkins builds Spring Boot JAR
         ↓
Jenkins builds Docker image
         ↓
Jenkins pushes to DockerHub (anuddeeph2/fitness-tracker:main-5)
         ↓
Jenkins updates k8s/helm-chart/values.yaml with new tag
         ↓
Jenkins commits to Git [skip ci]
         ↓
ArgoCD detects Git change
         ↓
ArgoCD syncs application to Kubernetes
         ↓
New pods deployed with new image
```

### Auto-Sync Behavior

**Develop Branch** → Dev Environment:
- Push to `develop` → Builds → Updates Git → ArgoCD auto-syncs to `kind-dev`

**Main Branch** → Test Environment:
- Push to `main` → Builds → Updates Git → ArgoCD auto-syncs to `kind-test`

**Main Branch** → Production:
- ArgoCD detects change but **waits for manual sync** (safety gate)

---

## Credentials Configuration

### Required Credentials in Controller

| Credential ID | Type | Purpose | Where Used |
|--------------|------|---------|------------|
| **github-credentials** | Username + Password (PAT) | Push Helm updates to Git | Update Helm Chart stage |
| **dockerhub-credentials** | Username + Password | Push Docker images | Docker Build & Push stage |

### GitHub Personal Access Token

**Scopes required**:
- ✅ `repo` (full control of private repositories)

### DockerHub Credentials

**Username**: `anuddeeph2`  
**Password**: DockerHub password or access token

---

## Kubernetes Cloud Configuration (For Jenkins Agents)

### Why Needed

Jenkins needs to spawn **ephemeral pods** (agents) to run pipeline stages. Without Kubernetes cloud configuration, builds get stuck "Waiting for executor".

### Configuration Settings

**Cloud Name**: `kubernetes`

**Connection**:
- **Kubernetes URL**: `https://kubernetes.default`
- **Namespace**: `cloudbees-ci`
- **Credentials**: `- none -` (uses in-cluster service account)
- **Jenkins URL**: `http://fitness-tracker-controller:8080`
- **Jenkins tunnel**: `fitness-tracker-controller:50000`

### Pod Template

**Name**: `maven-jdk17`  
**Labels**: `maven jdk17`

**Containers**:

1. **Maven Container**
   - Image: `maven:3.9-eclipse-temurin-17`
   - Command: `sleep`
   - Args: `99d`
   - Purpose: Build Java applications

2. **Docker Container**
   - Image: `docker:24-dind`
   - Privileged: `true`
   - Purpose: Build Docker images

**Volume**:
- Host path: `/var/run/docker.sock` → Mount: `/var/run/docker.sock`
- Purpose: Docker-in-Docker for image builds

---

## Issues Encountered & Resolutions

### Issue 1: Jenkinsfile Syntax Error

**Error**: `invalid option type 'timestamps'`

**Cause**: `timestamps()` not valid in `options {}` block

**Fix**: Removed `timestamps()` from Jenkinsfile

**Commit**: `26c0c7c` - "Fix Jenkinsfile: remove timestamps() option"

---

### Issue 2: OAuth Redirect Error (403)

**Error**: "HTTP ERROR 403 Incorrect redirect_uri"

**Cause**: CJOC and controller configured with `cloudbees-ci.local`, but accessing via ngrok URL

**Fix**: Updated all Kubernetes resources to use ngrok URL:
- ConfigMap: CJOC Jenkins URL
- StatefulSet: CloudBees networking hostname
- Ingresses: Wildcard hosts (accept any hostname)

**Documentation**: [CLOUDBEES-NGROK-CONFIGURATION.md](CLOUDBEES-NGROK-CONFIGURATION.md)

---

### Issue 3: Builds Stuck "Waiting for Executor"

**Error**: Builds queued but never start, executor status shows `0/0`

**Cause**: Kubernetes cloud not configured in controller

**Status**: ⚠️ **PENDING** - Controller being recreated with proper configuration

**Solution**: Configure Kubernetes cloud with pod template (see Kubernetes Cloud Configuration section above)

---

### Issue 4: ArgoCD ComparisonError

**Error**: ArgoCD applications showing "ComparisonError"

**Cause**: Helm overlay files (`values-dev.yaml`, `values-test.yaml`, `values-prod.yaml`) didn't exist

**Fix**: Created environment-specific value files in `k8s/overlays/{env}/`

**Committed files**:
- `k8s/overlays/dev/values-dev.yaml`
- `k8s/overlays/test/values-test.yaml`
- `k8s/overlays/prod/values-prod.yaml`

---

### Issue 5: ImagePullBackOff

**Error**: Kubernetes pods showing `ImagePullBackOff`

**Cause**: Docker image didn't exist yet - tried to deploy before building

**Resolution**: Correct order is:
1. First run CI pipeline to build Docker image
2. Then ArgoCD can deploy successfully

---

## Documentation Created

| Document | Purpose |
|----------|---------|
| [ARTIFACTS-AND-UNIFY-GUIDE.md](ARTIFACTS-AND-UNIFY-GUIDE.md) | Artifact storage and CloudBees Unify integration |
| [MULTIBRANCH-PIPELINE-SETUP.md](MULTIBRANCH-PIPELINE-SETUP.md) | Step-by-step multibranch pipeline configuration |
| [CLOUDBEES-NGROK-CONFIGURATION.md](CLOUDBEES-NGROK-CONFIGURATION.md) | Complete ngrok setup and Kubernetes changes |
| [SESSION-SUMMARY.md](SESSION-SUMMARY.md) | This document - complete overview |

---

## Current Status

### ✅ Completed

- [x] 3 Kind clusters deployed and networked
- [x] CloudBees CI installed (CJOC + Controller)
- [x] ArgoCD installed and configured
- [x] 3 ArgoCD applications created (dev, test, prod)
- [x] Helm chart with environment overlays
- [x] Jenkinsfile with complete CI/CD pipeline
- [x] Multibranch pipeline created
- [x] Branch discovery working (main, develop)
- [x] Ngrok external access configured
- [x] All OAuth authentication issues resolved
- [x] Ingresses configured for wildcard hostnames
- [x] GitHub repository connected
- [x] Docker registry configured (DockerHub)

### ⚠️ Pending

- [ ] Configure Kubernetes cloud in controller (for Jenkins agents)
- [ ] Add credentials (github-credentials, dockerhub-credentials)
- [ ] Run first successful build
- [ ] Verify artifact archiving works
- [ ] Verify CloudBees Unify metrics collection
- [ ] Test complete GitOps flow (code → build → deploy)
- [ ] Deploy to all 3 environments

---

## Next Steps

### Immediate (Required for First Build)

1. **Wait for controller to provision** (in progress)
2. **Configure Kubernetes cloud** in controller:
   - Go to: Manage Jenkins → Clouds
   - Add Kubernetes cloud with settings above
3. **Add credentials**:
   - `github-credentials` (GitHub PAT)
   - `dockerhub-credentials` (DockerHub password)
4. **Trigger build** on `main` or `develop` branch
5. **Monitor build progress** - should create Maven agent pod
6. **Verify artifacts** are archived in Jenkins

### Testing & Validation

1. **Build verification**:
   - Check console output for each stage
   - Verify Docker image pushed to DockerHub
   - Verify Helm values.yaml updated in Git
   - Check artifacts in Jenkins UI

2. **Deployment verification**:
   - Check ArgoCD sync status
   - Verify pods running in target cluster
   - Test application endpoints

3. **Multi-environment test**:
   - Push to `develop` → Verify deploys to dev
   - Merge to `main` → Verify deploys to test
   - Manually sync prod → Verify deploys to prod

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Developer Workstation                     │
└────────────────────────────┬────────────────────────────────────┘
                             │ git push
                             ↓
┌─────────────────────────────────────────────────────────────────┐
│                          GitHub Repository                       │
│              github.com/anuddeeph2/sample-spring-boot-app        │
└────────────┬────────────────────────────────┬───────────────────┘
             │ webhook                        │ ArgoCD polls
             ↓                                ↓
┌────────────────────────────┐   ┌──────────────────────────────┐
│   CloudBees CI (igs)       │   │   ArgoCD (igs)               │
│  ┌──────────────────────┐  │   │  ┌────────────────────────┐  │
│  │  CJOC                │  │   │  │  Applications:         │  │
│  │  (Operations Center) │  │   │  │  - fitness-tracker-dev │  │
│  └──────────────────────┘  │   │  │  - fitness-tracker-test│  │
│  ┌──────────────────────┐  │   │  │  - fitness-tracker-prod│  │
│  │ fitness-tracker      │  │   │  └────────────────────────┘  │
│  │ -controller          │  │   └──────────────────────────────┘
│  │  - Multibranch       │  │              │  │  │
│  │    Pipeline          │  │              │  │  │ kubectl apply
│  │  - Maven Agents      │  │              │  │  │
│  └──────────────────────┘  │              │  │  │
└─────────────┬──────────────┘              │  │  │
              │ docker push                 │  │  │
              ↓                              ↓  ↓  ↓
┌────────────────────────────┐   ┌─────────┴──┴──┴──────────┐
│   DockerHub Registry       │   │   Kubernetes Clusters    │
│   anuddeeph2/              │   │                          │
│   fitness-tracker          │   │ ┌──────────────────────┐ │
└────────────────────────────┘   │ │  igs (prod)          │ │
                                 │ │  - fitness-tracker   │ │
                                 │ └──────────────────────┘ │
                                 │ ┌──────────────────────┐ │
                                 │ │  kind-dev            │ │
                                 │ │  - fitness-tracker   │ │
                                 │ └──────────────────────┘ │
                                 │ ┌──────────────────────┐ │
                                 │ │  kind-test           │ │
                                 │ │  - fitness-tracker   │ │
                                 │ └──────────────────────┘ │
                                 └──────────────────────────┘
```

---

## Key Commands Reference

### Kubernetes

```bash
# Switch contexts
kubectl config use-context kind-igs
kubectl config use-context kind-dev
kubectl config use-context kind-test

# Check CloudBees CI pods
kubectl get pods -n cloudbees-ci

# Check ingresses
kubectl get ingress -n cloudbees-ci

# Port-forward to ingress
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 8080:80

# Restart CJOC
kubectl rollout restart statefulset cjoc -n cloudbees-ci
```

### ArgoCD

```bash
# List applications
argocd app list

# Sync application
argocd app sync fitness-tracker-dev

# Get application status
argocd app get fitness-tracker-dev

# Watch sync progress
argocd app wait fitness-tracker-dev
```

### Docker

```bash
# List Kind containers
docker ps | grep kind

# Check Docker network
docker network inspect kind
```

### Git

```bash
# Check remote
git remote -v

# Push to branch
git push origin main
git push origin develop

# View recent commits
git log --oneline -10
```

---

## Troubleshooting Commands

### Check CloudBees CI Accessibility

```bash
# Via localhost
curl -I http://localhost:8080/cjoc/login -H "Host: cloudbees-ci.local"

# Via ngrok
curl -I https://backspin-feline-pesticide.ngrok-free.dev/cjoc/login
```

### Scan for Old URLs

```bash
kubectl get configmap -n cloudbees-ci -o yaml | grep "cloudbees-ci.local"
kubectl get statefulset -n cloudbees-ci -o yaml | grep "cloudbees-ci.local"
kubectl get ingress -n cloudbees-ci -o yaml | grep "cloudbees-ci.local"
```

### Check Jenkins Logs

```bash
# CJOC logs
kubectl logs -n cloudbees-ci cjoc-0 --tail=100

# Controller logs
kubectl logs -n cloudbees-ci fitness-tracker-controller-0 --tail=100
```

### Check ArgoCD Sync Status

```bash
argocd app get fitness-tracker-dev --refresh
```

---

## Important URLs

| Service | URL |
|---------|-----|
| **CJOC** | `https://backspin-feline-pesticide.ngrok-free.dev/cjoc/` |
| **Controller** | `https://backspin-feline-pesticide.ngrok-free.dev/fitness-tracker-controller/` |
| **GitHub Repo** | `https://github.com/anuddeeph2/sample-spring-boot-app` |
| **DockerHub** | `https://hub.docker.com/r/anuddeeph2/fitness-tracker` |

---

## Contributors

- **User**: Anudeep Nalla
- **Assistant**: Claude (Anthropic)

**Session Date**: May 14, 2026

---

**End of Session Summary** 🎉
