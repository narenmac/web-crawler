# Web Crawler

Scalable web crawling platform built with React, Spring Boot microservices, Azure Storage, and AKS.

## Architecture

- **Frontend** — React 18 + TypeScript SPA
- **API Gateway** — REST API for jobs, schedules, and results
- **Crawler Orchestrator** — BFS coordination, job lifecycle, and schedule execution
- **URL Fetcher** — fetches pages, deduplicates content, stores raw HTML
- **Content Parser** — extracts links from stored HTML, feeds results back to orchestrator

## Azure Services

- AKS (manually provisioned)
- Azure Blob Storage for seed files and raw HTML
- Azure Queue Storage for crawl work distribution
- Azure Table Storage for jobs, schedules, URL metadata, and content hashes

## Project Structure

```text
web-crawler/
├── frontend/               # React SPA
├── api-gateway/             # Spring Boot API Gateway
├── crawler-orchestrator/    # Spring Boot BFS Orchestrator
├── url-fetcher/             # Spring Boot URL Fetcher Worker
├── content-parser/          # Spring Boot Content Parser Worker
├── k8s/                     # Kubernetes manifests (deployed via kubectl)
│   ├── namespaces.yaml
│   ├── frontend/
│   ├── api-gateway/
│   ├── orchestrator/
│   ├── url-fetcher/
│   └── content-parser/
├── docker-compose.yml       # Local dev with Azurite
└── sample-seed-urls.txt     # Test seed URLs
```

## Prerequisites (Manual Setup in Azure Portal)

1. **Resource Group**: `rg-web-crawler-dev`
2. **AKS Cluster**: `aks-web-crawler-dev` (2+ nodes, Standard_DS2_v2)
3. **Storage Account** with:
   - Queues: `url-queue`, `parse-queue`, `result-queue`, `job-control-queue`
   - Blob Containers: `raw-pages`, `seed-files`
   - Tables: `jobs`, `schedules`, `urlmetadata`, `contenthashes`
4. **NGINX Ingress Controller** on AKS:
   ```bash
   helm install nginx-ingress ingress-nginx/ingress-nginx \
     --namespace ingress-nginx --create-namespace
   ```

## GitHub Secrets Required

| Secret | Description |
|---|---|
| `DOCKER_HUB_USERNAME` | Docker Hub username |
| `DOCKER_HUB_TOKEN` | Docker Hub access token |
| `AZURE_CREDENTIALS` | Azure SP credentials JSON |
| `AKS_CLUSTER_NAME` | AKS cluster name (e.g., `aks-web-crawler-dev`) |
| `AKS_RESOURCE_GROUP` | Resource group (e.g., `rg-web-crawler-dev`) |
| `AZURE_STORAGE_CONNECTION_STRING` | Storage account connection string |

## Local Development

```bash
docker-compose up --build
```

- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080
- Orchestrator: http://localhost:8081
- URL Fetcher: http://localhost:8082
- Content Parser: http://localhost:8083

## Manual Deployment

```bash
# Connect to AKS
az aks get-credentials --resource-group rg-web-crawler-dev --name aks-web-crawler-dev

# Create namespace
kubectl apply -f k8s/namespaces.yaml

# Create storage secret
kubectl create secret generic azure-storage \
  --from-literal=connection-string="<YOUR_CONNECTION_STRING>" \
  --namespace=web-crawler \
  --dry-run=client -o yaml | kubectl apply -f -

# Deploy all services
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/api-gateway/
kubectl apply -f k8s/orchestrator/
kubectl apply -f k8s/url-fetcher/
kubectl apply -f k8s/content-parser/
```

## CI/CD

- **CI** (`ci.yml`) — lint, test, build on pull requests
- **CD** (`cd-dev.yml`) — build Docker images, push to Docker Hub, deploy to AKS via kubectl

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
