variable "cluster_host" {
  description = "AKS Kubernetes API server host."
  type        = string
}

variable "client_certificate" {
  description = "Decoded client certificate used to authenticate to the AKS cluster."
  type        = string
  sensitive   = true
}

variable "client_key" {
  description = "Decoded client key used to authenticate to the AKS cluster."
  type        = string
  sensitive   = true
}

variable "cluster_ca_certificate" {
  description = "Decoded cluster CA certificate used to validate the AKS API server."
  type        = string
  sensitive   = true
}

variable "storage_account_name" {
  description = "Storage account name shared with workloads through a ConfigMap."
  type        = string
}

variable "storage_connection_string" {
  description = "Storage account connection string injected into Kubernetes secrets."
  type        = string
  sensitive   = true
}

variable "frontend_image_tag" {
  description = "Container image tag for the frontend workload."
  type        = string
}

variable "api_gateway_image_tag" {
  description = "Container image tag for the API gateway workload."
  type        = string
}

variable "orchestrator_image_tag" {
  description = "Container image tag for the orchestrator workload."
  type        = string
}

variable "url_fetcher_image_tag" {
  description = "Container image tag for the URL fetcher workload."
  type        = string
}

variable "content_parser_image_tag" {
  description = "Container image tag for the content parser workload."
  type        = string
}

variable "docker_hub_username" {
  description = "Docker Hub username or organization containing the workload images."
  type        = string
}

variable "public_ip_address" {
  description = "Static public IP address reserved for the ingress controller."
  type        = string
}
