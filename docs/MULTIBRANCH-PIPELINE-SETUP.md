# Multibranch Pipeline Setup for CloudBees CI

## Overview

A Multibranch Pipeline automatically discovers, manages, and executes pipelines for all branches in your Git repository. This is ideal for:
- Feature branch testing
- Pull request validation
- Environment-based deployment (dev/test/prod branches)
- Multiple teams working on different branches

## Architecture

```
GitHub Repository (fitness-tracker)
├── main (production)
├── develop (integration)
├── feature/new-workout-api
└── feature/health-check-endpoint
            │
            ▼
CloudBees CI Multibranch Pipeline
├── Scans repository for branches
├── Creates pipeline job for each branch
├── Automatically builds on commit/PR
└── Deploys based on branch rules
```

## Prerequisites

1. **Git Repository** with your Spring Boot app
2. **CloudBees CI** running on Kind cluster
3. **GitHub Personal Access Token** (for private repos)
4. **Jenkinsfile** in repository root

## Step 1: Prepare Your Repository

### 1.1 Push Code to GitHub

```bash
cd /Users/analla/Downloads/cloudbees/IGS/sample-spring-app

# Initialize git if not already done
git init
git add .
git commit -m "Initial commit: Fitness Tracker app with multi-cluster pipeline"

# Add remote (replace with your repo)
git remote add origin https://github.com/YOUR-USERNAME/fitness-tracker.git
git branch -M main
git push -u origin main
```

### 1.2 Create Branch Strategy

```bash
# Create develop branch
git checkout -b develop
git push -u origin develop

# Create feature branch (example)
git checkout -b feature/add-nutrition-tracking
git push -u origin feature/add-nutrition-tracking
```

### 1.3 Rename Jenkinsfile

```bash
# Multibranch pipeline expects "Jenkinsfile" (not Jenkinsfile-MultiCluster)
cd /Users/analla/Downloads/cloudbees/IGS/sample-spring-app
mv Jenkinsfile-MultiCluster Jenkinsfile
git add Jenkinsfile
git commit -m "Rename Jenkinsfile for multibranch pipeline"
git push
```

## Step 2: Configure GitHub Credentials in CloudBees CI

### 2.1 Create GitHub Personal Access Token

1. Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Click "Generate new token (classic)"
3. Select scopes:
   - ✅ `repo` (Full control of private repositories)
   - ✅ `admin:repo_hook` (if using webhooks)
4. Generate and **copy the token**

### 2.2 Add Credentials to CloudBees CI

1. Open CloudBees CI: http://cloudbees.local
2. Navigate: **Manage Jenkins → Credentials → System → Global credentials**
3. Click **Add Credentials**
4. Fill in:
   - **Kind**: Username with password
   - **Scope**: Global
   - **Username**: Your GitHub username
   - **Password**: Paste the Personal Access Token
   - **ID**: `github-credentials` (important - used in pipeline)
   - **Description**: GitHub Personal Access Token
5. Click **OK**

### 2.3 Add DockerHub Credentials (if not already done)

1. **Add Credentials** again
2. Fill in:
   - **Kind**: Username with password
   - **Scope**: Global
   - **Username**: Your DockerHub username
   - **Password**: Your DockerHub password/token
   - **ID**: `dockerhub-credentials`
   - **Description**: DockerHub Registry Credentials
3. Click **OK**

## Step 3: Create Multibranch Pipeline Job

### 3.1 Create New Item

1. From CloudBees CI dashboard, click **New Item**
2. Enter name: `fitness-tracker-multibranch`
3. Select: **Multibranch Pipeline**
4. Click **OK**

### 3.2 Configure Branch Sources

1. **Branch Sources** section:
   - Click **Add source** → **Git** or **GitHub**

#### If using Git:
```
Project Repository: https://github.com/YOUR-USERNAME/fitness-tracker.git
Credentials: github-credentials (select from dropdown)
```

#### If using GitHub (recommended):
```
Credentials: github-credentials (select from dropdown)
Repository HTTPS URL: https://github.com/YOUR-USERNAME/fitness-tracker
```

2. **Behaviors** section (click "Add" to add these):
   - ✅ **Discover branches** - Strategy: All branches
   - ✅ **Discover pull requests from origin** - Strategy: Merging the pull request with the current target branch revision
   - ✅ **Clean before checkout**
   - ✅ **Clean after checkout**

### 3.3 Configure Build Configuration

1. **Build Configuration** section:
   - Mode: **by Jenkinsfile**
   - Script Path: `Jenkinsfile` (default)

### 3.4 Configure Scan Multibranch Pipeline Triggers

1. **Scan Multibranch Pipeline Triggers** section:
   - ✅ Check **Periodically if not otherwise run**
   - Interval: **1 hour** (or adjust as needed)

