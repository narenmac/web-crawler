variable "resource_group_name" {
  description = "Name of the resource group where storage resources will be created."
  type        = string
}

variable "location" {
  description = "Azure region where storage resources will be deployed."
  type        = string
}

variable "environment" {
  description = "Deployment environment name used for resource naming."
  type        = string
}

variable "storage_account_name" {
  description = "Globally unique name for the Azure Storage account."
  type        = string
}

variable "storage_pe_subnet_id" {
  description = "ID of the subnet dedicated to storage private endpoints."
  type        = string
}

variable "vnet_id" {
  description = "ID of the virtual network linked to private DNS zones."
  type        = string
}
