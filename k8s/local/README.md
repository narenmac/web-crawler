# Local Kubernetes Testing & Terraform Guide

## Prerequisites

1. **Docker Desktop** with Kubernetes enabled:
   - Docker Desktop → Settings → Kubernetes → ✅ Enable Kubernetes → Apply & Restart
   - Wait for the green Kubernetes icon in the system tray

2. **Terraform** (optional, for Terraform-based deployment):
   ```powershell
   winget install HashiCorp.Terraform
   ```

---

## Option A: Deploy with kubectl (simple)

```powershell
# 1. Build Docker images (tagged for local use)
.\k8s\local\deploy-local.ps1 -Build

# 2. Deploy to local Kubernetes
.\k8s\local\deploy-local.ps1 -Deploy

# Or do both at once:
.\k8s\local\deploy-local.ps1 -All
```

### Useful commands:
```powershell
# Check pod status
kubectl get pods -n web-crawler -w

# View logs from a service
kubectl logs -n web-crawler -l app=url-fetcher --tail=50 -f
kubectl logs -n web-crawler -l app=crawler-orchestrator --tail=50 -f

# Scale workers manually
kubectl scale deployment url-fetcher -n web-crawler --replicas=3
kubectl scale deployment content-parser -n web-crawler --replicas=2

# Watch HPA in action
kubectl get hpa -n web-crawler -w

# Teardown
.\k8s\local\deploy-local.ps1 -Teardown
```

---

## Option B: Deploy with Terraform

```powershell
cd terraform\local

# Initialize (first time only)
terraform init

# Preview what will be created
terraform plan

# Deploy
terraform apply

# Scale workers by changing variables
terraform apply -var="url_fetcher_replicas=3" -var="content_parser_replicas=2"

# Change max URLs
terraform apply -var="max_urls=50"

# Teardown
terraform destroy
```

### Why use Terraform locally?
- Practice the same IaC workflow used in production
- Variables make it easy to change config (`max_urls`, `replicas`)
- State tracking — Terraform knows what's deployed
- Same destroy/recreate workflow as cloud

---

## Accessing the Application

| Service       | URL                                     |
|---------------|------------------------------------------|
| Frontend      | http://localhost:30000                   |
| API Gateway   | http://localhost:30080/api/jobs          |
| Debug Queues  | http://localhost:30080/api/debug/queues  |

---

## Testing Auto-scaling

```powershell
# Watch pods and HPA simultaneously (two terminals):
kubectl get pods -n web-crawler -w
kubectl get hpa -n web-crawler -w

# Upload a large seed file with many URLs
# HPA will scale url-fetcher pods when CPU > 70%

# Or manually stress-test:
kubectl scale deployment url-fetcher -n web-crawler --replicas=4
# Watch them all compete for messages from url-queue
```

---

## Architecture in Kubernetes

```
┌──────────────────────────────────────────────────────────────┐
│  Kubernetes Cluster (Docker Desktop)                          │
│                                                              │
│  ┌─────────────┐     ┌─────────────┐                        │
│  │  Frontend   │     │ API Gateway │  ← NodePort :30000/30080│
│  │  (1 pod)    │     │  (1 pod)    │                        │
│  └─────────────┘     └──────┬──────┘                        │
│                              │ HTTP                           │
│                     ┌────────▼────────┐                      │
│                     │  Orchestrator   │ (1 pod, singleton)   │
│                     └────────┬────────┘                      │
│                              │ Azure Queues                   │
│              ┌───────────────┼───────────────┐               │
│     ┌────────▼────────┐            ┌────────▼────────┐      │
│     │  URL Fetcher    │            │ Content Parser  │      │
│     │  (1-5 pods HPA) │            │ (1-3 pods HPA)  │      │
│     └────────┬────────┘            └────────┬────────┘      │
│              │                               │               │
│              └───────────┬───────────────────┘               │
│                    ┌─────▼─────┐                             │
│                    │  Azurite  │  (blob/queue/table)          │
│                    │  (1 pod)  │                             │
│                    └───────────┘                             │
└──────────────────────────────────────────────────────────────┘
```

---

## Docker Compose vs Local K8s

| Feature              | Docker Compose      | Local Kubernetes           |
|----------------------|--------------------|-----------------------------|
| Auto-scaling (HPA)   | ❌ No              | ✅ Yes                      |
| Service discovery     | Container names    | DNS (same as production)    |
| Rolling updates       | ❌ Manual restart  | ✅ `kubectl rollout`        |
| Health checks         | Basic              | Full liveness/readiness     |
| Resource limits       | Optional           | Enforced                    |
| Matches production    | ❌ No              | ✅ Very close               |
| Startup speed         | ⚡ Fast            | 🐢 Slower (pod scheduling)  |
| Debugging ease        | ⚡ Simple          | 🔧 kubectl logs/exec        |
