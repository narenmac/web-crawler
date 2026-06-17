locals {
  common_tags = {
    environment = var.environment
    workload    = "web-crawler"
    managed_by  = "terraform"
  }
}

resource "azurerm_resource_group" "this" {
  name     = var.resource_group_name
  location = var.location
  tags     = local.common_tags
}

module "networking" {
  source = "./modules/networking"

  resource_group_name = azurerm_resource_group.this.name
  location            = azurerm_resource_group.this.location
  environment         = var.environment
}

module "aks" {
  source = "./modules/aks"

  resource_group_name         = azurerm_resource_group.this.name
  location                    = azurerm_resource_group.this.location
  environment                 = var.environment
  aks_subnet_id               = module.networking.aks_subnet_id
  log_analytics_workspace_id  = module.monitoring.log_analytics_workspace_id
  vm_size                     = var.aks_vm_size
  min_count                   = var.aks_node_count_min
  max_count                   = var.aks_node_count_max

  depends_on = [
    module.networking,
    module.monitoring
  ]
}

module "storage" {
  source = "./modules/storage"

  resource_group_name   = azurerm_resource_group.this.name
  location              = azurerm_resource_group.this.location
  environment           = var.environment
  storage_account_name  = var.storage_account_name
  storage_pe_subnet_id  = module.networking.storage_pe_subnet_id
  vnet_id               = module.networking.vnet_id

  depends_on = [module.networking]
}

module "keyvault" {
  source = "./modules/keyvault"

  resource_group_name        = azurerm_resource_group.this.name
  location                   = azurerm_resource_group.this.location
  environment                = var.environment
  storage_connection_string  = module.storage.primary_connection_string
  aks_principal_id           = module.aks.principal_id

  depends_on = [
    module.storage,
    module.aks
  ]
}

module "monitoring" {
  source = "./modules/monitoring"

  resource_group_name = azurerm_resource_group.this.name
  location            = azurerm_resource_group.this.location
  environment         = var.environment
}

module "k8s_resources" {
  source = "./modules/k8s-resources"

  cluster_host                = module.aks.host
  client_certificate          = module.aks.client_certificate
  client_key                  = module.aks.client_key
  cluster_ca_certificate      = module.aks.cluster_ca_certificate
  storage_account_name        = module.storage.storage_account_name
  storage_connection_string   = module.storage.primary_connection_string
  frontend_image_tag          = var.frontend_image_tag
  api_gateway_image_tag       = var.api_gateway_image_tag
  orchestrator_image_tag      = var.orchestrator_image_tag
  url_fetcher_image_tag       = var.url_fetcher_image_tag
  content_parser_image_tag    = var.content_parser_image_tag
  docker_hub_username         = var.docker_hub_username
  public_ip_address           = module.networking.public_ip_address

  depends_on = [
    module.aks,
    module.storage,
    module.keyvault
  ]
}