2. **Optional**: Set up webhook for instant builds (see Step 4)

### 3.5 Configure Orphaned Item Strategy

1. **Orphaned Item Strategy** section:
   - ✅ Check **Discard old items**
   - Days to keep old items: `7`
   - Max # of old items to keep: `10`

### 3.6 Save Configuration

Click **Save** at the bottom

## Step 4: Enhance Jenkinsfile for Multibranch

Update your Jenkinsfile to handle different branches:

```groovy
// Multi-Cluster CloudBees CI Pipeline - Multibranch Version

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
        APP_NAME = 'fitness-tracker'
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'your-dockerhub-username'  // UPDATE THIS
        IMAGE_NAME = "${DOCKER_REPO}/${APP_NAME}"
        
        // Use branch name in image tag for traceability
        BRANCH_NAME_CLEAN = "${env.BRANCH_NAME.replaceAll('/', '-')}"
        IMAGE_TAG = "${BRANCH_NAME_CLEAN}-${BUILD_NUMBER}"
        
        // Determine deployment environment based on branch
        AUTO_DEPLOY_ENV = determineEnvironment()
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Building branch: ${env.BRANCH_NAME}"
                echo "Image tag: ${IMAGE_TAG}"
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    sh 'mvn clean package -DskipTests=false'
                }
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                script {
                    docker.withRegistry('https://index.docker.io/v1/', 'dockerhub-credentials') {
                        def customImage = docker.build("${IMAGE_NAME}:${IMAGE_TAG}")
                        customImage.push()
                        
                        // Also push branch-specific latest tag
                        customImage.push("${BRANCH_NAME_CLEAN}-latest")
                        
                        // Push 'latest' only for main branch
                        if (env.BRANCH_NAME == 'main') {
                            customImage.push('latest')
                        }
                    }
                }
            }
        }

        stage('Determine Deployment') {
            steps {
                script {
                    // Auto-deploy based on branch
                    def deployEnv = params.DEPLOY_ENV ?: env.AUTO_DEPLOY_ENV
                    env.DEPLOY_ENV = deployEnv
                    
                    echo "Branch: ${env.BRANCH_NAME}"
                    echo "Deployment Environment: ${env.DEPLOY_ENV}"
                    echo "Image: ${IMAGE_NAME}:${IMAGE_TAG}"
                    
                    // Skip deployment for feature branches unless explicitly requested
                    if (env.BRANCH_NAME.startsWith('feature/') && !params.DEPLOY_ENV) {
                        env.SKIP_DEPLOY = 'true'
                        echo "⚠️ Feature branch detected - skipping auto-deployment"
                        echo "To deploy, re-run with DEPLOY_ENV parameter"
                    } else {
                        env.SKIP_DEPLOY = 'false'
                    }
                }
            }
        }

        stage('Update Helm Chart') {
            when {
                expression { env.SKIP_DEPLOY != 'true' }
            }
            steps {
                script {
                    dir('k8s/helm-chart') {
                        sh """
                            sed -i.bak 's|repository: .*|repository: ${IMAGE_NAME}|g' values.yaml
                            sed -i.bak 's|tag: .*|tag: "${IMAGE_TAG}"|g' values.yaml
                        """
                    }
                }
            }
        }

        stage('Deploy to Cluster') {
            when {
                expression { env.SKIP_DEPLOY != 'true' }
            }
            steps {
                script {
                    def clusterContext = ''
                    def namespace = ''
                    def valuesFile = ''
                    
                    switch(env.DEPLOY_ENV) {
                        case 'dev':
                            clusterContext = 'kind-kind-dev'
                            namespace = 'dev'
                            valuesFile = 'overlays/dev/values-dev.yaml'
                            break
                        case 'test':
                            clusterContext = 'kind-kind-test'
                            namespace = 'test'
                            valuesFile = 'overlays/test/values-test.yaml'
                            break
                        case 'prod':
                            clusterContext = 'kind-igs'
                            namespace = 'prod'
                            valuesFile = 'overlays/prod/values-prod.yaml'
                            break
                    }
                    
                    sh "kubectl config use-context ${clusterContext}"
                    
                    dir('k8s') {
                        sh """
                            helm upgrade --install ${APP_NAME} helm-chart \\
                                --namespace ${namespace} \\
                                --create-namespace \\
                                --values helm-chart/values.yaml \\
                                --values ${valuesFile} \\
                                --set image.tag=${IMAGE_TAG} \\
                                --set image.repository=${IMAGE_NAME} \\
                                --wait --timeout 5m
                        """
                    }
                    
                    // Verify deployment
                    sh "kubectl rollout status deployment/${APP_NAME} -n ${namespace} --timeout=5m"
                    
                    // Switch back to igs cluster for testing
                    sh "kubectl config use-context kind-igs"
                }
            }
        }

        // ... (rest of the stages: Verify, Get URL, Test, etc.)
    }

    post {
        always {
            echo "Pipeline completed for branch: ${env.BRANCH_NAME}"
        }
        success {
            echo "✅ Build successful for ${env.BRANCH_NAME}"
        }
        failure {
            echo "❌ Build failed for ${env.BRANCH_NAME}"
        }
    }
}

def determineEnvironment() {
    def branch = env.BRANCH_NAME
    
    switch(branch) {
        case 'main':
        case 'master':
            return 'prod'
        case 'develop':
        case 'development':
            return 'test'
        case ~/^release\/.*/:
            return 'test'
        case ~/^hotfix\/.*/:
            return 'prod'
        case ~/^feature\/.*/:
            return 'dev'
        default:
            return 'dev'
    }
}
```

