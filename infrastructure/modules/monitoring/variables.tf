variable "resource_group_name" {
  description = "Name of the resource group where monitoring resources will be created."
  type        = string
}

variable "location" {
  description = "Azure region where monitoring resources will be deployed."
  type        = string
}

variable "environment" {
  description = "Deployment environment name used for monitoring resource naming."
  type        = string
}
