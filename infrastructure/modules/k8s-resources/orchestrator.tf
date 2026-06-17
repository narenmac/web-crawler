resource "kubernetes_deployment_v1" "orchestrator" {
  metadata {
    name      = "orchestrator"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
    labels    = merge(local.common_labels, { app = "orchestrator" })
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "orchestrator"
      }
    }

    template {
      metadata {
        labels = merge(local.common_labels, { app = "orchestrator" })
      }

      spec {
        container {
          name  = "orchestrator"
          image = "${local.image_repositories.orchestrator}:${var.orchestrator_image_tag}"

          port {
            container_port = 8081
          }

          resources {
            limits   = local.spring_boot_resources.limits
            requests = local.spring_boot_resources.requests
          }

          liveness_probe {
            http_get {
              path = local.spring_boot_probe_path
              port = 8081
            }

            initial_delay_seconds = 60
            period_seconds        = 20
            timeout_seconds       = 5
            failure_threshold     = 3
          }

          readiness_probe {
            http_get {
              path = local.spring_boot_probe_path
              port = 8081
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

resource "kubernetes_service_v1" "orchestrator" {
  metadata {
    name      = "orchestrator"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
    labels    = merge(local.common_labels, { app = "orchestrator" })
  }

  spec {
    selector = {
      app = "orchestrator"
    }

    port {
      port        = 8081
      target_port = 8081
    }

    type = "ClusterIP"
  }
}
