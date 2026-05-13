# Apache JMeter Integration with CloudBees CI

## Overview

Apache JMeter is an open-source performance and load testing tool designed to test the performance of web applications, APIs, databases, and other services.

## What is JMeter?

**Apache JMeter** is a Java-based application designed to load test and measure performance of various services.

### What Does JMeter Do?

1. **Performance Testing**
   - Load testing (simulate multiple users)
   - Stress testing (test system limits)
   - Endurance testing (long-running tests)

2. **Protocol Support**
   - HTTP/HTTPS (Web applications)
   - REST/SOAP APIs
   - FTP, JDBC (Database)
   - JMS, LDAP, TCP, and more

3. **Metrics Collection**
   - Response times
   - Throughput (requests/second)
   - Error rates
   - Latency percentiles (p95, p99)

4. **Reporting**
   - HTML dashboards
   - CSV data files
   - Graphs and charts
   - Real-time monitoring

## JMeter Deployment Details

- **Namespace**: `jmeter`
- **Service**: `jmeter.jmeter.svc.cluster.local:8080`
- **Storage**: NFS persistent storage (5Gi)
- **Mode**: Command-line execution

## How to Use JMeter

### Method 1: Interactive Shell Access

Access JMeter container to run tests manually:

```bash
# Access JMeter pod
kubectl exec -it -n jmeter deployment/jmeter -- /bin/bash

# Inside the container, run JMeter
jmeter -n -t /path/to/test-plan.jmx -l results.jtl -e -o /path/to/report
```

### Method 2: Run JMeter from Host

```bash
# Copy test plan to JMeter pod
kubectl cp my-test-plan.jmx jmeter/jmeter-pod:/tmp/test-plan.jmx

# Execute test
kubectl exec -n jmeter deployment/jmeter -- jmeter -n -t /tmp/test-plan.jmx -l /tmp/results.jtl

# Copy results back
kubectl cp jmeter/jmeter-pod:/tmp/results.jtl ./results.jtl
```

### Method 3: CloudBees CI Pipeline Integration

## Sample JMeter Test Plan

