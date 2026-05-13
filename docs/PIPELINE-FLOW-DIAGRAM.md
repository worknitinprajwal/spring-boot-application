# Complete Pipeline Flow - Deployment → Testing

## Visual Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    CloudBees CI Pipeline                        │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│  1. Checkout     │
│  Code from Git   │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  2. Build &      │
│  Unit Tests      │
│  (Maven)         │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  3. Docker       │
│  Build & Push    │
│  to DockerHub    │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  4. Update       │
│  Helm Chart      │
│  (values.yaml)   │
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│  5. User Selects Environment                 │
│     ┌─────┐  ┌──────┐  ┌──────┐            │
│     │ dev │  │ test │  │ prod │            │
│     └──┬──┘  └───┬──┘  └───┬──┘            │
└────────┼─────────┼─────────┼────────────────┘
         │         │         │
         ▼         ▼         ▼
┌──────────────────────────────────────────────┐
│  6. Deploy to Selected Cluster               │
│     - Switch kubectl context                 │
│     - helm upgrade --install                 │
│     - --wait --timeout 5m                    │
└────────┬─────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│  7. Verify Deployment Success ✅             │
│     - Check pod status (Running?)            │
│     - Check replicas (all ready?)            │
│     - Test health endpoint on target cluster │
│                                              │
│     IF FAILS → ❌ STOP PIPELINE              │
└────────┬─────────────────────────────────────┘
         │
         │ ✅ Deployment Successful
         │
         ▼
┌──────────────────────────────────────────────┐
│  8. Get Application URL for Testing          │
│     - Get cluster IP (docker inspect)        │
│     - Build URL: http://IP:NodePort          │
│     - Example: http://172.18.0.3:30080       │
└────────┬─────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│  9. Verify Cross-Cluster Connectivity ✅     │
│     - From igs cluster                       │
│     - curl http://172.18.0.3:30080/health    │
│     - Retry 3 times if needed                │
│                                              │
│     IF FAILS → ❌ STOP PIPELINE              │
└────────┬─────────────────────────────────────┘
         │
         │ ✅ Connectivity Verified
         │
         ▼
┌──────────────────────────────────────────────┐
│  10. Pre-Test Validation                     │
│      - Log deployment details                │
│      - Confirm testing tools available       │
│      - Ready to start testing                │
└────────┬─────────────────────────────────────┘
         │
         │ ✅ All Checks Passed
         │
         ├─────────────────┬─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
┌────────────────┐ ┌────────────────┐ ┌────────────────┐
│ 11a. SoapUI    │ │ 11b. JMeter    │ │ 11c. ZAP       │
│ API Tests      │ │ Performance    │ │ Security Scan  │
│                │ │ Tests          │ │                │
│ - Test all     │ │ - Load test    │ │ - Spider app   │
│   endpoints    │ │ - 10-50 users  │ │ - Active scan  │
│ - Validate     │ │ - Response     │ │ - Find vulns   │
│   responses    │ │   times        │ │                │
│ - Generate     │ │ - Generate     │ │ - Generate     │
│   JUnit XML    │ │   HTML report  │ │   HTML report  │
│                │ │                │ │                │
│ Tests app at:  │ │ Tests app at:  │ │ Tests app at:  │
│ ${APP_URL}     │ │ ${APP_URL}     │ │ ${APP_URL}     │
└────────┬───────┘ └────────┬───────┘ └────────┬───────┘
         │                  │                  │
         └──────────┬───────┴──────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────┐
│  12. Publish Reports                         │
│      - JUnit test results                    │
│      - HTML reports (JMeter, ZAP)            │
│      - Archive artifacts                     │
│      - CloudBees Unify metrics               │
└────────┬─────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│  13. Summary & Cleanup                       │
│      - Print deployment summary              │
│      - Clean up test artifacts               │
│      - Mark build status (success/failure)   │
└──────────────────────────────────────────────┘
```

## Key Gates in the Pipeline

### Gate 1: Build Success ✅
```groovy
stage('Build & Test') {
    steps {
        sh 'mvn clean package'
        junit 'target/surefire-reports/*.xml'
    }
}
// If build fails → Pipeline STOPS
```

### Gate 2: Deployment Success ✅
```groovy
stage('Deploy to Cluster') {
    steps {
        sh 'helm upgrade --install ... --wait --timeout 5m'
        sh 'kubectl rollout status deployment/${APP_NAME} --timeout=5m'
    }
}
// If deployment fails → Pipeline STOPS
```

### Gate 3: Deployment Verification ✅
```groovy
stage('Verify Deployment Success') {
    steps {
        // Check pod status
        if (podStatus != 'Running') {
            error("Deployment failed!")
        }
        
        // Check replicas
        if (readyReplicas != desiredReplicas) {
            error("Not all replicas ready!")
        }
        
        // Test health endpoint
        sh 'curl http://${APP_NAME}:8080/actuator/health'
    }
}
// If verification fails → Pipeline STOPS
```

### Gate 4: Connectivity Verification ✅
```groovy
stage('Get Application URL for Testing') {
    steps {
        // Verify testing tools can reach app
        retry(3) {
            sh 'curl http://172.18.0.3:30080/actuator/health'
        }
    }
}
// If connectivity fails → Pipeline STOPS
```

### Gate 5: Testing Phase ✅
```groovy
// Only runs if ALL previous gates passed!
stage('API Functional Tests - SoapUI') {
    steps {
        // Run tests against deployed app
    }
}

stage('Performance Tests - JMeter') {
    when { expression { params.RUN_PERFORMANCE_TEST == true } }
    steps {
        // Run performance tests
    }
}

