# Local Kubernetes Deployment Script
# Prerequisites: Docker Desktop with Kubernetes enabled

param(
    [switch]$Build,
    [switch]$Deploy,
    [switch]$Teardown,
    [switch]$Status,
    [switch]$PortForward,
    [switch]$All
)

$ErrorActionPreference = "Stop"
$kubectl = "C:\Program Files\Docker\Docker\resources\bin\kubectl.exe"
$docker = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Build-Images {
    Write-Host "`n=== Building Docker images for local k8s ===" -ForegroundColor Cyan
    
    $services = @(
        @{ Name = "web-crawler-api-gateway"; Context = "api-gateway" },
        @{ Name = "web-crawler-crawler-orchestrator"; Context = "crawler-orchestrator" },
        @{ Name = "web-crawler-url-fetcher"; Context = "url-fetcher" },
        @{ Name = "web-crawler-content-parser"; Context = "content-parser" },
        @{ Name = "web-crawler-frontend"; Context = "frontend" }
    )

    foreach ($svc in $services) {
        Write-Host "`nBuilding $($svc.Name)..." -ForegroundColor Yellow
        $buildArgs = @()
        if ($svc.Context -eq "frontend") {
            $buildArgs = @("--build-arg", "REACT_APP_AUTH_DISABLED=true", "--build-arg", "REACT_APP_API_URL=http://localhost:8080")
        }
        & $docker build -t "$($svc.Name):local" @buildArgs "$repoRoot\$($svc.Context)"
        if ($LASTEXITCODE -ne 0) { throw "Failed to build $($svc.Name)" }
    }

    # Load images into containerd (Docker Desktop k8s uses containerd, not dockerd)
    Write-Host "`n=== Loading images into Kubernetes containerd ===" -ForegroundColor Cyan
    foreach ($svc in $services) {
        $img = "$($svc.Name):local"
        Write-Host "Loading $img into k8s node..." -ForegroundColor Yellow
        & $docker save $img | & $docker exec -i desktop-control-plane ctr -n k8s.io images import -
        if ($LASTEXITCODE -ne 0) { Write-Host "WARNING: Failed to load $img (may already exist)" -ForegroundColor DarkYellow }
    }

    Write-Host "`n=== All images built and loaded ===" -ForegroundColor Green
}

function Deploy-ToK8s {
    Write-Host "`n=== Deploying to local Kubernetes ===" -ForegroundColor Cyan

    # Check cluster is reachable
    & $kubectl cluster-info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Kubernetes cluster not reachable. Enable it in Docker Desktop Settings." -ForegroundColor Red
        exit 1
    }

    # Apply manifests in order
    Write-Host "Deploying Azurite..."
    & $kubectl apply -f "$repoRoot\k8s\local\azurite.yaml"
    
    Write-Host "Deploying ConfigMap..."
    & $kubectl apply -f "$repoRoot\k8s\local\configmap.yaml"
    
    Write-Host "Waiting for Azurite to be ready..."
    & $kubectl wait --namespace web-crawler --for=condition=ready pod -l app=azurite --timeout=60s

    Write-Host "Deploying services..."
    & $kubectl apply -f "$repoRoot\k8s\local\services.yaml"

    Write-Host "`n=== Deployment complete ===" -ForegroundColor Green
    Write-Host ""
    Write-Host "To access the app, start port-forwarding:" -ForegroundColor Yellow
    Write-Host "  .\k8s\local\deploy-local.ps1 -PortForward" -ForegroundColor White
    Write-Host ""
    Write-Host "Then open:" -ForegroundColor Yellow
    Write-Host "  Frontend:    http://localhost:3000" -ForegroundColor White
    Write-Host "  API Gateway: http://localhost:8080/api/jobs" -ForegroundColor White
    Write-Host ""
    Write-Host "Check status:  .\k8s\local\deploy-local.ps1 -Status"
    Write-Host "View pods:     kubectl get pods -n web-crawler"
    Write-Host "View logs:     kubectl logs -n web-crawler -l app=url-fetcher --tail=50 -f"
    Write-Host "Scale workers: kubectl scale deployment url-fetcher -n web-crawler --replicas=3"
}

function Show-Status {
    Write-Host "`n=== Local K8s Status ===" -ForegroundColor Cyan
    & $kubectl get pods -n web-crawler -o wide
    Write-Host ""
    & $kubectl get svc -n web-crawler
    Write-Host ""
    & $kubectl get hpa -n web-crawler
}

function Remove-Deployment {
    Write-Host "`n=== Tearing down local K8s deployment ===" -ForegroundColor Cyan
    & $kubectl delete namespace web-crawler --ignore-not-found
    Write-Host "Done." -ForegroundColor Green
}

function Start-PortForward {
    Write-Host "`n=== Starting port-forward ===" -ForegroundColor Cyan
    Write-Host "Frontend:    http://localhost:3000" -ForegroundColor Yellow
    Write-Host "API Gateway: http://localhost:8080" -ForegroundColor Yellow
    Write-Host "Press Ctrl+C to stop" -ForegroundColor DarkGray
    Write-Host ""

    # Start API gateway port-forward in background
    $apiJob = Start-Job -ScriptBlock {
        & "C:\Program Files\Docker\Docker\resources\bin\kubectl.exe" port-forward -n web-crawler svc/api-gateway 8080:8080
    }
    # Frontend port-forward in foreground
    try {
        & $kubectl port-forward -n web-crawler svc/frontend 3000:80
    } finally {
        Stop-Job $apiJob -ErrorAction SilentlyContinue
        Remove-Job $apiJob -ErrorAction SilentlyContinue
    }
}

# Main
if ($All) { $Build = $true; $Deploy = $true }
if ($Build) { Build-Images }
if ($Deploy) { Deploy-ToK8s }
if ($PortForward) { Start-PortForward }
if ($Teardown) { Remove-Deployment }
if ($Status) { Show-Status }

if (-not ($Build -or $Deploy -or $Teardown -or $Status -or $All)) {
    Write-Host @"
Usage:
  .\deploy-local.ps1 -All        # Build images + deploy to k8s
  .\deploy-local.ps1 -Build      # Build Docker images only
  .\deploy-local.ps1 -Deploy     # Deploy to k8s (images must exist)
  .\deploy-local.ps1 -Status     # Show pod/service status
  .\deploy-local.ps1 -Teardown   # Remove everything

Prerequisites:
  1. Docker Desktop installed with Kubernetes enabled
     (Settings > Kubernetes > Enable Kubernetes > Apply & Restart)
  2. Wait for the Kubernetes status icon to turn green

After deployment:
  Frontend:    http://localhost:30000
  API Gateway: http://localhost:30080/api/jobs
  Debug:       http://localhost:30080/api/debug/queues

Scale test:
  kubectl scale deployment url-fetcher -n web-crawler --replicas=3
  kubectl get pods -n web-crawler -w   # watch pods scale
"@
}
