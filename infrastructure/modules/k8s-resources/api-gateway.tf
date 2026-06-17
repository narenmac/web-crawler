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

          env_from {
            config_map_ref {
              name = kubernetes_config_map_v1.web_crawler.metadata[0].name
            }
          }

          env_from {
            secret_ref {
              name = kubernetes_secret_v1.web_crawler.metadata[0].name
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
