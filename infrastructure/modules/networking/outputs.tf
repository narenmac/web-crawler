output "vnet_id" {
  description = "ID of the virtual network."
  value       = azurerm_virtual_network.this.id
}

output "aks_subnet_id" {
  description = "ID of the AKS subnet."
  value       = azurerm_subnet.aks.id
}

output "storage_pe_subnet_id" {
  description = "ID of the subnet reserved for storage private endpoints."
  value       = azurerm_subnet.storage_private_endpoint.id
}

output "public_ip_id" {
  description = "ID of the static public IP address."
  value       = azurerm_public_ip.this.id
}

output "public_ip_address" {
  description = "Allocated static public IP address."
  value       = azurerm_public_ip.this.ip_address
}
