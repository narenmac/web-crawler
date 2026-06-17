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
