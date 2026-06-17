variable "resource_group_name" {
  description = "Name of the resource group where Key Vault will be created."
  type        = string
}

variable "location" {
  description = "Azure region where Key Vault will be deployed."
  type        = string
}

variable "environment" {
  description = "Deployment environment name used for Key Vault naming."
  type        = string
}

variable "storage_connection_string" {
  description = "Primary connection string for the Azure Storage account."
  type        = string
  sensitive   = true
}

variable "aks_principal_id" {
  description = "Principal ID of the AKS managed identity that requires secret access."
  type        = string
}
