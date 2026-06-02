# Aikido Security Integration - Internal Summary

## 📊 Quick Summary

**Aikido Security** has been integrated into the `fitness-tracker-sample-spring-boot-app` CI/CD pipeline for **CI gating purposes**. However, **Aikido results do NOT appear in CloudBees Unify Security Center** due to platform limitations.

---

## ✅ What's Working

### 1. Aikido Scanning in CI/CD Pipeline
- ✅ Runs on every commit to `develop` branch
- ✅ Scans for: **SCA (dependencies)** + **SAST (code vulnerabilities)**
- ✅ Latest scan: **17 vulnerabilities** (15 dependency + 2 SAST)
- ✅ Pipeline gating: Build fails if critical issues found

### 2. SARIF Export
- ✅ Generates SARIF 2.1.0 format for standardized reporting
- ✅ Archived as Jenkins artifact for every build
- ✅ Accessible via: `${BUILD_URL}/artifact/aikido-scan.sarif`

### 3. Report Access
- ✅ **Jenkins Artifacts**: Full SARIF + JSON results
- ✅ **Build Description Links**: Clickable links to Aikido dashboard
- ✅ **Aikido Dashboard**: External link added to every build

---

## ❌ What's NOT Working

### CloudBees Unify Security Center Visibility

**Problem**: Aikido scan results do NOT appear in CloudBees Unify Security Center UI

**Root Cause**:
1. **CloudBees Unify only supports officially recognized scanners**:
   - Checkov, Snyk, Trivy, CodeQL, Grype, etc.
   - Aikido is not on the supported list

2. **`registerSecurityScan()` API does not work for custom SARIF files**:
   - We attempted to register Aikido as "Snyk" scanner
   - Call succeeds without errors
   - But results are silently filtered out and never displayed in UI

3. **Only "Implicit Security Scanning" appears in Security Center**:
   - Implicit scanners = Automatic scans configured in CloudBees Unify Marketplace
   - Example: Checkov (currently showing 2 HIGH IAC findings)
   - Our Aikido scan = Explicit CI pipeline integration (not supported for UI display)

**Verified via CloudBees Unify MCP API**:
```json
{
  "tools": [
    {
      "name": "Checkov IAC",  // ✅ Visible
      "counts": {"HIGH": 2}
    }
    // ❌ Aikido/Snyk not present
  ]
}
```

---

## 🎯 Current Security Visibility Matrix

| Scanner | Type | In Unify Security Center? | Integration Method | Access Method |
|---------|------|--------------------------|-------------------|---------------|
| **Checkov** | IAC | ✅ YES (2 HIGH findings) | Implicit (automatic) | Security Center UI |
| **Aikido** | SCA + SAST | ❌ NO (17 vulnerabilities) | Explicit CI/CD | Jenkins artifacts + Dashboard link |
| **OWASP ZAP** | DAST | ❌ NO | Explicit CI/CD | Jenkins artifacts |
| **JMeter** | Performance | ❌ NO | Explicit CI/CD | Jenkins artifacts |

---

## 🛠️ Recommended Solutions

### Option 1: Accept Current State ✅ (Recommended for now)

**Keep Aikido for CI gating**:
- Use Aikido to fail/pass builds based on vulnerabilities
- Access results via Jenkins artifacts and Aikido dashboard
- Accept that it won't appear in CloudBees Unify Security Center

**Pros**:
- No changes needed
- Aikido still provides value as CI gate
- Full SARIF reports available

**Cons**:
- Security team can't see Aikido findings in Unify
- Requires manual artifact review

---

### Option 2: Enable Additional Implicit Scanners 🔧

**Enable in CloudBees Unify → Security → Marketplace**:

| Scanner | Type | Coverage | Will Appear in Unify? |
|---------|------|----------|----------------------|
| **Grype** | SCA | Dependency vulnerabilities | ✅ YES |
| **Trivy** | Container + SCA | Comprehensive scanning | ✅ YES |
| **Gitleaks** | Secrets | Leaked credentials | ✅ YES |

