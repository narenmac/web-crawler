resource "helm_release" "ingress_nginx" {
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  namespace        = "ingress-nginx"
  create_namespace = true
  version          = "4.11.1"

  set {
    name  = "controller.service.type"
    value = "LoadBalancer"
  }

  set {
    name  = "controller.service.loadBalancerIP"
    value = var.public_ip_address
  }

  set {
    name  = "controller.ingressClassResource.default"
    value = "true"
  }
}

resource "kubernetes_ingress_v1" "web_crawler" {
  metadata {
    name      = "web-crawler"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
    annotations = {
      "external-dns.alpha.kubernetes.io/target" = var.public_ip_address
      "kubernetes.io/ingress.class"             = "nginx"
      "web-crawler.io/public-ip-address"        = var.public_ip_address
    }
  }

  spec {
    ingress_class_name = "nginx"

    rule {
      http {
        path {
          path      = "/"
          path_type = "Prefix"

          backend {
            service {
              name = kubernetes_service_v1.frontend.metadata[0].name

              port {
                number = 80
              }
            }
          }
        }

        path {
          path      = "/api"
          path_type = "Prefix"

          backend {
            service {
              name = kubernetes_service_v1.api_gateway.metadata[0].name

              port {
                number = 8080
              }
            }
          }
        }
      }
    }
  }

  depends_on = [
    helm_release.ingress_nginx,
    kubernetes_service_v1.frontend,
    kubernetes_service_v1.api_gateway
  ]
}
