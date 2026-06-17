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

output "blob_container_names" {
  description = "Blob container names used by the application."
  value = {
    raw_html   = "raw-html"
    seed_files = "seed-files"
  }
}

output "queue_names" {
  description = "Queue names used by the application."
  value = {
    url         = "url-queue"
    parse       = "parse-queue"
    job_control = "job-control-queue"
    result      = "result-queue"
  }
}

output "table_names" {
  description = "Table names used by the application."
  value = {
    jobs           = "jobs"
    url_metadata   = "urlmetadata"
    content_hashes = "contenthashes"
    schedules      = "schedules"
  }
}
