#!/usr/bin/env groovy

/**
 * Build Helper Functions
 * Extracts common build operations
 */

def setupWorkspace() {
    sh """
        mkdir -p ${env.ARTIFACTS_DIR}
        mkdir -p ${env.REPORTS_DIR}
        echo "Build: ${env.BUILD_NUMBER}" > ${env.ARTIFACTS_DIR}/build-info.txt
        echo "Branch: ${env.BRANCH_NAME}" >> ${env.ARTIFACTS_DIR}/build-info.txt
        echo "Timestamp: \$(date)" >> ${env.ARTIFACTS_DIR}/build-info.txt

        # Install kubectl for ArgoCD verification
        if ! command -v kubectl &> /dev/null; then
            curl -LO "https://dl.k8s.io/release/\$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/arm64/kubectl"
            chmod +x kubectl && mv kubectl /usr/local/bin/
        fi
    """
}

def checkoutWithAnalysis() {
    checkout scm

    // Fix git ownership
    sh "git config --global --add safe.directory ${env.WORKSPACE}"

    env.GIT_COMMIT_SHORT = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
    env.GIT_COMMIT_MSG = sh(script: "git log -1 --pretty=%B", returnStdout: true).trim()

    // Check skip conditions
    if (env.GIT_COMMIT_MSG.contains('[skip ci]') || env.GIT_COMMIT_MSG.contains('[ci skip]')) {
        currentBuild.result = 'NOT_BUILT'
        error('Build skipped by [skip ci] tag in commit message')
    }

    // Analyze changed files
    def changedFiles = sh(script: "git diff --name-only HEAD~1 HEAD || echo 'ALL'", returnStdout: true).trim()

    def appCodeChanged = false
    def helmConfigChanged = false

    changedFiles.split('\n').each { file ->
        if (file.startsWith('src/') || file.startsWith('pom.xml') ||
            file.startsWith('Dockerfile') || file == 'ALL') {
            appCodeChanged = true
        }
        if (file.startsWith('k8s/') || file.startsWith('argocd/') || file.startsWith('helm/')) {
            helmConfigChanged = true
        }
    }

    env.APP_CODE_CHANGED = appCodeChanged.toString()
    env.HELM_CONFIG_CHANGED = helmConfigChanged.toString()

    if (!appCodeChanged && helmConfigChanged) {
        currentBuild.result = 'NOT_BUILT'
        currentBuild.description = "Config-only change (ArgoCD sync)"
        error('Build skipped - no application code changes detected')
    }

    sh """
        echo "Commit: ${env.GIT_COMMIT_SHORT}" >> ${env.ARTIFACTS_DIR}/build-info.txt
        cat >> ${env.ARTIFACTS_DIR}/build-info.txt << 'COMMIT_MSG_EOF'
Message: ${env.GIT_COMMIT_MSG}
COMMIT_MSG_EOF
    """
}

def buildAndTest() {
    sh """
        if [ -d "src/test/java" ]; then
            echo "📝 Running unit tests..."
            mvn -B clean test -Dmaven.repo.local=/root/.m2/repository
        else
            echo "⚠️  No unit tests found"
        fi

        echo "📦 Building JAR package..."
        mvn -B package -DskipTests -Dmaven.repo.local=/root/.m2/repository
    """

    // Copy artifacts
    sh """
        cp target/*.jar ${env.ARTIFACTS_DIR}/ || true
        if [ -d "target/surefire-reports" ]; then
            cp -r target/surefire-reports ${env.REPORTS_DIR}/unit-tests
        else
            mkdir -p ${env.REPORTS_DIR}/unit-tests
            echo "No unit tests were executed" > ${env.REPORTS_DIR}/unit-tests/README.txt
        fi
    """
}

def archiveBuildArtifacts() {
    sh """
        echo "📂 Checking workspace contents..."
        ls -la target/ || echo "❌ target/ directory not found"
        ls -la target/*.jar || echo "❌ No JAR files found in target/"
    """

    if (fileExists('target')) {
        archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: false, fingerprint: true
        echo "✅ JAR file archived successfully"
    } else {
        echo "❌ ERROR: target/ directory does not exist!"
    }

    if (fileExists('target/surefire-reports')) {
        junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
        archiveArtifacts artifacts: 'target/surefire-reports/**/*', allowEmptyArchive: true
    } else {
        echo "⚠️  No unit tests found"
    }
}

def updateHelmChart(String imageTag, String imageName) {
    withCredentials([usernamePassword(
        credentialsId: 'github-credentials',
        usernameVariable: 'GIT_USER',
        passwordVariable: 'GIT_PASS'
    )]) {
        sh """
            git config user.email "ci@cloudbees.com"
            git config user.name "CloudBees CI"

            # Pull latest
            git pull --rebase https://\${GIT_USER}:\${GIT_PASS}@github.com/anuddeeph2/sample-spring-boot-app.git ${env.BRANCH_NAME} || {
                git rebase --abort 2>/dev/null || true
            }

            # Update values.yaml
            cd k8s/helm-chart
            sed -i.bak 's|tag: .*|tag: ${imageTag}|' values.yaml
            sed -i.bak 's|repository: .*|repository: ${imageName}|' values.yaml
            rm -f values.yaml.bak

            cp values.yaml ${env.ARTIFACTS_DIR}/helm-values.yaml
            cd ../..

            # Commit and push
            git add k8s/helm-chart/values.yaml
            git commit -m "Update image tag to ${imageTag} [skip ci]" || echo "No changes"
            git push https://\${GIT_USER}:\${GIT_PASS}@github.com/anuddeeph2/sample-spring-boot-app.git HEAD:${env.BRANCH_NAME} || echo "⚠️  Push failed"
        """
    }
}

return this
