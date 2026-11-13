$ErrorActionPreference = 'Stop'
if (-not (Test-Path '.env')) { Copy-Item '.env.example' '.env'; Write-Host 'Created .env. Edit passwords and run this script again.' -ForegroundColor Yellow; exit 0 }
& "$PSScriptRoot/check-local-ports.ps1"
docker compose --env-file .env config | Out-Null
docker compose --env-file .env up -d --build
docker compose ps
Write-Host 'Customer: http://localhost:53000'
Write-Host 'Admin:    http://localhost:53001'
Write-Host 'Driver:   http://localhost:53002'
Write-Host 'API:      http://localhost:58080/actuator/health'
