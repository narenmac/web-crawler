# Web Crawler Application — Implementation Plan

## Problem Statement

Build a scalable web crawler application with a React frontend and Spring Boot microservices backend, deployed on Azure using Kubernetes. The crawler performs BFS traversal up to 10k unique URLs per job, stores raw HTML and extracted plain text in Azure Blob Storage, uses Azure Queues for async coordination across multiple worker pods, and supports multi-user access via Azure AD (Entra ID).

---

## High-Level Architecture (Azure)

```
                          ┌──────────────┐
                          │   Internet   │
                          └──────┬───────┘
                                 │
                          ┌──────▼───────┐
                          │  Azure DNS   │
                          │  (FQDN)      │
                          └──────┬───────┘
                                 │
                          ┌──────▼───────────────┐
                          │  Public IP Address    │
                          │  (Static, Standard)   │
                          └──────┬───────────────┘
                                 │
  ┌──────────────────────────────┼──────────────────────────────────────────┐
  │  VNet: vnet-web-crawler (10.0.0.0/16)                                  │
  │                              │                                          │
  │  ┌───────────────────────────▼────────────────────────────────────────┐ │
  │  │  Subnet: snet-aks (10.0.1.0/24)                                   │ │
  │  │  NSG: nsg-aks (allow 80/443 inbound, deny all other inbound)      │ │
  │  │                                                                    │ │
  │  │  ┌─────────────────────────────────────────────────────────────┐   │ │
  │  │  │  AKS Cluster (aks-web-crawler)                              │   │ │
  │  │  │                                                             │   │ │
  │  │  │  ┌───────────────────────────────────────────────────────┐  │   │ │
  │  │  │  │  Nginx Ingress Controller (LoadBalancer Service)      │  │   │ │
  │  │  │  │  ← Public IP attached here                           │  │   │ │
  │  │  │  │  Routes:                                              │  │   │ │
  │  │  │  │    /          → frontend (React/Nginx)               │  │   │ │
  │  │  │  │    /api/*     → api-gateway (Spring Boot)            │  │   │ │
  │  │  │  └───────────────────────┬───────────────────────────────┘  │   │ │
  │  │  │                          │                                  │   │ │
  │  │  │  ┌───────────────┐  ┌────▼─────────────┐  ┌─────────────┐ │   │ │
  │  │  │  │ Frontend      │  │ API Gateway      │  │ Orchestrator│ │   │ │
  │  │  │  │ (ClusterIP)   │  │ (ClusterIP)      │  │ (ClusterIP) │ │   │ │
  │  │  │  │ React/Nginx   │  │ Spring Boot      │  │ Spring Boot │ │   │ │
  │  │  │  │ Port 80       │  │ Port 8080        │  │ Port 8081   │ │   │ │
  │  │  │  └───────────────┘  └──────────────────┘  └──────┬──────┘ │   │ │
  │  │  │                                                   │        │   │ │
  │  │  │                          ┌────────────────────────┤        │   │ │
  │  │  │                          │                        │        │   │ │
  │  │  │               ┌─────────▼────────┐  ┌────────────▼──────┐ │   │ │
  │  │  │               │ URL Fetcher      │  │ Content Parser    │ │   │ │
  │  │  │               │ Workers          │  │ Workers           │ │   │ │
  │  │  │               │ (ClusterIP)      │  │ (ClusterIP)       │ │   │ │
  │  │  │               │ Spring Boot      │  │ Spring Boot       │ │   │ │
  │  │  │               │ HPA: 1-5 pods   │  │ HPA: 1-3 pods    │ │   │ │
  │  │  │               │ Port 8082        │  │ Port 8083         │ │   │ │
  │  │  │               │ Pulls: url-queue │  │ Pulls: parse-queue│ │   │ │
  │  │  │               └──────────────────┘  └───────────────────┘ │   │ │
  │  │  └─────────────────────────────────────────────────────────────┘   │ │
  │  └────────────────────────────────────────────────────────────────────┘ │
  │                                                                        │
  │  ┌────────────────────────────────────────────────────────────────────┐ │
  │  │  Subnet: snet-storage-pe (10.0.2.0/24)                           │ │
  │  │  NSG: nsg-storage-pe (allow AKS subnet inbound only)             │ │
  │  │                                                                    │ │
  │  │  ┌──────────────────────────────────────────────────────────────┐ │ │
  │  │  │  Private Endpoints                                           │ │ │
  │  │  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ │ │ │
  │  │  │  │ PE: Blob     │ │ PE: Table    │ │ PE: Queue            │ │ │ │
  │  │  │  │ Storage      │ │ Storage      │ │ Storage              │ │ │ │
  │  │  │  └──────┬───────┘ └──────┬───────┘ └──────────┬───────────┘ │ │ │
  │  │  └─────────┼────────────────┼────────────────────┼──────────────┘ │ │
  │  └────────────┼────────────────┼────────────────────┼────────────────┘ │
  └───────────────┼────────────────┼────────────────────┼──────────────────┘
                  │                │                    │
  ┌───────────────▼────────────────▼────────────────────▼──────────────────┐
  │  Azure Storage Account (stwebcrawler{env})                             │
  │                                                                        │
  │  Blob Containers:          Tables:              Queues:                │
  │  ├── raw-html              ├── jobs             ├── url-queue          │
  │  └── seed-files            ├── urlmetadata      ├── parse-queue       │
  │                            ├── contenthashes    └── job-control-queue  │
  │                            └── schedules                               │
  └────────────────────────────────────────────────────────────────────────┘

  ┌───────────────────────┐   ┌───────────────────────┐
  │  Azure AD (Entra ID)  │   │  Azure Key Vault      │
  │  App Registration:    │   │  kv-web-crawler-{env}  │
  │  - SPA (frontend)     │   │  - Storage conn string │
  │  - API (gateway)      │   │  - AD client secret    │
  └───────────────────────┘   └───────────────────────┘

  ┌───────────────────────┐
  │  Log Analytics        │
  │  Workspace            │
  │  + Container Insights │
  └───────────────────────┘
```

---

## Azure Networking & Security Layout

### Virtual Network (VNet)
| Resource | CIDR | Purpose |
|----------|------|---------|
| `vnet-web-crawler` | `10.0.0.0/16` | Main VNet for all resources |
| `snet-aks` | `10.0.1.0/24` | AKS node pool subnet (254 usable IPs) |
| `snet-storage-pe` | `10.0.2.0/24` | Private Endpoints for Azure Storage |

