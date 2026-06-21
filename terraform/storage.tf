resource "azurerm_storage_account" "main" {
  name                     = "sawebcrawler"
  resource_group_name      = azurerm_resource_group.main.name
  location                 = azurerm_resource_group.main.location
  account_tier             = "Standard"
  account_replication_type = var.storage_account_replication
  min_tls_version          = "TLS1_2"

  tags = var.tags
}

# Queues
resource "azurerm_storage_queue" "url_queue" {
  name                 = "url-queue"
  storage_account_name = azurerm_storage_account.main.name
}

resource "azurerm_storage_queue" "parse_queue" {
  name                 = "parse-queue"
  storage_account_name = azurerm_storage_account.main.name
}

resource "azurerm_storage_queue" "result_queue" {
  name                 = "result-queue"
  storage_account_name = azurerm_storage_account.main.name
}

resource "azurerm_storage_queue" "job_control_queue" {
  name                 = "job-control-queue"
  storage_account_name = azurerm_storage_account.main.name
}

resource "azurerm_storage_queue" "url_queue_poison" {
  name                 = "url-queue-poison"
  storage_account_name = azurerm_storage_account.main.name
}

resource "azurerm_storage_queue" "parse_queue_poison" {
  name                 = "parse-queue-poison"
  storage_account_name = azurerm_storage_account.main.name
}

# Tables
resource "azurerm_storage_table" "jobs" {
  name                 = "jobs"
  storage_account_name = azurerm_storage_account.main.name
}

resource "azurerm_storage_table" "urlmetadata" {
  name                 = "urlmetadata"
  storage_account_name = azurerm_storage_account.main.name
}

# Blob containers
resource "azurerm_storage_container" "web_pages" {
  name                  = "web-pages"
  storage_account_name  = azurerm_storage_account.main.name
  container_access_type = "private"
}

resource "azurerm_storage_container" "parsed_content" {
  name                  = "parsed-content"
  storage_account_name  = azurerm_storage_account.main.name
  container_access_type = "private"
}

resource "azurerm_storage_container" "seed_files" {
  name                  = "seed-files"
  storage_account_name  = azurerm_storage_account.main.name
  container_access_type = "private"
}
