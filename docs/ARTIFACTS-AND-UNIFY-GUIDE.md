# Build Artifacts & CloudBees Unify Integration Guide

## Overview

The updated Jenkinsfile now:
✅ Stores all build artifacts in workspace  
✅ Archives artifacts for each build  
✅ Generates comprehensive build reports  
✅ Publishes metrics to CloudBees Unify  
✅ Creates HTML build summaries  

## What Gets Stored in Workspace

### During Build

All artifacts are stored in these workspace directories:

```
${WORKSPACE}/
├── build-artifacts/          # All build outputs
│   ├── build-info.txt       # Build metadata
│   ├── *.jar                # Spring Boot JAR files
│   ├── helm-values.yaml     # Updated Helm values
│   ├── build-summary.html   # HTML build report
│   └── unify-data.json      # CloudBees Unify metrics
│
└── test-reports/             # All test outputs
    └── unit-tests/          # JUnit test reports
        └── *.xml
```

### After Build

Jenkins archives these artifacts permanently (accessible from build page):

- **Build Artifacts** - JAR files, Docker image info, Helm values
- **Test Reports** - JUnit XML reports, test summaries
- **HTML Reports** - Interactive build summary
- **Unify Data** - JSON file with metrics for CloudBees Unify

## Accessing Artifacts

### From Jenkins UI

