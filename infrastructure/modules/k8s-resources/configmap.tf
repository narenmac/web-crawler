resource "kubernetes_config_map_v1" "web_crawler" {
  metadata {
    name      = "web-crawler-config"
    namespace = kubernetes_namespace.web_crawler.metadata[0].name
  }

  data = {
    STORAGE_ACCOUNT_NAME      = var.storage_account_name
    URL_QUEUE_NAME            = var.queue_names.url
    PARSE_QUEUE_NAME          = var.queue_names.parse
    JOB_CONTROL_QUEUE_NAME    = var.queue_names.job_control
    RESULT_QUEUE_NAME         = var.queue_names.result
    RAW_HTML_CONTAINER_NAME   = var.blob_container_names.raw_html
    SEED_FILES_CONTAINER_NAME = var.blob_container_names.seed_files
    MAX_URLS_PER_JOB          = "1000"
  }
}
