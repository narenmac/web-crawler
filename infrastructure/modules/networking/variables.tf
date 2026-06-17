variable "resource_group_name" {
  description = "Name of the resource group where networking resources will be created."
  type        = string
}

variable "location" {
  description = "Azure region where networking resources will be deployed."
  type        = string
}

variable "environment" {
  description = "Deployment environment name used for resource naming."
  type        = string
}
