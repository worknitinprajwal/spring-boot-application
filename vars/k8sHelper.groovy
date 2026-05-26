#!/usr/bin/env groovy

/**
 * Kubernetes Helper Functions
 * Reduces Jenkinsfile size by extracting K8s operations
 */

def waitForDeployment(Map config = [:]) {
    def namespace = config.namespace ?: 'default'
    def deployment = config.deployment ?: ''
    def timeout = config.timeout ?: 300
    def kubeconfig = config.kubeconfig ?: env.KUBECONFIG

    if (!deployment) {
        error "Deployment name is required"
    }

    echo "⏳ Waiting for deployment ${deployment} in namespace ${namespace} (timeout: ${timeout}s)..."

    def startTime = System.currentTimeMillis()
    def deadline = startTime + (timeout * 1000)

    while (System.currentTimeMillis() < deadline) {
        try {
            def output = sh(
                script: "kubectl --kubeconfig=${kubeconfig} -n ${namespace} get deployment ${deployment} -o jsonpath='{.status.conditions[?(@.type==\"Available\")].status}'",
                returnStdout: true
            ).trim()

            if (output == 'True') {
                def elapsed = (System.currentTimeMillis() - startTime) / 1000
                echo "✅ Deployment ${deployment} is available (took ${elapsed}s)"
                return true
            }

            echo "⏳ Deployment not ready yet, waiting..."
            sleep 10
        } catch (Exception e) {
            echo "⚠️  Error checking deployment status: ${e.message}"
            sleep 10
        }
    }

    error "❌ Deployment ${deployment} failed to become ready within ${timeout}s"
}

def verifyArgoCDSync(Map config = [:]) {
    def appName = config.appName ?: ''
    def timeout = config.timeout ?: 600
    def maxAttempts = config.maxAttempts ?: 60

    if (!appName) {
        error "ArgoCD application name is required"
    }

    echo "🔍 Verifying ArgoCD sync status for ${appName}..."

    for (int i = 0; i < maxAttempts; i++) {
        try {
            def syncStatus = sh(
                script: "argocd app get ${appName} -o json | jq -r '.status.sync.status'",
                returnStdout: true
            ).trim()

            def healthStatus = sh(
                script: "argocd app get ${appName} -o json | jq -r '.status.health.status'",
                returnStdout: true
            ).trim()

            echo "ArgoCD Status - Sync: ${syncStatus}, Health: ${healthStatus}"

            if (syncStatus == 'Synced' && healthStatus == 'Healthy') {
                echo "✅ ArgoCD application ${appName} is synced and healthy"
                return true
            }

            if (healthStatus == 'Degraded') {
                error "❌ ArgoCD application ${appName} is degraded"
            }

            echo "⏳ Waiting for ArgoCD sync (attempt ${i + 1}/${maxAttempts})..."
            sleep 10
        } catch (Exception e) {
            echo "⚠️  Error checking ArgoCD status: ${e.message}"
            sleep 10
        }
    }

    error "❌ ArgoCD sync verification failed after ${maxAttempts} attempts"
}

def getPodLogs(Map config = [:]) {
    def namespace = config.namespace ?: 'default'
    def selector = config.selector ?: ''
    def tail = config.tail ?: 50
    def kubeconfig = config.kubeconfig ?: env.KUBECONFIG

    if (!selector) {
        error "Pod selector is required"
    }

    try {
        def logs = sh(
            script: "kubectl --kubeconfig=${kubeconfig} -n ${namespace} logs -l ${selector} --tail=${tail}",
            returnStdout: true
        ).trim()

        return logs
    } catch (Exception e) {
        echo "⚠️  Failed to get pod logs: ${e.message}"
        return ""
    }
}

return this
