# SoapUI Integration with CloudBees CI

## Overview

SoapUI is an open-source API testing tool for SOAP and REST APIs, providing functional testing, load testing, and API mocking capabilities.

## What is SoapUI?

**SoapUI** is a comprehensive API testing tool that allows you to test SOAP web services, REST APIs, and other web services.

### What Does SoapUI Do?

1. **Functional Testing**
   - Test SOAP and REST APIs
   - Validate request/response
   - Assert response data (JSON, XML)
   - Data-driven testing

2. **API Testing Features**
   - SOAP 1.1 and 1.2
   - REST/HTTP APIs
   - JSON, XML validation
   - Schema validation
   - Security testing (WS-Security)

3. **Test Automation**
   - Create reusable test suites
   - Parameterized tests
   - Groovy scripting support
   - CI/CD integration

4. **Reporting**
   - JUnit-style XML reports
   - HTML reports
   - Detailed assertion results
   - Test metrics and statistics

## SoapUI Deployment Details

- **Namespace**: `soapui`
- **Service**: `soapui.soapui.svc.cluster.local:8080`
- **Mode**: Command-line test runner
- **Image**: smartbear/soapui-testrunner (or ddavison/soapui)

## How to Use SoapUI

### Method 1: Interactive Shell Access

Access SoapUI container to run tests manually:

```bash
# Access SoapUI pod
kubectl exec -it -n soapui deployment/soapui -- /bin/bash

# Inside the container, run test
testrunner.sh /path/to/project.xml
```

### Method 2: Run SoapUI Tests from Host

```bash
# Copy SoapUI project to pod
kubectl cp my-soapui-project.xml soapui/soapui-pod:/tmp/project.xml

# Execute tests
kubectl exec -n soapui deployment/soapui -- testrunner.sh /tmp/project.xml

# Copy results back
kubectl cp soapui/soapui-pod:/tmp/test-results.xml ./test-results.xml
```

### Method 3: CloudBees CI Pipeline Integration

## Sample SoapUI Project

