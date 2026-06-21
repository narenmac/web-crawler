resource "azurerm_kubernetes_cluster" "main" {
  name                = "web-crawler-aks-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  dns_prefix          = "web-crawler-${var.environment}"

  default_node_pool {
    name                = "default"
    node_count          = var.aks_node_count
    vm_size             = var.aks_node_vm_size
    enable_auto_scaling = true
    min_count           = var.aks_min_node_count
    max_count           = var.aks_max_node_count
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin = "azure"
    service_cidr   = "10.0.0.0/16"
    dns_service_ip = "10.0.0.10"
  }

  tags = var.tags
}
