terraform {
  required_providers {
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 3.0"
    }
  }
}

data "azuread_client_config" "current" {}

locals {
  environment_suffix = lower(var.environment)
  name_prefix        = "web-crawler-${local.environment_suffix}"
  api_identifier_uri = coalesce(var.api_identifier_uri, "api://${local.name_prefix}-gateway")
}

resource "azuread_application" "spa" {
  display_name     = "web-crawler-spa-${local.environment_suffix}"
  sign_in_audience = "AzureADMyOrg"
  owners           = [data.azuread_client_config.current.object_id]

  dynamic "single_page_application" {
    for_each = length(var.spa_redirect_uris) > 0 ? [1] : []

    content {
      redirect_uris = var.spa_redirect_uris
    }
  }
}

resource "azuread_application" "api" {
  display_name     = "web-crawler-api-${local.environment_suffix}"
  sign_in_audience = "AzureADMyOrg"
  owners           = [data.azuread_client_config.current.object_id]
  identifier_uris  = [local.api_identifier_uri]
}
