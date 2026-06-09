# Aikido Security Integration Documentation

## Overview

Aikido Security is integrated into the CI/CD pipeline for **CI gating purposes only**. Aikido scan results are used to fail or pass builds based on security findings but **DO NOT appear** in CloudBees Unify Security Center UI.

## 🎯 Purpose

- ✅ **CI/CD Pipeline Gating**: Fail builds if critical security issues are found
- ✅ **SARIF Export**: Generate SARIF format for standardized security reporting
- ✅ **Jenkins Artifacts**: Archive results for manual review and compliance
- ❌ **CloudBees Unify Visibility**: Results do NOT appear in Security Center UI

## 🔍 Why Aikido Doesn't Appear in CloudBees Unify Security Center

CloudBees Unify Security Center only displays security findings from:

1. **Officially Supported Scanners**:
   - Checkov (IAC)
   - Snyk (SCA)
   - Trivy (Container/SCA)
   - CodeQL (SAST)
   - Grype (SCA)
   - And other pre-configured scanners

2. **Implicit Security Scanning** (Automatic):
   - Scanners configured in CloudBees Unify → Security → Marketplace
   - Runs automatically on every commit
   - No CI/CD integration required

3. **Limitations**:
   - `registerSecurityScan()` API **does not support** custom SARIF files
   - Even when registered as "Snyk" scanner, custom SARIF is filtered out
   - Aikido is not an officially supported scanner in CloudBees Unify

## 📊 Accessing Aikido Scan Results

### Method 1: Jenkins Build Artifacts

Every build archives Aikido SARIF files as Jenkins artifacts:

```
Jenkins Build → Artifacts Tab
- aikido-scan.sarif (SARIF 2.1.0 format)
- aikido-scan-details.json (Raw Aikido API response)
```

**Direct URL Pattern**:
```
https://<jenkins-url>/job/<job-name>/<build-number>/artifact/aikido-scan.sarif
```

### Method 2: CloudBees Unify Build Description

Build descriptions include clickable links (added automatically):

- 🛡️ **Aikido Security Report** → Links to Aikido dashboard
- 📄 **Aikido SARIF** → Links to Jenkins artifact

These links appear in:
- CloudBees Unify → Runs → Build Details
- Jenkins → Build Page

### Method 3: Aikido Dashboard

The Aikido dashboard URL is extracted from the scan and added to:
- Jenkins build description
- Build console output
- CloudBees Unify run metadata (as clickable link)

## 🔧 Pipeline Integration

### Aikido Scan Stage

```groovy
stage('Aikido Security Scan') {
    steps {
        container('maven') {
            script {
                try {
                    securityScanner().runAikidoScan()
                } catch (Exception e) {
                    echo "⚠️ Aikido scan encountered error: ${e.message}"
                    currentBuild.result = 'UNSTABLE'
                }
            }
        }
    }
}
```

### Publish Aikido to Unify Stage

```groovy
stage('Publish Aikido to Unify') {
    steps {
        container('maven') {
            script {
                securityScanner().publishAikidoToUnify()
            }
        }
    }
}
```