**Pros**:
- Results **WILL** appear in Security Center
- Automatic scanning on every commit
- Similar coverage to Aikido

**Cons**:
- May find different vulnerabilities than Aikido
- Requires CloudBees Unify admin configuration

---

### Option 3: Hybrid Approach 🎯 (Best of Both)

1. **Keep Aikido** for comprehensive CI/CD gating
2. **Enable Grype/Trivy** in CloudBees Unify for Security Center visibility
3. **Use both**:
   - Aikido = Source of truth for CI/CD decisions
   - Grype/Trivy = Compliance and Security Center visibility

**Pros**:
- CI/CD pipeline still uses Aikido
- Security team sees findings in Unify
- Redundant coverage (better)

**Cons**:
- Two scanners to maintain
- Potential finding discrepancies

---

## 📈 Current Build Metrics (Build #228)

**Aikido Scan Results**:
- Total Issues: **17**
- Dependency Vulnerabilities: **15**
- SAST Issues: **2**
- Gate Status: **PASSED** (no critical blockers)

**CloudBees Unify Security Center**:
- Checkov IAC: **2 HIGH** findings
- Aikido: **Not visible** (as expected)

**Report Access**:
- Jenkins: https://backspin-feline-pesticide.ngrok-free.dev/jenkins/job/fitness-tracker-sample-spring-boot-app/job/develop/228/artifact/
- Aikido Dashboard: Link available in build description
- SARIF: Direct download from Jenkins artifacts

---

## 🔗 Documentation

**Full Documentation**:
- Location: `sample-spring-boot-app/AIKIDO-INTEGRATION.md`
- Includes: Setup, limitations, alternatives, access methods

**Key Files**:
- `vars/securityScanner.groovy` - Aikido integration logic
- `scripts/aikido-to-sarif.sh` - SARIF conversion script
- `AIKIDO-INTEGRATION.md` - Complete documentation

---

## 💬 Discussion Points

### Questions for the Team:

1. **Is CI gating sufficient, or do we need Security Center visibility?**
   - If visibility is critical → Enable Grype/Trivy (Option 2)
   - If gating is sufficient → Keep current setup (Option 1)

2. **Should we enable additional implicit scanners?**
   - Grype/Trivy would provide similar coverage and Unify visibility
   - Requires CloudBees admin access to enable

3. **Is the current Aikido dashboard access acceptable?**
   - Links are added to every build description
   - SARIF files archived for compliance

### Action Items:

- [ ] Review current security requirements with security team
- [ ] Decide: Keep Aikido-only OR add Grype/Trivy for Unify visibility
- [ ] If adding scanners: Enable in CloudBees Unify Marketplace
- [ ] Document decision and update AIKIDO-INTEGRATION.md

---

## 🎯 Bottom Line

**Aikido is fully integrated and working** for CI/CD pipeline gating.

**Aikido results don't appear in CloudBees Unify Security Center** because:
- CloudBees Unify doesn't support custom SARIF scanners
- Aikido is not officially supported
- Only implicit scanners (Checkov, Trivy, Grype) appear in Unify

**Recommendations**:
1. ✅ Keep using Aikido for CI/CD gating
2. 🔧 Enable Grype/Trivy in CloudBees Unify for Security Center visibility
3. 📊 Use both for comprehensive coverage

---

## 📞 Contact

Questions? Reach out to:
- DevOps Team: CI/CD pipeline questions
- Security Team: Security scanner strategy
- CloudBees Support: Unify platform limitations

**Related Links**:
- CloudBees Unify Security Docs: https://docs.cloudbees.com/docs/cloudbees-unify/latest/aspm/security-center
- Aikido Documentation: https://aikido.dev/docs
- Sample Build: https://backspin-feline-pesticide.ngrok-free.dev/jenkins/job/fitness-tracker-sample-spring-boot-app/job/develop/228/
