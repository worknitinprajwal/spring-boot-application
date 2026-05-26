# Jenkins Build Troubleshooting History

## Problem: "Method Too Large" Error Chain

### Build #14 - Original Failure
**Error:**
```
Method too large: WorkflowScript.___cps___880 ()Lcom/cloudbees/groovy/cps/impl/CpsFunction;
```

**Cause:** Jenkinsfile was 2,491 lines / 127KB, exceeding Jenkins CPS 64KB bytecode limit

**Solution:** Refactored into shared libraries in `vars/` directory (reduced to 656 lines / 24KB)

---

### Build #16 - Class Resolution Failure  
**Error:**
```
unable to resolve class buildHelper
```

**Cause:** Used `new buildHelper()` syntax which is invalid for Jenkins global vars

**Solution:** Changed to `def helper = buildHelper` (removed `new` keyword)

**Commit:** `8340393`

---

### Build #17 - Missing Property Error
**Error:**
```
groovy.lang.MissingPropertyException: No such property: buildHelper for class: groovy.lang.Binding
```

**Cause:** Jenkins couldn't find `buildHelper` as a global variable - `vars/` directory isn't automatically loaded without configuration

**Solution Attempted:** Added `call()` method to all vars/ files to enable auto-loading

**Commit:** `5767ed7`

**Result:** ❌ Still failed - `call()` alone isn't sufficient

---

### Build #18 - No DSL Method Error
**Error:**
```
java.lang.NoSuchMethodError: No such DSL method 'buildHelper' found among steps
```

**Cause:** `vars/` directory functions aren't available without:
1. Jenkins Global Shared Library configuration (requires admin access), OR
2. Explicit `library()` or `@Library()` annotation to load them

**Root Issue:** Jenkins doesn't automatically load `vars/` from the repository without configuration

**Solution Attempted:** Use `library()` step with modernSCM to explicitly load from same repo

**Commit:** `aab9f0b` (pending test in build #19)

---

## Current Approach (Build #19)

### Library Loading via `library()` Step

**Code:**
```groovy
library identifier: 'local-lib@develop',
        retriever: modernSCM([$class: 'GitSCMSource',
                              remote: 'https://github.com/anuddeeph2/sample-spring-boot-app.git',
                              credentialsId: 'github-credentials'])
```

**How it Works:**
1. Jenkins Pipeline Library plugin loads the library dynamically
2. Uses Git SCM to fetch from same repository  
3. Looks for `vars/` directory in fetched code
4. Makes all vars/ files available as global variables

**Expected Result:** ✅ `buildHelper()`, `securityScanner()`, etc. should be available

---

## Fallback Plan (If Build #19 Fails)

### Option A: Inline Critical Functions
Embed the most important helper functions directly in Jenkinsfile using `@NonCPS` methods or script-level functions:

```groovy
def setupWorkspace() {
    sh """
        mkdir -p ${ARTIFACTS_DIR}
        mkdir -p ${REPORTS_DIR}
    """
}

def publishToUnify(type, config) {
    // Implementation here
}

pipeline {
    // Use functions directly
}
```

**Pros:**
- Works immediately, no configuration needed
- Stays under bytecode limit if functions are small

**Cons:**
- Jenkinsfile grows to ~1200-1500 lines
- Less modular than shared libraries

---

### Option B: Use Original Large Jenkinsfile with Optimizations
Go back to original 2490-line Jenkinsfile but apply surgical optimizations:
1. Remove large HTML generation blocks
2. Simplify post blocks
3. Extract only the LARGEST blocks to external scripts loaded via `load` step

**Pros:**
- We know the pattern works (builds 1-13 succeeded with it)
- Can still reduce size by 30-40%

**Cons:**
- Might still hit bytecode limit
- Less maintainable

---

### Option C: Configure Jenkins Global Shared Library (Requires Admin)
Add shared library configuration in Jenkins:
1. Go to Manage Jenkins → Configure System
2. Add Global Pipeline Libraries
3. Name: `fitness-tracker-lib`
4. Default version: `develop`
5. Retrieval method: Modern SCM (Git)
6. Project repository: https://github.com/anuddeeph2/sample-spring-boot-app.git

**Pros:**
- Clean separation of concerns
- Reusable across projects
- Current code works as-is

**Cons:**
- Requires Jenkins admin access
- Configuration dependency

---

## Why `vars/` Doesn't Auto-Load

### Jenkins Shared Library Requirements

For `vars/` to work as global variables, ONE of these must be true:

1. **Global Shared Library configured** (Manage Jenkins → Configure System)
2. **Folder-level Shared Library** (Folder configuration)
3. **Loaded via `@Library` annotation** (requires library configured somewhere)
4. **Loaded via `library()` step** (what we're trying now)

Simply having a `vars/` directory in the repository **is not enough**!

---

## Technical Details

### Why Refactoring Was Needed

Jenkins CPS (Continuation Passing Style) transformation has a **64KB bytecode limit per method**. Large pipelines hit this because:

1. Entire pipeline becomes one large method
2. Each stage, step, and closure adds bytecode
3. String interpolations, conditions, loops multiply bytecode size
4. 2,491 lines of Groovy → ~80KB bytecode (exceeded limit)

### Current File Sizes

| File | Lines | Purpose |
|------|-------|---------|
| Jenkinsfile.kubernetes | 643 | Main pipeline (91% smaller) |
| vars/buildHelper.groovy | 151 | Build operations |
| vars/securityScanner.groovy | 183 | Security scans |
| vars/testExecutor.groovy | 188 | Test execution |
| vars/testReporter.groovy | 195 | Test reporting |
| vars/unifyPublisher.groovy | 110 | CloudBees Unify API |
| vars/k8sHelper.groovy | 119 | Kubernetes ops |
| **Total** | **1,589** | Well distributed |

---

## Next Steps

### If Build #19 Succeeds ✅
- Document the `library()` approach as standard
- Update all documentation
- Mark issue as resolved

### If Build #19 Fails ❌
- Implement Option A (inline functions)
- Target: ~1,200 lines total
- Keep CloudBees Unify integration intact
- Ensure < 50KB bytecode

---

## CloudBees Unify Integration Status

✅ **Component Created:** `fitness-tracker` (ID: `a2c75673-32e5-4cda-8768-38b30e5c64b3`)  
✅ **Environments:** Development exists, Test needs creation  
✅ **Integration Code:** All `registerBuildArtifactMetadata()`, `registerDeployedArtifactMetadata()`, `registerSecurityScan()` calls preserved  

**Next:** Once pipeline runs successfully, verify data appears in CloudBees Unify UI

---

**Last Updated:** 2026-05-26 (Build #19 pending)  
**Current Commit:** `aab9f0b`  
**Status:** Testing `library()` step approach
