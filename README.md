# Suabhi Cab Booking

Independent premium cab-booking platform for airport transfers, local rentals, outstation journeys, corporate travel, weddings and group transportation.

This is a greenfield application. It has no runtime, database, source-code or deployment dependency on any other platform.

## Phase 1 technology baseline

| Area | Selection |
|---|---|
| Backend | Java 21, Spring Boot 4.1.2, Spring Modulith 2.1.1 |
| Frontend | Next.js 16.4.5, React 19.3, TypeScript |
| Database | PostgreSQL 18 + PostGIS |
| Cache | Redis 8 |
| Migrations | Flyway |
| Security | Spring Security, server-side sessions, CSRF, CORS and secure headers |
| Testing | JUnit 5, Mockito, Spring Modulith verification |
| CI/CD | Jenkins; GitHub Actions are not used |
| Deployment | Private cloud / customer-managed infrastructure |

## Repository layout

- backend/cab-booking-api — Spring Boot modular monolith
- frontend/apps/customer-web — customer booking experience
- frontend/apps/admin-web — operations and fleet administration
- frontend/apps/driver-web — driver PWA workspace
- infrastructure — Docker and reverse-proxy configuration
- docs — architecture and security decisions

## Prerequisites

- Java 21
- Maven 3.9.9+
- Node.js 22 LTS and Corepack-enabled pnpm 10
- Docker Engine / Docker Desktop with Compose

## Local setup

1. Copy .env.example to .env and set strong local passwords. Use the same value for POSTGRES_PASSWORD and DATABASE_PASSWORD.
2. Start infrastructure: docker compose up -d postgres redis
3. Start the API: cd backend/cab-booking-api && mvn spring-boot:run
4. Start the frontend workspace: cd frontend && corepack enable && pnpm install
5. Run customer UI: pnpm --filter @suabhi/customer-web dev
6. Run admin UI: pnpm --filter @suabhi/admin-web dev
7. Run driver UI: pnpm --filter @suabhi/driver-web dev

API health: http://localhost:8080/actuator/health
Fleet categories: http://localhost:8080/api/v1/public/fleet/categories
Frontend ports: customer 3000, admin 3001, driver 3002.

The first frontend install generates frontend/pnpm-lock.yaml. Commit that lockfile before enabling frozen-lockfile installs in Jenkins.

## Verification

Backend: cd backend/cab-booking-api && mvn clean verify

Frontend: cd frontend && pnpm install, then run lint/build scripts for each workspace.

The backend unit suite does not require a running database. Running the application requires PostgreSQL with PostGIS because Flyway creates the schema and Hibernate runs in validate mode.

## Phase 1 deliverables

- Independent project and build conventions.
- Spring Boot application and modular boundaries.
- Session-security baseline, CSRF, CORS, secure headers and health checks.
- PostgreSQL/PostGIS schema baseline and Flyway migration.
- Identity/role schema and data-driven fleet category/model catalogue.
- Customer, admin and driver frontend workspaces.
- Docker Compose for local PostgreSQL/PostGIS and Redis.
- Jenkins pipeline and private-cloud deployment notes.

Booking, live availability, fare calculation, payments, dispatch and GPS tracking are subsequent implementation milestones, not completed features in Phase 1.

## Security

- Never commit production secrets, credentials, API keys, certificates or payment keys.
- No default administrator is seeded. First-admin bootstrap must be an audited operation in the identity milestone.
- Production must set CSRF_COOKIE_SECURE=true and HSTS_ENABLED=true behind trusted HTTPS ingress.
- APP_CORS_ALLOWED_ORIGINS must contain exact first-party origins; wildcard origins are not allowed with credentials.
- Flyway is the only schema migration owner. Hibernate ddl-auto is validate.