### Network Security Groups (NSGs)
**nsg-aks** (attached to `snet-aks`):
| Priority | Direction | Action | Source | Dest Port | Purpose |
|----------|-----------|--------|--------|-----------|---------|
| 100 | Inbound | Allow | Internet | 80, 443 | HTTP/HTTPS to Ingress |
| 110 | Inbound | Allow | AzureLoadBalancer | * | Health probes |
| 200 | Inbound | Allow | VNet | * | Inter-subnet (storage PE) |
| 4096 | Inbound | Deny | * | * | Deny all other inbound |
| 100 | Outbound | Allow | * | 443 | HTTPS to internet (crawling) |
| 110 | Outbound | Allow | VNet | * | To storage private endpoints |
| 4096 | Outbound | Allow | * | * | Default outbound |

**nsg-storage-pe** (attached to `snet-storage-pe`):
| Priority | Direction | Action | Source | Dest Port | Purpose |
|----------|-----------|--------|--------|-----------|---------|
| 100 | Inbound | Allow | `10.0.1.0/24` | 443 | AKS → Storage PEs only |
| 4096 | Inbound | Deny | * | * | Deny all other |

### Load Balancer & Public IP
- **Azure Standard Load Balancer** — automatically provisioned by AKS when Nginx Ingress Controller creates a `LoadBalancer` type Service
- **Static Public IP** (`pip-web-crawler-{env}`) — pre-provisioned by Terraform, assigned to the LB
- **DNS**: Optional Azure DNS zone or use nip.io for dev

### Private Endpoints
Storage Account accessed only via Private Endpoints (no public access):
- `pe-blob` → Blob service
- `pe-table` → Table service
- `pe-queue` → Queue service
- Private DNS zones auto-created for name resolution within VNet

### Traffic Flow
```
User → Public IP → Azure LB → Nginx Ingress (in AKS) → Frontend / API Gateway
API Gateway → Orchestrator / Storage (via Private Endpoint)
Orchestrator → Azure Queue (via PE) → Workers pull URLs
Workers → Internet (crawl) → Azure Blob/Table (via PE)
```

---

## Repository Structure

```
web-crawler/
├── README.md
├── .github/
│   └── workflows/
│       ├── ci.yml                    # Build + test on PR
│       ├── cd-dev.yml                # Deploy to dev on merge to main
│       └── cd-prod.yml               # Deploy to prod on release tag
│
├── frontend/                         # React application
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── package.json
│   ├── tsconfig.json
│   ├── public/
│   └── src/
│       ├── App.tsx
│       ├── index.tsx
│       ├── auth/                     # MSAL Azure AD integration
│       ├── components/
│       │   ├── JobDashboard.tsx       # Main dashboard
│       │   ├── SeedUrlUpload.tsx      # File upload for seed URLs
│       │   ├── JobStatus.tsx          # Job progress & control
│       │   ├── ScheduleConfig.tsx     # Scheduling UI
│       │   └── CrawlResults.tsx       # Browse crawled URLs & content
│       ├── services/
│       │   └── apiClient.ts          # Axios client with auth
│       └── types/
│           └── index.ts
│
├── api-gateway/                      # Spring Boot API Gateway
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── ApiGatewayApplication.java
│       ├── config/
│       │   ├── SecurityConfig.java   # Azure AD JWT validation
│       │   └── CorsConfig.java
│       ├── controller/
│       │   ├── JobController.java     # CRUD for crawl jobs
│       │   ├── ScheduleController.java
│       │   └── ResultController.java  # Browse crawl results
│       ├── dto/
│       ├── service/
│       │   ├── JobService.java
│       │   ├── ScheduleService.java
│       │   └── ResultService.java
│       └── model/
│
├── crawler-orchestrator/             # Spring Boot Orchestrator
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── OrchestratorApplication.java
│       ├── service/
│       │   ├── JobManager.java        # Job lifecycle management
│       │   ├── BfsScheduler.java      # BFS level management
│       │   ├── QueuePublisher.java    # Enqueue URLs to crawl
│       │   ├── ScheduleExecutor.java  # Cron-based job scheduling
│       │   └── DeduplicationService.java  # SHA-256 content hash
│       ├── listener/
│       │   └── JobControlListener.java # Stop/cancel signals
│       └── model/
│
├── url-fetcher/                      # Spring Boot URL Fetcher Worker (scalable)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── FetcherApplication.java
│       ├── service/
│       │   ├── FetchService.java       # HTTP fetch URL content
│       │   ├── ContentHasher.java      # SHA-256 content hashing
│       │   ├── BlobStorageService.java  # Store raw HTML in blob
│       │   └── QueueConsumer.java      # Pull URLs from url-queue
│       └── model/
│
├── content-parser/                   # Spring Boot Content Parser Worker (scalable)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── ParserApplication.java
│       ├── service/
│       │   ├── LinkExtractor.java      # Extract links from HTML (Jsoup)
│       │   ├── BlobStorageService.java  # Read raw HTML from blob
│       │   └── QueueConsumer.java      # Pull from parse-queue
│       └── model/
│
├── infrastructure/                   # Terraform IaC
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   ├── providers.tf                  # azurerm, kubernetes, helm providers
│   ├── modules/
│   │   ├── networking/               # VNet, subnets, NSGs, public IP
│   │   ├── aks/                      # AKS cluster, node pools
│   │   ├── storage/                  # Blob + Table + Queue + Private Endpoints + DNS Zones
│   │   ├── keyvault/                 # Key Vault + secret refs
│   │   ├── identity/                 # Azure AD app registrations
│   │   ├── monitoring/              # Log Analytics + Container Insights
│   │   └── k8s-resources/           # All K8s resources via Terraform
│   │       ├── main.tf              # kubernetes + helm providers
│   │       ├── namespace.tf         # K8s namespace
│   │       ├── ingress.tf           # Nginx Ingress (Helm) + Ingress rules
│   │       ├── keda.tf              # KEDA (Helm) for queue-based autoscaling
│   │       ├── frontend.tf          # Deployment + ClusterIP Service
│   │       ├── api-gateway.tf       # Deployment + ClusterIP Service
│   │       ├── orchestrator.tf      # Deployment + ClusterIP Service
│   │       ├── url-fetcher.tf       # Deployment + ClusterIP + KEDA ScaledObject
│   │       ├── content-parser.tf    # Deployment + ClusterIP + KEDA ScaledObject
│   │       ├── configmap.tf         # Shared ConfigMap (storage endpoints, etc.)
│   │       └── secrets.tf           # K8s Secrets from Key Vault
│   └── environments/
│       ├── dev.tfvars
│       └── prod.tfvars
│
└── docker-compose.yml                # Local development
```

---

## Azure Storage Design

### Azure Table Storage

