# Suabhi Cab Booking

Standalone premium cab booking platform for Pune and Mumbai. This repository is independent of all other products.

## Frontend templates

- **Customer:** uploaded Rentaly dark vehicle-rental template is the primary customer design. The original CSS/JS/fonts/images and page templates are retained under `frontend/apps/customer-web/public/rentaly`; the Next.js app serves it from `/rentaly/`.
- **Admin:** uploaded React Material Admin is the primary operations UI foundation. It keeps its React/Vite/MUI/React Router stack, and the template sidebar/routes have been changed to Suabhi bookings, dispatch, fleet, drivers, customers, pricing, payments, corporate, service areas and reporting modules.
- **Driver:** separate Next.js PWA foundation.

The UI templates are not yet connected to real booking/payment/driver operations. The admin's demo login (`admin@suabhi.local` / `local-preview-only`) is bundled into the frontend and is for local loopback preview only; it is not a security boundary.

## Apply the supplied template package

Clone `feature/phase-1-foundation`, download `suabhi-cab-booking-template-docker-overlay.zip`, copy it into the repository root and run `Expand-Archive -Path .\suabhi-cab-booking-template-docker-overlay.zip -DestinationPath . -Force` in PowerShell. The ZIP contains all provided template assets and Docker configuration. After extracting it, run `git add -A`, commit the template integration, and push `feature/phase-1-foundation` to store the assets in GitHub.

## Run full stack using Docker Desktop (Windows)

See [`docs/deployment/local-docker-windows.md`](docs/deployment/local-docker-windows.md). Quick start:

```powershell
Copy-Item .env.example .env
notepad .env
docker compose --env-file .env config
docker compose --env-file .env up -d --build
docker compose ps
```

### Isolated local ports

| Service | Host port |
|---|---:|
| PostgreSQL/PostGIS | 55432 |
| Redis | 56379 |
| Spring Boot API | 58080 |
| Customer website | 53000 |
| Admin console | 53001 |
| Driver PWA | 53002 |

## Stack

- Java 21, Spring Boot 4.x, Spring Modulith modular monolith
- PostgreSQL 18 + PostGIS, Redis
- Customer Next.js, admin React 19 + Vite + Material UI, driver Next.js
- Three.js / React Three Fiber in the upcoming interactive fleet preview milestone
- Docker Compose and Jenkins; no GitHub Actions pipeline

## License notices

The React Material Admin source is distributed with its MIT license notice; that notice is retained in `frontend/apps/admin-web/LICENSE.txt`. The supplied Rentaly customer template did not include a clear license file in the upload. Verify your commercial/production-use rights with the template provider before publishing or redistributing those customer-theme assets.
