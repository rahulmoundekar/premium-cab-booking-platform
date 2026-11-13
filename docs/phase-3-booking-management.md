# Phase 3 — Booking Management

## Goal

Add a customer booking journey for Suabhi Cab Booking across Pune and Mumbai, using the existing fleet catalogue, customer identity, saved locations, service zones, PostGIS, idempotency keys, audit log and outbox foundations.

Phase 3 is delivered as a sequence of small, verifiable vertical slices. It does not introduce payments, driver dispatch or live tracking in the first booking increment.

## Existing foundations to reuse

- `app_users` and `user_roles` for customer identity and ownership.
- `vehicle_categories`, `vehicle_models` and `vehicles` for fleet capability.
- `saved_locations` and `service_zones` for customer addresses and operating coverage.
- PostGIS geography columns and GIST indexes for coordinates and spatial lookups.
- `idempotency_keys`, `audit_logs` and `outbox_events` for safe writes and reliable downstream events.
- Spring Security `ROLE_CUSTOMER` authorization and the current CSRF/session pattern.

Do not duplicate these structures in later migrations.

## Service types in scope

| Code | Customer use | Important inputs |
|---|---|---|
| `AIRPORT_TRANSFER` | Pune or Mumbai airport pickup/drop | Airport direction, terminal/flight details where relevant, pickup date/time, pickup/drop |
| `LOCAL_RENTAL` | Local use in Pune or Mumbai | Start time, package duration, pickup area, estimated/allowed usage |
| `OUTSTATION` | Intercity and outstation journey | Pickup/drop, one-way or round-trip, start time, return time where applicable |

Fleet category codes come from the existing catalogue. Passenger and luggage counts must be validated against the selected category/model and confirmed vehicle configuration.

## Proposed delivery sequence

### 3.1 Journey and route quote

- Capture pickup, drop and ordered intermediate stops with formatted address, provider place reference and validated latitude/longitude.
- Validate pickup and drop against active service zones.
- Add a server-side route-provider interface. The server, not the browser, obtains road distance and travel duration.
- Keep provider-specific place/route APIs behind adapters. Do not commit to a vendor or expose an API key until the selected provider and credentials are configured.
- Do not accept a client-submitted distance or duration as authoritative pricing input.

### 3.2 Configurable pricing and fare quote

- Store effective-dated rate cards by service type, operating city and vehicle category.
- Support base/minimum fare, per-kilometre, per-hour or per-day rates, included usage, driver allowance and explicit surcharge/tax components as applicable to the service.
- Do not hard-code business prices in JavaScript, controller code or SQL seed data. No commercial tariff amounts were supplied at Phase 3 start.
- Calculate on the server using trusted route metrics and a selected active rate card.
- Persist a complete immutable quote snapshot: input, route-provider reference, rate-card version, component amounts, currency and expiry.
- Use INR as the initial currency, with an explicit ISO currency code stored on every quote and booking.

### 3.3 Booking creation and lifecycle

- Create a booking only from an unexpired quote owned by the authenticated customer.
- Enforce one booking per quote and support an `Idempotency-Key` for retries.
- Snapshot addresses, stops, route metrics, service type, vehicle category, pickup/return schedule and fare breakdown so later catalogue/rate changes do not rewrite a confirmed booking.
- Use a database transaction for quote consumption, booking creation, booking event and outbox event.
- Proposed initial states: `PENDING_CONFIRMATION`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`. Transitions must be explicit and audited.
- Do not mark a booking `CONFIRMED` until the availability/confirmation rule for that service is satisfied.

### 3.4 Customer booking APIs and UI

Initial API shape (final DTOs are defined with implementation):

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/customer/quotes` | Calculate and save a fare quote |
| GET | `/api/v1/customer/quotes/{quoteId}` | Read an owned quote |
| POST | `/api/v1/customer/bookings` | Create a booking from an owned quote |
| GET | `/api/v1/customer/bookings` | List the signed-in customer's bookings |
| GET | `/api/v1/customer/bookings/{bookingId}` | Read one owned booking |
| POST | `/api/v1/customer/bookings/{bookingId}/cancel` | Cancel when policy permits |

All customer reads/writes must enforce ownership server-side. Do not rely on hidden UI controls or customer IDs supplied by the browser.

### 3.5 Availability and dispatch boundary

- Availability search is advisory until the database reservation/confirmation transaction succeeds.
- Vehicle/driver assignment belongs to a later dispatch slice.
- Before assigning a physical vehicle, add database-enforced schedule-overlap protection; a Redis lock alone is not sufficient.
- Cancellation, rejection and rescheduling must emit audited state changes and outbox events.

## Proposed persistence objects

Create a new Flyway migration (next version after V1) for the Phase 3 objects, subject to implementation review:

- `pricing_rate_cards`: effective-dated pricing configuration by service type, city and vehicle category.
- `fare_quotes`: expiring quote with route metrics and immutable price snapshot.
- `bookings`: customer-owned booking aggregate and immutable quote snapshot.
- `booking_stops`: ordered pickup/drop/intermediate location snapshot.
- `booking_events`: append-only booking lifecycle history.

Use foreign keys, status/service-type check constraints, non-negative monetary and distance checks, indexes for customer/date/status lookups, and uniqueness constraints for booking references and quote consumption. Do not alter V1 or rewrite existing customer/fleet data.

## API and domain rules

1. The authenticated principal supplies the customer identity; never accept a customer ID as proof of ownership.
2. A quote expires and cannot be consumed twice.
3. The backend calculates every amount from a versioned rate card and trusted route result.
4. Monetary values use decimal/numeric types, never floating point.
5. Quote and booking snapshots preserve the price and journey accepted at that time.
6. All state transitions are validated by a domain service and written to `booking_events`, `audit_logs` and `outbox_events` in one transaction.
7. Retried create/cancel requests must be idempotent.
8. PII, authentication tokens and full payment details must not be written to ordinary application logs.

## Required tests

- Pricing unit tests for each service type, boundaries, minimums, included usage, surcharges and rounding.
- Route-provider adapter contract tests and rejection of missing/stale route metrics.
- Service-zone and coordinate validation.
- Quote expiry, quote/customer ownership and one-booking-per-quote constraints.
- Concurrent/idempotent booking creation.
- Every allowed and forbidden booking state transition.
- Customer A cannot read, cancel or consume Customer B's quote/booking.
- Migration validation against PostgreSQL 18 + PostGIS.
- Docker Compose API health and customer UI smoke tests.

## Configuration still needed before live price quotes

- Selected route/geocoding provider and environment-specific API credentials.
- Approved Pune and Mumbai tariff tables for each service type and vehicle category.
- Local rental package durations and included kilometres/hours.
- Outstation one-way/round-trip, driver allowance, overnight and return-empty rules.
- Airport pickup/parking/terminal surcharges and toll treatment.
- Taxes, cancellation windows/fees, quote validity and confirmation policy.

Until these are supplied, implement the pricing engine as configurable rules with test fixtures only; do not publish invented fares or promise a live quote.
