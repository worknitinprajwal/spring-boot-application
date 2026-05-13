// Complete CloudBees CI Pipeline with Testing Stack Integration
// Builds Docker image, pushes to DockerHub, updates Helm chart, and deploys via ArgoCD

pipeline {
    agent any

    parameters {
        choice(
            name: 'DEPLOY_ENV',
            choices: ['dev', 'test', 'prod'],
            description: 'Select deployment environment'
        )
        booleanParam(
            name: 'RUN_SECURITY_SCAN',
            defaultValue: true,
            description: 'Run OWASP ZAP security scan'
        )
        booleanParam(
            name: 'RUN_PERFORMANCE_TEST',
            defaultValue: false,
            description: 'Run JMeter performance tests'
        )
    }

    environment {
        // Application details
        APP_NAME = 'fitness-tracker'
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'anuddeeph2'  // CHANGE THIS
        IMAGE_NAME = "${DOCKER_REPO}/${APP_NAME}"
        IMAGE_TAG = "${BUILD_NUMBER}"

        // Kubernetes cluster contexts
        CLUSTER_DEV = 'kind-dev'
        CLUSTER_TEST = 'kind-test'
        CLUSTER_PROD = 'igs'  // Your main cluster

        // Testing tools
        ZAP_URL = 'http://zap.zap.svc.cluster.local:8080'
        JMETER_POD = sh(script: "kubectl get pod -n jmeter -l app=jmeter -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo 'not-found'", returnStdout: true).trim()
        SOAPUI_POD = sh(script: "kubectl get pod -n soapui -l app=soapui -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo 'not-found'", returnStdout: true).trim()

        // ArgoCD
        ARGOCD_SERVER = 'argocd-server.argocd.svc.cluster.local'

        // CloudBees Unify
        UNIFY_ORG = 'your-org'  // CHANGE THIS
    }

    tools {
        maven 'Maven-3.9'  // Configure in Global Tool Configuration
        jdk 'JDK-17'       // Configure in Global Tool Configuration
    }

    stages {
        stage('Checkout') {
            steps {
                echo "🔄 Checking out code..."
                checkout scm

                script {
                    // Get git commit info
                    env.GIT_COMMIT_SHORT = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.GIT_COMMIT_MSG = sh(script: "git log -1 --pretty=%B", returnStdout: true).trim()
                }
            }
        }

        stage('Build & Test') {
            steps {
                echo "🏗️ Building Spring Boot application..."
                dir('sample-spring-app') {
                    sh 'mvn clean package -DskipTests=false'
                }

                // Publish test results
                junit 'sample-spring-app/target/surefire-reports/*.xml'
            }
        }

        stage('Docker Build & Push') {
            steps {
                echo "🐳 Building Docker image..."
                script {
                    dir('sample-spring-app') {
                        // Login to DockerHub
                        withCredentials([usernamePassword(
                            credentialsId: 'dockerhub-credentials',  // Create this in Jenkins
                            usernameVariable: 'DOCKER_USER',
                            passwordVariable: 'DOCKER_PASS'
                        )]) {
                            sh """
                                echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin ${DOCKER_REGISTRY}

                                docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                                docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest

                                docker push ${IMAGE_NAME}:${IMAGE_TAG}
                                docker push ${IMAGE_NAME}:latest

                                echo "✅ Image pushed: ${IMAGE_NAME}:${IMAGE_TAG}"
                            """
                        }
                    }
                }
            }
        }

        stage('Update Helm Chart') {
            steps {
                echo "📝 Updating Helm chart with new image tag..."
                script {
                    dir('sample-spring-app/k8s/helm-chart') {
                        // Update values.yaml with new image tag
                        sh """
                            sed -i.bak 's|tag:.*|tag: ${IMAGE_TAG}|' values.yaml
                            sed -i.bak 's|repository:.*|repository: ${IMAGE_NAME}|' values.yaml
                            rm -f values.yaml.bak

                            cat values.yaml | grep -A 3 'image:'
                        """

                        // Commit and push changes (if using GitOps)
                        withCredentials([usernamePassword(
                            credentialsId: 'github-credentials',  // Create this in Jenkins
                            usernameVariable: 'GIT_USER',
                            passwordVariable: 'GIT_PASS'
                        )]) {
                            sh """
                                git config user.email "ci@example.com"
                                git config user.name "CloudBees CI"

                                git add values.yaml
                                git commit -m "Update image tag to ${IMAGE_TAG} [skip ci]" || echo "No changes to commit"

                                # Push to remote (update URL with your repo)
                                git push https://\${GIT_USER}:\${GIT_PASS}@github.com/your-username/your-repo.git HEAD:main || echo "Push failed or no changes"
                            """
                        }
                    }
                }
            }
        }

        stage('Deploy to Environment') {
            steps {
                echo "🚀 Deploying to ${params.DEPLOY_ENV} environment..."
                script {
                    // Select cluster based on environment
                    def clusterContext = ''
                    def namespace = ''

                    switch(params.DEPLOY_ENV) {
                        case 'dev':
                            clusterContext = env.CLUSTER_DEV
                            namespace = 'dev'
                            break
                        case 'test':
                            clusterContext = env.CLUSTER_TEST
                            namespace = 'test'
                            break
                        case 'prod':
                            clusterContext = env.CLUSTER_PROD
                            namespace = 'prod'
                            break
                    }

                    echo "Deploying to cluster: ${clusterContext}, namespace: ${namespace}"

                    // Switch kubectl context
                    sh "kubectl config use-context ${clusterContext}"

                    // Create namespace if not exists
                    sh "kubectl create namespace ${namespace} --dry-run=client -o yaml | kubectl apply -f -"

                    // Deploy using Helm
                    dir('sample-spring-app/k8s/helm-chart') {
                        sh """
                            helm upgrade --install ${APP_NAME} . \\
                                --namespace ${namespace} \\
                                --set image.tag=${IMAGE_TAG} \\
                                --set image.repository=${IMAGE_NAME} \\
                                --wait --timeout 5m
                        """
                    }

                    // Wait for rollout
                    sh "kubectl rollout status deployment/${APP_NAME} -n ${namespace} --timeout=5m"

                    // Get application URL
                    env.APP_URL = sh(script: "kubectl get svc ${APP_NAME} -n ${namespace} -o jsonpath='{.metadata.name}.${namespace}.svc.cluster.local:{.spec.ports[0].port}'", returnStdout: true).trim()
                    echo "✅ Application deployed at: http://${env.APP_URL}"
                }
            }
        }

        stage('API Functional Tests - SoapUI') {
            steps {
                echo "🧪 Running SoapUI API tests..."
                script {
                    if (env.SOAPUI_POD == 'not-found') {
                        echo "⚠️ SoapUI pod not found, skipping..."
                        return
                    }

                    // Create SoapUI project for REST API testing
                    writeFile file: 'api-tests.xml', text: """<?xml version="1.0" encoding="UTF-8"?>
<con:soapui-project name="Fitness Tracker API Tests" xmlns:con="http://eviware.com/soapui/config">
  <con:interface name="Fitness API" type="rest">
    <con:resource name="Health" path="/actuator/health">
      <con:method name="GET">
        <con:request name="Health Check">
          <con:endpoint>http://${env.APP_URL}</con:endpoint>
        </con:request>
      </con:method>
    </con:resource>
    <con:resource name="Workouts" path="/api/workouts">
      <con:method name="GET">
        <con:request name="Get All Workouts">
          <con:endpoint>http://${env.APP_URL}</con:endpoint>
        </con:request>
      </con:method>
    </con:resource>
  </con:interface>
  <con:testSuite name="API Tests">
    <con:testCase name="Health Check">
      <con:testStep type="restrequest" name="Health Check">
        <con:config>
          <restRequest>
            <method>GET</method>
            <endpoint>http://${env.APP_URL}</endpoint>
            <resource>/actuator/health</resource>
          </restRequest>
          <assertion type="Valid HTTP Status Codes">
            <configuration><codes>200</codes></configuration>
          </assertion>
        </config>
      </con:testStep>
    </con:testCase>
  </con:testSuite>
</con:soapui-project>"""

                    // Copy and run SoapUI tests
                    sh "kubectl cp api-tests.xml soapui/${env.SOAPUI_POD}:/tmp/api-tests.xml"

                    sh """
                        kubectl exec -n soapui ${env.SOAPUI_POD} -- \\
                            testrunner.sh -r -j -f /tmp/reports /tmp/api-tests.xml || true
                    """

                    // Collect results
                    sh """
                        kubectl exec -n soapui ${env.SOAPUI_POD} -- tar czf /tmp/reports.tar.gz -C /tmp reports 2>/dev/null || true
                        kubectl cp soapui/${env.SOAPUI_POD}:/tmp/reports.tar.gz ./soapui-reports.tar.gz || true
                        tar xzf soapui-reports.tar.gz || echo "No SoapUI reports found"
                    """

                    // Publish results
                    junit allowEmptyResults: true, testResults: 'reports/TEST-*.xml'
                    archiveArtifacts artifacts: 'reports/**/*', allowEmptyArchive: true
                }
            }
        }

        stage('Performance Tests - JMeter') {
            when {
                expression { params.RUN_PERFORMANCE_TEST == true }
            }
            steps {
                echo "⚡ Running JMeter performance tests..."
                script {
                    if (env.JMETER_POD == 'not-found') {
                        echo "⚠️ JMeter pod not found, skipping..."
                        return
                    }

                    // Create simple JMeter test plan
                    writeFile file: 'load-test.jmx', text: """<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Load Test">
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Users">
        <intProp name="ThreadGroup.num_threads">10</intProp>
        <intProp name="ThreadGroup.ramp_time">5</intProp>
        <longProp name="ThreadGroup.duration">30</longProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Health Check">
          <stringProp name="HTTPSampler.domain">\${__P(host,${env.APP_URL.split(':')[0]})}</stringProp>
          <stringProp name="HTTPSampler.port">\${__P(port,${env.APP_URL.split(':')[1]})}</stringProp>
          <stringProp name="HTTPSampler.path">/actuator/health</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>"""

                    // Copy and run JMeter test
                    sh "kubectl cp load-test.jmx jmeter/${env.JMETER_POD}:/tmp/load-test.jmx"

                    sh """
                        kubectl exec -n jmeter ${env.JMETER_POD} -- \\
                            jmeter -n -t /tmp/load-test.jmx \\
                            -l /tmp/results.jtl \\
                            -e -o /tmp/report
                    """

                    // Collect results
                    sh """
                        kubectl cp jmeter/${env.JMETER_POD}:/tmp/results.jtl ./jmeter-results.jtl
                        kubectl exec -n jmeter ${env.JMETER_POD} -- tar czf /tmp/report.tar.gz -C /tmp report
                        kubectl cp jmeter/${env.JMETER_POD}:/tmp/report.tar.gz ./jmeter-report.tar.gz
                        tar xzf jmeter-report.tar.gz
                    """

                    // Publish HTML report
                    publishHTML([
                        reportDir: 'report',
                        reportFiles: 'index.html',
                        reportName: 'JMeter Performance Report'
                    ])

                    archiveArtifacts artifacts: 'jmeter-results.jtl,jmeter-report.tar.gz'
                }
            }
        }

        stage('Security Scan - OWASP ZAP') {
            when {
                expression { params.RUN_SECURITY_SCAN == true }
            }
            steps {
                echo "🔒 Running OWASP ZAP security scan..."
                script {
                    def targetUrl = "http://${env.APP_URL}"

                    // Spider the application
                    sh "curl '${env.ZAP_URL}/JSON/spider/action/scan/?url=${targetUrl}'"

                    // Wait for spider
                    timeout(time: 5, unit: 'MINUTES') {
                        waitUntil {
                            def status = sh(script: "curl -s '${env.ZAP_URL}/JSON/spider/view/status/'", returnStdout: true)
                            return status.contains('"status":"100"')
                        }
                    }

                    echo "Spider complete. Starting active scan..."

                    // Active scan
                    sh "curl '${env.ZAP_URL}/JSON/ascan/action/scan/?url=${targetUrl}'"

                    // Wait for active scan
                    timeout(time: 10, unit: 'MINUTES') {
                        waitUntil {
                            def status = sh(script: "curl -s '${env.ZAP_URL}/JSON/ascan/view/status/'", returnStdout: true)
                            return status.contains('"status":"100"')
                        }
                    }

                    // Generate reports
                    sh """
                        curl '${env.ZAP_URL}/OTHER/core/other/htmlreport/' > zap-report.html
                        curl '${env.ZAP_URL}/JSON/core/view/alerts/' > zap-alerts.json
                    """

                    archiveArtifacts artifacts: 'zap-*.html,zap-*.json'

                    publishHTML([
                        reportDir: '.',
                        reportFiles: 'zap-report.html',
                        reportName: 'ZAP Security Report'
                    ])

                    // Check for high-risk vulnerabilities
                    def highRisk = sh(
                        script: "curl -s '${env.ZAP_URL}/JSON/core/view/alerts/?risk=High' | grep -c '\"risk\":\"High\"' || echo '0'",
                        returnStdout: true
                    ).trim().toInteger()

                    echo "High-risk vulnerabilities found: ${highRisk}"

                    if (highRisk > 0) {
                        unstable("⚠️ ${highRisk} high-risk security vulnerabilities detected!")
                    }
                }
            }
        }

        stage('Publish to CloudBees Unify') {
            steps {
                echo "📊 Publishing results to CloudBees Unify..."
                script {
                    // Aggregate test results
                    def testSummary = [
                        build: env.BUILD_NUMBER,
                        commit: env.GIT_COMMIT_SHORT,
                        environment: params.DEPLOY_ENV,
                        image: "${env.IMAGE_NAME}:${env.IMAGE_TAG}",
                        tests: [
                            functional: fileExists('reports/TEST-*.xml'),
                            performance: params.RUN_PERFORMANCE_TEST,
                            security: params.RUN_SECURITY_SCAN
                        ],
                        status: currentBuild.result ?: 'SUCCESS'
                    ]

                    echo "Test Summary: ${testSummary}"

                    // You can use CloudBees Unify API here to push metrics
                    // Or use the CloudBees Analytics plugin
                }
            }
        }
    }

    post {
        always {
            echo "🧹 Cleaning up..."
            sh """
                kubectl exec -n jmeter ${env.JMETER_POD} -- rm -rf /tmp/*.jmx /tmp/*.jtl /tmp/report || true
                kubectl exec -n soapui ${env.SOAPUI_POD} -- rm -rf /tmp/*.xml /tmp/reports || true
            """
        }
        success {
            echo "✅ Pipeline completed successfully!"
            echo "Deployed ${env.IMAGE_NAME}:${env.IMAGE_TAG} to ${params.DEPLOY_ENV}"
        }
        failure {
            echo "❌ Pipeline failed!"
        }
        unstable {
            echo "⚠️ Pipeline completed with warnings"
        }
    }
}
