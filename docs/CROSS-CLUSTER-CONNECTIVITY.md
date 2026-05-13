# Cross-Cluster Connectivity Guide

## Architecture Overview

```
┌─────────────────────────────────────────┐
│   Cluster: igs (Main/Control Plane)    │
│                                         │
│  ┌──────────────┐  ┌────────────────┐ │
│  │ CloudBees CI │  │    ArgoCD      │ │
│  └──────────────┘  └────────────────┘ │
│                                         │
│  ┌─────┐ ┌────────┐ ┌────────┐       │
│  │ ZAP │ │ JMeter │ │ SoapUI │       │
│  └─────┘ └────────┘ └────────┘       │
└─────────────────────────────────────────┘
           │ │ │
           │ │ │  How to connect?
           │ │ │
           ▼ ▼ ▼
┌────────────────────┐  ┌────────────────────┐
│  Cluster: kind-dev │  │ Cluster: kind-test │
│                    │  │                    │
│  ┌──────────────┐ │  │  ┌──────────────┐ │
│  │ Spring Boot  │ │  │  │ Spring Boot  │ │
│  │     App      │ │  │  │     App      │ │
│  └──────────────┘ │  │  └──────────────┘ │
└────────────────────┘  └────────────────────┘
```

## Solutions for Cross-Cluster Connectivity

### Solution 1: NodePort + Docker Network (Recommended for Kind)

Kind clusters share the Docker network, so they can communicate!

#### Step 1: Expose Apps via NodePort

```bash
# Update Helm values for dev environment
cat <<EOF > sample-spring-app/k8s/overlays/dev/values-dev.yaml
# Inherits from main values.yaml
service:
  type: NodePort
  nodePort: 30080  # Fixed port for dev

ingress:
  enabled: true
  hosts:
    - host: fitness-tracker-dev.local
      paths:
        - path: /
          pathType: Prefix
EOF

# Update Helm values for test environment
cat <<EOF > sample-spring-app/k8s/overlays/test/values-test.yaml
service:
  type: NodePort
  nodePort: 30081  # Fixed port for test

ingress:
  enabled: true
  hosts:
    - host: fitness-tracker-test.local
      paths:
        - path: /
          pathType: Prefix
EOF
```

#### Step 2: Get Node IP Addresses

Kind clusters are Docker containers, so we can get their IPs:

```bash
# Get dev cluster control-plane IP
DEV_NODE_IP=$(docker inspect kind-dev-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
echo "Dev cluster IP: $DEV_NODE_IP"

# Get test cluster control-plane IP
TEST_NODE_IP=$(docker inspect kind-test-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
echo "Test cluster IP: $TEST_NODE_IP"

# Save for use in Jenkins
echo "DEV_NODE_IP=$DEV_NODE_IP" > cluster-ips.env
echo "TEST_NODE_IP=$TEST_NODE_IP" >> cluster-ips.env
```

#### Step 3: Access Apps from igs Cluster

```bash
# From igs cluster, you can access:
# Dev app: http://$DEV_NODE_IP:30080
# Test app: http://$TEST_NODE_IP:30081

# Test connectivity
kubectl run curl-test --image=curlimages/curl:latest --rm -it --restart=Never -- \
  curl http://$DEV_NODE_IP:30080/actuator/health
```

#### Step 4: Update Jenkinsfile

```groovy
environment {
    // Load cluster IPs
    DEV_NODE_IP = sh(script: "docker inspect kind-dev-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'", returnStdout: true).trim()
    TEST_NODE_IP = sh(script: "docker inspect kind-test-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'", returnStdout: true).trim()
}

stage('Set Target URL') {
    steps {
        script {
            switch(params.DEPLOY_ENV) {
                case 'dev':
                    env.APP_URL = "http://${env.DEV_NODE_IP}:30080"
                    break
                case 'test':
                    env.APP_URL = "http://${env.TEST_NODE_IP}:30081"
                    break
                case 'prod':
                    env.APP_URL = "http://fitness-tracker.prod.svc.cluster.local:8080"
                    break
            }
            echo "Target URL: ${env.APP_URL}"
        }
    }
}

stage('SoapUI Tests') {
    steps {
        echo "Testing application at: ${env.APP_URL}"
        // SoapUI will use APP_URL which points to the correct cluster
        sh "kubectl exec -n soapui ${SOAPUI_POD} -- testrunner.sh /tmp/tests.xml"
    }
}
```

