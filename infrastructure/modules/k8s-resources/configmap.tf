resource "kubernetes_config_map_v1" "web_crawler" {
  metadata {
    name      = "web-crawler-config"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
  }

  data = {
    STORAGE_ACCOUNT_NAME      = var.storage_account_name
    URL_QUEUE_NAME            = "url-queue"
    PARSE_QUEUE_NAME          = "parse-queue"
    JOB_CONTROL_QUEUE_NAME    = "job-control-queue"
    RAW_HTML_CONTAINER_NAME   = "raw-html"
    SEED_FILES_CONTAINER_NAME = "seed-files"
    MAX_URLS_PER_JOB          = "1000"
  }
}
