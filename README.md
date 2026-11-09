# Suabhi Cab Booking

Independent premium cab-booking platform for Pune and Mumbai. This is a standalone product with separate source, database, infrastructure and deployment.

## Primary frontend templates

- **Customer website:** supplied Rentaly dark vehicle-rental template; original static pages, styles, scripts, fonts and images are retained under `frontend/apps/customer-web/public/rentaly` and served through the Next.js customer app.
- **Admin console:** supplied React Material Admin template; retained as React 19 + Vite + Material UI + React Router to preserve its components and design system. Its navigation is adapted for cab operations.
- **Driver app:** separate Next.js PWA foundation.

The customer theme upload did not include a clear license notice. Confirm commercial/production-use rights with the template provider before publishing or redistributing those assets. The admin template's MIT license notice is retained with its source.

## Apply the supplied frontend template overlay

The backend foundation and Docker files are committed on `feature/phase-1-foundation`. The full UI template overlay, including bundled image/font assets, is delivered separately as `suabhi-cab-booking-template-docker-overlay.zip`.

After cloning this branch, copy that ZIP to the repository root and extract it over the clone:

```powershell
Expand-Archive -Path .\suabhi-cab-booking-template-docker-overlay.zip -DestinationPath . -Force
```

Then follow [the Windows Docker Desktop guide](docs/deployment/local-docker-windows.md).

## Local Docker ports

| Service | Host port |
|---|---:|
| PostgreSQL + PostGIS | 55432 |
| Redis | 56379 |
| Spring Boot API | 58080 |
| Customer website | 53000 |
| Admin console | 53001 |
| Driver PWA | 53002 |

All ports bind to loopback (127.0.0.1). Service containers, volumes, project and network use Suabhi-specific names to avoid interfering with other local applications.

## Stack

- Backend: Java 21, Spring Boot 4.1.2, Spring Modulith modular monolith
- Customer and driver: Next.js App Router, React and TypeScript
- Admin: React 19, Vite and Material UI (based on the supplied admin template)
- Data: PostgreSQL 18 + PostGIS, Redis and Flyway
- Deployment: Docker Compose for local/private-cloud environments; Jenkins for CI/CD. No GitHub Actions pipeline.

## Phase 1 scope

Phase 1 establishes the backend/security foundation, PostGIS schema, roles, audit/outbox/idempotency tables, fleet categories/models, a public fleet catalogue API, the customer/admin/driver UI foundations, and Docker deployment configuration.

The static customer forms and admin/driver workspaces are not yet real booking operations. Authentication, inventory administration, availability, fare calculation, booking, payment/refunds, dispatch and live trip tracking remain subsequent milestones. Do not expose the local admin demo login beyond loopback or use it in production.
