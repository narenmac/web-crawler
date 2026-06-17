variable "environment" {
  description = "Deployment environment name, such as dev or prod."
  type        = string
}

variable "location" {
  description = "Azure region where all resources will be deployed."
  type        = string
  default     = "eastus"
}

variable "resource_group_name" {
  description = "Name of the Azure resource group that will contain the infrastructure."
  type        = string
}

variable "aks_node_count_min" {
  description = "Minimum number of AKS nodes for the autoscaling system node pool."
  type        = number
}

variable "aks_node_count_max" {
  description = "Maximum number of AKS nodes for the autoscaling system node pool."
  type        = number
}

variable "aks_vm_size" {
  description = "Azure VM size for the AKS default node pool."
  type        = string
}

variable "storage_account_name" {
  description = "Globally unique Azure Storage account name for application data."
  type        = string
}

variable "frontend_image_tag" {
  description = "Container image tag for the frontend service."
  type        = string
}

variable "api_gateway_image_tag" {
  description = "Container image tag for the API gateway service."
  type        = string
}

variable "orchestrator_image_tag" {
  description = "Container image tag for the crawler orchestrator service."
  type        = string
}

variable "url_fetcher_image_tag" {
  description = "Container image tag for the URL fetcher service."
  type        = string
}

variable "content_parser_image_tag" {
  description = "Container image tag for the content parser service."
  type        = string
}

variable "docker_hub_username" {
  description = "Docker Hub username or organization that hosts the service images."
  type        = string
}

variable "spa_redirect_uris" {
  description = "Optional redirect URIs for the frontend SPA Azure AD application registration."
  type        = list(string)
  default     = []
}

variable "api_identifier_uri" {
  description = "Optional identifier URI for the API gateway Azure AD application registration."
  type        = string
  default     = null
}