1. **Go to build page**: Click on build number (e.g., #1, #2)

2. **View artifacts**:
   - Click **"Build Artifacts"** in left sidebar
   - Download individual files or entire archive

3. **View test results**:
   - Click **"Test Result"** in left sidebar
   - See pass/fail statistics and trends

4. **View HTML reports**:
   - Click **"Build Summary"** in left sidebar
   - See comprehensive build information

### Direct URLs

```
# Build artifacts
https://your-jenkins-url/job/fitness-tracker/job/main/1/artifact/

# Test results
https://your-jenkins-url/job/fitness-tracker/job/main/1/testReport/

# HTML build summary
https://your-jenkins-url/job/fitness-tracker/job/main/1/Build_20Summary/

# Console output
https://your-jenkins-url/job/fitness-tracker/job/main/1/console
```

## What Data Goes to CloudBees Unify

The pipeline generates a `unify-data.json` file containing:

```json
{
    "build": {
        "number": "5",
        "branch": "main",
        "commit": "a4089e8",
        "timestamp": "2026-05-14T11:30:00Z",
        "status": "SUCCESS"
    },
    "artifacts": {
        "docker_image": "anuddeeph2/fitness-tracker:main-5",
        "jar_file": "fitness-tracker.jar",
        "helm_chart": "values.yaml"
    },
    "tests": {
        "total": 10,
        "passed": 10,
        "failed": 0,
        "skipped": 0
    },
    "links": {
        "artifacts": "https://jenkins-url/job/fitness-tracker/job/main/5/artifact/",
        "reports": "https://jenkins-url/job/fitness-tracker/job/main/5/testReport/",
        "console": "https://jenkins-url/job/fitness-tracker/job/main/5/console"
    }
}
```

## CloudBees Unify Integration

### Automatic Collection

If CloudBees Analytics plugin is installed, it automatically collects:

- ✅ Build duration
- ✅ Build status (success/failure)
- ✅ Test results (pass/fail counts)
- ✅ Artifact information
- ✅ SCM details (branch, commit)

### Manual Collection (via Unify API)

You can push metrics to Unify using the REST API:

```groovy
stage('Push to Unify') {
    steps {
        script {
            def unifyData = readJSON file: "${ARTIFACTS_DIR}/unify-data.json"

            withCredentials([string(credentialsId: 'unify-api-token', variable: 'UNIFY_TOKEN')]) {
                sh """
                    curl -X POST 'https://api.cloudbees.io/v1/analytics/builds' \\
                        -H 'Authorization: Bearer ${UNIFY_TOKEN}' \\
                        -H 'Content-Type: application/json' \\
                        -d '${unifyData}'
                """
            }
        }
    }
}
```

## Build Summary HTML Report

Each build generates an HTML report showing:

- 🚀 Build number, branch, commit
- 📦 Docker image details
- 🧪 Test results summary
- 📊 Links to all reports and artifacts

Access it from the build page → **"Build Summary"** link

## Artifact Retention

Configured in Jenkinsfile `options` block:

```groovy
buildDiscarder(logRotator(
    numToKeepStr: '10',           // Keep last 10 builds
    artifactNumToKeepStr: '5',    // Keep artifacts for last 5 builds
    daysToKeepStr: '30'           // Keep builds for 30 days
))
```

This means:
- Last **10 builds** are kept
- Artifacts stored for last **5 builds** only (saves disk space)
- Builds older than **30 days** are deleted

## Example: Accessing Build #5 Artifacts

### Via Jenkins UI

1. Navigate: **fitness-tracker** → **main** → **#5**
2. Click **"Build Artifacts"**
3. See:
   ```
   build-artifacts/
   ├── build-info.txt
   ├── fitness-tracker-1.0.0.jar
   ├── helm-values.yaml
   ├── build-summary.html
   └── unify-data.json
   
   test-reports/
   └── unit-tests/
       └── TEST-*.xml
   ```

### Via REST API

```bash
# Get build info
curl -u admin:password https://jenkins-url/job/fitness-tracker/job/main/5/api/json

# Download artifact
curl -u admin:password -O https://jenkins-url/job/fitness-tracker/job/main/5/artifact/build-artifacts/build-info.txt

# Get test results
curl -u admin:password https://jenkins-url/job/fitness-tracker/job/main/5/testReport/api/json
```

## Viewing in CloudBees Unify

### Connect Jenkins to Unify

1. **In Jenkins**: Go to **Manage Jenkins** → **Configure System**
2. Find **CloudBees Analytics** section
3. Enter:
   - **Unify URL**: `https://api.cloudbees.io`
   - **Organization**: Your org name
   - **API Token**: From Unify settings
4. Click **Test Connection** → **Save**

### View in Unify Dashboard

1. Login to CloudBees Unify: https://app.cloudbees.io
2. Go to **Analytics** section
3. Select your organization
4. See dashboards with:
   - Build trends (success rate, duration)
   - Test results over time
   - Failure analysis
   - Build frequency per branch
   - Deployment frequency

## Troubleshooting

### Artifacts not showing up

**Check:**
1. `archiveArtifacts` steps in Jenkinsfile
2. Workspace permissions
3. Disk space on Jenkins controller

**Solution:**
```bash
# Check workspace
kubectl exec -it fitness-tracker-controller-0 -n cloudbees-ci -- ls -la /var/jenkins_home/workspace/fitness-tracker_main/build-artifacts
```

### Unify not receiving data

**Check:**
1. CloudBees Analytics plugin installed
2. Unify credentials configured
3. Network connectivity from Jenkins to Unify

**Solution:**
```bash
# Test Unify API
curl -H "Authorization: Bearer YOUR_TOKEN" https://api.cloudbees.io/v1/analytics/builds
```

### HTML reports not rendering

**Check:**
1. `publishHTML` plugin installed
2. HTML files exist in workspace

**Solution:**
```groovy
// Install required plugin
// Manage Jenkins → Plugins → Available → "HTML Publisher"
```

## Best Practices

1. ✅ **Archive only necessary artifacts** - Don't archive huge files
2. ✅ **Set retention policies** - Avoid filling up disk
3. ✅ **Use meaningful names** - Easy to find artifacts later
4. ✅ **Include metadata** - build-info.txt with context
5. ✅ **Generate reports** - HTML reports are easy to share
6. ✅ **Push to Unify** - Centralized analytics across teams

## Summary

Your pipeline now:

| Feature | Status | Location |
|---------|--------|----------|
| JAR Files | ✅ Archived | `build-artifacts/*.jar` |
| Test Reports | ✅ Archived | `test-reports/unit-tests/` |
| Docker Image Info | ✅ Archived | `build-artifacts/build-info.txt` |
| Helm Values | ✅ Archived | `build-artifacts/helm-values.yaml` |
| Build Summary HTML | ✅ Published | Build page → "Build Summary" |
| JUnit Results | ✅ Published | Build page → "Test Result" |
| Unify Metrics | ✅ Generated | `build-artifacts/unify-data.json` |
| CloudBees Analytics | ✅ Auto-collected | Unify Dashboard |

**All artifacts are stored in workspace during build and archived permanently after build completes!** 🎉