**What this stage does**:
1. ✅ Validates SARIF format
2. ✅ Archives SARIF as Jenkins artifact
3. ✅ Adds Aikido dashboard link to build description
4. ❌ Does NOT register with CloudBees Unify (commented out - doesn't work)

## 📈 Current Security Visibility in CloudBees Unify

| Scanner | Type | Visibility in Unify | Integration Method |
|---------|------|-------------------|-------------------|
| **Checkov** | IAC | ✅ Visible | Implicit Security Scanning |
| **Aikido** | SCA + SAST | ❌ Not Visible | CI/CD (gating only) |
| **OWASP ZAP** | DAST | ❌ Not Visible | CI/CD (archived) |
| **JMeter** | Performance | ❌ Not Visible | CI/CD (SLA gating) |

### Checkov (Currently Showing in Unify)
- **Findings**: 2 HIGH severity issues
- **Source**: Implicit security scanning (automatic)
- **Integration**: CloudBees Unify runs Checkov automatically on every commit
- **Visibility**: Full visibility in Security Center

### Aikido (Not Showing in Unify)
- **Findings**: 17 vulnerabilities (15 dependency + 2 SAST)
- **Source**: Explicit CI pipeline registration (not supported for custom scanners)
- **Integration**: `registerSecurityScan()` called but results filtered out
- **Visibility**: Access via Jenkins artifacts and Aikido dashboard only

## 🛠️ Alternative Solutions

### Option 1: Enable Additional Implicit Scanners in CloudBees Unify

Go to **CloudBees Unify → Security → Marketplace** and enable:

| Scanner | Type | Coverage |
|---------|------|----------|
| **Grype** | SCA | Similar to Aikido dependency scanning |
| **Trivy** | Container + SCA | Comprehensive vulnerability detection |
| **Gitleaks** | Secret Scanning | Detect leaked credentials |

These will:
- ✅ Run automatically on every commit
- ✅ Appear in CloudBees Unify Security Center
- ✅ Provide visibility similar to Aikido

### Option 2: Continue Using Aikido for CI Gating

Keep current setup:
- ✅ Aikido scans during CI/CD
- ✅ Fail builds on critical issues
- ✅ Archive SARIF for compliance
- ✅ Link to Aikido dashboard from Unify
- ❌ Accept that results won't appear in Security Center UI

### Option 3: Hybrid Approach (Recommended)

1. **Keep Aikido** for comprehensive SCA + SAST in CI/CD
2. **Enable Grype/Trivy** in CloudBees Unify for Security Center visibility
3. **Use Aikido as source of truth**, Unify scanners for compliance visibility

## 📝 SARIF Format

Aikido SARIF is converted from Aikido's JSON API response:

**Input**: `build-artifacts/aikido-scan-details.json` (Aikido API response)

**Output**: `aikido-scan.sarif` (SARIF 2.1.0 format)

**SARIF Structure**:
```json
{
  "$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
  "version": "2.1.0",
  "runs": [
    {
      "tool": {
        "driver": {
          "name": "Snyk",
          "informationUri": "https://snyk.io",
          "fullDescription": {
            "text": "Powered by Aikido Security (aikido.dev)"
          }
        }
      },
      "results": [...]
    }
  ]
}
```

**Note**: Even though `tool.driver.name` is set to "Snyk" (a recognized scanner), CloudBees Unify still filters out the results because the SARIF structure doesn't match Snyk's exact format.

## 🔗 Useful Links

- **Aikido Documentation**: https://aikido.dev/docs
- **CloudBees Unify Security Docs**: https://docs.cloudbees.com/docs/cloudbees-unify/latest/aspm/security-center-components
- **SARIF Specification**: https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html
- **CloudBees Unify Implicit Scanning**: https://docs.cloudbees.com/docs/cloudbees-unify/latest/aspm/implicit-security-analysis

## 📊 Metrics and Reporting

### Current Build Status (Example)

**Build #228**:
- ✅ **Aikido Scan**: 17 vulnerabilities found
  - 15 dependency issues
  - 2 SAST issues
- ✅ **Checkov (Unify)**: 2 HIGH IAC findings
- ✅ **Artifacts**: SARIF archived
- ✅ **Links**: Added to build description

**Access**:
- Jenkins: `${BUILD_URL}artifact/aikido-scan.sarif`
- Aikido Dashboard: Link in build description
- CloudBees Unify: Run details → Links section

## 🚀 Future Enhancements

If CloudBees Unify adds Aikido support in the future:

1. Uncomment the `registerSecurityScan()` call in `vars/securityScanner.groovy`
2. Change `scanner` parameter from 'Snyk' to 'Aikido'
3. Results should then appear in Security Center

**Commented code location**:
```groovy
// File: vars/securityScanner.groovy
// Lines: 232-238 (registerSecurityScan call)
```

## ✅ Summary

| Feature | Status |
|---------|--------|
| Aikido scanning in CI/CD | ✅ Working |
| CI/CD pipeline gating | ✅ Working |
| SARIF generation | ✅ Working |
| Jenkins artifact archiving | ✅ Working |
| Build description links | ✅ Working |
| CloudBees Unify visibility | ❌ Not supported |

**Aikido serves as a CI gating tool, not a CloudBees Unify Security Center scanner.**
