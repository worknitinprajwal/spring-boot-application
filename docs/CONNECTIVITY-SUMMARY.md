# Cross-Cluster Connectivity - Quick Summary

## Your Question

> "ZAP, ArgoCD, CloudBees are running on different cluster (igs) and I want the Spring Boot to be deployed on other cluster. How will connectivity take place?"

## The Answer

### Simple Solution: NodePort + Docker Network

**Kind clusters share the Docker network!** They can talk to each other directly.

```
┌────────────────────────────────────┐
│   Docker Network (172.18.0.0/16)  │
│                                    │
│  igs cluster (172.18.0.2)         │
│  ├─ CloudBees CI                  │
│  ├─ ArgoCD                        │
│  ├─ ZAP                           │
│  ├─ JMeter                        │
│  └─ SoapUI                        │
│      │                            │
│      │ Can access via IP:Port    │
│      ▼                            │
│  kind-dev (172.18.0.3)            │
│  └─ Spring Boot on NodePort 30080│
│      │                            │
│  kind-test (172.18.0.4)           │
│  └─ Spring Boot on NodePort 30081│
└────────────────────────────────────┘
```

## How It Works

### 1. Deploy App with NodePort

```yaml
# Dev cluster service
service:
  type: NodePort
  nodePort: 30080  # Fixed port

# Test cluster service  
service:
  type: NodePort
  nodePort: 30081  # Different fixed port
```

### 2. Get Cluster IPs

```bash
# Get dev cluster IP (it's a Docker container!)
DEV_IP=$(docker inspect kind-dev-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
# Result: 172.18.0.3

# Get test cluster IP
TEST_IP=$(docker inspect kind-test-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
# Result: 172.18.0.4
```

### 3. Access from Testing Tools

```bash
# From igs cluster, ZAP/JMeter/SoapUI can access:
# Dev app: http://172.18.0.3:30080
# Test app: http://172.18.0.4:30081

# Test it:
kubectl run test --image=curlimages/curl --rm -it --context kind-igs -- \
  curl http://172.18.0.3:30080/actuator/health
```

### 4. Jenkins Pipeline Handles It Automatically

```groovy
stage('Get App URL') {
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
        }
    }
}

stage('Run ZAP Scan') {
    steps {
        // ZAP on igs cluster scans app on dev/test cluster
        sh "curl '${ZAP_URL}/JSON/spider/action/scan/?url=${APP_URL}'"
    }
}
```

## ArgoCD Connectivity

**ArgoCD uses Kubernetes API** - no special network setup needed!

```bash
# When you register a cluster:
argocd cluster add kind-kind-dev --name dev-cluster

# ArgoCD:
# 1. Creates ServiceAccount in kind-dev cluster
# 2. Gets credentials (token, certificate)
# 3. Stores in Secret on igs cluster
# 4. Uses Kubernetes API to deploy

# ArgoCD can now deploy to kind-dev from igs!
```

## Complete Flow

```
1. Developer pushes code
       ↓
2. CloudBees CI (on igs) builds Docker image
       ↓
3. Push to DockerHub
       ↓
4. Update Helm chart in Git
       ↓
5. User selects environment (dev/test/prod)
       ↓
6. ArgoCD (on igs) deploys to selected cluster
   - Uses Kubernetes API
   - No network magic needed!
       ↓
7. Jenkins gets cluster IP dynamically
   - docker inspect kind-dev-control-plane
   - Gets: 172.18.0.3
       ↓
8. App URL = http://172.18.0.3:30080
       ↓
9. Testing tools (on igs) test the app
   - ZAP: curl http://172.18.0.3:30080 (Active Scan)
   - JMeter: kubectl exec ... jmeter -Jhost=172.18.0.3 -Jport=30080
   - SoapUI: kubectl exec ... testrunner.sh (uses http://172.18.0.3:30080)
       ↓
10. Reports collected and published
```

## Key Points

### ✅ Testing Tools Connectivity

**Question**: How does ZAP on igs test app on kind-dev?

**Answer**: 
- App exposed via NodePort (30080)
- Kind cluster has Docker IP (172.18.0.3)
- ZAP accesses: `http://172.18.0.3:30080`
- No traffic simulation needed (ZAP Active Scan)

### ✅ ArgoCD Connectivity

**Question**: How does ArgoCD on igs deploy to kind-dev?

**Answer**:
- Uses Kubernetes API (built-in)
- ServiceAccount credentials
- No special network setup

### ✅ CloudBees CI Connectivity

**Question**: How does CloudBees CI access apps?

**Answer**:
- CI doesn't directly access apps
- CI runs testing tools via kubectl
- Testing tools access apps via NodePort
- Reports come back to CI

## Files to Use

1. **CROSS-CLUSTER-CONNECTIVITY.md** - Detailed guide with all solutions
2. **Jenkinsfile-MultiCluster** - Updated pipeline with cross-cluster support
3. **sample-spring-app/k8s/overlays/** - Environment-specific Helm values

## Quick Setup

```bash
# 1. Create overlay values
mkdir -p sample-spring-app/k8s/overlays/{dev,test}

# 2. Dev values with NodePort
cat <<EOF > sample-spring-app/k8s/overlays/dev/values-dev.yaml
service:
  type: NodePort
  nodePort: 30080
EOF

# 3. Test values with different NodePort
cat <<EOF > sample-spring-app/k8s/overlays/test/values-test.yaml
service:
  type: NodePort
  nodePort: 30081
EOF

# 4. Use Jenkinsfile-MultiCluster instead of Jenkinsfile

# 5. Run pipeline!
```

## Testing the Setup

```bash
# Deploy to dev
helm install fitness-tracker sample-spring-app/k8s/helm-chart \
  --namespace dev --create-namespace \
  --values sample-spring-app/k8s/overlays/dev/values-dev.yaml \
  --context kind-kind-dev

# Get dev IP
DEV_IP=$(docker inspect kind-dev-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')

# Test from igs cluster
kubectl run test --image=curlimages/curl --rm -it --context kind-igs -- \
  curl http://$DEV_IP:30080/actuator/health

# Success! ✅
```

## Why This Works

1. **Kind clusters = Docker containers**
2. **Docker containers share network**
3. **NodePort exposes service on node IP**
4. **Node IP = Docker container IP**
5. **Accessible from any other container on same network!**

---

**That's it! Simple, reliable, no complex networking needed.** 🎉
