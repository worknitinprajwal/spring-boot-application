# Demo trigger: adjust cluster defaults below
variable "cluster_name" {
  description = "Name of the kind cluster"
  type        = string
  default     = "fitness-tracker"
}

variable "namespace" {
  description = "Kubernetes namespace for the application"
  type        = string
  default     = "fitness-tracker"
}

variable "argocd_namespace" {
  description = "Namespace for ArgoCD"
  type        = string
  default     = "argocd"
}

variable "image_repository" {
  description = "Docker image repository"
  type        = string
  default     = "worknitinprajwal/fitness-tracker"
}

variable "image_tag" {
  description = "Docker image tag"
  type        = string
  default     = "latest"
}

variable "github_repo_url" {
  description = "Git repository URL for ArgoCD to watch"
  type        = string
  default     = "https://github.com/worknitinprajwal/spring-boot-application.git"
}

variable "helm_chart_path" {
  description = "Path to Helm chart within the repo"
  type        = string
  default     = "k8s/helm-chart"
}
