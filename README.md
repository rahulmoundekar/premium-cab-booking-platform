# Suabhi Cab Booking

Standalone premium cab-booking platform for Pune and Mumbai. This repository has its own application, database, identity store, fleet catalogue and deployment boundary.

## Current implementation

- **Backend:** Java 21, Spring Boot 4.x, Spring Modulith, Spring Security, PostgreSQL 18/PostGIS, Redis and Flyway.
- **Customer website:** original Suabhi Next.js interface with live public fleet catalogue, registration, login, profile management and logout. The copied Rentaly / Designesia demo pages, assets, fonts and vendor scripts are not included in the current customer app.
- **Admin console:** React 19, Vite and Material UI, based on the supplied React Material Admin template. The template's MIT license notice is retained at `frontend/apps/admin-web/LICENSE.txt`.
- **Driver app:** separate Next.js PWA starter. Driver workflows are not implemented yet.
- **Infrastructure:** Docker Compose for local development; Jenkinsfile for CI/CD. GitHub Actions are intentionally not used.

## Current capabilities

### Backend APIs

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/public/fleet/categories` | List active vehicle categories |
| GET | `/api/v1/public/fleet/categories/{categoryCode}/models` | List models for a category |
| GET | `/api/v1/auth/csrf` | Obtain CSRF token |
| POST | `/api/v1/auth/register` | Register a customer |
| POST | `/api/v1/auth/login` | Sign in using a server-side session |
| GET | `/api/v1/auth/me` | Read signed-in identity |
| POST | `/api/v1/auth/logout` | End the session |
| GET/PATCH | `/api/v1/customer/profile` | Read/update customer profile |

Booking, fare calculation, payments, driver assignment, live tracking, account recovery and email verification are not implemented yet.

### Authentication and security baseline

- BCrypt password hashing and server-side HTTP sessions.
- Session ID rotation after successful login.
- CSRF protection for unsafe HTTP methods.
- Customer routes require the CUSTOMER role.
- Local cookie flags are configured for HTTP development. Production deployment must use HTTPS, Secure cookies, HSTS, exact CORS origins and managed secrets.
- The admin preview login is demo-only, bundled into the frontend, and is not an authentication boundary. Keep the admin service bound to loopback until real backend authentication and authorization are implemented.

## Local Docker setup (Windows)

See [Local Docker on Windows](docs/deployment/local-docker-windows.md).

```powershell
Copy-Item .env.example .env
notepad .env
docker compose --env-file .env config
docker compose --env-file .env up -d --build
docker compose ps
```

### Local service URLs and ports

| Service | URL / host port |
|---|---:|
| Customer website | http://localhost:53000 |
| Admin console | http://localhost:53001 |
| Driver PWA | http://localhost:53002 |
| Spring Boot API | http://localhost:58080 |
| PostgreSQL/PostGIS | 55432 |
| Redis | 56379 |

All host ports are bound to loopback by Docker Compose. Do not expose the local demo configuration to a network.

## Architecture direction

Start as a modular monolith. PostgreSQL is the system of record; PostGIS stores location and service-boundary data; Redis is for caching/coordination, not the authoritative booking ledger. Booking confirmation will need transactional, database-enforced schedule-conflict prevention. See [Phase 1 architecture](docs/architecture/phase-1-foundation.md) and [Phase 2 identity](docs/phase-2-identity.md).

## Licensing

The React Material Admin source is accompanied by its MIT license notice, retained in the admin application. The customer-facing app currently contains original Suabhi interface code rather than copied Rentaly / Designesia assets. Verify licensing for any third-party assets added in future changes before redistribution or commercial deployment.
