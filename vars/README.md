# Jenkins Shared Library Functions (vars/)

This directory contains shared library functions that are automatically loaded by Jenkins from the workspace.

## How It Works

Each `.groovy` file in this directory becomes a global variable in Jenkins pipelines, using the `call()` method pattern.

### Pattern

```groovy
// vars/myHelper.groovy
def call() {
    return this  // Returns instance of this class
}

def myMethod() {
    echo "Hello from myMethod"
}

def anotherMethod(String param) {
    echo "Parameter: ${param}"
}
```

### Usage in Jenkinsfile

```groovy
pipeline {
    stages {
        stage('Example') {
            steps {
                script {
                    // Call methods using the function name with ()
                    myHelper().myMethod()
                    myHelper().anotherMethod("test")
                }
            }
        }
    }
}
```

## Available Functions

### buildHelper()
Build and workspace management functions.

Methods:
- `setupWorkspace()` - Creates artifact directories and installs tools
- `checkoutWithAnalysis()` - Checks out code and analyzes changes
- `buildAndTest()` - Runs Maven build and tests
- `archiveBuildArtifacts()` - Archives JAR files and test reports
- `updateHelmChart(imageTag, imageName)` - Updates Helm values.yaml with new image

### securityScanner()
Security scanning integration (Aikido, ZAP).

Methods:
- `runAikidoScan()` - Executes Aikido security scan
- `convertAikidoToSARIF()` - Converts Aikido results to SARIF format
- `publishAikidoToUnify()` - Publishes Aikido scan to CloudBees Unify
- `convertZAPToSARIF()` - Converts ZAP results to SARIF format
- `publishZAPToUnify()` - Publishes ZAP scan to CloudBees Unify

### testExecutor()
Test execution functions for parallel testing stages.

Methods:
- `runAPITests()` - Executes REST API functional tests
- `runJMeterTests()` - Executes JMeter performance tests
- `runZAPTests()` - Executes OWASP ZAP security tests
- `runUITests()` - Executes UI automation tests

### testReporter()
Test report generation and publishing.

Methods:
- `archiveAPITests()` - Archives API test results and generates HTML
- `archiveJMeterReports()` - Archives JMeter reports
- `archiveZAPReports()` - Archives ZAP security reports
- `archiveUITests()` - Archives UI test reports
- `createTestDashboard()` - Creates consolidated test dashboard HTML

### unifyPublisher()
CloudBees Unify integration functions.

Methods:
- `publishBuildArtifact(Map config)` - Publishes build artifact metadata to Unify
  - Parameters: `name`, `version`, `url`, `type`, `label`
  - Returns: `artifactId` (String)
- `publishDeployment(Map config)` - Publishes deployment info to Unify
  - Parameters: `artifactId`, `targetEnvironment`, `labels`
- `publishTestResults(Map config)` - Publishes test results to Unify
  - Parameters: `testResults`, `allowEmptyResults`
- `publishSecurityScan(Map config)` - Publishes security scan to Unify
  - Parameters: `artifacts`, `format`, `scanner`, `archive`

## Why This Pattern?

This pattern allows the shared library to work **without requiring Jenkins Global Shared Library configuration**.

Traditional approach (requires Jenkins admin config):
```groovy
@Library('my-shared-library') _
```

This approach (works immediately):
```groovy
// No @Library annotation needed
buildHelper().setupWorkspace()
```

## Benefits

✅ No Jenkins configuration required
✅ Works immediately from workspace
✅ Reduces Jenkinsfile bytecode size
✅ Clean, maintainable code structure
✅ Easy to test and debug

## Troubleshooting

### "No such property" error
If you see `groovy.lang.MissingPropertyException: No such property: functionName`:
1. Verify the vars file has a `call()` method
2. Ensure you're using `functionName()` with parentheses
3. Check the file is named correctly (e.g., `buildHelper.groovy`)

### Method not found
If a method isn't found:
1. Check the method exists in the vars file
2. Verify the method is defined with `def methodName()`
3. Ensure `return this` is at the end of the file
