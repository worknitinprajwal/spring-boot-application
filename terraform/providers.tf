terraform {
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.36"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
  }
  required_version = ">= 1.0"
}

provider "helm" {
  kubernetes {
    config_path = pathexpand("~/.kube/kind-config-${var.cluster_name}")
  }
}

provider "kubernetes" {
  config_path = pathexpand("~/.kube/kind-config-${var.cluster_name}")
}
