resource "helm_release" "keda" {
  name             = "keda"
  repository       = "https://kedacore.github.io/charts"
  chart            = "keda"
  namespace        = "keda"
  create_namespace = true
  version          = "2.14.2"
}

resource "helm_release" "keda_scaled_objects" {
  name      = "keda-scaled-objects"
  chart     = "${path.module}/../../charts/keda-scaled-objects"
  namespace = kubernetes_namespace.web_crawler.metadata[0].name

  set {
    name  = "namespace"
    value = kubernetes_namespace.web_crawler.metadata[0].name
  }

  set {
    name  = "urlFetcher.deploymentName"
    value = kubernetes_deployment_v1.url_fetcher.metadata[0].name
  }

  set {
    name  = "urlFetcher.queueName"
    value = var.queue_names.url
  }

  set {
    name  = "contentParser.deploymentName"
    value = kubernetes_deployment_v1.content_parser.metadata[0].name
  }

  set {
    name  = "contentParser.queueName"
    value = var.queue_names.parse
  }

  depends_on = [
    helm_release.keda,
    kubernetes_deployment_v1.url_fetcher,
    kubernetes_deployment_v1.content_parser
  ]
}
