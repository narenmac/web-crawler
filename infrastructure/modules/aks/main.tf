locals {
  environment_suffix = lower(var.environment)
  cluster_name       = "aks-web-crawler-${local.environment_suffix}"
  dns_prefix         = "akswebcrawler${replace(local.environment_suffix, "-", "")}"
}

resource "azurerm_kubernetes_cluster" "this" {
  name                = local.cluster_name
  location            = var.location
  resource_group_name = var.resource_group_name
  dns_prefix          = local.dns_prefix

  role_based_access_control_enabled = true

  default_node_pool {
    name                = "system"
    vm_size             = var.vm_size
    vnet_subnet_id      = var.aks_subnet_id
    enable_auto_scaling = true
    min_count           = var.min_count
    max_count           = var.max_count
    type                = "VirtualMachineScaleSets"
  }

  identity {
    type = "SystemAssigned"
  }

  azure_active_directory_role_based_access_control {
    managed            = true
    azure_rbac_enabled = true
  }

  oms_agent {
    log_analytics_workspace_id = var.log_analytics_workspace_id
  }

  network_profile {
    network_plugin    = "azure"
    network_policy    = "azure"
    load_balancer_sku = "standard"
  }
}