## Step 5: Branch-Specific Deployment Rules

### Recommended Branch Strategy

| Branch Pattern | Auto-Deploy To | Image Tag | Notes |
|---------------|----------------|-----------|-------|
| `main` | prod | `main-123`, `latest` | Production releases only |
| `develop` | test | `develop-123` | Integration testing |
| `release/*` | test | `release-v1.0-123` | Release candidates |
| `hotfix/*` | prod | `hotfix-issue-123` | Emergency fixes |
| `feature/*` | dev (manual) | `feature-xyz-123` | Feature development |
| `PR-*` | none | `pr-456-123` | Pull request validation |

### Update Jenkinsfile with Branch Logic

Add this section after `stage('Build & Test')`:

```groovy
stage('Branch Strategy Info') {
    steps {
        script {
            def branchInfo = [:]
            
            switch(env.BRANCH_NAME) {
                case 'main':
                    branchInfo.env = 'prod'
                    branchInfo.autoTest = true
                    branchInfo.requireApproval = true
                    break
                case 'develop':
                    branchInfo.env = 'test'
                    branchInfo.autoTest = true
                    branchInfo.requireApproval = false
                    break
                case ~/^feature\/.*/:
                    branchInfo.env = 'dev'
                    branchInfo.autoTest = false
                    branchInfo.requireApproval = false
                    break
                case ~/^PR-.*/:
                    branchInfo.env = 'none'
                    branchInfo.autoTest = false
                    branchInfo.requireApproval = false
                    break
            }
            
            env.BRANCH_DEPLOY_ENV = branchInfo.env
            env.AUTO_TEST = branchInfo.autoTest.toString()
            env.REQUIRE_APPROVAL = branchInfo.requireApproval.toString()
            
            echo """
            ═══════════════════════════════════════
            Branch Strategy:
            - Branch: ${env.BRANCH_NAME}
            - Environment: ${branchInfo.env}
            - Auto Test: ${branchInfo.autoTest}
            - Require Approval: ${branchInfo.requireApproval}
            ═══════════════════════════════════════
            """
        }
    }
}
```

## Step 6: Set Up GitHub Webhook (Optional but Recommended)

### 6.1 Get Jenkins Webhook URL

```
http://cloudbees.local/github-webhook/
```

If CloudBees is not publicly accessible, use **ngrok**:

```bash
ngrok http 80
# Use the ngrok URL: https://abc123.ngrok.io/github-webhook/
```

### 6.2 Configure GitHub Webhook

1. Go to your GitHub repository
2. Navigate: **Settings → Webhooks → Add webhook**
3. Fill in:
   - **Payload URL**: `http://cloudbees.local/github-webhook/` (or ngrok URL)
   - **Content type**: `application/json`
   - **Secret**: (leave empty or set a secret)
   - **Which events**: Select "Just the push event" or customize
   - ✅ **Active**
4. Click **Add webhook**

### 6.3 Test Webhook

```bash
# Make a change and push
echo "# Test webhook" >> README.md
git add README.md
git commit -m "Test webhook trigger"
git push

# Check CloudBees CI - should see automatic build triggered
```

## Step 7: Test Multibranch Pipeline

### 7.1 Scan Repository

1. Go to `fitness-tracker-multibranch` job
2. Click **Scan Multibranch Pipeline Now**
3. Watch Jenkins discover all branches

### 7.2 Verify Branch Jobs Created

You should see sub-jobs for each branch:
```
fitness-tracker-multibranch
├── main
├── develop
└── feature-add-nutrition-tracking
```

### 7.3 Trigger Build for a Branch

1. Click on a branch (e.g., `main`)
2. Click **Build with Parameters**
3. Select deployment environment
4. Click **Build**

### 7.4 Test Feature Branch Workflow

