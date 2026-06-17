terraform {
  required_version = ">= 1.5.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.0"
    }
  }

  backend "azurerm" {
    resource_group_name  = "replace-with-tfstate-rg"
    storage_account_name = "replacewithtfstate"
    container_name       = "tfstate"
    key                  = "web-crawler.terraform.tfstate"
  }
}

provider "azurerm" {
  features {}
}