### Solution 2: Port-Forward via Jenkins Agent

Create port-forwards during the pipeline run.

```groovy
stage('Setup Port Forward') {
    steps {
        script {
            def targetContext = ''
            def targetNamespace = ''
            def localPort = 9090
            
            switch(params.DEPLOY_ENV) {
                case 'dev':
                    targetContext = 'kind-kind-dev'
                    targetNamespace = 'dev'
                    break
                case 'test':
                    targetContext = 'kind-kind-test'
                    targetNamespace = 'test'
                    break
            }
            
            if (targetContext) {
                // Start port-forward in background
                sh """
                    kubectl config use-context ${targetContext}
                    kubectl port-forward -n ${targetNamespace} svc/fitness-tracker ${localPort}:8080 &
                    echo \$! > pf.pid
                    sleep 5
                """
                
                env.APP_URL = "http://localhost:${localPort}"
                echo "Port-forward established: ${env.APP_URL}"
            }
        }
    }
}

stage('Run Tests') {
    steps {
        // Tests use localhost:9090
        sh "kubectl exec -n soapui ${SOAPUI_POD} -- testrunner.sh /tmp/tests.xml"
    }
}

post {
    always {
        // Kill port-forward
        sh "kill \$(cat pf.pid) || true"
    }
}
```

### Solution 3: Kubernetes Service Mesh (Advanced)

Use Istio or Linkerd to connect clusters.

**Not recommended for Kind clusters** - too complex for local dev.

### Solution 4: External Load Balancer

Expose apps via LoadBalancer service (requires MetalLB or cloud provider).

```bash
# Install MetalLB on kind-dev
kubectl apply -f https://raw.githubusercontent.com/metallb/metallb/v0.13.0/config/manifests/metallb-native.yaml

# Configure IP pool
cat <<EOF | kubectl apply -f -
apiVersion: metallb.io/v1beta1
kind: IPAddressPool
metadata:
  name: dev-pool
  namespace: metallb-system
spec:
  addresses:
  - 172.18.100.1-172.18.100.10
EOF

# Update service type to LoadBalancer
service:
  type: LoadBalancer
```

## Recommended Solution for Your Setup

**Use Solution 1: NodePort + Docker Network**

### Why?
- ✅ Simple and reliable
- ✅ Kind clusters share Docker network
- ✅ No additional components needed
- ✅ Fixed ports for each environment
- ✅ Works from Jenkins running in igs cluster

### Complete Implementation

#### 1. Update Helm Chart

```bash
cd /Users/analla/Downloads/cloudbees/IGS/sample-spring-app

# Create overlay directories
mkdir -p k8s/overlays/{dev,test,prod}

# Dev values
cat <<EOF > k8s/overlays/dev/values-dev.yaml
replicaCount: 1

service:
  type: NodePort
  nodePort: 30080

ingress:
  hosts:
    - host: fitness-tracker-dev.local
      paths:
        - path: /
          pathType: Prefix

env:
  - name: SPRING_PROFILES_ACTIVE
    value: "dev"
EOF

# Test values
cat <<EOF > k8s/overlays/test/values-test.yaml
replicaCount: 2

service:
  type: NodePort
  nodePort: 30081

ingress:
  hosts:
    - host: fitness-tracker-test.local
      paths:
        - path: /
          pathType: Prefix

env:
  - name: SPRING_PROFILES_ACTIVE
    value: "test"
EOF

# Prod values (stays ClusterIP since it's on same cluster)
cat <<EOF > k8s/overlays/prod/values-prod.yaml
replicaCount: 3

service:
  type: ClusterIP

ingress:
  hosts:
    - host: fitness-tracker.local
      paths:
        - path: /
          pathType: Prefix

env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"

resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 500m
    memory: 512Mi
EOF
```