**Jobs Table** (`PartitionKey`: userId, `RowKey`: jobId)
| Field | Type | Description |
|-------|------|-------------|
| status | String | PENDING, RUNNING, STOPPING, COMPLETED, FAILED, CANCELLED |
| totalUrls | Int | Total URLs discovered |
| crawledUrls | Int | URLs successfully crawled |
| currentBfsLevel | Int | Current BFS depth level |
| maxUrls | Int | Cap (10,000) |
| createdAt | DateTime | Job creation timestamp |
| completedAt | DateTime | Job completion timestamp |

**URL Metadata Table** (`PartitionKey`: jobId, `RowKey`: SHA-256 of URL)
| Field | Type | Description |
|-------|------|-------------|
| url | String | The actual URL |
| status | String | QUEUED, CRAWLING, COMPLETED, FAILED, SKIPPED_DUPLICATE |
| bfsLevel | Int | BFS depth from seed |
| contentHash | String | SHA-256 of page content |
| blobPath | String | Path to raw HTML in blob storage |
| parentUrl | String | URL that linked to this |
| crawledAt | DateTime | When crawled |

**Content Hash Table** (`PartitionKey`: jobId, `RowKey`: SHA-256 content hash)
| Field | Type | Description |
|-------|------|-------------|
| firstSeenUrl | String | First URL with this content |
| blobPath | String | Blob path of stored content |

**Schedule Table** (`PartitionKey`: userId, `RowKey`: scheduleId)
| Field | Type | Description |
|-------|------|-------------|
| cronExpression | String | Schedule interval |
| seedFileUrl | String | Blob path to seed file |
| enabled | Boolean | Active/inactive |
| lastRunJobId | String | Last triggered job |
| nextRunAt | DateTime | Next scheduled execution |

### Azure Blob Storage

```
raw-html/
  └── {jobId}/{urlHash}.html

seed-files/
  └── {userId}/{uploadId}.txt
```

### Azure Queue Storage

| Queue | Purpose | Message Content |
|-------|---------|----------------|
| `url-queue` | URLs to fetch | `{ jobId, url, bfsLevel, parentUrl }` |
| `parse-queue` | Pages to parse for link extraction | `{ jobId, url, blobPath, contentHash }` |
| `job-control-queue` | Stop/cancel signals | `{ jobId, action: "STOP" }` |

---

## BFS Crawling Algorithm

```
1. User uploads seed URLs file → stored in Blob Storage
2. Orchestrator creates job, enqueues all seed URLs (BFS level 0) to url-queue
3. URL Fetcher Workers pull from url-queue:
   a. Fetch URL content via HTTP
   b. Compute SHA-256 content hash
   c. Check content-hash table → if duplicate, mark SKIPPED_DUPLICATE, skip storage
   d. Store raw HTML in blob storage
   e. Enqueue blob path to parse-queue for link extraction
4. Content Parser Workers pull from parse-queue (runs independently):
   a. Read raw HTML from blob storage
   b. Extract all links from HTML (Jsoup)
   c. Report discovered links to orchestrator via result-queue
5. Orchestrator collects discovered links:
   a. Deduplicate against already-queued URLs
   b. If total < 10k cap, enqueue new URLs at BFS level + 1
   c. Track BFS level completion
6. Repeat until: all URLs processed OR 10k cap reached OR user stops job
```

---

## Microservices Detail

### 1. Frontend (React + TypeScript)

**Key Features:**
- MSAL.js for Azure AD authentication
- File upload for seed URLs (drag-and-drop, .txt/.csv)
- Job dashboard: create, monitor progress (polling or SSE), stop
- Schedule management: create/edit/delete recurring crawls
- Results browser: paginated list of crawled URLs with links to view content
- Job guard: disable "Start Crawl" button if a job is already running

**Key Libraries:** React 18, TypeScript, MSAL React, Axios, React Router, TailwindCSS

---

## UI Wireframes (ASCII Mocks)

### Page Layout & Navigation

```
┌─────────────────────────────────────────────────────────────────────────┐
│  🕷️ Web Crawler                    [Dashboard] [Schedules] [Results]  👤 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│                        (Page Content Area)                              │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Screen 1: Dashboard (No Active Job)

```
┌─────────────────────────────────────────────────────────────────────────┐
│  🕷️ Web Crawler                    [Dashboard] [Schedules] [Results]  👤 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─── Start New Crawl ────────────────────────────────────────────────┐ │
│  │                                                                     │ │
│  │  Upload Seed URLs:                                                  │ │
│  │  ┌─────────────────────────────────────────────────────────────┐   │ │
│  │  │                                                             │   │ │
│  │  │     📁 Drag & drop a .txt or .csv file here                │   │ │
│  │  │         or click to browse                                  │   │ │
│  │  │                                                             │   │ │
│  │  │     ✅ urls.txt (42 URLs loaded)                            │   │ │
│  │  │                                                             │   │ │
│  │  └─────────────────────────────────────────────────────────────┘   │ │
│  │                                                                     │ │
│  │  Max URLs: [10,000]     BFS Depth: [Unlimited ▼]                   │ │
│  │                                                                     │ │
│  │                              [🚀 Start Crawling]                    │ │
│  │                                                                     │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌─── Recent Jobs ────────────────────────────────────────────────────┐ │
│  │                                                                     │ │
│  │  Job ID          Status       URLs     Started          Source      │ │
│  │  ─────────────────────────────────────────────────────────────────  │ │
│  │  job-a1b2c3      ✅ COMPLETED  9,847   Jun 16, 14:30   Manual     │ │
│  │  job-d4e5f6      ✅ COMPLETED  10,000  Jun 15, 09:00   Scheduled  │ │
│  │  job-g7h8i9      ❌ FAILED     3,201   Jun 14, 22:15   Manual     │ │
│  │  job-j0k1l2      🚫 CANCELLED  5,500   Jun 13, 11:00   Manual     │ │
│  │                                                                     │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Screen 2: Dashboard (Active Job Running)

