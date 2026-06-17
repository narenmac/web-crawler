resource "kubernetes_deployment_v1" "url_fetcher" {
  metadata {
    name      = "url-fetcher"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
    labels    = merge(local.common_labels, { app = "url-fetcher" })
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "url-fetcher"
      }
    }

    template {
      metadata {
        labels = merge(local.common_labels, { app = "url-fetcher" })
      }

      spec {
        container {
          name  = "url-fetcher"
          image = "${local.image_repositories.url_fetcher}:${var.url_fetcher_image_tag}"

          port {
            container_port = 8082
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

resource "kubernetes_service_v1" "url_fetcher" {
  metadata {
    name      = "url-fetcher"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
    labels    = merge(local.common_labels, { app = "url-fetcher" })
  }

  spec {
    selector = {
      app = "url-fetcher"
    }

    port {
      port        = 8082
      target_port = 8082
    }

    type = "ClusterIP"
  }
}

resource "kubernetes_manifest" "url_fetcher_scaled_object" {
  manifest = {
    apiVersion = "keda.sh/v1alpha1"
    kind       = "ScaledObject"
    metadata = {
      name      = "url-fetcher-scaler"
      namespace = kubernetes_namespace.web_crawler.metadata[0].name
    }
    spec = {
      scaleTargetRef = {
        name = kubernetes_deployment_v1.url_fetcher.metadata[0].name
      }
      minReplicaCount = 1
      maxReplicaCount = 5
      triggers = [
        {
          type = "azure-queue"
          metadata = {
            queueName         = "url-queue"
            queueLength       = "100"
            connectionFromEnv = "AZURE_STORAGE_CONNECTION_STRING"
          }
        }
      ]
    }
  }

  depends_on = [
    helm_release.keda,
    kubernetes_deployment_v1.url_fetcher
  ]
}
