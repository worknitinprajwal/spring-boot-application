resource "null_resource" "kind_cluster" {
  triggers = {
    cluster_name = var.cluster_name
  }

  provisioner "local-exec" {
    command = <<-EOT
      set -e
      CLUSTER="${var.cluster_name}"
      KUBECONFIG="${pathexpand(format("~/.kube/kind-config-%s", var.cluster_name))}"
      export KUBECONFIG
      mkdir -p "$(dirname "$KUBECONFIG")"
      if kind get clusters 2>/dev/null | grep -q "^$CLUSTER$"; then
        echo "Cluster $CLUSTER already exists — ensuring kubeconfig..."
        kind get kubeconfig --name "$CLUSTER" > "$KUBECONFIG"
      else
        echo "Creating kind cluster: $CLUSTER"
        kind create cluster \
          --name "$CLUSTER" \
          --config ${path.module}/kind-config.yaml \
          --kubeconfig "$KUBECONFIG"
      fi
    EOT
  }

  provisioner "local-exec" {
    when    = destroy
    command = <<-EOT
      set -e
      CLUSTER="${self.triggers.cluster_name}"
      if kind get clusters 2>/dev/null | grep -q "^$CLUSTER$"; then
        echo "Deleting kind cluster: $CLUSTER"
        kind delete cluster --name "$CLUSTER"
        rm -f "$HOME/.kube/kind-config-$CLUSTER"
      fi
    EOT
  }
}

resource "null_resource" "wait_for_cluster" {
  depends_on = [null_resource.kind_cluster]

  provisioner "local-exec" {
    command = <<-EOT
      export KUBECONFIG="${pathexpand(format("~/.kube/kind-config-%s", var.cluster_name))}"
      echo "Waiting for cluster to be ready..."
      kubectl wait --for=condition=Ready nodes --all --timeout=120s
    EOT
  }
}

resource "null_resource" "argocd_namespace" {
  depends_on = [null_resource.wait_for_cluster]

  provisioner "local-exec" {
    command = <<-EOT
      export KUBECONFIG="${pathexpand(format("~/.kube/kind-config-%s", var.cluster_name))}"
      kubectl create namespace ${var.argocd_namespace} --dry-run=client -o yaml | kubectl apply -f -
    EOT
  }
}

resource "null_resource" "application_namespace" {
  depends_on = [null_resource.wait_for_cluster]

  provisioner "local-exec" {
    command = <<-EOT
      export KUBECONFIG="${pathexpand(format("~/.kube/kind-config-%s", var.cluster_name))}"
      kubectl create namespace ${var.namespace} --dry-run=client -o yaml | kubectl apply -f -
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
      export KUBECONFIG="${pathexpand(format("~/.kube/kind-config-%s", var.cluster_name))}"
      echo "Waiting for ArgoCD server to be ready..."
      kubectl wait \
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
      export KUBECONFIG="${pathexpand(format("~/.kube/kind-config-%s", var.cluster_name))}"
      cat <<EOF | kubectl apply -f -
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
      export KUBECONFIG="${pathexpand(format("~/.kube/kind-config-%s", var.cluster_name))}"
      echo "=== ArgoCD Admin Password ==="
      kubectl -n ${var.argocd_namespace} get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
      echo ""
    EOT
  }
}