```
┌─────────────────────────────────────────────────────────────────────────┐
│  🕷️ Web Crawler                    [Dashboard] [Schedules] [Results]  👤 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─── Active Crawl Job ── job-x9y8z7 ────────────────────────────────┐ │
│  │                                                                     │ │
│  │  Status: 🔄 RUNNING                    Source: Manual               │ │
│  │  Started: Jun 17, 14:30 IST            Elapsed: 00:23:15           │ │
│  │                                                                     │ │
│  │  Progress:                                                          │ │
│  │  ┌──████████████████████░░░░░░░░░░░░░░──┐                          │ │
│  │  │           4,823 / 10,000 URLs         │  48%                    │ │
│  │  └──────────────────────────────────────┘                          │ │
│  │                                                                     │ │
│  │  BFS Level: 3          Fetching: 12/s         Parsing: 18/s        │ │
│  │  Duplicates Skipped: 342       Failed: 15     Queued: 2,340        │ │
│  │                                                                     │ │
│  │  ┌─ Live Stats ──────────────────────────────────────────────┐     │ │
│  │  │  URL Fetcher Pods: 4/5      │  Content Parser Pods: 2/3  │     │ │
│  │  │  url-queue depth: 1,240     │  parse-queue depth: 85     │     │ │
│  │  └───────────────────────────────────────────────────────────┘     │ │
│  │                                                                     │ │
│  │                        [⏹️ Stop Crawling]                           │ │
│  │                                                                     │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌─── Start New Crawl (disabled) ─────────────────────────────────────┐ │
│  │                                                                     │ │
│  │  ⚠️ A crawl job is already running. Please wait for it to          │ │
│  │     complete or stop it before starting a new one.                  │ │
│  │                                                                     │ │
│  │                       [🚀 Start Crawling]  (greyed out)             │ │
│  │                                                                     │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Screen 3: Schedules Page

```
┌─────────────────────────────────────────────────────────────────────────┐
│  🕷️ Web Crawler                    [Dashboard] [Schedules] [Results]  👤 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─── Create Schedule ────────────────────────────────────────────────┐ │
│  │                                                                     │ │
│  │  Seed File:   [Select uploaded file ▼]                              │ │
│  │                                                                     │ │
│  │  Interval:    ○ Every 1 hour                                        │ │
│  │               ○ Every 6 hours                                       │ │
│  │               ● Every 12 hours                                      │ │
│  │               ○ Every 24 hours (daily)                              │ │
│  │               ○ Every 7 days (weekly)                               │ │
│  │               ○ Custom cron: [____________]                         │ │
│  │                                                                     │ │
│  │                              [➕ Create Schedule]                    │ │
│  │                                                                     │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌─── Active Schedules ──────────────────────────────────────────────┐  │
│  │                                                                     │ │
│  │  ID        Seed File    Interval    Next Run          Last Status  │ │
│  │  ──────────────────────────────────────────────────────────────── │ │
│  │  sched-01  urls.txt     Every 6h    Jun 17, 20:00     ✅ COMPLETED│ │
│  │  sched-02  sites.csv    Daily       Jun 18, 02:00     ⏭️ SKIPPED │ │
│  │  sched-03  blogs.txt    Weekly      Jun 23, 09:00     ✅ COMPLETED│ │
│  │                                                                     │ │
│  │  Actions per row: [✏️ Edit] [⏸️ Pause] [▶️ Trigger Now] [🗑️ Delete] │ │
│  │                                                                     │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Screen 4: Crawl Results Browser

