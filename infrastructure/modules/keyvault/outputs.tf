output "key_vault_id" {
  description = "ID of the Azure Key Vault."
  value       = azurerm_key_vault.this.id
}

output "key_vault_uri" {
  description = "Vault URI of the Azure Key Vault."
  value       = azurerm_key_vault.this.vault_uri
}