Create a simple test plan (save as `sample-api-test.jmx`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="API Performance Test">
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Users">
        <intProp name="ThreadGroup.num_threads">10</intProp>
        <intProp name="ThreadGroup.ramp_time">5</intProp>
        <longProp name="ThreadGroup.duration">60</longProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="HTTP Request">
          <stringProp name="HTTPSampler.domain">${__P(target_host,example.com)}</stringProp>
          <stringProp name="HTTPSampler.port">80</stringProp>
          <stringProp name="HTTPSampler.path">/api/endpoint</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

## CloudBees CI Pipeline Examples

### Example 1: Basic Performance Test

```groovy
pipeline {
    agent any
    
    environment {
        JMETER_POD = sh(
            script: "kubectl get pod -n jmeter -l app=jmeter -o jsonpath='{.items[0].metadata.name}'",
            returnStdout: true
        ).trim()
    }
    
    stages {
        stage('Deploy Application') {
            steps {
                echo 'Deploying application...'
                sh 'kubectl apply -f k8s/deployment.yaml'
                sh 'kubectl wait --for=condition=ready pod -l app=myapp --timeout=60s'
            }
        }
        
        stage('Upload Test Plan') {
            steps {
                echo 'Uploading JMeter test plan...'
                sh "kubectl cp test-plans/api-load-test.jmx jmeter/${JMETER_POD}:/tmp/test-plan.jmx"
            }
        }
        
        stage('Run Performance Test') {
            steps {
                echo 'Executing JMeter test...'
                sh """
                    kubectl exec -n jmeter ${JMETER_POD} -- \\
                        jmeter -n \\
                        -t /tmp/test-plan.jmx \\
                        -l /tmp/results.jtl \\
                        -e -o /tmp/report \\
                        -Jtarget_host=myapp.default.svc.cluster.local \\
                        -Jtarget_port=8080
                """
            }
        }
        
        stage('Collect Results') {
            steps {
                echo 'Collecting test results...'
                sh "kubectl cp jmeter/${JMETER_POD}:/tmp/results.jtl ./jmeter-results.jtl"
                sh "kubectl exec -n jmeter ${JMETER_POD} -- tar czf /tmp/report.tar.gz -C /tmp report"
                sh "kubectl cp jmeter/${JMETER_POD}:/tmp/report.tar.gz ./jmeter-report.tar.gz"
                sh "tar xzf jmeter-report.tar.gz"
                
                archiveArtifacts artifacts: 'jmeter-results.jtl,jmeter-report.tar.gz'
                
                publishHTML([
                    reportDir: 'report',
                    reportFiles: 'index.html',
                    reportName: 'JMeter Performance Report'
                ])
            }
        }
        
        stage('Performance Gate') {
            steps {
                script {
                    // Parse results and check against thresholds
                    def results = sh(
                        script: """
                            kubectl exec -n jmeter ${JMETER_POD} -- awk -F',' '
                                NR>1 {
                                    sum+=\$2; count++; 
                                    if(\$8=="false") errors++
                                }
                                END {
                                    print "avg="int(sum/count)"|errors="errors
                                }' /tmp/results.jtl
                        """,
                        returnStdout: true
                    ).trim()
                    
                    echo "Performance Results: ${results}"
                    
                    // Parse results
                    def metrics = results.split('\\|').collectEntries { 
                        def (key, value) = it.split('=')
                        [(key): value.toInteger()]
                    }
                    
                    echo "Average Response Time: ${metrics.avg}ms"
                    echo "Error Count: ${metrics.errors}"
                    
                    // Check thresholds
                    if (metrics.avg > 2000) {
                        unstable("Average response time ${metrics.avg}ms exceeds threshold of 2000ms")
                    }
                    
                    if (metrics.errors > 10) {
                        error("Error count ${metrics.errors} exceeds threshold of 10")
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo 'Cleaning up...'
            sh """
                kubectl exec -n jmeter ${JMETER_POD} -- rm -rf /tmp/test-plan.jmx /tmp/results.jtl /tmp/report /tmp/report.tar.gz || true
            """
        }
    }
}
```

### Example 2: Distributed Load Testing

```groovy
pipeline {
    agent any
    
    stages {
        stage('Distributed Load Test') {
            steps {
                script {
                    // Get all JMeter pods (if you scale up replicas)
                    def jmeterPods = sh(
                        script: "kubectl get pods -n jmeter -l app=jmeter -o jsonpath='{.items[*].metadata.name}'",
                        returnStdout: true
                    ).trim().split()
                    
                    echo "Running distributed test across ${jmeterPods.size()} JMeter instances"
                    
                    // Upload test plan to all pods
                    jmeterPods.each { pod ->
                        sh "kubectl cp test-plan.jmx jmeter/${pod}:/tmp/test-plan.jmx"
                    }
                    
                    // Start tests in parallel
                    def parallelTests = [:]
                    jmeterPods.eachWithIndex { pod, index ->
                        parallelTests["JMeter-${index}"] = {
                            sh """
                                kubectl exec -n jmeter ${pod} -- \\
                                    jmeter -n -t /tmp/test-plan.jmx \\
                                    -l /tmp/results-${index}.jtl
                            """
                        }
                    }
                    
                    parallel parallelTests
                    
                    // Collect all results
                    jmeterPods.eachWithIndex { pod, index ->
                        sh "kubectl cp jmeter/${pod}:/tmp/results-${index}.jtl ./results-${index}.jtl"
                    }
                }
            }
        }
    }
}
```

### Example 3: API Performance Testing with JSON Report

```groovy
pipeline {
    agent any
    
    stages {
        stage('API Load Test') {
            steps {
                script {
                    def jmeterPod = sh(
                        script: "kubectl get pod -n jmeter -l app=jmeter -o jsonpath='{.items[0].metadata.name}'",
                        returnStdout: true
                    ).trim()
                    
                    // Create simple test plan inline
                    writeFile file: 'api-test.jmx', text: '''<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="API Test">
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Users">
        <intProp name="ThreadGroup.num_threads">50</intProp>
        <intProp name="ThreadGroup.ramp_time">10</intProp>
        <longProp name="ThreadGroup.duration">60</longProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="API Request">
          <stringProp name="HTTPSampler.domain">${__P(host)}</stringProp>
          <stringProp name="HTTPSampler.path">/api/health</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>'''
                    
                    sh "kubectl cp api-test.jmx jmeter/${jmeterPod}:/tmp/api-test.jmx"
                    
                    sh """
                        kubectl exec -n jmeter ${jmeterPod} -- \\
                            jmeter -n -t /tmp/api-test.jmx \\
                            -l /tmp/results.csv \\
                            -Jhost=myapp.default.svc.cluster.local
                    """
                    
                    sh "kubectl cp jmeter/${jmeterPod}:/tmp/results.csv ./jmeter-results.csv"
                    archiveArtifacts artifacts: 'jmeter-results.csv'
                }
            }
        }
    }
}
```

## JMeter Command Line Options

Common JMeter CLI options:

```bash
jmeter -n -t <test_plan.jmx> -l <results.jtl> [options]

Options:
  -n                    Non-GUI mode
  -t <file>            Test plan file
  -l <file>            Log file (results)
  -e                   Generate report after test
  -o <folder>          Output folder for report
  -j <file>            JMeter log file
  -J<property>=<value> Define JMeter property
  -G<file>             Property file
  -D<prop>=<value>     Define system property
  -X                   Stop on error
```

## Performance Testing Best Practices

1. **Start Small**: Begin with 1-5 users, then scale up
2. **Ramp Up Gradually**: Don't hit with max load immediately
3. **Monitor Resources**: Watch CPU, memory, network during tests
4. **Set Realistic Thresholds**: Base on SLA requirements
5. **Test Regularly**: Run performance tests on every release
6. **Distributed Testing**: Use multiple JMeter instances for high load

## Common Test Scenarios

### 1. Baseline Performance Test
- 10-50 concurrent users
- 5-10 minute duration
- Establish baseline metrics

### 2. Load Test
- Expected peak load (e.g., 500 users)
- 15-30 minute duration
- Verify system handles normal peak load

### 3. Stress Test
- 2-3x expected peak load
- Increase until system breaks
- Find breaking point

### 4. Endurance/Soak Test
- Normal load
- 4-8 hour duration
- Check for memory leaks, degradation

## Scaling JMeter

Scale JMeter deployment for distributed testing:

```bash
# Scale to 3 replicas
kubectl scale deployment jmeter -n jmeter --replicas=3

# Check status
kubectl get pods -n jmeter
```

## Accessing JMeter Logs

```bash
# View JMeter logs
kubectl logs -n jmeter deployment/jmeter

# Follow logs in real-time
kubectl logs -n jmeter deployment/jmeter -f

# Get logs from specific pod
kubectl logs -n jmeter <pod-name>
```

## Troubleshooting

### Test Plan Not Found
```bash
# Verify file exists
kubectl exec -n jmeter deployment/jmeter -- ls -la /tmp/

# Check file permissions
kubectl exec -n jmeter deployment/jmeter -- cat /tmp/test-plan.jmx
```

### Out of Memory
```bash
# Check JMeter heap settings
kubectl exec -n jmeter deployment/jmeter -- env | grep HEAP

# Increase memory in deployment (edit values.yaml)
resources:
  limits:
    memory: 4Gi
```

## Next Steps

1. Create your first JMeter test plan (.jmx file)
2. Test it manually using `kubectl exec`
3. Integrate into CloudBees CI pipeline
4. Set performance thresholds
5. Add to CI/CD workflow

## Additional Resources

- JMeter Documentation: https://jmeter.apache.org/usermanual/
- JMeter Best Practices: https://jmeter.apache.org/usermanual/best-practices.html
- Creating Test Plans: https://jmeter.apache.org/usermanual/build-test-plan.html
