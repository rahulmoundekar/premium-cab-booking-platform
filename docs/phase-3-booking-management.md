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


## Implemented first vertical slice — provider-neutral journey preview

The first Phase 3 increment is implemented on `feature/phase-3-booking-management`.

### Provider ports

- `GeocodingProvider`: place search by query/city and resolution of a customer-supplied address/reference/optional coordinate pair into a normalized place.
- `RouteProvider`: server-side route calculation from an ordered list of normalized places. The returned distance and duration are required to be positive.
- `City` and `ServiceType` are application-owned enums. No Google, Mapbox, HERE or other vendor SDK types are exposed in the application contract.
- Spring provides fallback implementations only when no real adapter bean exists. Those fallback implementations return HTTP 503 and never fabricate a location, distance or duration. A real adapter can be added as a Spring bean implementing the relevant interface.

### Customer API

Both endpoints require an authenticated customer session. Mutating requests also require the existing CSRF header.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/customer/journeys/places?query={text}&city={PUNE%7CMUMBAI}` | Search place suggestions through the configured geocoding adapter |
| POST | `/api/v1/customer/journeys/preview` | Resolve pickup/stops/drop-off and calculate a server-side route preview |

Example preview request:

```json
{
  "serviceType": "OUTSTATION",
  "originCity": "PUNE",
  "departureAt": "2027-01-15T08:30:00+05:30",
  "pickup": {
    "formattedAddress": "Shivajinagar, Pune, Maharashtra",
    "providerReference": null,
    "latitude": null,
    "longitude": null
  },
  "stops": [],
  "dropOff": {
    "formattedAddress": "Mumbai Airport, Mumbai, Maharashtra",
    "providerReference": null,
    "latitude": null,
    "longitude": null
  }
}
```

The departure time must be in the future. Pickup, drop-off and up to eight stops are accepted. Optional coordinates must be supplied as a valid latitude/longitude pair; geographic bounds are validated. The application calls the geocoder to normalize every place and then sends the ordered locations to the route adapter.

### Current runtime behavior and boundaries

- No provider API key or vendor has been selected. Until one or both adapter beans are configured, place search or preview returns HTTP 503 with a clear provider-not-configured error.
- A journey preview is transient. It is not yet a persisted fare quote or booking.
- There is no route distance, fare, service-zone eligibility or availability fabricated by this slice.
- The implementation adds no database migration and does not change the Phase 1 schema.

### Tests added

- Journey preview resolves pickup, intermediate stops and drop-off in order before routing.
- Route request preserves service type and origin city.
- Place search trims surrounding whitespace.
- Route results reject non-positive distance or duration.
- Provider-adapter fallback behavior is tested to return HTTP 503.

Run the full Maven verification suite before relying on the new API:

```powershell
docker run --rm -v "${PWD.Path}:/workspace" -w /workspace/backend/cab-booking-api maven:3.9.9-eclipse-temurin-21 mvn -B -ntp clean verify
```


## Google Maps provider adapter — implementation

The first concrete adapter implements the existing provider-neutral `GeocodingProvider` and `RouteProvider` ports. It is enabled only when `GOOGLE_MAPS_ENABLED=true`; otherwise the existing explicit HTTP 503 fallback remains active.

### Google services used

- Places API (New) Autocomplete returns place ID and display text, biased around the selected operating city (Pune or Mumbai) and restricted to India.
- Places API (New) Place Details resolves a selected place ID to a formatted address and coordinates.
- Geocoding API resolves manually entered addresses or reverse-geocodes a supplied coordinate pair when no place ID is present.
- Routes API Compute Routes calculates driving distance, duration and encoded polyline on the server. Pickup, intermediate stops and drop-off are sent in their original order.

Autocomplete suggestions intentionally return null coordinates. The browser should retain the selected `providerReference` (Google place ID), then submit that reference in the journey preview request. The server resolves it through Place Details before routing. This avoids making a separate Details call for every suggestion.

The current provider-neutral search contract does not carry a Places session token. Autocomplete calls are therefore independent requests for billing purposes. Before high-volume public launch, add a provider-neutral autocomplete session context and pass one session token through the suggestion and selection-resolution flow. Debounce customer keystrokes and enforce Google Cloud quotas/budget alerts in the meantime.

### Local configuration

1. In Google Cloud, create/select a project and enable **Places API (New)**, **Geocoding API**, and **Routes API**.
2. Enable billing and create a server-side API key.
3. Restrict the key to the three required APIs and, outside local development, to the API server's egress IP addresses. Do not use this key in browser JavaScript.
4. Put the key only in the local, untracked `.env` file:

```dotenv
GOOGLE_MAPS_ENABLED=true
GOOGLE_MAPS_API_KEY=replace_with_your_server_key
GOOGLE_MAPS_CONNECT_TIMEOUT=3s
GOOGLE_MAPS_READ_TIMEOUT=8s
GOOGLE_MAPS_REQUESTS_PER_SECOND=8
GOOGLE_MAPS_MAX_SUGGESTIONS=5
```

5. Rebuild and recreate only the API service:

```powershell
docker compose --env-file .env build cab-booking-api
docker compose --env-file .env up -d --force-recreate cab-booking-api
docker compose ps cab-booking-api
curl.exe http://localhost:58080/actuator/health
```

When `GOOGLE_MAPS_ENABLED=true` but the key is missing, the API fails fast at startup rather than silently using the fallback. When the flag is false or omitted, no Google network calls are made and the 503 fallback remains active.

### Resilience and security

- API key is sent in the `X-Goog-Api-Key` header for Places and Routes; the Geocoding API key is passed as a query parameter as required by that endpoint. Provider errors are translated to sanitized application errors so responses do not expose the key.
- HTTP connect/read timeouts are configurable.
- Requests are rate-limited per API process (default 8 request starts per second, configurable up to 100).
- One bounded retry is made for network failures and transient 5xx responses. Client errors and quota/rate-limit responses are not retried.
- Google response field masks request only the fields required by this journey flow.
- API key material must never be committed, returned in API responses, or written to application logs.

The in-process rate limiter is per API instance; configure Google Cloud project quotas as the authoritative limit across multiple replicas.

### Adapter tests

Mock HTTP tests cover Autocomplete mapping, Place Details resolution, Geocoding fallback, ordered route intermediates and server-calculated route metrics, and sanitized handling of provider authorization errors. These tests do not require a real API key or incur Google Maps usage charges.

Live Google API verification remains a separate manual step after the operator supplies a valid restricted key.