stage('Security Scan - OWASP ZAP') {
    when { expression { params.RUN_SECURITY_SCAN == true } }
    steps {
        // Run security scan
    }
}
```

## Detailed Stage-by-Stage Flow

### Stage: Deploy to Cluster

```
Input:
- Docker image: your-repo/fitness-tracker:123
- Target cluster: kind-kind-dev
- Namespace: dev

Actions:
1. kubectl config use-context kind-kind-dev
2. helm upgrade --install fitness-tracker ... --wait
3. kubectl rollout status deployment/fitness-tracker

Output:
- Deployment status: SUCCESS or FAIL
- If FAIL → Pipeline STOPS ❌
- If SUCCESS → Continue to next stage ✅
```

### Stage: Verify Deployment Success

```
Input:
- Cluster: kind-kind-dev
- Namespace: dev
- App: fitness-tracker

Checks:
1. Pod status = Running? ✅
   kubectl get pods -l app=fitness-tracker
   
2. All replicas ready? ✅
   readyReplicas (2) == desiredReplicas (2)
   
3. Health endpoint works? ✅
   curl http://fitness-tracker:8080/actuator/health
   Response: {"status":"UP"}

Output:
- If ANY check fails → Pipeline STOPS ❌
- If ALL checks pass → Continue ✅
```

### Stage: Get Application URL for Testing

```
Input:
- Environment: dev
- Cluster: kind-kind-dev

Actions:
1. Get cluster IP:
   docker inspect kind-dev-control-plane
   Result: 172.18.0.3
   
2. Build URL:
   http://172.18.0.3:30080
   
3. Test connectivity from igs cluster:
   kubectl run test ... -- curl http://172.18.0.3:30080/health

Output:
- APP_URL: http://172.18.0.3:30080
- If connectivity fails → Pipeline STOPS ❌
- If connectivity works → Continue ✅
```

### Stage: Run Tests (Only if Deployment Successful!)

```
Conditions:
✅ Deployment successful
✅ All replicas ready
✅ Health check passed
✅ Connectivity verified

Tests Run:
1. SoapUI (Always)
   - Tests all API endpoints
   - Validates responses
   - Generates JUnit XML
   
2. JMeter (If RUN_PERFORMANCE_TEST = true)
   - Load test with 10+ users
   - Measures response times
   - Generates HTML report
   
3. ZAP (If RUN_SECURITY_SCAN = true)
   - Spider application
   - Active security scan
   - Finds vulnerabilities
   - Generates HTML report

All tests use: APP_URL = http://172.18.0.3:30080
```

## Example: dev Environment Flow

```
1. Build Docker image
   ✅ Image: myrepo/fitness-tracker:123

2. Deploy to kind-dev cluster
   kubectl config use-context kind-kind-dev
   helm upgrade --install fitness-tracker ...
   ✅ Deployment successful

3. Verify deployment
   - Pods: 2/2 Running ✅
   - Health: {"status":"UP"} ✅
   
4. Get app URL for testing
   - Cluster IP: 172.18.0.3
   - NodePort: 30080
   - URL: http://172.18.0.3:30080
   - Connectivity: ✅ Working

5. Run SoapUI tests
   kubectl exec -n soapui pod -- testrunner.sh
   Target: http://172.18.0.3:30080
   ✅ All tests passed

6. Run JMeter tests (if enabled)
   kubectl exec -n jmeter pod -- jmeter -n ...
   Target: http://172.18.0.3:30080
   ✅ Performance acceptable

7. Run ZAP scan (if enabled)
   curl 'http://zap.zap.svc.cluster.local:8080/JSON/spider/...'
   Target: http://172.18.0.3:30080
   ✅ No high-risk vulnerabilities

8. Publish reports
   ✅ All reports available in Jenkins

9. Pipeline complete! ✅
```

## What Happens if Deployment Fails?

```
Stage: Deploy to Cluster
   helm upgrade --install ... --wait
   ❌ TIMEOUT: deployment did not become ready

Result:
   - Pipeline STOPS immediately
   - Status: FAILED ❌
   - Testing stages are SKIPPED
   - User sees: "Deployment failed! Check logs."

Why?
   - No point running tests if app isn't deployed
   - Tests would fail anyway
   - Faster feedback to developer
```

## What Happens if Tests Fail?

```
Scenario 1: SoapUI test fails
   - Deployment: ✅ SUCCESS
   - Tests: ❌ FAILED
   - Pipeline Status: FAILED ❌
   - JMeter/ZAP: Still run (if enabled)

Scenario 2: High-risk security vulnerability
   - Deployment: ✅ SUCCESS  
   - Tests: ⚠️ UNSTABLE
   - Pipeline Status: UNSTABLE ⚠️
   - Deployment stays running

Scenario 3: Performance degradation
   - Deployment: ✅ SUCCESS
   - Tests: ⚠️ UNSTABLE
   - Pipeline Status: UNSTABLE ⚠️
   - Warning logged, build continues
```

## Summary

### The Flow
1. ✅ Deploy → 2. ✅ Verify → 3. ✅ Get URL → 4. ✅ Test

### Key Points
- **Testing only runs if deployment succeeds**
- **Multiple verification gates before testing**
- **Cross-cluster connectivity tested before tests**
- **All tests target the deployed app via NodePort**
- **Pipeline fails fast if any gate fails**

### Answer to Your Question
> "Once the app is deployed on dev or test cluster successfully, then new workflow/stage should start to perform testing?"

**YES! That's exactly how it works!**

The pipeline has explicit gates:
1. Deploy stage uses `--wait` (waits for deployment)
2. Verify stage checks pod status and health
3. Get URL stage verifies connectivity
4. **Only then** do testing stages run

If deployment fails at any point → Testing stages are SKIPPED! ✅
