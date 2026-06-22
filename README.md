# Web Crawler

Scalable web crawling platform built with React, Spring Boot microservices, Azure Storage, and AKS.

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────────┐
│   Frontend  │────▶│  API Gateway │────▶│ Crawler Orchestrator │
│  (React)    │     │ (Spring Boot)│     │   (Spring Boot)      │
└─────────────┘     └──────────────┘     └──────────┬───────────┘
                                                     │
                          ┌──────────────────────────┼──────────────────┐
                          │          Azure Queues     │                  │
                          ▼                          ▼                  ▼
                   ┌─────────────┐          ┌─────────────┐    ┌──────────────┐
                   │ url-queue   │          │ parse-queue  │    │ result-queue │
                   └──────┬──────┘          └──────┬──────┘    └──────┬───────┘
                          ▼                        ▼                   │
                   ┌─────────────┐          ┌──────────────┐           │
                   │ URL Fetcher │          │Content Parser│           │
                   │ (1-8 pods)  │          │  (1-5 pods)  │           │
                   └─────────────┘          └──────────────┘           │
                          │                        │                   │
                          ▼                        ▼                   ▼
                   ┌──────────────────────────────────────────────────────┐
                   │              Azure Storage Account                    │
                   │  Blobs: raw-html, parsed-content, seed-files         │
                   │  Tables: jobs, urlmetadata                           │
                   └──────────────────────────────────────────────────────┘
```

### Services

| Service | Description | Port |
|---------|-------------|------|
| **Frontend** | React 18 + TypeScript SPA | 80 (nginx) |
| **API Gateway** | REST API for jobs, schedules, results | 8080 |
| **Crawler Orchestrator** | BFS coordination, job lifecycle, scheduling | 8081 |
| **URL Fetcher** | Fetches pages, deduplicates content, stores raw HTML | 8082 |
| **Content Parser** | Extracts links from HTML, feeds back to orchestrator | 8083 |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, TypeScript, Axios |
| Backend | Spring Boot 3.2, Java 17 |
| Messaging | Azure Queue Storage |
| Storage | Azure Blob Storage, Azure Table Storage |
| Container Registry | Docker Hub |
| Orchestration | Kubernetes (AKS) |
| Infrastructure | Terraform |
| CI/CD | GitHub Actions |
| Local Dev | Docker Compose + Azurite |

## Project Structure

```text
web-crawler/
├── frontend/               # React SPA
├── api-gateway/            # Spring Boot API Gateway
├── crawler-orchestrator/   # Spring Boot BFS Orchestrator
├── url-fetcher/            # Spring Boot URL Fetcher Worker
├── content-parser/         # Spring Boot Content Parser Worker
├── terraform/              # Infrastructure as Code (AKS, Storage)
├── k8s/
│   ├── azure/              # Production Kubernetes manifests
│   └── local/              # Local dev Kubernetes manifests
├── .github/workflows/      # CI/CD pipeline
├── docker-compose.yml      # Local dev with Azurite
└── sample-seed-urls.txt    # Test seed URLs
```

## Azure Deployment

### Prerequisites

1. [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli) installed
2. [Terraform](https://developer.hashicorp.com/terraform/downloads) >= 1.5
3. [Docker](https://docs.docker.com/get-docker/) installed
4. An Azure subscription
5. A Docker Hub account

### Step 1: Provision Infrastructure with Terraform

```bash
cd terraform

# First time: create a blob container 'tfstate' in your storage account for state
# Azure Portal → Storage Account → Containers → + Container → name: tfstate

# Initialize and apply
az login
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

This creates: Resource Group, AKS cluster (1 node, Standard_B4als_v2), and Storage Account (queues, tables, blob containers).

### Step 2: Build and Push Images

