# CloudBees Unify Integration Guide

## ✅ Verified Against Official Documentation

This implementation follows the **immediate publishing pattern** - artifacts are registered with CloudBees Unify immediately after they're created, not in a separate stage later.

---

## Integration Pattern: Immediate Publishing

### Principle
**Publish immediately after creation** - Each artifact/scan is registered with Unify as soon as it's generated:

| What | When Published | Where in Pipeline |
|------|---------------|-------------------|
| Security Scan (Aikido) | Immediately after scan completes | Aikido Security Scan stage → post block |
| Docker Image Artifact | Immediately after push to registry | Docker Build & Push stage → after Kaniko push |
| Deployed Artifact | Immediately after deployment verified | Wait for Deployment stage → after health check |
| Test Results | After all tests complete | Publish Test Results stage |

---

## Integration Points

### **1. Security Scan Registration** 🔒 (Immediate)

**API**: `registerSecurityScan()`  
**Documentation**: https://docs.cloudbees.com/docs/cloudbees-unify/latest/continuous-integration/ci-security-scans

**When**: Immediately after Aikido scan completes (in `post { always }` block)

**Implementation**:
```groovy
stage('Aikido Security Scan') {
    steps {
        // Run Aikido scan
        sh 'aikido-api-client scan-release...'
        
        // Convert to SARIF 2.1.0
        sh 'create-sarif...'
    }
    post {
        always {
            // ✅ PUBLISH IMMEDIATELY
            registerSecurityScan(
                artifacts: 'build-artifacts/aikido-scan.sarif',
                format: 'sarif',
                scanner: 'Aikido',
                archive: true
            )
        }
    }
}
```

**Location**: Line ~443-458 in Jenkinsfile.kubernetes

---

### **2. Docker Artifact Registration** 📦 (Immediate)

**API**: `registerBuildArtifactMetadata()`  
**Documentation**: https://docs.cloudbees.com/docs/cloudbees-unify/latest/continuous-integration/ci-register-artifacts

**When**: Immediately after Docker image pushed to registry

**Implementation**:
```groovy
stage('Docker Build & Push') {
    steps {
        container('kaniko') {
            // Build and push Docker image
            sh '/kaniko/executor --destination=${IMAGE_NAME}:${IMAGE_TAG}...'
        }
        
        container('maven') {
            // ✅ PUBLISH IMMEDIATELY (capture UUID)
            script {
                env.DOCKER_ARTIFACT_ID = registerBuildArtifactMetadata(
                    name: "fitness-tracker-docker",
                    version: "${IMAGE_TAG}",
                    url: "docker.io/${IMAGE_NAME}:${IMAGE_TAG}",
                    type: "Docker",
                    label: "development,latest"
                )
                echo "Docker artifact registered (ID: ${env.DOCKER_ARTIFACT_ID})"
            }
        }
    }
}
```

**Location**: Line ~515-530 in Jenkinsfile.kubernetes

**CRITICAL**: The UUID is captured in `env.DOCKER_ARTIFACT_ID` for deployment registration.

---

### **3. Deployed Artifact Registration** 🚀 (Immediate)

**API**: `registerDeployedArtifactMetadata()`  
**Documentation**: https://docs.cloudbees.com/docs/cloudbees-unify/latest/continuous-integration/ci-register-deployed-artifacts

**When**: Immediately after deployment verified healthy

**Implementation**:
```groovy
stage('Wait for Deployment') {
    steps {
        container('maven') {
            script {
                // Verify deployment is healthy
                sh 'kubectl get pods... && curl health-check...'
                
                // ✅ PUBLISH IMMEDIATELY (uses captured UUID)
                registerDeployedArtifactMetadata(
                    artifactId: env.DOCKER_ARTIFACT_ID,  // From step 2
                    targetEnvironment: "development",     // Must exist in Unify
                    labels: "argocd,gitops,fitness-tracker-dev"
                )
            }
        }
    }
}
```

**Location**: Line ~754-768 in Jenkinsfile.kubernetes

**Prerequisites**:
- Artifact must be registered first (step 2)
- Target environment must exist in CloudBees Unify

---

### **4. ZAP Security Scan Registration** 🔐 (Immediate)

**API**: `registerSecurityScan()`  
**When**: Immediately after ZAP scan completes

