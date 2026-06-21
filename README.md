# Web Crawler

Scalable web crawling platform built with React, Spring Boot microservices, Azure Storage, and AKS.

## Architecture

- **Frontend** — React 18 + TypeScript SPA
- **API Gateway** — REST API for jobs, schedules, and results
- **Crawler Orchestrator** — BFS coordination, job lifecycle, and schedule execution
- **URL Fetcher** — fetches pages, deduplicates content, stores raw HTML
- **Content Parser** — extracts links from stored HTML, feeds results back to orchestrator

## Azure Services

- **AKS** — Kubernetes cluster (provisioned via Terraform)
- **Docker Hub** — Container registry for Docker images
- **Azure Blob Storage** — seed files, raw HTML, parsed content
- **Azure Queue Storage** — work distribution (url-queue, parse-queue, result-queue, job-control-queue)
- **Azure Table Storage** — jobs, URL metadata

## Project Structure

```text
web-crawler/
├── frontend/               # React SPA
├── api-gateway/            # Spring Boot API Gateway
├── crawler-orchestrator/   # Spring Boot BFS Orchestrator
├── url-fetcher/            # Spring Boot URL Fetcher Worker
├── content-parser/         # Spring Boot Content Parser Worker
├── terraform/              # Infrastructure as Code (AKS, ACR, Storage)
├── k8s/
│   ├── azure/              # Production Kubernetes manifests
│   └── local/              # Local dev Kubernetes manifests
├── .github/workflows/      # CI/CD pipelines
├── docker-compose.yml      # Local dev with Azurite
└── sample-seed-urls.txt    # Test seed URLs
```

## Azure Deployment

### Prerequisites

1. [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli) installed
2. [Terraform](https://developer.hashicorp.com/terraform/downloads) >= 1.5
3. An Azure subscription with permissions to create resources

### Step 1: Provision Infrastructure with Terraform

```bash
cd terraform

# Create backend storage for Terraform state (one-time)
az group create --name terraform-state-rg --location eastus
az storage account create --name tfstatewebcrawler --resource-group terraform-state-rg \
  --sku Standard_LRS --kind StorageV2
az storage container create --name tfstate --account-name tfstatewebcrawler

# Initialize and apply
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

This creates: Resource Group, AKS cluster, and Storage Account (with all queues, tables, blob containers).

### Step 2: Deploy to AKS

```bash
# Get AKS credentials
az aks get-credentials --resource-group web-crawler-rg --name web-crawler-aks-dev

# Build and push images to Docker Hub
docker login
for svc in api-gateway crawler-orchestrator url-fetcher content-parser frontend; do
  docker build -t <your-dockerhub-username>/web-crawler-$svc:latest ./$svc
  docker push <your-dockerhub-username>/web-crawler-$svc:latest
done

# Deploy to AKS
sed -i "s|DOCKER_REGISTRY|<your-dockerhub-username>|g" k8s/azure/services.yaml
kubectl create namespace web-crawler --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret docker-registry dockerhub-secret -n web-crawler \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=<your-dockerhub-username> \
  --docker-password=<your-dockerhub-token>
kubectl create secret generic azure-storage-secret -n web-crawler \
  --from-literal=connection-string="$(terraform output -raw storage_connection_string)"
kubectl apply -f k8s/azure/
```

### Step 3: Install NGINX Ingress (optional)

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm install nginx-ingress ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace
```

Then update `k8s/azure/ingress.yaml` with your domain.

## CI/CD (GitHub Actions)

Push to `main` triggers automatic build and deploy. Configure these GitHub Secrets:

| Secret | Description |
|---|---|
| `AZURE_CREDENTIALS` | Azure SP credentials JSON (`az ad sp create-for-rbac --sdk-auth`) |
| `AZURE_STORAGE_CONNECTION_STRING` | Storage account connection string |
| `DOCKER_HUB_USERNAME` | Your Docker Hub username |
| `DOCKER_HUB_TOKEN` | Docker Hub access token |

The workflow (`.github/workflows/deploy.yml`) will:
1. Build all 5 service images
2. Push to ACR (tagged with commit SHA)
3. Deploy to AKS with rolling updates

## Local Development

### Option A: Docker Compose (simplest)

```bash
# Start all services with Azurite (local Azure Storage emulator)
docker compose up --build

# Fresh start (clears all data)
docker compose down -v && docker compose up --build
```

| Service           | URL                          |
|-------------------|------------------------------|
| Frontend          | http://localhost:3000         |
| API Gateway       | http://localhost:8080         |
| Debug Queues      | http://localhost:8080/api/debug/queues |
| Orchestrator      | http://localhost:8081         |
| URL Fetcher       | http://localhost:8082         |
| Content Parser    | http://localhost:8083         |

Auth is disabled locally — no Microsoft login required.

### Option B: Local Kubernetes (Docker Desktop)

This option runs the app in a real Kubernetes cluster on your machine, closer to production.

#### Prerequisites
1. **Docker Desktop** with Kubernetes enabled:
   - Settings → Kubernetes → ✅ Enable Kubernetes → Apply & Restart
   - Wait for the green Kubernetes icon

#### Deploy

```powershell
# Build images, load into containerd, and deploy to k8s
.\k8s\local\deploy-local.ps1 -All

# Start port-forwarding (keep this terminal open)
.\k8s\local\deploy-local.ps1 -PortForward
```

| Service       | URL                                    |
|---------------|----------------------------------------|
| Frontend      | http://localhost:3000                   |
| API Gateway   | http://localhost:8080/api/jobs          |
| Debug Queues  | http://localhost:8080/api/debug/queues  |

#### Useful commands

```powershell
# Check pod status
kubectl get pods -n web-crawler

# View logs
kubectl logs -n web-crawler -l app=url-fetcher --tail=50 -f
kubectl logs -n web-crawler -l app=crawler-orchestrator --tail=50 -f

# Scale workers (test auto-scaling behavior)
kubectl scale deployment url-fetcher -n web-crawler --replicas=3
kubectl scale deployment content-parser -n web-crawler --replicas=2

# Watch HPA
kubectl get hpa -n web-crawler -w

# Teardown
.\k8s\local\deploy-local.ps1 -Teardown
```

#### Notes
- Docker Desktop Kubernetes uses **containerd** — images are loaded via `docker save | docker exec -i desktop-control-plane ctr -n k8s.io images import -` (the deploy script handles this automatically)
- **NodePort doesn't bind to localhost** on Docker Desktop — port-forward is required
- Init containers wait for Azurite before services start
- Images must be reloaded after Kubernetes restarts (`.\k8s\local\deploy-local.ps1 -Build`)

## Manual Deployment

See **Azure Deployment** section above for the recommended Terraform-based approach.

## CI/CD

Automated via GitHub Actions (`.github/workflows/deploy.yml`):
- Push to `main` → builds all images → pushes to ACR → deploys to AKS
- Manual trigger also supported via `workflow_dispatch`

## API Endpoints

### Jobs
- `POST /api/jobs` — upload seed file and start crawl
- `GET /api/jobs` — list jobs
- `GET /api/jobs/{id}` — job status
- `POST /api/jobs/{id}/stop` — stop a crawl
- `DELETE /api/jobs/{id}` — delete a job

### Results
- `GET /api/results/{jobId}` — list crawled URLs
- `GET /api/results/{jobId}/{urlHash}/content` — view crawled page

### Schedules
- `POST /api/schedules` — create recurring crawl
- `GET /api/schedules` — list schedules
- `PUT /api/schedules/{id}` — update schedule
- `DELETE /api/schedules/{id}` — delete schedule
