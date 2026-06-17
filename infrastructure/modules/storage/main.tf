locals {
  environment_suffix = lower(var.environment)
  dns_zones = {
    blob  = "privatelink.blob.core.windows.net"
    table = "privatelink.table.core.windows.net"
    queue = "privatelink.queue.core.windows.net"
  }
  blob_containers = toset(["raw-html", "seed-files"])
  table_names     = toset(["jobs", "urlmetadata", "contenthashes", "schedules"])
  queue_names     = toset(["url-queue", "parse-queue", "job-control-queue", "result-queue"])
}

resource "azurerm_storage_account" "this" {
  name                            = var.storage_account_name
  resource_group_name             = var.resource_group_name
  location                        = var.location
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  min_tls_version                 = "TLS1_2"
  public_network_access_enabled   = false
  allow_nested_items_to_be_public = false
}

resource "azurerm_storage_container" "containers" {
  for_each = local.blob_containers

  name                  = each.value
  storage_account_name  = azurerm_storage_account.this.name
  container_access_type = "private"
}

resource "azurerm_storage_table" "tables" {
  for_each = local.table_names

  name                 = each.value
  storage_account_name = azurerm_storage_account.this.name
}

resource "azurerm_storage_queue" "queues" {
  for_each = local.queue_names

  name                 = each.value
  storage_account_name = azurerm_storage_account.this.name
}

resource "azurerm_private_dns_zone" "blob" {
  name                = local.dns_zones.blob
  resource_group_name = var.resource_group_name
}

resource "azurerm_private_dns_zone" "table" {
  name                = local.dns_zones.table
  resource_group_name = var.resource_group_name
}

resource "azurerm_private_dns_zone" "queue" {
  name                = local.dns_zones.queue
  resource_group_name = var.resource_group_name
}

resource "azurerm_private_dns_zone_virtual_network_link" "blob" {
  name                  = "link-blob-${local.environment_suffix}"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.blob.name
  virtual_network_id    = var.vnet_id
}

resource "azurerm_private_dns_zone_virtual_network_link" "table" {
  name                  = "link-table-${local.environment_suffix}"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.table.name
  virtual_network_id    = var.vnet_id
}

resource "azurerm_private_dns_zone_virtual_network_link" "queue" {
  name                  = "link-queue-${local.environment_suffix}"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.queue.name
  virtual_network_id    = var.vnet_id
}

resource "azurerm_private_endpoint" "blob" {
  name                = "pe-blob-${local.environment_suffix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  subnet_id           = var.storage_pe_subnet_id

  private_service_connection {
    name                           = "psc-blob-${local.environment_suffix}"
    private_connection_resource_id = azurerm_storage_account.this.id
    subresource_names              = ["blob"]
    is_manual_connection           = false
  }

  private_dns_zone_group {
    name                 = "dns-zone-group-blob"
    private_dns_zone_ids = [azurerm_private_dns_zone.blob.id]
  }
}

resource "azurerm_private_endpoint" "table" {
  name                = "pe-table-${local.environment_suffix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  subnet_id           = var.storage_pe_subnet_id

  private_service_connection {
    name                           = "psc-table-${local.environment_suffix}"
    private_connection_resource_id = azurerm_storage_account.this.id
    subresource_names              = ["table"]
    is_manual_connection           = false
  }

  private_dns_zone_group {
    name                 = "dns-zone-group-table"
    private_dns_zone_ids = [azurerm_private_dns_zone.table.id]
  }
}

resource "azurerm_private_endpoint" "queue" {
  name                = "pe-queue-${local.environment_suffix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  subnet_id           = var.storage_pe_subnet_id

  private_service_connection {
    name                           = "psc-queue-${local.environment_suffix}"
    private_connection_resource_id = azurerm_storage_account.this.id
    subresource_names              = ["queue"]
    is_manual_connection           = false
  }

  private_dns_zone_group {
    name                 = "dns-zone-group-queue"
    private_dns_zone_ids = [azurerm_private_dns_zone.queue.id]
  }
}