**Implementation**:
```groovy
stage('Security Testing - OWASP ZAP') {
    steps {
        // Run ZAP scan
        sh 'zap-baseline.py...'
        
        // Convert to SARIF
        sh 'create-sarif...'
    }
    post {
        always {
            container('maven') {
                // ✅ PUBLISH IMMEDIATELY
                registerSecurityScan(
                    artifacts: 'build-artifacts/zap-reports/zap-scan.sarif',
                    format: 'sarif',
                    scanner: 'OWASP ZAP',
                    archive: true
                )
            }
        }
    }
}
```

**Location**: Line ~1407-1423 in Jenkinsfile.kubernetes

---

### **5. Test Results Publishing** 🧪 (Batch)

**API**: `junit` step  
**Documentation**: https://docs.cloudbees.com/docs/cloudbees-unify/latest/continuous-integration/ci-test-results

**When**: After all parallel tests complete

**Implementation**:
```groovy
stage('Publish Test Results to Unify') {
    steps {
        // Convert all test results to JUnit XML
        sh 'xsltproc jtl-to-junit.xsl...'  // JMeter
        sh 'cp zap.xml zap-junit.xml'      // ZAP
        // pytest already generates junit    // UiPath
        
        // Publish all at once
        junit(
            testResults: 'target/surefire-reports/*.xml,build-artifacts/**/junit*.xml',
            allowEmptyResults: true
        )
    }
}
```

**Location**: Line ~2000-2030 in Jenkinsfile.kubernetes

**Test Types**:
- Maven/JUnit (native)
- JMeter (JTL → JUnit XML)
- ZAP (XML → JUnit XML)
- UiPath/pytest (native JUnit XML)

---

## Complete Pipeline Flow

```
1. Setup
2. Checkout
3. Build & Test (Maven)

4. Aikido Security Scan
   └─→ ✅ registerSecurityScan() IMMEDIATELY

5. Docker Build & Push
   └─→ ✅ registerBuildArtifactMetadata() IMMEDIATELY (capture UUID)

6. Update Helm Chart (Git push)
7. ArgoCD Deployment
8. Verify ArgoCD Sync

9. Wait for Deployment (verify healthy)
   └─→ ✅ registerDeployedArtifactMetadata() IMMEDIATELY (use UUID)

10. Prepare Test Directories

11. Run Tests in Parallel:
    ├── API Testing
    ├── JMeter Performance
    ├── ZAP Security
    │   └─→ ✅ registerSecurityScan() IMMEDIATELY
    ├── UiPath UI Automation
    └── Consolidate Metrics

12. Collect Build Artifacts

13. Publish Test Results to Unify (batch)
    └─→ ✅ junit() with all test XMLs
```

---

## Why Immediate Publishing?

### ✅ Benefits

1. **Real-time Visibility**: Artifacts appear in Unify as soon as they're created
2. **Failure Resilience**: If later stages fail, early artifacts are still registered
3. **Accurate Timestamps**: Registration time matches creation time
4. **Parallel-Friendly**: Each stage independently publishes its results
5. **Clear Ownership**: Each stage is responsible for publishing its own artifacts

### ❌ Anti-Pattern: Separate "Publish" Stage

**Don't do this**:
```groovy
stage('Build') { ... }
stage('Deploy') { ... }
stage('Publish Everything') {  // ❌ BAD
    registerBuildArtifactMetadata(...)
    registerDeployedArtifactMetadata(...)
}
```

**Why it's bad**:
- Delayed visibility in Unify
- If "Publish" stage fails, nothing is registered
- Harder to maintain (one stage does everything)
- Doesn't scale with parallel workflows

---

## API Reference

### `registerBuildArtifactMetadata()`

**Returns**: String (UUID of registered artifact)

**Required Parameters**:
- `name`: Artifact name
- `url`: Artifact location
- `version`: Artifact version

**Optional Parameters**:
- `digest`: SHA hash (recommended)
- `label`: Comma-separated labels
- `type`: Artifact type (Docker, Maven, etc.)

**Example**:
```groovy
def artifactId = registerBuildArtifactMetadata(
    name: "my-app",
    version: "1.0.0",
    url: "docker.io/myapp:1.0.0",
    digest: "sha256:abc123",
    type: "Docker",
    label: "prod,latest"
)
// Store UUID for deployment registration
env.ARTIFACT_ID = artifactId
```

---

### `registerDeployedArtifactMetadata()`

