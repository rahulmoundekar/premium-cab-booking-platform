# Phase 1 — Architecture and foundation

Suabhi Cab Booking is a standalone product with its own source, database, identity store, fleet catalogue, deployment and operations boundary.

Start as a modular monolith. Initial Spring Modulith modules are common, identity and fleet. Planned modules include geography, pricing, availability, booking, payment, dispatch, trips, tracking, notifications, corporate accounts, invoices and reporting.

PostgreSQL is the system of record. PostGIS stores location points and service boundaries. Redis is a cache/coordination layer, never the authoritative booking ledger. Flyway owns schema changes. Confirmed bookings will retain quote and route snapshots.

Availability search is advisory. Booking confirmation must use a database transaction and database-enforced schedule-conflict prevention. A Redis lock alone must never be the only double-booking control.

Customer, admin and driver experiences are separate Next.js workspaces. Three.js is added to the customer fleet-preview milestone, not loaded into the Phase 1 shell.

Deployment target is private-cloud/customer-managed infrastructure. Jenkins is the CI/CD orchestrator; GitHub Actions are intentionally not used.
