# CloudBees CI - Ngrok URL Configuration

## Overview

This document details all changes made to the CloudBees CI deployment to use **ngrok URL exclusively** instead of `cloudbees-ci.local`.

**Ngrok URL**: `https://backspin-feline-pesticide.ngrok-free.dev`

---

## Changes Made to Kubernetes Cluster

### 1. ConfigMap: `cjoc-configure-jenkins-groovy`

**Purpose**: Sets CJOC's Jenkins URL on startup via Groovy script

**Location**: `cloudbees-ci` namespace

**Changed from:**
```groovy
jenkins.model.JenkinsLocationConfiguration.get().setUrl("http://cloudbees-ci.local/cjoc/")
```

**Changed to:**
```groovy
jenkins.model.JenkinsLocationConfiguration.get().setUrl("https://backspin-feline-pesticide.ngrok-free.dev/cjoc/")
```

**Command used:**
```bash
kubectl get configmap cjoc-configure-jenkins-groovy -n cloudbees-ci -o yaml | \
  sed 's|http://cloudbees-ci.local/cjoc/|https://backspin-feline-pesticide.ngrok-free.dev/cjoc/|g' | \
  kubectl apply -f -
```

---

### 2. StatefulSet: `cjoc` (JAVA_OPTS)

**Purpose**: Configures CloudBees networking hostname and port

**Location**: `cloudbees-ci` namespace

**Changed from:**
```bash
-Dcom.cloudbees.networking.hostname="cloudbees-ci.local"
-Dcom.cloudbees.networking.port=80
```

**Changed to:**
```bash
-Dcom.cloudbees.networking.hostname="backspin-feline-pesticide.ngrok-free.dev"
-Dcom.cloudbees.networking.port=80
```

**Command used:**
```bash
kubectl get statefulset cjoc -n cloudbees-ci -o json | \
  jq '.spec.template.spec.containers[].env |= map(if .name == "JAVA_OPTS" then .value |= gsub("cloudbees-ci.local"; "backspin-feline-pesticide.ngrok-free.dev") else . end)' | \
  kubectl apply -f -
```

**Impact**: OAuth redirects and internal networking now use the ngrok domain.

---

### 3. Ingress: `cjoc`

**Purpose**: Routes HTTP traffic to CJOC service

**Location**: `cloudbees-ci` namespace

**Changed from:**
```yaml
spec:
  rules:
  - host: cloudbees-ci.local
    http:
      paths:
      - backend:
          service:
            name: cjoc
            port:
              number: 80
        path: /cjoc
        pathType: Prefix
```

**Changed to:**
```yaml
spec:
  rules:
  - http:  # Wildcard - accepts ANY hostname
      paths:
      - backend:
          service:
            name: cjoc
            port:
              number: 80
        path: /cjoc
        pathType: Prefix
```

**Command used:**
```bash
cat > /tmp/patch-ingress.yaml << 'EOF'
spec:
  rules:
  - http:
      paths:
      - backend:
          service:
            name: cjoc
            port:
              number: 80
        path: /cjoc
        pathType: Prefix
EOF

kubectl patch ingress cjoc -n cloudbees-ci --patch-file /tmp/patch-ingress.yaml
```

**Impact**: CJOC now accessible via **any hostname** including ngrok.

---

### 4. Ingress: `fitness-tracker-controller`

**Purpose**: Routes HTTP traffic to managed controller service

**Location**: `cloudbees-ci` namespace

**Changed from:**
```yaml
spec:
  rules:
  - host: cloudbees-ci.local
```

**Changed to:**
```yaml
spec:
  rules:
  - http:  # Wildcard - accepts ANY hostname
      paths:
      - backend:
          service:
            name: fitness-tracker-controller
            port:
              number: 80
        path: /fitness-tracker-controller
        pathType: Prefix
```

**Command used:**
```bash
cat > /tmp/patch-controller-ingress.yaml << 'EOF'
spec:
  rules:
  - http:
      paths:
      - backend:
          service:
            name: fitness-tracker-controller
            port:
              number: 80
        path: /fitness-tracker-controller
        pathType: Prefix
EOF

kubectl patch ingress fitness-tracker-controller -n cloudbees-ci --patch-file /tmp/patch-controller-ingress.yaml
```

**Impact**: Controller accessible via ngrok URL.

---

### 5. Ingress: `managed-master-hibernation-monitor`

**Purpose**: Routes traffic to controller hibernation service

**Location**: `cloudbees-ci` namespace

**Changed from:**
```yaml
spec:
  rules:
  - host: cloudbees-ci.local
```

**Changed to:**
```yaml
spec:
  rules:
  - http:  # Wildcard - accepts ANY hostname
      paths:
      - backend:
          service:
            name: managed-master-hibernation-monitor
            port:
              number: 8090
        path: /hibernation
        pathType: Prefix
```

**Command used:**
```bash
cat > /tmp/patch-hibernation-ingress.yaml << 'EOF'
spec:
  rules:
  - http:
      paths:
      - backend:
          service:
            name: managed-master-hibernation-monitor
            port:
              number: 8090
        path: /hibernation
        pathType: Prefix
EOF

kubectl patch ingress managed-master-hibernation-monitor -n cloudbees-ci --patch-file /tmp/patch-hibernation-ingress.yaml
```

---

### 6. CJOC Pod Restart

**Purpose**: Apply all configuration changes

**Command used:**
```bash
kubectl rollout restart statefulset cjoc -n cloudbees-ci
kubectl wait --for=condition=ready pod cjoc-0 -n cloudbees-ci --timeout=180s
```