```bash
docker login -u naren433

docker build -t naren433/web-crawler-api-gateway:latest ./api-gateway
docker build -t naren433/web-crawler-orchestrator:latest ./crawler-orchestrator
docker build -t naren433/web-crawler-url-fetcher:latest ./url-fetcher
docker build -t naren433/web-crawler-content-parser:latest ./content-parser
docker build -t naren433/web-crawler-frontend:latest --build-arg REACT_APP_AUTH_DISABLED=true --build-arg REACT_APP_API_BASE_URL=/api ./frontend

docker push naren433/web-crawler-api-gateway:latest
docker push naren433/web-crawler-orchestrator:latest
docker push naren433/web-crawler-url-fetcher:latest
docker push naren433/web-crawler-content-parser:latest
docker push naren433/web-crawler-frontend:latest
```

### Step 3: Deploy to AKS

```bash
# Connect to AKS
az aks get-credentials --resource-group web-crawler-rg --name web-crawler-aks-dev

# Create namespace and secrets
kubectl create namespace web-crawler
kubectl create secret docker-registry dockerhub-secret -n web-crawler \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=naren433 \
  --docker-password=<YOUR_DOCKER_HUB_TOKEN>
kubectl create secret generic azure-storage-secret -n web-crawler \
  --from-literal=connection-string="<YOUR_STORAGE_CONNECTION_STRING>"

# Deploy all resources
kubectl apply -f k8s/azure/

# Install NGINX Ingress Controller
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm install nginx-ingress ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace

# Get public IP (wait for EXTERNAL-IP to appear)
kubectl get svc -n ingress-nginx
```

Access the app at `http://<EXTERNAL-IP>`.

### Step 4: Verify

```bash
kubectl get pods -n web-crawler
kubectl get ingress -n web-crawler
```

## CI/CD (GitHub Actions)

Push to `main` triggers automatic build and deploy. Configure these GitHub Secrets:

| Secret | Description |
|---|---|
| `AZURE_CREDENTIALS` | Azure SP credentials JSON (`az ad sp create-for-rbac --sdk-auth`) |
| `AZURE_STORAGE_CONNECTION_STRING` | Storage account connection string |
| `DOCKER_HUB_USERNAME` | Docker Hub username |
| `DOCKER_HUB_TOKEN` | Docker Hub access token |

The workflow (`.github/workflows/deploy.yml`) will:
1. Build all 5 service images
2. Push to Docker Hub (tagged with commit SHA)
3. Deploy to AKS with rolling updates

## Local Development

### Option A: Docker Compose (simplest)

```bash
# Start all services with Azurite (local Azure Storage emulator)
docker compose up --build

# Fresh start (clears all data)
docker compose down -v && docker compose up --build
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| API Gateway | http://localhost:8080 |
| Debug Queues | http://localhost:8080/api/debug/queues |

Auth is disabled locally — no Microsoft login required.

### Option B: Local Kubernetes (Docker Desktop)

#### Prerequisites
- **Docker Desktop** with Kubernetes enabled (Settings → Kubernetes → ✅ Enable)

#### Deploy

```powershell
.\k8s\local\deploy-local.ps1 -All
.\k8s\local\deploy-local.ps1 -PortForward
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| API Gateway | http://localhost:8080/api/jobs |

#### Useful commands

```powershell
kubectl get pods -n web-crawler
kubectl logs -n web-crawler -l app=url-fetcher --tail=50 -f
kubectl logs -n web-crawler -l app=crawler-orchestrator --tail=50 -f
.\k8s\local\deploy-local.ps1 -Teardown
```

#### Notes
- Docker Desktop k8s uses **containerd** — the deploy script handles image loading automatically
- **NodePort doesn't bind to localhost** — port-forward is required
- Images must be reloaded after k8s restarts (`.\k8s\local\deploy-local.ps1 -Build`)

## API Endpoints

### Jobs
- `POST /api/jobs` — upload seed file and start crawl
- `GET /api/jobs` — list jobs
- `GET /api/jobs/{id}` — job status
- `POST /api/jobs/{id}/stop` — stop a crawl
- `DELETE /api/jobs/{id}` — delete a job

### Results
- `GET /api/results/{jobId}` — list crawled URLs
- `GET /api/results/{jobId}/{urlHash}/content` — view crawled page content

### Schedules
- `POST /api/schedules` — create recurring crawl
- `GET /api/schedules` — list schedules
- `PUT /api/schedules/{id}` — update schedule
- `DELETE /api/schedules/{id}` — delete schedule
