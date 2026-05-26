# CloudBees Unify Setup Guide

## Prerequisites

Before the pipeline can register artifacts/scans with CloudBees Unify, you need to:

1. ✅ CloudBees CI controller integrated with CloudBees Unify
2. ✅ Component created in Unify for this repository
3. ✅ Environments created in Unify
4. ✅ CloudBees Unify plugin installed on Jenkins

---

## Step 1: Verify CloudBees CI Integration

### Check if Controller is Connected

1. **Log into CloudBees Unify**
2. Navigate to: **Settings** → **Integrations** → **CI/CD**
3. Look for your CloudBees CI controller in the list
4. Status should show: **Connected** ✅

### If Not Connected

1. In CloudBees Unify: **Settings** → **Integrations** → **Add Integration**
2. Select: **CloudBees CI** or **Jenkins**
3. Follow the wizard to connect your controller
4. You'll need:
   - Controller URL (e.g., `http://backspin-feline-pesticide.ngrok-free.dev/fitness-tracker-controller`)
   - API Token from Jenkins
   - Service account credentials

---

## Step 2: Create Component in CloudBees Unify

### Why This is Required

The error you're seeing:
```
unable to retrieve run details for this workflow. 
check that the component has been created for this repository
```

This means CloudBees Unify doesn't know about your repository yet!

### How to Create Component

1. **Log into CloudBees Unify**

2. **Navigate to**: Components → **Create Component** (or **+ New Component**)

3. **Fill in Component Details**:

   | Field | Value |
   |-------|-------|
   | **Name** | `fitness-tracker` |
   | **Repository URL** | `https://github.com/anuddeeph2/sample-spring-boot-app` |
   | **Type** | Application |
   | **Owner** | Your team/organization |
   | **Description** | Fitness Tracker Spring Boot application |

4. **Advanced Settings** (if available):
   - **Default Branch**: `main`
   - **Build System**: CloudBees CI / Jenkins
   - **Pipeline Path**: `Jenkinsfile.kubernetes`

5. **Click**: **Create** or **Save**

### Verify Component Creation

After creating, you should see:
- Component appears in: **Components** list
- Status: **Active**
- Connected Workflows: Your Jenkins job should appear

---

## Step 3: Create Environments

CloudBees Unify needs to know about your deployment environments.

### Create Development Environment

1. Navigate to: **Environments** → **Create Environment**
2. Fill in:
   - **Name**: `development` ← Must match exactly!
   - **Type**: Development
   - **Description**: Dev cluster (kind-kind-dev)
   - **Component**: Select `fitness-tracker`

### Create Test Environment

1. Navigate to: **Environments** → **Create Environment**
2. Fill in:
   - **Name**: `test` ← Must match exactly!
   - **Type**: Test / Staging
   - **Description**: Test cluster (kind-kind-test)
   - **Component**: Select `fitness-tracker`

### Environment Names Must Match

The pipeline uses these exact names:

```groovy
// develop branch → development environment
def targetEnv = env.BRANCH_NAME == 'develop' ? 'development' : 'test'

registerDeployedArtifactMetadata(
    artifactId: env.DOCKER_ARTIFACT_ID,
    targetEnvironment: targetEnv  // 'development' or 'test'
)
```

**Important**: If you use different names, update the Jenkinsfile!

---

## Step 4: Verify CloudBees Unify Plugin

### Check Plugin Installation

1. **In Jenkins**: Navigate to **Manage Jenkins** → **Plugins** → **Installed**
2. Search for: `CloudBees Unify`
3. Should see: **CloudBees Unify Plugin** (installed ✅)

### If Not Installed

1. Go to: **Manage Jenkins** → **Plugins** → **Available**
2. Search for: `CloudBees Unify`
3. Check the box and click: **Install without restart**

### Configure Plugin

1. **Manage Jenkins** → **Configure System**
2. Find section: **CloudBees Unify**
3. Enter:
   - **Unify URL**: Your CloudBees Unify instance URL
   - **API Token**: Generate from Unify Settings → API Tokens
4. Click: **Test Connection**
5. Should see: ✅ Connection successful

