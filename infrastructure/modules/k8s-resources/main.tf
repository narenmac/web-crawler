terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.0"
    }
  }
}

locals {
  namespace = "web-crawler"
  common_labels = {
    "app.kubernetes.io/part-of"    = "web-crawler"
    "app.kubernetes.io/managed-by" = "terraform"
  }
  image_repositories = {
    frontend       = "${var.docker_hub_username}/web-crawler-frontend"
    api_gateway    = "${var.docker_hub_username}/api-gateway"
    orchestrator   = "${var.docker_hub_username}/crawler-orchestrator"
    url_fetcher    = "${var.docker_hub_username}/url-fetcher"
    content_parser = "${var.docker_hub_username}/content-parser"
  }
  spring_boot_probe_path = "/actuator/health"
  spring_boot_resources = {
    requests = {
      cpu    = "250m"
      memory = "256Mi"
    }
    limits = {
      cpu    = "500m"
      memory = "512Mi"
    }
  }
  frontend_resources = {
    requests = {
      cpu    = "50m"
      memory = "64Mi"
    }
    limits = {
      cpu    = "100m"
      memory = "128Mi"
    }
  }
}

provider "kubernetes" {
  host                   = var.cluster_host
  client_certificate     = var.client_certificate
  client_key             = var.client_key
  cluster_ca_certificate = var.cluster_ca_certificate
}

provider "helm" {
  kubernetes {
    host                   = var.cluster_host
    client_certificate     = var.client_certificate
    client_key             = var.client_key
    cluster_ca_certificate = var.cluster_ca_certificate
  }
}