#### 2. Update Jenkinsfile with Dynamic URLs

```groovy
stage('Determine App URL') {
    steps {
        script {
            def appUrl = ''
            def appPort = 8080
            
            switch(params.DEPLOY_ENV) {
                case 'dev':
                    def devNodeIp = sh(
                        script: "docker inspect kind-dev-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'",
                        returnStdout: true
                    ).trim()
                    appUrl = "http://${devNodeIp}:30080"
                    break
                    
                case 'test':
                    def testNodeIp = sh(
                        script: "docker inspect kind-test-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'",
                        returnStdout: true
                    ).trim()
                    appUrl = "http://${testNodeIp}:30081"
                    break
                    
                case 'prod':
                    // Prod is on same cluster (igs)
                    appUrl = "http://fitness-tracker.prod.svc.cluster.local:8080"
                    break
            }
            
            env.APP_URL = appUrl
            echo "✅ Application URL: ${env.APP_URL}"
            
            // Verify connectivity
            sh """
                kubectl run connectivity-check --image=curlimages/curl:latest --rm -i --restart=Never -- \\
                    curl -f ${env.APP_URL}/actuator/health || exit 1
            """
        }
    }
}

stage('Deploy to Environment') {
    steps {
        script {
            def clusterContext = ''
            def namespace = ''
            def valuesFile = ''
            
            switch(params.DEPLOY_ENV) {
                case 'dev':
                    clusterContext = 'kind-kind-dev'
                    namespace = 'dev'
                    valuesFile = 'overlays/dev/values-dev.yaml'
                    break
                case 'test':
                    clusterContext = 'kind-kind-test'
                    namespace = 'test'
                    valuesFile = 'overlays/test/values-test.yaml'
                    break
                case 'prod':
                    clusterContext = 'kind-igs'
                    namespace = 'prod'
                    valuesFile = 'overlays/prod/values-prod.yaml'
                    break
            }
            
            // Switch context
            sh "kubectl config use-context ${clusterContext}"
            
            // Deploy with environment-specific values
            dir('sample-spring-app/k8s') {
                sh """
                    helm upgrade --install fitness-tracker helm-chart \\
                        --namespace ${namespace} \\
                        --create-namespace \\
                        --values helm-chart/values.yaml \\
                        --values ${valuesFile} \\
                        --set image.tag=${IMAGE_TAG} \\
                        --set image.repository=${IMAGE_NAME} \\
                        --wait --timeout 5m
                """
            }
            
            // Switch back to igs context for testing
            sh "kubectl config use-context kind-igs"
        }
    }
}
```

#### 3. Test Connectivity Manually

```bash
# Deploy to dev cluster
kubectl config use-context kind-kind-dev
helm upgrade --install fitness-tracker sample-spring-app/k8s/helm-chart \\
    --namespace dev --create-namespace \\
    --values sample-spring-app/k8s/overlays/dev/values-dev.yaml

# Get dev node IP
DEV_IP=$(docker inspect kind-dev-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')

# Test from igs cluster
kubectl config use-context kind-igs
kubectl run curl-test --image=curlimages/curl:latest --rm -it --restart=Never -- \\
    curl http://$DEV_IP:30080/actuator/health

# Or test from your local machine
curl http://$DEV_IP:30080/actuator/health
```

## ArgoCD Connectivity

ArgoCD connectivity is ALREADY handled when you register clusters!

