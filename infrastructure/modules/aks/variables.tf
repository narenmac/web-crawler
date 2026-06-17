variable "resource_group_name" {
  description = "Name of the resource group where the AKS cluster will be created."
  type        = string
}

variable "location" {
  description = "Azure region where the AKS cluster will be deployed."
  type        = string
}

variable "environment" {
  description = "Deployment environment name used for AKS resource naming."
  type        = string
}

variable "aks_subnet_id" {
  description = "ID of the subnet dedicated to the AKS cluster nodes."
  type        = string
}

variable "log_analytics_workspace_id" {
  description = "ID of the Log Analytics workspace used for Container Insights."
  type        = string
}

variable "vm_size" {
  description = "Azure VM size for the AKS default node pool."
  type        = string
}

variable "min_count" {
  description = "Minimum number of nodes for the autoscaling AKS node pool."
  type        = number
}

variable "max_count" {
  description = "Maximum number of nodes for the autoscaling AKS node pool."
  type        = number
}