---

## Port-Forward Configuration

To make ngrok work, a port-forward is required to route traffic from localhost to the Kubernetes ingress controller:

**Command:**
```bash
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 8080:80
```

**Ngrok Configuration:**
```bash
ngrok http 8080
```

This creates:
```
ngrok tunnel → localhost:8080 → ingress-nginx → CloudBees services
```

---

## Access URLs

After all changes:

| Service | URL |
|---------|-----|
| **CJOC (Operations Center)** | `https://backspin-feline-pesticide.ngrok-free.dev/cjoc/` |
| **Managed Controller** | `https://backspin-feline-pesticide.ngrok-free.dev/fitness-tracker-controller/` |
| **Hibernation Monitor** | `https://backspin-feline-pesticide.ngrok-free.dev/hibernation/` |

---

## Verification Commands

### Check all references to cloudbees-ci.local are removed:

```bash
# Scan ConfigMaps
kubectl get configmap -n cloudbees-ci -o yaml | grep "cloudbees-ci.local"

# Scan StatefulSets
kubectl get statefulset -n cloudbees-ci -o yaml | grep "cloudbees-ci.local"

# Scan Ingresses
kubectl get ingress -n cloudbees-ci -o yaml | grep "cloudbees-ci.local"
```

**Expected result**: No matches found (or only in metadata/annotations)

### Verify Ingress Configuration:

```bash
kubectl get ingress -n cloudbees-ci
```

**Expected output:**
```
NAME                                 CLASS   HOSTS   ADDRESS      PORTS   AGE
cjoc                                 nginx   *       172.18.0.3   80      23h
fitness-tracker-controller           nginx   *       172.18.0.3   80      1h
managed-master-hibernation-monitor   nginx   *       172.18.0.3   80      23h
```

Note: `HOSTS` column shows `*` (wildcard)

### Verify CJOC Configuration:

```bash
kubectl get statefulset cjoc -n cloudbees-ci -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="JAVA_OPTS")].value}' | grep "com.cloudbees.networking.hostname"
```

**Expected output:**
```
com.cloudbees.networking.hostname="backspin-feline-pesticide.ngrok-free.dev"
```

---

## OAuth Authentication Flow

With these changes, OAuth authentication now works correctly:

1. User accesses controller via ngrok: `https://backspin-feline-pesticide.ngrok-free.dev/fitness-tracker-controller/`
2. Controller redirects to CJOC for authentication
3. CJOC authenticates user
4. CJOC redirects back to controller **using ngrok URL** (not cloudbees-ci.local)
5. User successfully signed in ✅

---

## Troubleshooting

### Issue: OAuth redirect error "Incorrect redirect_uri"

**Cause**: CJOC still has `cloudbees-ci.local` configured somewhere

**Fix**:
1. Verify CJOC Jenkins URL:
   ```bash
   kubectl exec -n cloudbees-ci cjoc-0 -- cat /var/jenkins_home/jenkins.model.JenkinsLocationConfiguration.xml
   ```
   Should show ngrok URL.

2. Update via CJOC UI:
   - Go to: `https://backspin-feline-pesticide.ngrok-free.dev/cjoc/manage/configure`
   - Update "Jenkins URL" to ngrok URL
   - Click "Save"

### Issue: Controller not accessible

**Cause**: Port-forward or ngrok tunnel died

**Fix**:
1. Check port-forward:
   ```bash
   ps aux | grep "port-forward.*ingress-nginx"
   ```

2. Restart if needed:
   ```bash
   kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 8080:80 &
   ```

3. Check ngrok:
   ```bash
   ps aux | grep ngrok
   ```

### Issue: Builds stuck "Waiting for executor"

**Cause**: Kubernetes cloud not configured in controller

**Solution**: See [MULTIBRANCH-PIPELINE-SETUP.md](MULTIBRANCH-PIPELINE-SETUP.md) for Kubernetes cloud configuration.

---

## Backup Files

All configuration backups saved to `/tmp/`:

- `/tmp/cjoc-cm-backup.yaml` - Original ConfigMap
- `/tmp/cjoc-sts-backup.yaml` - Original StatefulSet

To restore from backup:
```bash
kubectl apply -f /tmp/cjoc-cm-backup.yaml
kubectl apply -f /tmp/cjoc-sts-backup.yaml
kubectl rollout restart statefulset cjoc -n cloudbees-ci
```

---

## Important Notes

1. **Ngrok URL is temporary** - The `backspin-feline-pesticide.ngrok-free.dev` URL will change if ngrok is restarted. To make permanent:
   - Use a paid ngrok plan with fixed domain, OR
   - Set up proper DNS and Ingress with real domain

2. **Port-forward must always run** - The `kubectl port-forward` command must be active for ngrok to work.

3. **Domain field in controller config** - The "Domain" field should remain as path-only (e.g., `fitness-tracker-controller`), not a full URL.

4. **Controllers created before these changes** - May need to be recreated or have their Jenkins URL updated manually.

---

## Summary

✅ All Kubernetes resources updated to use ngrok URL  
✅ All ingresses accept wildcard hostnames  
✅ CJOC configured with ngrok URL  
✅ OAuth authentication works via ngrok  
✅ Controllers can be accessed and signed into via ngrok  
✅ No more `cloudbees-ci.local` dependencies  

**Everything in CloudBees CI now uses the ngrok URL exclusively!** 🎉
