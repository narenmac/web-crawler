resource "kubernetes_deployment_v1" "api_gateway" {
  metadata {
    name      = "api-gateway"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
    labels    = merge(local.common_labels, { app = "api-gateway" })
  }

  spec {
    replicas = 2

    selector {
      match_labels = {
        app = "api-gateway"
      }
    }

    template {
      metadata {
        labels = merge(local.common_labels, { app = "api-gateway" })
      }

      spec {
        container {
          name  = "api-gateway"
          image = "${local.image_repositories.api_gateway}:${var.api_gateway_image_tag}"

          port {
            container_port = 8080
          }

          resources {
            limits   = local.spring_boot_resources.limits
            requests = local.spring_boot_resources.requests
          }

          liveness_probe {
            http_get {
              path = local.spring_boot_probe_path
              port = 8080
            }

            initial_delay_seconds = 60
            period_seconds        = 20
            timeout_seconds       = 5
            failure_threshold     = 3
          }

          readiness_probe {
            http_get {
              path = local.spring_boot_probe_path
              port = 8080
            }

            initial_delay_seconds = 20
            period_seconds        = 10
            timeout_seconds       = 5
            failure_threshold     = 6
          }

          env_from {
            config_map_ref {
              name = kubernetes_config_map_v1.web_crawler.metadata[0].name
            }
          }

          env {
            name = "AZURE_STORAGE_CONNECTION_STRING"

            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.web_crawler.metadata[0].name
                key  = "AZURE_STORAGE_CONNECTION_STRING"
              }
            }
          }
        }
      }
    }
  }
}

resource "kubernetes_service_v1" "api_gateway" {
  metadata {
    name      = "api-gateway"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
    labels    = merge(local.common_labels, { app = "api-gateway" })
  }

  spec {
    selector = {
      app = "api-gateway"
    }

    port {
      port        = 8080
      target_port = 8080
    }

    type = "ClusterIP"
  }
}
