# Suabhi Cab Booking — local Docker Desktop guide (Windows)

## Apply the supplied template overlay

Clone the feature branch:

```powershell
git clone -b feature/phase-1-foundation https://github.com/rahulmoundekar/premium-cab-booking-platform.git
Set-Location premium-cab-booking-platform
```

Download the separately supplied `suabhi-cab-booking-template-docker-overlay.zip`, copy it to the repository root and extract over the clone:

```powershell
Expand-Archive -Path .\suabhi-cab-booking-template-docker-overlay.zip -DestinationPath . -Force
```

The ZIP contains the uploaded React Material admin source, Rentaly customer pages/assets and matching Docker files. Apply it before starting Compose.

## Reserved loopback-only host ports

| Service | Host port | Local URL |
|---|---:|---|
| PostgreSQL + PostGIS | 55432 | localhost:55432 |
| Redis | 56379 | localhost:56379 |
| Spring Boot API | 58080 | http://localhost:58080 |
| Customer website | 53000 | http://localhost:53000 |
| Admin console | 53001 | http://localhost:53001 |
| Driver PWA | 53002 | http://localhost:53002 |

The ports bind to 127.0.0.1 and have unique Suabhi container, volume and network names. Inside Compose use postgres:5432, redis:6379 and cab-booking-api:8080.

## Start

```powershell
docker version
docker compose version
Copy-Item .env.example .env
notepad .env
.scripts\check-local-ports.ps1
docker compose --env-file .env config
docker compose --env-file .env up -d --build
docker compose ps
```

Set unique strong values for POSTGRES_PASSWORD and REDIS_PASSWORD in .env. Keep VITE_DEMO_MODE=true only for local preview. The admin preview login is admin@suabhi.local / local-preview-only. It is bundled into browser JavaScript and is not a secret. Do not expose demo mode beyond loopback.

## Open

- Customer: http://localhost:53000
- Admin: http://localhost:53001
- Driver: http://localhost:53002
- Health: http://localhost:58080/actuator/health
- Fleet API: http://localhost:58080/api/v1/public/fleet/categories

## Operate

```powershell
docker compose logs -f cab-booking-api
docker compose logs -f customer-web
docker compose down
```

`docker compose down` preserves named database/cache volumes. Use `docker compose down -v` only when intentionally deleting this application's local PostgreSQL and Redis data. Do not run global prune commands to troubleshoot this project.

The first image build downloads Maven/NPM dependencies and container images. It requires Docker Desktop plus registry access. This guide does not imply the build was run in the assistant execution environment.

The customer theme's static booking forms and the admin/driver UI shells are not yet connected to real login, booking, fare, payment, dispatch or tracking APIs. Complete those modules, security checks, backups and TLS before any shared/production deployment.
