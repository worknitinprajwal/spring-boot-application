# Fitness Tracker - Sample Spring Boot Application

A sample Spring Boot REST API for tracking fitness workouts, demonstrating complete GitOps CI/CD with CloudBees CI, ArgoCD multi-cluster deployment, and comprehensive automated testing.

## Features

- REST API for managing workouts (CRUD operations)
- Spring Boot Actuator for health checks and monitoring
- H2 in-memory database
- Docker containerized with multi-stage builds
- Kubernetes Helm chart deployment
- **Complete GitOps CI/CD pipeline** with CloudBees CI + ArgoCD
- **Multi-cluster deployment** (dev → test → prod)
- **Automated testing** (Unit, Functional, Performance, Security)
- **Jira integration** for automated issue tracking

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/workouts` | Get all workouts |
| GET | `/api/workouts/{id}` | Get workout by ID |
| POST | `/api/workouts` | Create new workout |
| PUT | `/api/workouts/{id}` | Update workout |
| DELETE | `/api/workouts/{id}` | Delete workout |
| GET | `/api/workouts/health` | Health check |
| GET | `/actuator/health` | Spring Actuator health |

## Quick Start

### Local Development

```bash
# Run with Maven
mvn spring-boot:run

# Or build and run JAR
mvn clean package
java -jar target/fitness-tracker-1.0.0.jar

# Access at http://localhost:8080
```

### Docker

```bash
# Build image
docker build -t fitness-tracker:latest .

# Run container
docker run -p 8080:8080 fitness-tracker:latest
```

### Kubernetes with Helm

```bash
# Install
helm install fitness-tracker k8s/helm-chart --namespace dev --create-namespace

# Upgrade
helm upgrade fitness-tracker k8s/helm-chart --namespace dev

# Uninstall
helm uninstall fitness-tracker --namespace dev
```

## GitOps CI/CD Pipeline

### Architecture

```
GitHub Push → Webhook → CloudBees CI (kind-igs)
    ↓
Build & Test → Docker Hub → Update Git values.yaml
    ↓
ArgoCD Detects Change → Multi-Cluster Sync
    ↓
kind-dev (develop) | kind-test (main)
```

### Pipeline Stages (Jenkinsfile.kubernetes)

1. **Setup & Checkout** - Clone repository, detect changes
2. **Build & Test** - Maven build with JUnit tests
3. **Aikido Security Scan** - Dependency vulnerability scanning
4. **Docker Build & Push** - Build image with build-specific tag, push to DockerHub
5. **Update Helm Chart** - Commit new image tag to Git values.yaml
6. **ArgoCD Deployment** - Trigger ArgoCD sync to target cluster
7. **Verify ArgoCD Sync** - Wait for ArgoCD to sync successfully
8. **Wait for Deployment** - Verify pods running with correct image
9. **Run Tests in Parallel** - SoapUI API + JMeter performance + ZAP security tests
10. **Collect Build Artifacts** - Archive test results and reports
11. **Publish to CloudBees Unify** - Send metrics and results
12. **Jira Integration** - Auto-create ticket on pipeline failure

### Branch Strategy

| Branch | ArgoCD App | Target Cluster | Image Tag Pattern |
|--------|------------|----------------|-------------------|
| `develop` | fitness-tracker-dev | kind-dev (172.18.0.5:6443) | `develop-{BUILD_NUMBER}` |
| `main` | fitness-tracker-test | kind-test (172.18.0.8:6443) | `main-{BUILD_NUMBER}` |

### Key Features

- **Automated GitOps**: ArgoCD reads image tags from Git, no manual intervention
- **Multi-cluster**: Single ArgoCD on kind-igs manages deployments to multiple clusters
- **Dynamic Tagging**: Each build creates unique image tag (e.g., `develop-5`, `main-12`)
- **Failure Tracking**: Automatic Jira ticket creation on pipeline failures
- **Comprehensive Testing**: Parallel execution of API, performance, and security tests

## Testing

### Functional Tests (SoapUI)

Tests all API endpoints for correct responses.

```bash
# Run SoapUI tests
kubectl exec -n soapui deployment/soapui -- testrunner.sh /path/to/project.xml
```

### Performance Tests (JMeter)

Load testing with configurable users and duration.

```bash
# Run JMeter test
kubectl exec -n jmeter deployment/jmeter -- jmeter -n -t load-test.jmx -l results.jtl
```

### Security Tests (OWASP ZAP)

Automated security vulnerability scanning.

```bash
# Spider application
curl 'http://zap.zap.svc.cluster.local:8080/JSON/spider/action/scan/?url=http://fitness-tracker'

