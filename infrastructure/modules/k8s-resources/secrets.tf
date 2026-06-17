resource "kubernetes_secret_v1" "web_crawler" {
  metadata {
    name      = "web-crawler-secrets"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
  }

  data = {
    AZURE_STORAGE_CONNECTION_STRING = var.storage_connection_string
  }

  type = "Opaque"
}
