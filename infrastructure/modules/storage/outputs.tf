output "storage_account_name" {
  description = "Name of the Azure Storage account."
  value       = azurerm_storage_account.this.name
}

output "storage_account_id" {
  description = "ID of the Azure Storage account."
  value       = azurerm_storage_account.this.id
}

output "primary_connection_string" {
  description = "Primary connection string for the Azure Storage account."
  value       = azurerm_storage_account.this.primary_connection_string
  sensitive   = true
}

output "primary_access_key" {
  description = "Primary access key for the Azure Storage account."
  value       = azurerm_storage_account.this.primary_access_key
  sensitive   = true
}
