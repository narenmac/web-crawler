output "spa_client_id" {
  description = "Client ID of the frontend SPA Azure AD application registration."
  value       = azuread_application.spa.client_id
}

output "api_client_id" {
  description = "Client ID of the API gateway Azure AD application registration."
  value       = azuread_application.api.client_id
}

output "api_identifier_uri" {
  description = "Identifier URI assigned to the API gateway Azure AD application registration."
  value       = local.api_identifier_uri
}
