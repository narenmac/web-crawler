resource "kubernetes_namespace" "web_crawler" {
  metadata {
    name   = local.namespace
    labels = local.common_labels
  }
}