---

## Step 5: Re-run the Pipeline

After completing the above steps:

1. **Trigger a new build** on the `develop` branch
2. **Monitor the console output** for:
   ```
   ✅ Aikido security scan registered with CloudBees Unify
   ✅ Docker artifact registered with CloudBees Unify (ID: xxx)
   ✅ Deployed artifact registered with CloudBees Unify (Environment: development)
   ```

3. **Verify in CloudBees Unify**:
   - **Components** → **fitness-tracker** → Should show recent build
   - **Components** → **Artifacts** → Should see Docker image
   - **Components** → **Security Insights** → Should see Aikido scan
   - **Components** → **Runs** → **[Build]** → **Deployments** → Should see deployment

---

## Troubleshooting

### Error: "unable to retrieve run details for this workflow"

**Cause**: Component doesn't exist in CloudBees Unify

**Solution**: Follow **Step 2** above to create the component

---

### Error: "Failed to register Security Scan... errorCode=5, httpStatus=404"

**Cause**: Same as above - component not found

**Solution**: Create component and environments in Unify

---

### Error: "targetEnvironment 'development' not found"

**Cause**: Environment hasn't been created in CloudBees Unify

**Solution**: Follow **Step 3** to create `development` and `test` environments

---

### Warning: "CloudBees Unify plugin not installed or not configured"

**Cause**: Plugin missing or not connected to Unify

**Solution**: Follow **Step 4** to install and configure the plugin

---

### Aikido JSON File Not Found

**Symptoms**:
```
⚠️ Aikido JSON not found, using SARIF wrapper...
```

**Cause**: Aikido scan didn't return a scan ID (check `aikido-scan-output.txt`)

**Debug**:
1. Check archived artifact: `aikido-scan-output.txt`
2. Look for: "Aikido Security scan started with id: XXXXX"
3. If no scan ID → Aikido API issue (check API key, repository name)
4. If scan ID exists but no JSON → curl command failed (check network)

---

## Verification Checklist

Before running the pipeline, verify:

- [ ] CloudBees CI controller connected to Unify
- [ ] Component `fitness-tracker` exists in Unify
- [ ] Environment `development` exists in Unify
- [ ] Environment `test` exists in Unify
- [ ] CloudBees Unify plugin installed in Jenkins
- [ ] Plugin configured with Unify URL and API token
- [ ] Test connection successful
- [ ] Jenkins credentials added:
  - [ ] `github-credentials`
  - [ ] `dockerhub-credentials`
  - [ ] `AIKIDO_CLIENT_API_KEY`

---

## Quick Setup Commands

If using CloudBees Unify API/CLI (alternative to UI):

### Create Component
```bash
# Using CloudBees Unify API
curl -X POST https://unify.example.com/api/v1/components \
  -H "Authorization: Bearer $UNIFY_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "fitness-tracker",
    "repositoryUrl": "https://github.com/anuddeeph2/sample-spring-boot-app",
    "type": "APPLICATION"
  }'
```

### Create Environments
```bash
# Development
curl -X POST https://unify.example.com/api/v1/environments \
  -H "Authorization: Bearer $UNIFY_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "development",
    "type": "DEVELOPMENT",
    "componentId": "COMPONENT_ID_HERE"
  }'

# Test
curl -X POST https://unify.example.com/api/v1/environments \
  -H "Authorization: Bearer $UNIFY_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test",
    "type": "TEST",
    "componentId": "COMPONENT_ID_HERE"
  }'
```

---

## Additional Resources

- [CloudBees Unify Documentation](https://docs.cloudbees.com/docs/cloudbees-unify/latest/)
- [CI Integration Guide](https://docs.cloudbees.com/docs/cloudbees-unify/latest/continuous-integration/intro)
- [Creating Components](https://docs.cloudbees.com/docs/cloudbees-unify/latest/components/)
- [Environment Management](https://docs.cloudbees.com/docs/cloudbees-unify/latest/environments/)

---

**Last Updated**: 2026-05-26  
**Pipeline Version**: v2.3  
**Status**: Setup required before first run
