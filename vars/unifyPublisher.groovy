#!/usr/bin/env groovy

/**
 * CloudBees Unify Publisher Helper Functions
 * Reduces Jenkinsfile size by extracting repetitive Unify API calls
 */

def call() {
    return this
}

def publishSecurityScan(Map config = [:]) {
    def artifacts = config.artifacts ?: ''
    def format = config.format ?: 'sarif'
    def scanner = config.scanner ?: 'Unknown'
    def archive = config.get('archive', true)

    if (!artifacts) {
        echo "⚠️  No artifacts specified for security scan publication"
        return
    }

    if (!fileExists(artifacts)) {
        echo "⚠️  Security scan artifacts not found: ${artifacts}"
        return
    }

    try {
        echo "📊 Publishing ${scanner} security scan to CloudBees Unify..."
        registerSecurityScan(
            artifacts: artifacts,
            format: format,
            scanner: scanner,
            archive: archive
        )
        echo "✅ ${scanner} security scan published to CloudBees Unify"
    } catch (Exception e) {
        echo "⚠️  Failed to publish ${scanner} scan to Unify: ${e.message}"
    }
}

def publishBuildArtifact(Map config = [:]) {
    def name = config.name ?: 'artifact'
    def version = config.version ?: env.BUILD_NUMBER
    def url = config.url ?: ''
    def type = config.type ?: 'Generic'
    def label = config.label ?: 'latest'

    if (!url) {
        echo "⚠️  No URL specified for artifact publication"
        return null
    }

    try {
        echo "📦 Publishing ${name} artifact to CloudBees Unify via plugin..."
        def artifactId = registerBuildArtifactMetadata(
            name: name,
            version: version,
            url: url,
            type: type,
            label: label
        )
        echo "✅ Artifact published to CloudBees Unify (ID: ${artifactId})"
        return artifactId
    } catch (Exception e) {
        if (e.message?.contains('unable to retrieve run details')) {
            echo "⚠️  Plugin failed (404 - workflow mapping issue)"
            echo "🔄 Attempting direct API call as fallback..."
            try {
                return unifyApiClient().registerBuildArtifact(
                    name: name,
                    version: version,
                    url: url,
                    type: type
                )
            } catch (Exception apiError) {
                echo "⚠️  Direct API also failed: ${apiError.message}"
                return null
            }
        } else {
            echo "⚠️  Failed to publish artifact to Unify: ${e.message}"
            return null
        }
    }
}

def publishDeployment(Map config = [:]) {
    def artifactId = config.artifactId ?: env.DOCKER_ARTIFACT_ID
    def targetEnv = config.targetEnvironment ?: 'Development'
    def labels = config.labels ?: ''

    if (!artifactId) {
        echo "⚠️  No artifact ID specified for deployment publication"
        return
    }

    try {
        echo "🚀 Publishing deployment to ${targetEnv} environment via plugin..."
        registerDeployedArtifactMetadata(
            artifactId: artifactId,
            targetEnvironment: targetEnv,
            labels: labels
        )
        echo "✅ Deployment published to CloudBees Unify (Environment: ${targetEnv})"
    } catch (Exception e) {
        if (e.message?.contains('unable to retrieve run details')) {
            echo "⚠️  Plugin failed (404 - workflow mapping issue)"
            echo "🔄 Attempting direct API call as fallback..."
            try {
                unifyApiClient().registerDeployment(
                    artifactId: artifactId,
                    targetEnvironment: targetEnv,
                    labels: labels
                )
            } catch (Exception apiError) {
                echo "⚠️  Direct API also failed: ${apiError.message}"
            }
        } else {
            echo "⚠️  Failed to publish deployment to Unify: ${e.message}"
        }
    }
}

def publishTestResults(Map config = [:]) {
    def testResults = config.testResults ?: 'target/surefire-reports/*.xml'
    def allowEmpty = config.get('allowEmptyResults', true)

    echo "📊 Publishing test results to CloudBees Unify..."
    junit(
        testResults: testResults,
        allowEmptyResults: allowEmpty,
        skipPublishingChecks: true,  // Disable GitHub Checks to avoid warnings
        healthScaleFactor: 0.0,  // Don't mark build as unstable based on test results
        keepLongStdio: false,  // Disable long stdio capture to avoid warnings
        testDataPublishers: []  // Disable extra CloudBees publishers that may generate warnings
    )
    echo "✅ Test results published to CloudBees Unify"
}

return this
