resource "kubernetes_deployment_v1" "content_parser" {
  metadata {
    name      = "content-parser"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
    labels    = merge(local.common_labels, { app = "content-parser" })
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "content-parser"
      }
    }

    template {
      metadata {
        labels = merge(local.common_labels, { app = "content-parser" })
      }

      spec {
        container {
          name  = "content-parser"
          image = "${local.image_repositories.content_parser}:${var.content_parser_image_tag}"

          port {
            container_port = 8083
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

resource "kubernetes_service_v1" "content_parser" {
  metadata {
    name      = "content-parser"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
    labels    = merge(local.common_labels, { app = "content-parser" })
  }

  spec {
    selector = {
      app = "content-parser"
    }

    port {
      port        = 8083
      target_port = 8083
    }

    type = "ClusterIP"
  }
}

resource "kubernetes_manifest" "content_parser_scaled_object" {
  manifest = {
    apiVersion = "keda.sh/v1alpha1"
    kind       = "ScaledObject"
    metadata = {
      name      = "content-parser-scaler"
      namespace = kubernetes_namespace.web_crawler.metadata[0].name
    }
    spec = {
      scaleTargetRef = {
        name = kubernetes_deployment_v1.content_parser.metadata[0].name
      }
      minReplicaCount = 1
      maxReplicaCount = 3
      triggers = [
        {
          type = "azure-queue"
          metadata = {
            queueName         = "parse-queue"
            queueLength       = "200"
            connectionFromEnv = "AZURE_STORAGE_CONNECTION_STRING"
          }
        }
      ]
    }
  }

  depends_on = [
    helm_release.keda,
    kubernetes_deployment_v1.content_parser
  ]
}
