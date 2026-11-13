# Run Suabhi Cab Booking locally with Docker Desktop (Windows)

This deployment is isolated from your other local projects. All containers, volumes and the private network have a `suabhi-cab-` prefix. Host ports are deliberately different from common defaults.

## Port map

| Service | Container port | Local host port | URL / connection |
|---|---:|---:|---|
| PostgreSQL + PostGIS | 5432 | **55432** | `localhost:55432` |
| Redis | 6379 | **56379** | `localhost:56379` |
| Spring Boot API | 8080 | **58080** | `http://localhost:58080` |
| Customer website | 3000 | **53000** | `http://localhost:53000` |
| Admin console | 80 | **53001** | `http://localhost:53001` |
| Driver PWA | 3000 | **53002** | `http://localhost:53002` |

Within Docker, services must use service DNS and container ports: PostgreSQL `postgres:5432`, Redis `redis:6379`, backend `cab-booking-api:8080`. Do not put host-mapped ports in container-to-container URLs.

## 0. Clone the branch and apply the uploaded template package

The GitHub feature branch contains the original backend foundation. The supplied UI templates include many image/font assets, so the full template integration is also delivered as a repository-root overlay ZIP.

```powershell
git clone -b feature/phase-1-foundation https://github.com/rahulmoundekar/premium-cab-booking-platform.git
Set-Location premium-cab-booking-platform
```

Download `suabhi-cab-booking-template-docker-overlay.zip` from the ChatGPT response and copy it into this repository root. Then extract its contents over the clone (the archive paths are already repository-root-relative):

```powershell
Expand-Archive -Path .\suabhi-cab-booking-template-docker-overlay.zip -DestinationPath . -Force
```

The archive replaces the initial placeholder frontend workspaces with the supplied customer and admin templates and adds the Docker files. The root `.gitignore` excludes the ZIP and local `.env` file.

Commit the template assets and frontend integration to your branch so the complete project is stored in GitHub:

```powershell
git status --short
git add -A
git commit -m "feat: integrate supplied customer and admin templates"
git push origin feature/phase-1-foundation
```

## 1. Check prerequisites and port availability

Open PowerShell in the repository directory. Confirm Docker Desktop is running:

```powershell
docker version
docker compose version
```

Check that the selected host ports are not already occupied:

```powershell
$ports = 55432,56379,58080,53000,53001,53002
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object { $ports -contains $_.LocalPort } |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

If another application uses one of these ports, change the corresponding value in `.env` before starting. Do not stop or remove any other project's containers or volumes.

## 2. Configure local environment

```powershell
Copy-Item .env.example .env
notepad .env
```

Replace `POSTGRES_PASSWORD` and `REDIS_PASSWORD` with unique, strong local-only values. Keep `.env` private; it is ignored by Git. Keep `VITE_DEMO_MODE=true` only for local preview.

The admin demo login (`admin@suabhi.local` / `local-preview-only`) is bundled into the JavaScript and is not a secret. It is only for a local loopback preview. Do not expose this admin build to a LAN or the internet. For any shared/production deployment, disable demo mode and complete the real identity, session and authorization APIs first.

## 3. Validate Compose, build and start the stack

```powershell
docker compose --env-file .env config
docker compose --env-file .env up -d --build
```

The first build downloads base images and package dependencies, so it requires Docker registry and package-registry access. It may take longer than later starts.

## 4. Verify containers and logs

```powershell
docker compose ps
docker compose logs -f postgres redis
docker compose logs -f cab-booking-api
```

Open these URLs:

- Customer: http://localhost:53000
- Admin: http://localhost:53001
- Driver: http://localhost:53002
- API health: http://localhost:58080/actuator/health
- Fleet categories API: http://localhost:58080/api/v1/public/fleet/categories

PostgreSQL and Redis are bound to loopback only. Connect to PostgreSQL using host `localhost`, port `55432`, database `suabhi_cab_booking_db`, user `cab_booking_app`, and the password in `.env`. Connect to Redis on `localhost:56379` with the configured password.

## 5. Common operations

```powershell
# Stop containers but preserve database/cache data
docker compose down

# Start again without rebuilding
docker compose --env-file .env up -d

# Rebuild only after source/config changes
docker compose --env-file .env up -d --build

# Follow API logs
docker compose logs -f cab-booking-api

# Inspect the database container
docker exec -it suabhi-cab-postgres psql -U cab_booking_app -d suabhi_cab_booking_db
```

To remove this application's persistent data permanently (database and Redis), use this only when you intentionally want a clean reset:

```powershell
docker compose down -v
```

Do not use `docker system prune --volumes` to troubleshoot this project; it can remove resources belonging to other applications.

## 6. Private-cloud deployment notes

The Compose file binds host ports to `127.0.0.1`; place a TLS-terminating reverse proxy or private-cloud ingress in front of the web/API services. For a non-local deployment, use a secret manager, real TLS certificates, `SESSION_COOKIE_SECURE=true`, `CSRF_COOKIE_SECURE=true`, `HSTS_ENABLED=true`, exact CORS origins, backups and restricted database access. Do not publish PostgreSQL or Redis to the public network.

The admin workspace's demo mode must be disabled, and identity/authorization APIs must be completed before the operations console is exposed to users. The customer Rentaly forms and driver PWA are currently UI foundations, not production booking/trip flows.


## Admin dependency lockfile repair

The admin template's original lockfile contained four incorrect transitive versions (0.1.0). The branch lockfile is repaired to the versions that match the template's original dependency graph: d3-interpolate 3.0.1, d3-ease 3.0.1, d3-timer 3.0.1 and json-buffer 3.0.1. Keep package.json and package-lock.json together; do not delete the lockfile.

After pulling the latest feature branch and extracting the corrected template overlay, rebuild only the admin image first:

```powershell
git pull origin feature/phase-1-foundation
docker compose build --no-cache --progress=plain admin-web
```

If npm ci succeeds, rebuild/start the stack:

```powershell
docker compose --env-file .env up -d --build
docker compose ps
```