# Active scan
curl 'http://zap.zap.svc.cluster.local:8080/JSON/ascan/action/scan/?url=http://fitness-tracker'

# Get results
curl 'http://zap.zap.svc.cluster.local:8080/JSON/core/view/alerts/'
```

## Multi-Cluster GitOps Deployment

### Cluster Architecture

| Cluster | Purpose | Context | Server URL | Deployed From |
|---------|---------|---------|------------|---------------|
| **kind-igs** | CI/CD Control Plane | kind-igs | 172.18.0.2:6443 | CloudBees CI + ArgoCD running here |
| **kind-dev** | Development | kind-kind-dev | 172.18.0.5:6443 | `develop` branch via ArgoCD |
| **kind-test** | QA/Testing | kind-kind-test | 172.18.0.8:6443 | `main` branch via ArgoCD |

### ArgoCD Configuration

ArgoCD running on **kind-igs** manages applications on **kind-dev** and **kind-test**:

```bash
# View registered clusters
argocd cluster list

# Check application status
argocd app get fitness-tracker-dev
argocd app get fitness-tracker-test

# Manual sync (if needed)
argocd app sync fitness-tracker-dev
```

**ArgoCD UI**: https://backspin-feline-pesticide.ngrok-free.dev/argocd  
**Credentials**: admin / GJIRlUm5H-YShZnM

### How It Works

1. **Developer pushes** to `develop` or `main` branch
2. **GitHub webhook** triggers Jenkins build on kind-igs
3. **Jenkins builds** Docker image with unique tag (e.g., `main-5`)
4. **Jenkins pushes** image to Docker Hub
5. **Jenkins updates** `k8s/helm-chart/values.yaml` in Git with new tag
6. **ArgoCD detects** Git change automatically (polls every 3 minutes)
7. **ArgoCD syncs** to target cluster using Helm chart from Git
8. **Kubernetes deploys** pods with the new image tag
9. **Pipeline verifies** deployment health and runs tests

### GitOps Best Practices

✅ **Single Source of Truth**: Git repository contains desired state  
✅ **Declarative Configuration**: Helm charts define application state  
✅ **Automated Sync**: ArgoCD continuously reconciles cluster state with Git  
✅ **Audit Trail**: All changes tracked via Git commits  
✅ **Rollback**: Easy rollback by reverting Git commits

See [ARGOCD-ACCESS-GUIDE.md](../ARGOCD-ACCESS-GUIDE.md) and [create-argocd-apps.sh](../create-argocd-apps.sh) for setup details.

## Configuration

### Required Jenkins Credentials

| Credential ID | Type | Purpose |
|---------------|------|---------|
| `DOCKERHUB_PASSWORD` | Secret text | Docker Hub authentication |
| `GITHUB_TOKEN` | Secret text | Git push authentication |
| `GITHUB_USERNAME` | Secret text | Git username |
| `AIKIDO_API_KEY` | Secret text | Aikido security scanning |
| `jira-api-token` | Secret text | Jira ticket creation on failures |

### Git Commit with Jira Ticket

Link commits to Jira issues for traceability:

```bash
git commit -m "CBDEMO-123: implement new feature"
git push origin main
```

This creates automatic links in Jira between tickets and code changes.

### Update Configuration Files

**Jenkinsfile.kubernetes** - Update these variables:
```groovy
DOCKER_REPO = 'anuddeeph2'  // Your Docker Hub username
JIRA_SITE = 'https://cloudbees.atlassian.net'
JIRA_PROJECT_KEY = 'CBDEMO'
JIRA_EMAIL = 'your-email@example.com'
```

**k8s/helm-chart/values.yaml** - Image repository:
```yaml
image:
  repository: anuddeeph2/sample-spring-boot-app  // Your Docker Hub repo
  tag: develop-1  # Managed by Jenkins, don't edit manually
  pullPolicy: Always
