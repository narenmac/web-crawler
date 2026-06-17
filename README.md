# Web Crawler

Scalable web crawling platform built with React, Spring Boot microservices, Azure Storage, and AKS.

## Architecture

- **Frontend** — React 18 + TypeScript SPA with MSAL sign-in
- **API Gateway** — authenticated REST API for jobs, schedules, and results
- **Crawler Orchestrator** — BFS coordination, job lifecycle, and schedule execution
- **URL Fetcher** — fetches pages, applies robots.txt and politeness rules, stores raw HTML
- **Content Parser** — reads stored HTML, extracts links, and feeds results back to the orchestrator

## Azure Services

- AKS with Cluster Autoscaler
- Azure Blob Storage for seed files and raw HTML
- Azure Queue Storage for crawl work distribution
- Azure Table Storage for jobs, schedules, URL metadata, and content hashes
- Azure AD / Entra ID for authentication
- Azure Key Vault for secret management

## Local Development

```bash
docker-compose up --build
```

- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080
- Orchestrator: http://localhost:8081
- URL Fetcher: http://localhost:8082
- Content Parser: http://localhost:8083

## Project Structure

```text
web-crawler/
├── frontend/
├── api-gateway/
├── crawler-orchestrator/
├── url-fetcher/
├── content-parser/
├── infrastructure/
└── docker-compose.yml
```

## Configuration

Environment variables are optional unless noted; each service keeps sensible local defaults.

| Variable | Used by | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | all Spring services | Activates `local`, `dev`, or production configuration |
| `AZURE_STORAGE_CONNECTION_STRING` | all Spring services | Azure Storage connection string |
| `AZURE_AD_ENABLED` | api-gateway | Enables/disables Entra ID validation |
| `AZURE_AD_TENANT_ID` | api-gateway, frontend | Tenant used for API and SPA auth |
| `AZURE_AD_CLIENT_ID` | api-gateway | Resource server client id |
| `APP_CORS_ALLOWED_ORIGINS` | api-gateway | Allowed browser origins |
| `LOCAL_USER_ID` | api-gateway | Fallback user id in local profile |
| `ORCHESTRATOR_START_ENDPOINT` | api-gateway | Internal orchestrator start endpoint |
| `SEED_CONTAINER_NAME` | api-gateway | Blob container for uploaded seed files |
| `CONTENT_CONTAINER_NAME` | api-gateway | Blob container for fetched HTML content |
| `JOB_CONTROL_QUEUE_NAME` | api-gateway, orchestrator | Queue used for stop/start control messages |
| `URL_QUEUE_NAME` | orchestrator, url-fetcher | Queue for fetch work items |
| `PARSE_QUEUE_NAME` | orchestrator, url-fetcher, content-parser | Queue for parse work items |
| `RESULT_QUEUE_NAME` | orchestrator, content-parser | Queue for extracted link results |
| `URL_POISON_QUEUE_NAME` | url-fetcher | Dead-letter queue for fetch failures |
| `PARSE_POISON_QUEUE_NAME` | content-parser | Dead-letter queue for parser failures |
| `URL_METADATA_TABLE_NAME` | url-fetcher, orchestrator | Table storing per-URL crawl metadata |
| `CONTENT_HASHES_TABLE_NAME` | url-fetcher | Table for duplicate-content detection |
| `JOBS_TABLE_NAME` | orchestrator | Table storing crawl jobs |
| `SCHEDULES_TABLE_NAME` | orchestrator | Table storing recurring schedules |
| `SEED_FILES_CONTAINER_NAME` | orchestrator | Blob container for schedule seed files |
| `RAW_HTML_CONTAINER_NAME` | url-fetcher, content-parser | Blob container for raw fetched HTML |
| `MAX_URLS` | orchestrator | Upper bound for discovered URLs per job |
| `FETCH_TIMEOUT_MS` | url-fetcher | Outbound HTTP timeout |
| `FETCH_USER_AGENT` | url-fetcher | User-agent used for page and robots.txt requests |
| `FETCH_MAX_REDIRECTS` | url-fetcher | Redirect hop limit |
| `FETCH_POLITENESS_DELAY` | url-fetcher | Minimum per-domain delay (ISO-8601 duration, default `PT1S`) |
| `ROBOTS_CACHE_TTL` | url-fetcher | In-memory robots.txt cache TTL |
| `QUEUE_POLL_MS` | url-fetcher, content-parser | Worker poll interval |
| `JOB_CONTROL_POLL_MS` | orchestrator | Job-control queue poll interval |
| `RESULT_QUEUE_POLL_MS` | orchestrator | Result queue poll interval |
| `MAX_DEQUEUE_COUNT` | url-fetcher, content-parser | Dead-letter threshold before poison-queue handoff |
| `SHUTDOWN_WAIT_TIMEOUT` | url-fetcher, content-parser | Graceful worker drain timeout |
| `REACT_APP_API_URL` / `REACT_APP_API_BASE_URL` | frontend | Base API URL |
| `REACT_APP_AZURE_TENANT_ID` | frontend | MSAL tenant |
| `REACT_APP_AZURE_CLIENT_ID` | frontend | MSAL SPA client id |
| `REACT_APP_AZURE_REDIRECT_URI` | frontend | MSAL redirect URI |
| `REACT_APP_AZURE_SCOPES` | frontend | Comma/space-separated login scopes |

Each Spring service also exposes `/actuator/health` and `/actuator/info`.

## API Documentation

### Jobs
- `POST /api/jobs` — upload a `.txt` or `.csv` seed file and create a crawl job
- `GET /api/jobs` — list the current user's jobs
- `GET /api/jobs/{id}` — get job status and counters
- `POST /api/jobs/{id}/stop` — request a graceful stop
- `DELETE /api/jobs/{id}` — delete a job and its metadata

### Results
- `GET /api/results/{jobId}?page=&size=&status=&q=` — list crawl results with pagination and filtering
- `GET /api/results/{jobId}/{urlHash}/content` — fetch stored raw HTML for a crawled URL

### Schedules
- `POST /api/schedules` — create a recurring crawl schedule
- `GET /api/schedules` — list schedules for the current user
- `PUT /api/schedules/{id}` — update a schedule
- `DELETE /api/schedules/{id}` — delete a schedule
- `POST /api/schedules/{id}/trigger` — run a schedule immediately

## Scaling

Worker scaling is handled by **KEDA** on AKS:

- `url-fetcher` scales from Azure Queue depth on `url-queue`
- `content-parser` scales from Azure Queue depth on `parse-queue`
- idle workers scale down to reduce cost
- queue-driven scaling keeps fetch and parse capacity independent, so large crawls can burst without overprovisioning the API tier

## CI/CD

- **CI** (`ci.yml`) — lint, test, build, and Terraform validation on pull requests
- **CD Dev** (`cd-dev.yml`) — build images and apply dev infrastructure
- **CD Prod** (`cd-prod.yml`) — build release images and apply prod infrastructure

## Deployment

```bash
cd infrastructure
terraform init
terraform plan -var-file="environments/dev.tfvars"
terraform apply -var-file="environments/dev.tfvars"
```
