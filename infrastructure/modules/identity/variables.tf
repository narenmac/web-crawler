variable "environment" {
  description = "Deployment environment name used for Azure AD application display names."
  type        = string
}

variable "spa_redirect_uris" {
  description = "Optional redirect URIs for the frontend SPA Azure AD application registration."
  type        = list(string)
  default     = []
}

variable "api_identifier_uri" {
  description = "Optional identifier URI for the API gateway Azure AD application registration."
  type        = string
  default     = null
}