```
┌─────────────────────────────────────────────────────────────────────────┐
│  🕷️ Web Crawler                    [Dashboard] [Schedules] [Results]  👤 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─── Select Job ─────────────────────────────────────────────────────┐ │
│  │  Job: [job-a1b2c3 - Jun 16, 14:30 - 9,847 URLs ▼]                 │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌─── Job Summary ────────────────────────────────────────────────────┐ │
│  │  Total URLs: 9,847  │  Unique Content: 9,505  │  Duplicates: 342  │ │
│  │  BFS Levels: 4      │  Failed: 15             │  Duration: 1h 23m │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌─── Filter & Search ───────────────────────────────────────────────┐  │
│  │  Search: [_________________________]  Status: [All ▼]  Level: [All▼]│ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌─── Crawled URLs ──────────────────────────────────────────────────┐  │
│  │                                                                     │ │
│  │  #     URL                              Level  Status     Content  │ │
│  │  ────────────────────────────────────────────────────────────────── │ │
│  │  1     https://example.com              0      ✅ OK      [📄 View]│ │
│  │  2     https://example.com/about        1      ✅ OK      [📄 View]│ │
│  │  3     https://example.com/blog         1      ✅ OK      [📄 View]│ │
│  │  4     https://other.com/page           2      🔁 DEDUP   [📄 View]│ │
│  │  5     https://broken.com/404           2      ❌ FAILED  —        │ │
│  │  ...                                                                │ │
│  │                                                                     │ │
│  │  [◀ Prev]  Page 1 of 197  [Next ▶]     Showing 1-50 of 9,847      │ │
│  │                                                                     │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Screen 5: Content Viewer (Modal/Panel)

```
┌─────────────────────────────────────────────────────────────────────────┐
│  📄 Content Viewer                                              [✕]    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  URL:    https://example.com/about                                      │
│  Status: ✅ COMPLETED                                                   │
│  Level:  1 (from: https://example.com)                                  │
│  Hash:   a3f2b8c9d4e5...                                               │
│  Size:   45.2 KB                                                        │
│  Crawled: Jun 16, 14:32 IST                                            │
│                                                                         │
│  ┌─── Raw HTML ──────────────────────────────────────────────────────┐ │
│  │  <!DOCTYPE html>                                                   │ │
│  │  <html lang="en">                                                  │ │
│  │  <head>                                                            │ │
│  │    <meta charset="UTF-8">                                          │ │
│  │    <title>About Us - Example</title>                               │ │
│  │  </head>                                                           │ │
│  │  <body>                                                            │ │
│  │    <h1>About Us</h1>                                               │ │
│  │    <p>We are a company that...</p>                                  │ │
│  │    ...                                                             │ │
│  │  </body>                                                           │ │
│  │  </html>                                                           │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  [📋 Copy HTML]  [⬇️ Download]                                         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Screen 6: Login Page (Azure AD)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│                                                                         │
│                          🕷️ Web Crawler                                 │
│                                                                         │
│                    Scalable Web Crawling Platform                        │
│                                                                         │
│                                                                         │
│                   ┌───────────────────────────┐                         │
│                   │                           │                         │
│                   │  🔑 Sign in with Azure AD │                         │
│                   │                           │                         │
│                   └───────────────────────────┘                         │
│                                                                         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2. API Gateway (Spring Boot)

**Responsibilities:**
- Azure AD JWT token validation
- REST endpoints for job management, scheduling, and results
- Rate limiting
- Request validation (max file size, URL format validation)
- Proxies to internal services

**Key Endpoints:**
```
POST   /api/jobs                  - Create new crawl job (upload seed file)
GET    /api/jobs                  - List user's jobs
GET    /api/jobs/{id}             - Get job status/details
POST   /api/jobs/{id}/stop        - Stop a running job
DELETE /api/jobs/{id}             - Delete a job and its data

POST   /api/schedules             - Create a schedule
GET    /api/schedules             - List user's schedules
PUT    /api/schedules/{id}        - Update schedule
DELETE /api/schedules/{id}        - Delete schedule

GET    /api/results/{jobId}       - List crawled URLs (paginated)
GET    /api/results/{jobId}/{urlHash}/content  - Get raw HTML content
```

### 3. Crawler Orchestrator (Spring Boot)

**Responsibilities:**
- Job lifecycle management (single active job enforcement)
- BFS level coordination
- URL deduplication (track visited URLs per job)
- 10k URL cap enforcement
- Enqueue seed URLs and discovered URLs
- Listen for stop signals on job-control-queue
- Schedule executor (Spring `@Scheduled` or Quartz)

### 4. URL Fetcher Worker (Spring Boot)

**Responsibilities:**
- Pull URLs from `url-queue`
- HTTP fetch with configurable timeout, user-agent, retry
- robots.txt compliance
- SHA-256 content hashing
- Content-hash dedup check against Azure Table
- Store raw HTML to blob storage
- Enqueue blob path to `parse-queue` for link extraction
- Respect politeness delays (configurable per-domain rate limiting)

**Scalability:** HPA on AKS scales fetcher pods 1-5 based on `url-queue` depth.

### 5. Content Parser Worker (Spring Boot)

**Responsibilities:**
- Pull jobs from `parse-queue`
- Read raw HTML from blob storage
- Extract all links from HTML (Jsoup)
- Report discovered links to orchestrator (via result-queue or direct Table update)
- Update URL metadata status in Azure Table

**Scalability:** HPA on AKS scales parser pods 1-3 based on `parse-queue` depth.

---

## Scheduled Crawling System

### Overview

Users can configure recurring crawl jobs that run automatically at specified intervals. The scheduling system is built into the **Orchestrator** using Spring's Quartz Scheduler, with schedule definitions stored in **Azure Table Storage**.

### User Flow

```
┌──────────────────────────────────────────────────────────────────────┐
│  USER SCHEDULING FLOW                                                │
│                                                                      │
│  1. User uploads seed URLs file (stored in Blob: seed-files/)       │
│                                                                      │
│  2. User creates schedule via UI:                                    │
│     ┌──────────────────────────────────────────────────────────┐     │
│     │  Schedule Configuration                                  │     │
│     │                                                          │     │
│     │  Seed File:  [my-urls.txt]              (uploaded)       │     │
│     │                                                          │     │
│     │  Interval:   [Every 6 hours ▼]                           │     │
│     │              Options:                                    │     │
│     │              • Every 1 hour                              │     │
│     │              • Every 6 hours                             │     │
│     │              • Every 12 hours                            │     │
│     │              • Every 24 hours (daily)                    │     │
│     │              • Every 7 days (weekly)                     │     │
│     │              • Custom cron expression                    │     │
│     │                                                          │     │
│     │  Enabled:    [✓]                                         │     │
│     │                                                          │     │
│     │  [Save Schedule]    [Cancel]                              │     │
│     └──────────────────────────────────────────────────────────┘     │
│                                                                      │
│  3. Schedule saved to Azure Table + Quartz job registered           │
│                                                                      │
│  4. At next interval, Orchestrator auto-triggers crawl job          │
│                                                                      │
│  5. User sees scheduled job appear in Job Dashboard with            │
│     source: "SCHEDULED" badge                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### Scheduling Architecture

```
┌──────────┐     ┌──────────────┐     ┌─────────────────────────────────────┐
│ React UI │────▶│ API Gateway  │────▶│ Orchestrator                        │
│          │     │              │     │                                     │
│ Schedule │     │ POST /api/   │     │  ┌───────────────────────────────┐  │
│ Config   │     │  schedules   │     │  │ Quartz Scheduler              │  │
│ Page     │     │              │     │  │                               │  │
└──────────┘     └──────────────┘     │  │  Schedule A: Every 6h        │  │
                                      │  │  Schedule B: Daily at 2am    │  │
                                      │  │  Schedule C: Weekly Monday   │  │
                                      │  │                               │  │
                                      │  │  On trigger:                  │  │
                                      │  │  1. Check: is a job running? │  │
                                      │  │     YES → Skip, log warning  │  │
                                      │  │     NO  → Create new job     │  │
                                      │  │  2. Load seed file from blob │  │
                                      │  │  3. Create job (same as      │  │
                                      │  │     manual trigger flow)     │  │
                                      │  └───────────────────────────────┘  │
                                      │                                     │
                                      │  ┌───────────────────────────────┐  │
                                      │  │ On Startup:                   │  │
                                      │  │ Load all enabled schedules   │  │
                                      │  │ from Azure Table → register  │  │
                                      │  │ Quartz jobs                   │  │
                                      │  └───────────────────────────────┘  │
                                      └────────────┬────────────────────────┘
                                                   │
                                      ┌────────────▼────────────────────┐
                                      │  Azure Table: schedules         │
                                      │  PartitionKey: userId           │
                                      │  RowKey: scheduleId             │
                                      │  cronExpression: "0 */6 * * *"  │
                                      │  seedFileUrl: blob path         │
                                      │  enabled: true                  │
                                      │  lastRunJobId: "job-xyz"        │
                                      │  nextRunAt: 2026-06-18T02:00Z   │
                                      │  lastRunStatus: COMPLETED       │
                                      └─────────────────────────────────┘
```

### Schedule API Endpoints

```
POST   /api/schedules                - Create a new schedule
  Body: { seedFileId, intervalType, cronExpression?, enabled }

GET    /api/schedules                - List user's schedules
  Response: [{ id, seedFileName, interval, nextRunAt, lastRunStatus, enabled }]

PUT    /api/schedules/{id}           - Update schedule (interval, enable/disable)
  Body: { intervalType?, cronExpression?, enabled? }

DELETE /api/schedules/{id}           - Delete a schedule

POST   /api/schedules/{id}/trigger   - Manually trigger a scheduled crawl now
```

### Conflict Resolution

When a scheduled crawl triggers but another job is already running:

```
Scheduled trigger fires
        │
        ▼
Is another job RUNNING?
        │
   ┌────┴────┐
   │ YES     │ NO
   │         │
   ▼         ▼
SKIP this    Create job
run, log     normally
warning,     │
update       ▼
lastRunStatus Job starts
= SKIPPED    crawling
```

- **No queueing of scheduled runs** — if a job is running, the scheduled run is simply skipped
- The `lastRunStatus` field in the schedule table tracks: `COMPLETED`, `FAILED`, `SKIPPED` (conflict), `RUNNING`
- Users can see skipped runs in the UI schedule history

---

## Scaling Architecture

### Multi-Instance Scaling View

```
                    ┌─────────────────────────┐
                    │     Nginx Ingress        │
                    │     (LoadBalancer)        │
                    └────────┬────────┬────────┘
                             │        │
                    ┌────────▼──┐  ┌──▼────────┐
                    │ Frontend  │  │ Frontend   │
                    │ Pod 1     │  │ Pod 2      │
                    │ (Nginx)   │  │ (Nginx)    │  ← Static replicas: 2
                    └───────────┘  └────────────┘
                             │
                    ┌────────▼──┐  ┌────────────┐
                    │ API GW    │  │ API GW     │
                    │ Pod 1     │  │ Pod 2      │  ← Static replicas: 2
                    └─────┬─────┘  └─────┬──────┘
                          │              │
                    ┌─────▼──────────────▼─────┐
                    │     Orchestrator          │
                    │     Pod 1 (singleton)     │  ← Single instance (leader)
                    │     - BFS level mgmt     │
                    │     - 10k cap tracking   │
                    │     - Job lifecycle       │
                    └──────┬───────────┬────────┘
                           │           │
              ┌────────────▼──┐   ┌────▼──────────────┐
              │   url-queue   │   │   parse-queue      │
              │  (Azure Queue)│   │  (Azure Queue)     │
              └──┬──┬──┬──┬───┘   └──┬──┬──┬───────────┘
                 │  │  │  │          │  │  │
    ┌────────────┘  │  │  └───┐     │  │  └────────┐
    │     ┌─────────┘  │      │     │  │           │
    ▼     ▼            ▼      ▼     ▼  ▼           ▼
┌──────┐┌──────┐┌──────┐┌──────┐ ┌──────┐┌──────┐┌──────┐
│Fetch ││Fetch ││Fetch ││Fetch │ │Parse ││Parse ││Parse │
│Pod 1 ││Pod 2 ││Pod 3 ││Pod 4 │ │Pod 1 ││Pod 2 ││Pod 3 │
│      ││      ││      ││  5↕  │ │      ││      ││ 3↕   │
└──┬───┘└──┬───┘└──┬───┘└──┬───┘ └──┬───┘└──┬───┘└──┬───┘
   │       │       │       │        │       │       │
   └───────┴───────┴───────┘        └───────┴───────┘
           │                                │
           ▼                                ▼
   ┌───────────────┐                ┌───────────────┐
   │ Azure Blob    │                │ Azure Table   │
   │ (raw-html)    │                │ (url metadata)│
   └───────────────┘                └───────────────┘
```

### Horizontal Pod Autoscaler (HPA) Configuration

| Service | Min Pods | Max Pods | Scale Metric | Target | Scale Behavior |
|---------|----------|----------|-------------|--------|----------------|
| Frontend | 2 | 4 | CPU utilization | 70% | Static for most loads |
| API Gateway | 2 | 4 | CPU utilization | 70% | Static for most loads |
| Orchestrator | 1 | 1 | — | — | Singleton (stateful BFS coordination) |
| URL Fetcher | 1 | 5 | `url-queue` message count | 100 msgs/pod | Scale up fast, scale down slow |
| Content Parser | 1 | 3 | `parse-queue` message count | 200 msgs/pod | Scale up fast, scale down slow |

### Scaling Scenarios

**Idle (no active crawl job):**
```
Frontend: 2 pods  |  API GW: 2 pods  |  Orchestrator: 1 pod
URL Fetcher: 1 pod (idle)  |  Content Parser: 1 pod (idle)
Total: 7 pods
```

**Light crawl (< 1000 URLs queued):**
```
Frontend: 2 pods  |  API GW: 2 pods  |  Orchestrator: 1 pod
URL Fetcher: 2 pods  |  Content Parser: 1 pod
Total: 8 pods
```

**Heavy crawl (5000+ URLs queued, peak load):**
```
Frontend: 2 pods  |  API GW: 2 pods  |  Orchestrator: 1 pod
URL Fetcher: 5 pods (max)  |  Content Parser: 3 pods (max)
Total: 13 pods
```

### Scaling Flow (Queue-Driven Autoscaling)

```
url-queue depth ↑ ──► KEDA/HPA detects ──► Scale URL Fetcher pods ↑
                                              │
                                              ▼
                                     More URLs fetched
                                              │
                                              ▼
                                     parse-queue depth ↑
                                              │
                                              ▼
                              KEDA/HPA detects ──► Scale Content Parser pods ↑
                                                         │
                                                         ▼
                                                More links extracted
                                                         │
                                                         ▼
                                          Orchestrator enqueues new URLs
                                                         │
                                                         ▼
                                               url-queue depth ↑ (cycle)
```

### Queue-Based Scaling with KEDA

For Azure Queue-based autoscaling, we use [KEDA (Kubernetes Event-Driven Autoscaling)](https://keda.sh/) which natively supports Azure Storage Queues:

```yaml
# Example KEDA ScaledObject for URL Fetcher
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: url-fetcher-scaler
spec:
  scaleTargetRef:
    name: url-fetcher
  minReplicaCount: 1
  maxReplicaCount: 5
  cooldownPeriod: 60
  triggers:
    - type: azure-queue
      metadata:
        queueName: url-queue
        queueLength: "100"     # Scale up when > 100 msgs per pod
        connectionFromEnv: AZURE_STORAGE_CONNECTION_STRING
```

**KEDA is deployed via Helm in Terraform** (`k8s-resources/keda.tf`), and ScaledObjects replace standard HPA for queue-driven services.

### How Dynamic Pod Spawning Works

The scaling is fully automatic — no manual intervention needed. Here's the step-by-step mechanics:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DYNAMIC SCALING FLOW                              │
│                                                                     │
│  1. QUEUE FILLS UP                                                  │
│     url-queue: 500 messages waiting                                 │
│                    │                                                │
│                    ▼                                                │
│  2. KEDA POLLS QUEUE (every 30s)                                   │
│     KEDA operator checks Azure Queue message count                 │
│     Current: 500 msgs / 100 per pod = 5 pods needed               │
│     Running: 2 pods → Need 3 more                                  │
│                    │                                                │
│                    ▼                                                │
│  3. KEDA UPDATES HPA                                               │
│     Sets desiredReplicas = 5 on the Deployment                     │
│                    │                                                │
│                    ▼                                                │
│  4. K8s SCHEDULER CREATES PODS                                     │
│     Kubernetes creates 3 new url-fetcher pods                      │
│     Scheduler finds nodes with available resources                 │
│                    │                                                │
│                    ▼                                                │
│  5. IF NO NODE CAPACITY → AKS CLUSTER AUTOSCALER KICKS IN         │
│     Detects "unschedulable" pods                                   │
│     Provisions new VM nodes in the AKS node pool                   │
│     New nodes join cluster → pending pods get scheduled             │
│                    │                                                │
│                    ▼                                                │
│  6. NEW PODS START CONSUMING                                       │
│     Each new pod connects to Azure Queue                           │
│     Pulls messages independently (competing consumers)             │
│     No coordination needed — queue handles distribution            │
│                    │                                                │
│                    ▼                                                │
│  7. SCALE DOWN (when queue drains)                                 │
│     KEDA detects low queue depth → reduces replica count           │
│     K8s gracefully terminates excess pods (respects terminationGracePeriodSeconds) │
│     AKS Cluster Autoscaler removes underutilized nodes after cooldown │
└─────────────────────────────────────────────────────────────────────┘
```

**Key point: Workers are stateless.** Each worker pod:
- Connects to Azure Queue on startup
- Pulls messages one at a time (or in small batches)
- Processes independently — no coordination with other workers
- Can be killed at any time (in-flight message returns to queue after visibility timeout)

This is why scaling is trivial: just add more pods that pull from the same queue.

### Scaling to 1 Million URLs

The current design (10k URLs, 5 max fetchers) is a configuration choice, not an architectural limit. Here's what changes for 1M URLs:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    10K URLs (Current)    vs    1M URLs (Scaled)            │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  AKS Node Pool:                                                            │
│    2-5 nodes (Standard_D2s_v3)    →    5-20 nodes (Standard_D4s_v3)       │
│    2 vCPU, 8GB per node                4 vCPU, 16GB per node              │
│                                                                            │
│  URL Fetcher:                                                              │
│    1-5 pods (KEDA max: 5)         →    5-50 pods (KEDA max: 50)           │
│    queueLength: 100 msgs/pod           queueLength: 500 msgs/pod         │
│                                                                            │
│  Content Parser:                                                           │
│    1-3 pods (KEDA max: 3)         →    3-20 pods (KEDA max: 20)           │
│    queueLength: 200 msgs/pod           queueLength: 500 msgs/pod         │
│                                                                            │
│  Azure Queue:                                                              │
│    Standard tier (sufficient)     →    Standard tier (handles millions)    │
│                                                                            │
│  Azure Table:                                                              │
│    Single table (sufficient)      →    Partition by jobId (already done)   │
│    10k rows per job                    1M rows per job                     │
│                                                                            │
│  Azure Blob:                                                               │
│    ~10k blobs                     →    ~1M blobs                          │
│    ~5 GB storage                       ~500 GB storage                    │
│                                                                            │
│  Orchestrator:                                                             │
│    In-memory URL visited set      →    Azure Table-backed visited set     │
│    (10k fits in memory)                (1M needs external storage)        │
│                                                                            │
│  Config change:                                                            │
│    MAX_URLS_PER_JOB=10000         →    MAX_URLS_PER_JOB=1000000          │
│    (application.yml)                   (application.yml)                  │
│                                                                            │
│  Subnet sizing:                                                            │
│    snet-aks: /24 (254 IPs)        →    snet-aks: /22 (1022 IPs)          │
│    (enough for 5 nodes + pods)         (enough for 20 nodes + 70 pods)   │
└────────────────────────────────────────────────────────────────────────────┘
```

**What stays the same at 1M scale:**
- Architecture (same services, same queues, same storage)
- Code (no code changes — just config)
- KEDA autoscaling (just increase `maxReplicaCount`)
- AKS Cluster Autoscaler (just increase node pool max)

**What needs adjustment (config/infra only):**

| Change | Where | How |
|--------|-------|-----|
| Increase max URL cap | `application.yml` | `MAX_URLS_PER_JOB=1000000` |
| Increase max fetcher pods | KEDA ScaledObject | `maxReplicaCount: 50` |
| Increase max parser pods | KEDA ScaledObject | `maxReplicaCount: 20` |
| Larger AKS node pool | Terraform `aks` module | `max_count: 20`, `vm_size: Standard_D4s_v3` |
| Larger subnet | Terraform `networking` module | `/22` instead of `/24` |
| URL visited set | Orchestrator code | Move from in-memory `HashSet` to Azure Table lookup |
| Politeness delays | Worker config | May need per-domain rate limiting to avoid getting blocked |

---

## Infrastructure (Terraform)

### Azure Resources
1. **Resource Group** — `rg-web-crawler-{env}`
2. **Virtual Network** — `vnet-web-crawler` (`10.0.0.0/16`)
   - Subnet `snet-aks` (`10.0.1.0/24`) — AKS node pool
   - Subnet `snet-storage-pe` (`10.0.2.0/24`) — Storage Private Endpoints
3. **Network Security Groups**
   - `nsg-aks` — Allow 80/443 inbound, allow HTTPS outbound (crawling), inter-VNet
   - `nsg-storage-pe` — Allow inbound only from AKS subnet
4. **Public IP** — `pip-web-crawler-{env}` (Static, Standard SKU)
5. **AKS Cluster** — `aks-web-crawler-{env}` (Azure CNI networking)
   - Default node pool: Standard_D2s_v3, min 2 / max 5 nodes
   - **Cluster Autoscaler enabled** — auto-provisions/deprovisions nodes when pods are unschedulable
   - Node pool sizing configurable via Terraform variables (scale to D4s_v3, max 20 for 1M URLs)
6. **Storage Account** — `stwebcrawler{env}` (public network access disabled)
   - Blob containers: `raw-html`, `seed-files`
   - Tables: `jobs`, `urlmetadata`, `contenthashes`, `schedules`
   - Queues: `url-queue`, `parse-queue`, `job-control-queue`
   - Private Endpoints: `pe-blob`, `pe-table`, `pe-queue` in `snet-storage-pe`
   - Private DNS Zones: `privatelink.blob.core.windows.net`, etc.
7. **Azure AD App Registration** — SPA (frontend) + API (gateway)
8. **Docker Hub** — Container images (as specified)
9. **Key Vault** — `kv-web-crawler-{env}` — Storage connection strings, AD secrets
10. **Log Analytics Workspace** + Container Insights — AKS monitoring

### Kubernetes Resources (managed by Terraform via `kubernetes` and `helm` providers)
Terraform uses the AKS cluster credentials (output from the `aks` module) to configure the `kubernetes` and `helm` providers. All K8s resources are created automatically by `terraform apply` — no separate `kubectl apply` needed.

| Resource | Terraform Resource Type | Details |
|----------|------------------------|---------|
| Namespace | `kubernetes_namespace` | `web-crawler` namespace |
| Nginx Ingress | `helm_release` | `ingress-nginx` chart, `LoadBalancer` with Public IP |
| KEDA | `helm_release` | KEDA for Azure Queue-based autoscaling |
| Ingress Rules | `kubernetes_ingress_v1` | `/` → frontend, `/api/*` → api-gateway |
| Frontend | `kubernetes_deployment_v1` + `kubernetes_service_v1` | React/Nginx, ClusterIP:80, 2 replicas |
| API Gateway | `kubernetes_deployment_v1` + `kubernetes_service_v1` | Spring Boot, ClusterIP:8080, 2 replicas |
| Orchestrator | `kubernetes_deployment_v1` + `kubernetes_service_v1` | Spring Boot, ClusterIP:8081, 1 replica (singleton) |
| URL Fetcher | `kubernetes_deployment_v1` + `kubernetes_service_v1` + KEDA `ScaledObject` | Spring Boot, 1-5 pods, scales on url-queue depth |
| Content Parser | `kubernetes_deployment_v1` + `kubernetes_service_v1` + KEDA `ScaledObject` | Spring Boot, 1-3 pods, scales on parse-queue depth |
| ConfigMap | `kubernetes_config_map_v1` | Storage endpoints, queue names, app config |
| Secrets | `kubernetes_secret_v1` | Pulled from Key Vault via Terraform `azurerm_key_vault_secret` data sources |

---

## CI/CD (GitHub Actions)

### CI Pipeline (`ci.yml`) — on PR
1. **Frontend:** `npm ci → npm run lint → npm test → npm run build`
2. **API Gateway:** `mvn verify`
3. **Orchestrator:** `mvn verify`
4. **URL Fetcher:** `mvn verify`
5. **Content Parser:** `mvn verify`
6. **Terraform:** `terraform fmt -check → terraform validate → terraform plan`

### CD Pipeline (`cd-dev.yml`) — on merge to main
1. Build Docker images for all services
2. Push to Docker Hub (`{dockerhub-user}/web-crawler-{service}:${SHA}`)
3. `terraform plan` with updated image tags (passed as variables)
4. `terraform apply -auto-approve` → provisions/updates Azure infra + all K8s resources
5. Run smoke tests

### CD Prod (`cd-prod.yml`) — on release tag
1. Promote tested images from dev
2. `terraform apply` for prod (with prod.tfvars + image tags)
3. Terraform handles rolling deployment to prod AKS automatically

---

## Implementation Phases

### Phase 1: Project Scaffolding & Infrastructure Foundation
- Initialize monorepo structure (all folders, root configs)
- Create Spring Boot projects (api-gateway, crawler-orchestrator, url-fetcher, content-parser)
- Create React app with TypeScript + TailwindCSS
- Write Dockerfiles for all 5 services
- Create docker-compose.yml for local dev (Azurite for storage emulation)
- Set up Terraform modules (resource group, storage account, AKS skeleton)

### Phase 2: Core Crawling Engine
- Implement Azure Storage clients (Table, Blob, Queue) as shared library
- Build url-fetcher worker: HTTP fetch, content hashing, raw HTML blob storage, enqueue to parse-queue
- Build content-parser worker: pull from parse-queue, read raw HTML from blob, extract links (Jsoup), report to orchestrator
- Build BFS orchestrator: seed URL enqueue, level tracking, 10k cap
- Implement content-hash deduplication via Azure Table
- Implement URL deduplication (visited set per job)
- Add job lifecycle: create, run, complete, fail states
- Wire up Azure Queue producers/consumers between all services

### Phase 3: API Gateway & Job Management
- Implement Azure AD JWT validation (Spring Security + MSAL)
- Build REST endpoints for jobs, results, schedules
- Implement single-active-job enforcement
- Implement stop/cancel job flow via job-control-queue
- Add seed file upload to blob storage
- Paginated results API with blob content retrieval

### Phase 4: Frontend Application
- Set up MSAL React for Azure AD login
- Build seed URL file upload component
- Build job dashboard with real-time status polling
- Build stop/cancel job controls
- Build schedule management UI
- Build crawl results browser with link to raw HTML content viewer
- Responsive design with TailwindCSS

### Phase 5: Scheduling System
- Implement schedule CRUD in API Gateway
- Build schedule executor in orchestrator (Spring Scheduling / Quartz)
- Wire schedule triggers to job creation
- Handle conflict (scheduled job while another is running → skip or queue)

### Phase 6: Infrastructure & Deployment
- Complete Terraform modules for Azure resources (networking, AKS, storage, keyvault, identity, monitoring)
- Build `k8s-resources` Terraform module: namespace, ingress (Helm), deployments, services, HPA, configmaps, secrets
- Wire `kubernetes` + `helm` providers using AKS cluster output credentials
- Image tags passed as Terraform variables (updated by CI/CD pipeline)
- Set up Docker Hub image push workflow
- Build CI pipeline (lint, test, build for all services + `terraform validate`)
- Build CD pipelines: `terraform apply` deploys both Azure infra and K8s resources in one step
- Configure HPA for url-fetcher (1-5 pods on url-queue depth) and content-parser (1-3 pods on parse-queue depth)

### Phase 7: Hardening & Polish
- Add robots.txt compliance to crawler
- Add per-domain politeness delays
- Add retry logic with exponential backoff
- Add structured logging (JSON) + correlation IDs
- Add health checks and readiness probes for K8s
- Error handling and graceful shutdown
- End-to-end testing with Azurite
- Documentation (README, API docs, deployment guide)

---

## Key Design Decisions

1. **Single active job enforcement:** Orchestrator checks Azure Table before starting; only one job can have status=RUNNING at a time.
2. **BFS ordering:** Orchestrator manages BFS levels by tagging each URL with its level. Fetchers process URLs regardless of level order (parallelism), but new URLs are enqueued at `parentLevel + 1`.
3. **10k cap:** Orchestrator tracks total URLs enqueued per job. Once 10k reached, stops enqueuing new discovered URLs but lets in-flight crawls complete.
4. **Content dedup:** SHA-256 hash of response body. Check Azure Table before storing; if hash exists, mark URL as SKIPPED_DUPLICATE and link to existing blob path.
5. **Graceful stop:** STOP signal on job-control-queue. Workers check for stop flag before processing next URL. Orchestrator marks job as STOPPING → CANCELLED.
6. **Separated fetcher/parser:** URL Fetcher handles network I/O (HTTP fetch, blob storage). Content Parser handles CPU work (link extraction). Each scales independently via separate HPA + queue.
7. **No plain text storage:** Only raw HTML is stored. The Content Parser extracts links for BFS traversal but does not store parsed output.
8. **Docker Hub** for container registry (as specified), not ACR.
9. **Azurite** for local development to emulate Azure Storage.
