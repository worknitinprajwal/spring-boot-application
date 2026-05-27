#!/usr/bin/env groovy

/**
 * CloudBees Unify API Client - Direct REST API Integration
 * Use this when CloudBees Unify Jenkins plugin is not available
 */

def call() {
    return this
}

def registerSecurityScan(Map config = [:]) {
    def artifacts = config.artifacts ?: ''
    def format = config.format ?: 'sarif'
    def scanner = config.scanner ?: 'Unknown'

    if (!artifacts || !fileExists(artifacts)) {
        echo "⚠️  Security scan file not found: ${artifacts}"
        return
    }

    try {
        withCredentials([string(credentialsId: 'cloudbees-unify-api-token', variable: 'UNIFY_TOKEN')]) {
            def scanContent = readFile(file: artifacts, encoding: 'UTF-8')
            def scanBase64 = scanContent.bytes.encodeBase64().toString()

            sh """
                curl -X POST https://api.cloudbees.io/v1/organizations/${env.UNIFY_ORG_ID}/components/${env.UNIFY_COMPONENT_ID}/scans \\
                  -H "Authorization: Bearer \${UNIFY_TOKEN}" \\
                  -H "Content-Type: application/json" \\
                  -d '{
                    "scanner": "${scanner}",
                    "format": "${format}",
                    "buildNumber": "${env.BUILD_NUMBER}",
                    "branch": "${env.BRANCH_NAME}",
                    "scanData": "${scanBase64}"
                  }' \\
                  -w "\\nHTTP_STATUS:%{http_code}" \\
                  -s -o /tmp/unify-response.json

                if grep -q "HTTP_STATUS:20[0-9]" <<< "\$(tail -1 /tmp/unify-response.json)"; then
                    echo "✅ Security scan registered with CloudBees Unify"
                else
                    echo "⚠️  Failed to register scan (check API token and component ID)"
                fi
            """
        }
    } catch (Exception e) {
        echo "⚠️  CloudBees Unify API call failed: ${e.message}"
        echo "This is expected if 'cloudbees-unify-api-token' credential doesn't exist"
    }
}

def registerBuildArtifact(Map config = [:]) {
    def name = config.name ?: 'artifact'
    def version = config.version ?: env.BUILD_NUMBER
    def url = config.url ?: ''
    def type = config.type ?: 'Docker'

    if (!url) {
        echo "⚠️  No artifact URL provided"
        return null
    }

    try {
        withCredentials([string(credentialsId: 'cloudbees-unify-api-token', variable: 'UNIFY_TOKEN')]) {
            def response = sh(
                script: """
                    curl -X POST https://api.cloudbees.io/v1/organizations/${env.UNIFY_ORG_ID}/components/${env.UNIFY_COMPONENT_ID}/artifacts \\
                      -H "Authorization: Bearer \${UNIFY_TOKEN}" \\
                      -H "Content-Type: application/json" \\
                      -d '{
                        "name": "${name}",
                        "version": "${version}",
                        "url": "${url}",
                        "type": "${type}",
                        "buildNumber": "${env.BUILD_NUMBER}",
                        "branch": "${env.BRANCH_NAME}"
                      }' \\
                      -s
                """,
                returnStdout: true
            ).trim()

            echo "✅ Build artifact registered with CloudBees Unify"

            // Extract artifact ID from response (if needed)
            def artifactId = sh(
                script: "echo '${response}' | jq -r '.id // \"unknown\"' 2>/dev/null || echo 'unknown'",
                returnStdout: true
            ).trim()

            return artifactId
        }
    } catch (Exception e) {
        echo "⚠️  CloudBees Unify API call failed: ${e.message}"
        return null
    }
}

def registerDeployment(Map config = [:]) {
    def artifactId = config.artifactId ?: ''
    def targetEnv = config.targetEnvironment ?: 'Development'
    def labels = config.labels ?: ''

    if (!artifactId) {
        echo "⚠️  No artifact ID provided for deployment"
        return
    }

    try {
        withCredentials([string(credentialsId: 'cloudbees-unify-api-token', variable: 'UNIFY_TOKEN')]) {
            sh """
                curl -X POST https://api.cloudbees.io/v1/organizations/${env.UNIFY_ORG_ID}/components/${env.UNIFY_COMPONENT_ID}/deployments \\
                  -H "Authorization: Bearer \${UNIFY_TOKEN}" \\
                  -H "Content-Type: application/json" \\
                  -d '{
                    "artifactId": "${artifactId}",
                    "environment": "${targetEnv}",
                    "buildNumber": "${env.BUILD_NUMBER}",
                    "branch": "${env.BRANCH_NAME}",
                    "labels": "${labels}"
                  }' \\
                  -s
            """

            echo "✅ Deployment registered to ${targetEnv} environment"
        }
    } catch (Exception e) {
        echo "⚠️  CloudBees Unify API call failed: ${e.message}"
    }
}

return this