```bash
# When you run this:
argocd cluster add kind-kind-dev --name dev-cluster

# ArgoCD:
1. Creates a ServiceAccount in kind-dev cluster
2. Gets credentials (token, cert)
3. Stores in secret on igs cluster
4. Can now deploy to kind-dev from igs!
```

**How it works**:
- ArgoCD on `igs` uses Kubernetes API to talk to other clusters
- Uses the ServiceAccount token for authentication
- All communication goes through Kubernetes API server
- No network magic needed - it's built-in!

## Network Diagram

```
┌────────────────────────────────────────────────┐
│        Docker Network (kind network)           │
│                                                │
│  ┌──────────────┐                             │
│  │  igs cluster │                             │
│  │  172.18.0.2  │                             │
│  │              │                             │
│  │ ZAP, JMeter, │                             │
│  │  SoapUI      │                             │
│  │              │                             │
│  │  Can access: │                             │
│  │  172.18.0.3:30080 (dev app) ───────────┐  │
│  │  172.18.0.4:30081 (test app) ─────────┐│  │
│  └──────────────┘                        ││  │
│                                          ││  │
│  ┌──────────────┐                       ││  │
│  │ kind-dev     │◄──────────────────────┘│  │
│  │ 172.18.0.3   │                        │  │
│  │ NodePort:    │                        │  │
│  │   30080      │                        │  │
│  └──────────────┘                        │  │
│                                          │  │
│  ┌──────────────┐                       │  │
│  │ kind-test    │◄──────────────────────┘  │
│  │ 172.18.0.4   │                          │
│  │ NodePort:    │                          │
│  │   30081      │                          │
│  └──────────────┘                          │
│                                            │
└────────────────────────────────────────────┘
```

## Verification Script

Create a script to test connectivity:

```bash
#!/bin/bash
# test-connectivity.sh

echo "Testing cross-cluster connectivity..."

# Get cluster IPs
echo "Getting cluster IPs..."
IGS_IP=$(docker inspect igs-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
DEV_IP=$(docker inspect kind-dev-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
TEST_IP=$(docker inspect kind-test-control-plane --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')

echo "igs cluster: $IGS_IP"
echo "dev cluster: $DEV_IP"
echo "test cluster: $TEST_IP"

# Test from igs cluster to dev app
echo ""
echo "Testing igs → dev connectivity..."
kubectl run test-dev --image=curlimages/curl:latest --rm -i --restart=Never --context kind-igs -- \\
    curl -v http://$DEV_IP:30080/actuator/health

# Test from igs cluster to test app
echo ""
echo "Testing igs → test connectivity..."
kubectl run test-test --image=curlimages/curl:latest --rm -i --restart=Never --context kind-igs -- \\
    curl -v http://$TEST_IP:30081/actuator/health

echo ""
echo "✅ Connectivity tests complete!"
```

## Summary

### Question: How will ZAP/JMeter/SoapUI on igs test apps on other clusters?

**Answer**: Use NodePort + Docker Network

1. **Deploy apps with NodePort** on dev/test clusters
2. **Get cluster IPs** from Docker (they're containers!)
3. **Access via IP:NodePort** from igs cluster
   - Dev: `http://172.18.0.3:30080`
   - Test: `http://172.18.0.4:30081`

### Question: How does ArgoCD deploy to other clusters?

**Answer**: Kubernetes API

- ArgoCD uses Kubernetes API to deploy
- Registered clusters = ArgoCD has credentials
- No network magic needed - it's built-in Kubernetes!

### Question: How does CloudBees CI access apps?

**Answer**: Through testing tools

- CloudBees CI doesn't directly access apps
- CI triggers testing tools (ZAP, JMeter, SoapUI)
- Testing tools access apps via NodePort
- Reports come back to CI

## Next Steps

1. Create overlay values files for dev/test/prod
2. Update Jenkinsfile with dynamic URL logic
3. Test connectivity manually
4. Run pipeline end-to-end

**This solution is simple, reliable, and perfect for Kind clusters!** 🎉