**Required Parameters**:
- `targetEnvironment`: Environment name (must exist in Unify)
- **One of**:
  - `artifactId`: UUID from `registerBuildArtifactMetadata()`
  - `artifactUrl`: Direct artifact URL

**Optional Parameters**:
- `labels`: Comma-separated labels
- `allowNoMatchingComponent`: Boolean (default: false)

**Example**:
```groovy
registerDeployedArtifactMetadata(
    artifactId: env.ARTIFACT_ID,  // From build registration
    targetEnvironment: "production",
    labels: "argocd,v1.0.0"
)
```

---

### `registerSecurityScan()`

**Required Parameters**:
- `artifacts`: File pattern (wildcards supported)

**Optional Parameters**:
- `format`: `sarif` (default) or `json`
- `scanner`: Scanner name (REQUIRED if format ≠ sarif)
- `archive`: Boolean (default: true)

**Example**:
```groovy
registerSecurityScan(
    artifacts: 'scan-results.sarif',
    format: 'sarif',
    scanner: 'MyScanner',  // Optional for SARIF
    archive: true
)
```

**SARIF Requirements**:
- Must be SARIF 2.1.0 format
- Schema: `https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json`

---

### `junit` step

**Parameters**:
- `testResults`: Ant-style file pattern
- `allowEmptyResults`: Boolean
- `healthScaleFactor`: Float

**Example**:
```groovy
junit(
    testResults: '**/test-reports/*.xml',
    allowEmptyResults: true,
    healthScaleFactor: 1.0
)
```

---

## Viewing Results in CloudBees Unify

### Artifacts
**Path**: Components → Artifacts

### Deployments
**Path**: Components → Runs → [Build] → Deployments

### Security Scans
**Path**: Components → Security Insights

### Test Results
**Path**: Components → Runs → [Build] → Test results

### Analytics Dashboards
**Path**: Organization → VSM Dashboards
- Software Delivery Activity
- Security Insights
- Test Insights

---

## Troubleshooting

### Artifact Not Appearing in Unify

**Check**:
1. CloudBees Unify plugin installed?
2. Controller integrated with Unify?
3. Using Multibranch Pipeline?
4. Check console output for registration errors

### Deployment Not Linked to Artifact

**Cause**: Didn't capture UUID from `registerBuildArtifactMetadata()`

**Fix**:
```groovy
// ❌ Wrong
registerBuildArtifactMetadata(...)  // UUID lost

// ✅ Correct
def id = registerBuildArtifactMetadata(...)
env.ARTIFACT_ID = id  // Store for later
```

### Security Scan Build Marked Unstable

**Cause**: Using `format: 'json'` without `scanner` parameter

**Fix**:
```groovy
registerSecurityScan(
    artifacts: 'results.json',
    format: 'json',
    scanner: 'MyScanner'  // REQUIRED for non-SARIF
)
```

---

## Implementation Summary

### Stages with Immediate Publishing

| Stage | Line | Publishes |
|-------|------|-----------|
| Aikido Security Scan | ~443-458 | `registerSecurityScan()` |
| Docker Build & Push | ~515-530 | `registerBuildArtifactMetadata()` |
| Wait for Deployment | ~754-768 | `registerDeployedArtifactMetadata()` |
| ZAP Security | ~1407-1423 | `registerSecurityScan()` |
| Publish Test Results | ~2000-2030 | `junit()` |

### Removed Stages
- ~~"Publish to CloudBees Unify"~~ (Line ~1996-2100) - Removed because publishing is now immediate

---

## References

- [CI Overview](https://docs.cloudbees.com/docs/cloudbees-unify/latest/continuous-integration/intro)
- [Register Artifacts](https://docs.cloudbees.com/docs/cloudbees-unify/latest/continuous-integration/ci-register-artifacts)
- [Register Deployments](https://docs.cloudbees.com/docs/cloudbees-unify/latest/continuous-integration/ci-register-deployed-artifacts)
- [Security Scans](https://docs.cloudbees.com/docs/cloudbees-unify/latest/continuous-integration/ci-security-scans)
- [Test Results](https://docs.cloudbees.com/docs/cloudbees-unify/latest/continuous-integration/ci-test-results)
- [CI Analytics](https://docs.cloudbees.com/docs/cloudbees-unify/latest/continuous-integration/ci-analytics)

---

**Last Updated**: 2026-05-26  
**Pipeline Version**: v2.2 (Immediate Publishing Pattern)  
**Pattern**: Publish immediately after creation, not in separate stage