```

## Project Structure

```
sample-spring-app/
├── src/
│   └── main/
│       ├── java/com/example/demo/
│       │   ├── FitnessTrackerApplication.java
│       │   ├── Workout.java
│       │   ├── WorkoutRepository.java
│       │   └── WorkoutController.java
│       └── resources/
│           └── application.yml
├── k8s/
│   └── helm-chart/
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/
│           ├── deployment.yaml
│           ├── service.yaml
│           └── ingress.yaml
├── Dockerfile
├── Jenkinsfile
├── pom.xml
└── README.md
```

## Example API Usage

### Create a Workout

```bash
curl -X POST http://fitness-tracker.local/api/workouts \
  -H "Content-Type: application/json" \
  -d '{
    "type": "Running",
    "duration": 30,
    "caloriesBurned": 300,
    "date": "2024-01-15T10:00:00",
    "notes": "Morning run"
  }'
```

### Get All Workouts

```bash
curl http://fitness-tracker.local/api/workouts
```

### Update a Workout

```bash
curl -X PUT http://fitness-tracker.local/api/workouts/1 \
  -H "Content-Type: application/json" \
  -d '{
    "type": "Running",
    "duration": 45,
    "caloriesBurned": 450,
    "date": "2024-01-15T10:00:00",
    "notes": "Extended morning run"
  }'
```

### Delete a Workout

```bash
curl -X DELETE http://fitness-tracker.local/api/workouts/1
```

## Monitoring

Access Spring Boot Actuator endpoints:

```bash
# Health check
curl http://fitness-tracker.local/actuator/health

# Metrics
curl http://fitness-tracker.local/actuator/metrics

# All endpoints
curl http://fitness-tracker.local/actuator
```

## Troubleshooting

### Pipeline fails to push to DockerHub

- Check DockerHub credentials in Jenkins
- Verify Docker registry is accessible
- Ensure DockerHub username is correct in Jenkinsfile

### ArgoCD not syncing

- Check ArgoCD Application status: `argocd app get fitness-tracker-dev`
- Verify Git repository is accessible
- Check if Helm chart is valid: `helm lint k8s/helm-chart`

### Tests failing

- SoapUI: Check if application is accessible from soapui pod
- JMeter: Verify target URL is correct
- ZAP: Ensure ZAP service is running: `kubectl get pods -n zap`

## Related Documentation

- [MULTI-CLUSTER-ARGOCD-SETUP.md](../MULTI-CLUSTER-ARGOCD-SETUP.md) - Multi-cluster setup guide
- [ZAP-INTEGRATION-GUIDE.md](../ZAP-INTEGRATION-GUIDE.md) - ZAP security testing
- [JMETER-INTEGRATION-GUIDE.md](../JMETER-INTEGRATION-GUIDE.md) - JMeter performance testing
- [SOAPUI-INTEGRATION-GUIDE.md](../SOAPUI-INTEGRATION-GUIDE.md) - SoapUI API testing
- [COMPLETE-TESTING-STACK.md](../COMPLETE-TESTING-STACK.md) - Testing stack overview

## License

MIT License - Sample application for demonstration purposes.
# Test webhook trigger - Mon May 18 13:16:29 IST 2026
# Webhook test - 2026-05-18 13:33:14
# Webhook test - 2026-05-18 13:44:44
