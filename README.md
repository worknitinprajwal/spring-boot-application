# Fitness Tracker - Sample Spring Boot Application

A sample Spring Boot REST API for tracking fitness workouts, demonstrating complete CI/CD with CloudBees CI, ArgoCD, and comprehensive testing.

## Features

- REST API for managing workouts (CRUD operations)
- Spring Boot Actuator for health checks
- H2 in-memory database
- Docker containerized
- Kubernetes Helm chart
- Complete CI/CD pipeline with testing

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

## CI/CD Pipeline

The Jenkins pipeline (`Jenkinsfile`) includes:

1. **Build & Test** - Maven build with unit tests
2. **Docker Build & Push** - Build image and push to DockerHub
3. **Update Helm Chart** - Update values.yaml with new image tag
4. **Deploy** - Deploy to selected environment (dev/test/prod)
5. **API Tests** - SoapUI functional testing
6. **Performance Tests** - JMeter load testing (optional)
7. **Security Scan** - OWASP ZAP security scanning
8. **Publish Reports** - Send results to CloudBees Unify

### Pipeline Parameters

- `DEPLOY_ENV`: Choose deployment environment (dev/test/prod)
- `RUN_SECURITY_SCAN`: Enable/disable ZAP security scan
- `RUN_PERFORMANCE_TEST`: Enable/disable JMeter performance test

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

## Multi-Cluster Deployment

The application can be deployed to multiple Kubernetes clusters:

- **kind-dev**: Development environment
- **kind-test**: QA/Testing environment
- **kind-igs**: Production environment

ArgoCD manages deployments with GitOps approach.

See [MULTI-CLUSTER-ARGOCD-SETUP.md](../MULTI-CLUSTER-ARGOCD-SETUP.md) for details.

## Configuration

### DockerHub Credentials

Create Jenkins credential with ID: `dockerhub-credentials`

```bash
# In Jenkins: Manage Jenkins → Credentials → Add Credentials
# Type: Username with password
# ID: dockerhub-credentials
# Username: your-dockerhub-username
# Password: your-dockerhub-token
```

### GitHub Credentials

Create Jenkins credential with ID: `github-credentials`

```bash
# In Jenkins: Manage Jenkins → Credentials → Add Credentials
# Type: Username with password
# ID: github-credentials
# Username: your-github-username
# Password: your-github-token
```

### Update Jenkinsfile

1. Change `DOCKER_REPO` to your DockerHub username
2. Change Git push URL to your repository
3. Adjust cluster contexts if needed

### Update Helm values.yaml

```yaml
image:
  repository: your-dockerhub-username/fitness-tracker
  tag: latest
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
