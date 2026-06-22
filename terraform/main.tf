locals {
  kubeconfig_path = pathexpand("~/.kube/kind-config-${var.cluster_name}")
}

resource "null_resource" "kind_cluster" {
  triggers = {
    cluster_name = var.cluster_name
  }

  provisioner "local-exec" {
    command = <<-EOT
      set -e
      if kind get clusters 2>/dev/null | grep -q "^${var.cluster_name}$"; then
        echo "Cluster ${var.cluster_name} already exists"
      else
        echo "Creating kind cluster: ${var.cluster_name}"
        kind create cluster \
          --name ${var.cluster_name} \
          --config ${path.module}/kind-config.yaml \
          --kubeconfig ${local.kubeconfig_path}
      fi
    EOT
  }

  provisioner "local-exec" {
    when    = destroy
    command = <<-EOT
      set -e
      if kind get clusters 2>/dev/null | grep -q "^${self.triggers.cluster_name}$"; then
        echo "Deleting kind cluster: ${self.triggers.cluster_name}"
        kind delete cluster --name ${self.triggers.cluster_name}
        rm -f ${local.kubeconfig_path}
      fi
    EOT
  }
}

resource "null_resource" "wait_for_cluster" {
  depends_on = [null_resource.kind_cluster]

  provisioner "local-exec" {
    command = <<-EOT
      echo "Waiting for cluster to be ready..."
      kubectl --kubeconfig ${local.kubeconfig_path} wait --for=condition=Ready nodes --all --timeout=120s
    EOT
  }
}

resource "null_resource" "argocd_namespace" {
  depends_on = [null_resource.wait_for_cluster]

  provisioner "local-exec" {
    command = <<-EOT
      kubectl --kubeconfig ${local.kubeconfig_path} create namespace ${var.argocd_namespace} --dry-run=client -o yaml | kubectl --kubeconfig ${local.kubeconfig_path} apply -f -
    EOT
  }
}

resource "null_resource" "application_namespace" {
  depends_on = [null_resource.wait_for_cluster]

  provisioner "local-exec" {
    command = <<-EOT
      kubectl --kubeconfig ${local.kubeconfig_path} create namespace ${var.namespace} --dry-run=client -o yaml | kubectl --kubeconfig ${local.kubeconfig_path} apply -f -
    EOT
  }
}

resource "helm_release" "argocd" {
  depends_on = [null_resource.argocd_namespace]

  name             = "argocd"
  repository       = "https://argoproj.github.io/argo-helm"
  chart            = "argo-cd"
  namespace        = var.argocd_namespace
  create_namespace = false
  version          = "7.8.1"

  set {
    name  = "server.service.type"
    value = "NodePort"
  }
  set {
    name  = "server.service.nodePort"
    value = "30001"
  }
  set {
    name  = "configs.params.server\\.insecure"
    value = "true"
  }

  timeout = 180
}

resource "null_resource" "wait_for_argocd" {
  depends_on = [helm_release.argocd]

  provisioner "local-exec" {
    command = <<-EOT
      echo "Waiting for ArgoCD server to be ready..."
      kubectl --kubeconfig ${local.kubeconfig_path} wait \
        --namespace ${var.argocd_namespace} \
        --for=condition=ready pod \
        --selector=app.kubernetes.io/name=argocd-server \
        --timeout=180s
    EOT
  }
}

resource "null_resource" "argocd_application" {
  depends_on = [null_resource.wait_for_argocd, null_resource.application_namespace]

  provisioner "local-exec" {
    command = <<-EOT
      cat <<EOF | kubectl --kubeconfig ${local.kubeconfig_path} apply -f -
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: fitness-tracker
  namespace: ${var.argocd_namespace}
spec:
  project: default
  source:
    repoURL: ${var.github_repo_url}
    targetRevision: main
    path: ${var.helm_chart_path}
    helm:
      valueFiles:
        - values.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: ${var.namespace}
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
      - ServerSideApply=true
EOF
    EOT
  }
}

resource "null_resource" "argocd_password" {
  depends_on = [null_resource.wait_for_argocd]

  provisioner "local-exec" {
    command = <<-EOT
      echo "=== ArgoCD Admin Password ==="
      kubectl --kubeconfig ${local.kubeconfig_path} -n ${var.argocd_namespace} get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
      echo ""
    EOT
  }
}