Create a simple SoapUI project (save as `api-test-project.xml`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<con:soapui-project name="API Test Project" xmlns:con="http://eviware.com/soapui/config">
  <con:settings/>
  <con:interface name="REST API" type="rest">
    <con:resource name="Health Check" path="/health">
      <con:method name="GET">
        <con:request name="Request 1">
          <con:endpoint>http://myapp.default.svc.cluster.local:8080</con:endpoint>
        </con:request>
      </con:method>
    </con:resource>
  </con:interface>
  <con:testSuite name="API Test Suite">
    <con:testCase name="Health Check Test">
      <con:testStep type="restrequest" name="Health Check Request">
        <con:config>
          <con:restRequest>
            <con:method>GET</con:method>
            <con:endpoint>http://myapp.default.svc.cluster.local:8080</con:endpoint>
            <con:resource>/health</con:resource>
          </con:restRequest>
          <con:assertion type="Valid HTTP Status Codes" name="Status Code">
            <con:configuration>
              <codes>200</codes>
            </con:configuration>
          </con:assertion>
        </con:config>
      </con:testStep>
    </con:testCase>
  </con:testSuite>
</con:soapui-project>
```

## CloudBees CI Pipeline Examples

### Example 1: Basic REST API Test

```groovy
pipeline {
    agent any
    
    environment {
        SOAPUI_POD = sh(
            script: "kubectl get pod -n soapui -l app=soapui -o jsonpath='{.items[0].metadata.name}'",
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
        
        stage('Upload SoapUI Project') {
            steps {
                echo 'Uploading SoapUI project...'
                sh "kubectl cp soapui-projects/api-tests.xml soapui/${SOAPUI_POD}:/tmp/project.xml"
            }
        }
        
        stage('Run API Tests') {
            steps {
                echo 'Executing SoapUI tests...'
                sh """
                    kubectl exec -n soapui ${SOAPUI_POD} -- \\
                        testrunner.sh \\
                        -f /tmp/reports \\
                        -r \\
                        -j \\
                        /tmp/project.xml
                """
            }
        }
        
        stage('Collect Results') {
            steps {
                echo 'Collecting test results...'
                
                // Copy JUnit XML reports
                sh """
                    kubectl exec -n soapui ${SOAPUI_POD} -- \\
                        tar czf /tmp/reports.tar.gz -C /tmp reports
                """
                sh "kubectl cp soapui/${SOAPUI_POD}:/tmp/reports.tar.gz ./soapui-reports.tar.gz"
                sh "tar xzf soapui-reports.tar.gz"
                
                // Archive artifacts
                archiveArtifacts artifacts: 'reports/*.xml,reports/*.txt'
                
                // Publish JUnit results
                junit 'reports/TEST-*.xml'
            }
        }
    }
    
    post {
        always {
            echo 'Cleaning up...'
            sh """
                kubectl exec -n soapui ${SOAPUI_POD} -- \\
                    rm -rf /tmp/project.xml /tmp/reports /tmp/reports.tar.gz || true
            """
        }
        success {
            echo 'All API tests passed!'
        }
        failure {
            echo 'API tests failed - check reports for details'
        }
    }
}
```

### Example 2: SOAP Web Service Testing

```groovy
pipeline {
    agent any
    
    stages {
        stage('Test SOAP Service') {
            steps {
                script {
                    def soapuiPod = sh(
                        script: "kubectl get pod -n soapui -l app=soapui -o jsonpath='{.items[0].metadata.name}'",
                        returnStdout: true
                    ).trim()
                    
                    // Create SOAP test project
                    writeFile file: 'soap-test.xml', text: '''<?xml version="1.0" encoding="UTF-8"?>
<con:soapui-project name="SOAP Test">
  <con:interface name="Calculator Service" type="wsdl">
    <con:definition>http://www.dneonline.com/calculator.asmx?WSDL</con:definition>
  </con:interface>
  <con:testSuite name="Calculator Tests">
    <con:testCase name="Add Test">
      <con:testStep type="request" name="Add Request">
        <con:config>
          <con:request name="Add">
            <con:endpoint>http://www.dneonline.com/calculator.asmx</con:endpoint>
            <con:request><![CDATA[
              <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                <soapenv:Body>
                  <Add xmlns="http://tempuri.org/">
                    <intA>5</intA>
                    <intB>3</intB>
                  </Add>
                </soapenv:Body>
              </soapenv:Envelope>
            ]]></con:request>
          </con:request>
          <con:assertion type="XPath Match">
            <con:configuration>
              <path>//AddResult</path>
              <content>8</content>
            </con:configuration>
          </con:assertion>
        </con:config>
      </con:testStep>
    </con:testCase>
  </con:testSuite>
</con:soapui-project>'''
                    
                    sh "kubectl cp soap-test.xml soapui/${soapuiPod}:/tmp/soap-test.xml"
                    
                    sh """
                        kubectl exec -n soapui ${soapuiPod} -- \\
                            testrunner.sh -r -j -f /tmp/reports /tmp/soap-test.xml
                    """
                    
                    sh "kubectl exec -n soapui ${soapuiPod} -- tar czf /tmp/reports.tar.gz -C /tmp reports"
                    sh "kubectl cp soapui/${soapuiPod}:/tmp/reports.tar.gz ./soap-reports.tar.gz"
                    sh "tar xzf soap-reports.tar.gz"
                    
                    junit 'reports/TEST-*.xml'
                }
            }
        }
    }
}
```

### Example 3: Data-Driven API Testing

```groovy
pipeline {
    agent any
    
    stages {
        stage('Data-Driven API Tests') {
            steps {
                script {
                    def soapuiPod = sh(
                        script: "kubectl get pod -n soapui -l app=soapui -o jsonpath='{.items[0].metadata.name}'",
                        returnStdout: true
                    ).trim()
                    
                    // Copy test data CSV
                    sh "kubectl cp test-data.csv soapui/${soapuiPod}:/tmp/test-data.csv"
                    
                    // Copy SoapUI project with data source
                    sh "kubectl cp data-driven-test.xml soapui/${soapuiPod}:/tmp/project.xml"
                    
                    // Run tests with specific test suite
                    sh """
                        kubectl exec -n soapui ${soapuiPod} -- \\
                            testrunner.sh \\
                            -s "Data Driven Suite" \\
                            -f /tmp/reports \\
                            -r -j \\
                            /tmp/project.xml
                    """
                    
                    sh "kubectl exec -n soapui ${soapuiPod} -- tar czf /tmp/reports.tar.gz -C /tmp reports"
                    sh "kubectl cp soapui/${soapuiPod}:/tmp/reports.tar.gz ./reports.tar.gz"
                    sh "tar xzf reports.tar.gz"
                    
                    junit 'reports/TEST-*.xml'
                }
            }
        }
    }
}
```

### Example 4: Security Testing (WS-Security)

```groovy
pipeline {
    agent any
    
    stages {
        stage('API Security Tests') {
            steps {
                script {
                    def soapuiPod = sh(
                        script: "kubectl get pod -n soapui -l app=soapui -o jsonpath='{.items[0].metadata.name}'",
                        returnStdout: true
                    ).trim()
                    
                    // Copy security test project
                    sh "kubectl cp security-tests.xml soapui/${soapuiPod}:/tmp/security-test.xml"
                    
                    // Run security scans
                    sh """
                        kubectl exec -n soapui ${soapuiPod} -- \\
                            securitytestrunner.sh \\
                            -f /tmp/security-reports \\
                            /tmp/security-test.xml
                    """
                    
                    sh "kubectl exec -n soapui ${soapuiPod} -- tar czf /tmp/sec-reports.tar.gz -C /tmp security-reports"
                    sh "kubectl cp soapui/${soapuiPod}:/tmp/sec-reports.tar.gz ./security-reports.tar.gz"
                    sh "tar xzf security-reports.tar.gz"
                    
                    archiveArtifacts artifacts: 'security-reports/**/*'
                }
            }
        }
    }
}
```

## SoapUI TestRunner Command Options

Common testrunner.sh options:

```bash
testrunner.sh [options] <soapui-project-file>

Options:
  -s <test_suite>      Test suite to run
  -c <test_case>       Test case to run
  -f <folder>          Output folder for reports
  -r                   Print reports
  -j                   Output JUnit-style reports
  -a                   Generate all reports
  -e <endpoint>        Override endpoint URL
  -h <host>            Override host
  -x <project_pass>    Project password
  -P <property>=<val>  Set project property
  -G <property>=<val>  Set global property
  -D                   Enable system properties
```

## Creating SoapUI Projects

### Method 1: Use SoapUI GUI (Desktop)

1. Download SoapUI from https://www.soapui.org/downloads/soapui/
2. Create your test project in the GUI
3. Export the project as XML
4. Use the XML file in your pipeline

### Method 2: Create XML Manually

For simple REST tests, you can create the XML structure manually (see examples above).

### Method 3: Generate from OpenAPI/Swagger

```bash
# If you have OpenAPI spec, convert to SoapUI project
# Use tools like swagger2soapui
```

## REST API Test Template

```groovy
// Quick REST API test without external project file
pipeline {
    agent any
    
    stages {
        stage('Quick REST Test') {
            steps {
                script {
                    // Write test inline
                    writeFile file: 'rest-test.xml', text: """<?xml version="1.0" encoding="UTF-8"?>
<con:soapui-project name="Quick REST Test" xmlns:con="http://eviware.com/soapui/config">
  <con:interface name="API" type="rest" basePath="">
    <con:resource name="HealthCheck" path="/health">
      <con:method name="GET">
        <con:request name="Request">
          <con:endpoint>http://myapp.default.svc.cluster.local:8080</con:endpoint>
        </con:request>
      </con:method>
    </con:resource>
  </con:interface>
  <con:testSuite name="Tests">
    <con:testCase name="Health">
      <con:testStep type="restrequest" name="Check">
        <con:config>
          <restRequest>
            <method>GET</method>
            <endpoint>http://myapp.default.svc.cluster.local:8080</endpoint>
            <resource>/health</resource>
          </restRequest>
          <assertion type="Valid HTTP Status Codes">
            <configuration><codes>200</codes></configuration>
          </assertion>
        </config>
      </con:testStep>
    </con:testCase>
  </con:testSuite>
</con:soapui-project>"""
                    
                    def pod = sh(script: "kubectl get pod -n soapui -l app=soapui -o jsonpath='{.items[0].metadata.name}'", returnStdout: true).trim()
                    sh "kubectl cp rest-test.xml soapui/\${pod}:/tmp/test.xml"
                    sh "kubectl exec -n soapui \${pod} -- testrunner.sh -r -j -f /tmp/reports /tmp/test.xml"
                    sh "kubectl exec -n soapui \${pod} -- tar czf /tmp/reports.tar.gz -C /tmp reports"
                    sh "kubectl cp soapui/\${pod}:/tmp/reports.tar.gz ./reports.tar.gz"
                    sh "tar xzf reports.tar.gz"
                    junit 'reports/TEST-*.xml'
                }
            }
        }
    }
}
```

## Best Practices

1. **Organize Tests by Feature**: Group related tests into test suites
2. **Use Assertions**: Always validate responses with assertions
3. **Parameterize Tests**: Use properties for environments, credentials
4. **Data-Driven Testing**: Use CSV/Excel for test data
5. **Version Control**: Store SoapUI projects in Git
6. **CI/CD Integration**: Run tests on every commit/PR

## Common Test Scenarios

### 1. Smoke Tests
- Basic endpoint availability
- Health check endpoints
- Quick validation (5-10 tests)

### 2. Functional Tests
- Complete API coverage
- All CRUD operations
- Edge cases and validation

### 3. Integration Tests
- Multi-service workflows
- End-to-end scenarios
- Database state validation

### 4. Security Tests
- Authentication/Authorization
- Input validation
- SQL injection, XSS tests

## Troubleshooting

### Test Runner Not Found
```bash
# Check if testrunner.sh exists
kubectl exec -n soapui deployment/soapui -- which testrunner.sh

# Or check SoapUI installation
kubectl exec -n soapui deployment/soapui -- ls -la /usr/local/bin/
```

### Project File Issues
```bash
# Verify project file is valid XML
kubectl exec -n soapui deployment/soapui -- cat /tmp/project.xml | head -20

# Check file was copied correctly
kubectl exec -n soapui deployment/soapui -- ls -lh /tmp/project.xml
```

### Memory Issues
```bash
# Increase heap size in deployment
# Edit values.yaml or patch deployment
resources:
  limits:
    memory: 1Gi
```

## Accessing SoapUI Logs

```bash
# View logs
kubectl logs -n soapui deployment/soapui

# Follow logs
kubectl logs -n soapui deployment/soapui -f

# Get test execution logs
kubectl exec -n soapui deployment/soapui -- cat /tmp/soapui.log
```

## Next Steps

1. Create your first SoapUI project (REST or SOAP)
2. Test it manually using `kubectl exec`
3. Integrate into CloudBees CI pipeline
4. Add assertions and validations
5. Implement data-driven tests
6. Add to CI/CD workflow

## Additional Resources

- SoapUI Documentation: https://www.soapui.org/docs/
- REST Testing: https://www.soapui.org/docs/rest-testing/
- SOAP Testing: https://www.soapui.org/docs/soap-and-wsdl/
- Groovy Scripting: https://www.soapui.org/docs/scripting-and-properties/