```bash
# Create new feature branch
git checkout -b feature/improve-health-endpoint

# Make changes
echo "// Improved health check" >> src/main/java/com/example/demo/WorkoutController.java
git add .
git commit -m "Improve health endpoint response"
git push -u origin feature/improve-health-endpoint

# Watch CloudBees CI automatically:
# 1. Detect new branch
# 2. Create pipeline job for it
# 3. Build and test (but NOT deploy, since it's a feature branch)
```

## Report Storage in Multibranch Pipeline

### Where Reports Are Stored

All reports are stored per branch and per build:

```
CloudBees CI Dashboard
└── fitness-tracker-multibranch/
    ├── main/
    │   ├── Build #1
    │   │   ├── Test Results (JUnit)
    │   │   ├── HTML Reports (JMeter, ZAP)
    │   │   └── Artifacts (ZIP files)
    │   └── Build #2
    │       └── ...
    ├── develop/
    │   └── Build #1
    │       └── ...
    └── feature-xyz/
        └── Build #1
            └── ...
```

### Accessing Reports

1. **Test Results (JUnit)**:
   - Navigate: Branch → Build → Test Results
   - View: Test trends, failures, history

2. **HTML Reports**:
   - Navigate: Branch → Build → HTML Reports
   - Available: JMeter Report, ZAP Security Report

3. **Artifacts**:
   - Navigate: Branch → Build → Build Artifacts
   - Download: Test reports, logs, config files

4. **CloudBees Unify**:
   - All builds push metrics to Unify
   - View across all branches in Unify dashboard

### Report Retention Configuration

Add to Jenkinsfile:

```groovy
options {
    buildDiscarder(logRotator(
        numToKeepStr: '10',           // Keep last 10 builds
        artifactNumToKeepStr: '5',    // Keep artifacts for last 5 builds
        daysToKeepStr: '30'           // Keep builds for 30 days
    ))
}
```

## Advanced: Pull Request Validation

### Configure PR Discovery

1. In Multibranch Pipeline configuration
2. **Branch Sources** → **Behaviors** → Add:
   - **Discover pull requests from origin**
   - Strategy: "Merging the pull request with the current target branch revision"

### PR Jenkinsfile Logic

Add to Jenkinsfile:

```groovy
stage('PR Validation') {
    when {
        expression { env.CHANGE_ID != null }  // Only for PRs
    }
    steps {
        script {
            echo "Pull Request #${env.CHANGE_ID}"
            echo "Target Branch: ${env.CHANGE_TARGET}"
            echo "PR Title: ${env.CHANGE_TITLE}"
            
            // Run all tests but don't deploy
            env.SKIP_DEPLOY = 'true'
            env.RUN_SECURITY_SCAN = 'true'
            env.RUN_PERFORMANCE_TEST = 'true'
        }
    }
}
```

### GitHub PR Status Checks

CloudBees will automatically:
- ✅ Update PR status (pending/success/failure)
- 📊 Comment with test results
- 🔗 Link to build details

## Troubleshooting

### Issue: No branches discovered

**Solution**:
1. Check credentials are correct
2. Verify repository URL
3. Click "Scan Multibranch Pipeline Now"
4. Check Jenkins logs: Manage Jenkins → System Log

### Issue: Builds not triggering on push

**Solution**:
1. Verify webhook is configured and active
2. Check webhook deliveries in GitHub
3. Ensure CloudBees is accessible from internet (use ngrok if needed)
4. Test webhook: Settings → Webhooks → Recent Deliveries → Redeliver

### Issue: Permission denied during deployment

**Solution**:
1. Ensure kubectl config has correct contexts
2. Verify ServiceAccount has proper RBAC permissions
3. Test manually: `kubectl --context kind-kind-dev get pods`

### Issue: Docker push fails

**Solution**:
1. Verify DockerHub credentials in Jenkins
2. Test login: `docker login -u USERNAME`
3. Check image name format: `username/image:tag`

## Summary

You've now set up a complete multibranch pipeline that:

✅ **Automatically discovers** all branches in your Git repository  
✅ **Creates pipeline jobs** for each branch  
✅ **Triggers builds** on every push (via webhook)  
✅ **Deploys based on branch rules**:
- `main` → prod cluster
- `develop` → test cluster  
- `feature/*` → dev cluster (manual)
- PRs → validate only, no deployment

✅ **Stores all reports** per branch and build:
- JUnit test results
- JMeter performance reports
- ZAP security reports
- Build artifacts

✅ **Integrates with CloudBees Unify** for unified dashboards

## Next Steps

1. Push your code to GitHub
2. Create multibranch pipeline in CloudBees CI
3. Set up webhook for automatic builds
4. Test with feature branches and PRs
5. Monitor reports in CloudBees and Unify

**Your pipeline is now production-ready!** 🚀
