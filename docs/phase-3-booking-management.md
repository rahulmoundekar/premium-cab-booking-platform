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

Autocomplete suggestions intentionally return null coordinates. Google requires its attribution/logo to be displayed when these predictions are shown without a Google map; include the prescribed Google attribution in the customer UI before exposing the suggestion list. The city circle is a ranking bias, not a hard city boundary, so service-zone validation must remain a separate server-side eligibility check.  The browser should retain the selected `providerReference` (Google place ID), then submit that reference in the journey preview request. The server resolves it through Place Details before routing. This avoids making a separate Details call for every suggestion.

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


## Phase 3.2 — Configurable rate cards and pricing engine

The pricing foundation is now implemented as a separate Spring Modulith module. It deliberately contains no sample commercial tariffs; the business must approve and configure the actual Pune and Mumbai prices.

### Rate-card persistence

Flyway migration `V2__pricing_rate_cards.sql` adds:

- `pricing_rate_cards`: versioned rates scoped by service type, operating city and active vehicle category, with currency, base/minimum fare, kilometre/hour/day rates, included usage, driver allowance and effective dates.
- `pricing_rate_card_components`: ordered fixed or percentage surcharges and tax components.
- No rate-card rows are seeded.

A rate card is created inactive. Administrators activate it explicitly. The service checks for overlapping active effective periods, and a PostgreSQL GiST exclusion constraint protects against concurrent activation races for the same service/city/category. Rate cards are versioned rather than edited in place; deactivate an existing version and create the next version for a pricing change.

### Administrator API

Only users with `ROLE_ADMIN` or `ROLE_SUPER_ADMIN` may use:

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/admin/pricing/rate-cards` | List configured rate cards and components |
| POST | `/api/v1/admin/pricing/rate-cards` | Create an inactive version |
| POST | `/api/v1/admin/pricing/rate-cards/{id}/activate` | Activate after effective-period overlap validation |
| POST | `/api/v1/admin/pricing/rate-cards/{id}/deactivate` | Deactivate a version |

The creation request requires the service, city, active vehicle category, ISO currency code, all rate values (use explicit zero for unused rates), effective dates and optional component list. New rate cards are inactive by default.

### Deterministic pricing rules

The pure `PricingEngine` accepts trusted server-side route distance/duration, plus a rental package duration for local rental. It rejects inactive cards and service/city mismatches.

- Airport transfer: base fare plus billable kilometres beyond included kilometres.
- Local rental: base fare plus billable kilometres and billable package hours beyond the included allowances. The selected package duration must be supplied; route duration is not treated as the rental package duration.
- Outstation: base fare plus billable kilometres, ceiling-based billable days and driver allowance per day.
- The minimum fare is applied to the core subtotal before surcharges and taxes.
- Fixed and percentage surcharges are supported. Percentage surcharges apply to the core subtotal.
- Taxes can be fixed or percentage-based. Percentage taxes apply to the core subtotal plus taxable surcharges; tax-on-tax is not applied.
- All monetary arithmetic uses `BigDecimal` and rounds monetary line items to two decimal places using HALF_UP.

### Important boundary

This increment delivers rate-card administration and tested fare calculation rules. It does **not** yet expose a customer fare-quote endpoint or persist quote snapshots. That next quote slice must combine a trusted server-calculated route, a currently effective rate card, category capacity validation, authenticated customer ownership, quote expiry and an immutable price snapshot. Do not accept client-submitted distance, duration or fare values as authoritative inputs.

### Verification

The pricing unit tests cover airport minimum fare and surcharge/tax calculations, outstation ceiling-day and driver-allowance calculations, local-rental included-hour and kilometre handling, service/city mismatch rejection, inactive cards, and rate-card activation overlap/expiry rules. Run the complete API test suite and validate Flyway V2 against the local PostgreSQL 18 + PostGIS database before proceeding to quote persistence.


### Modulith boundaries

The Pricing module depends only on the named `journey::types` interface for the shared `City` and `ServiceType` enums. It does not depend on Journey's provider implementations.

Vehicle-category validation uses the public `fleet.FleetCategoryCatalog` contract. Fleet keeps its JPA entity and repository internal; Pricing receives only a category UUID and code. The module-structure verification test remains enabled and should be run as part of `mvn clean verify`.


## Phase 3.3 — Customer fare quote snapshots

The customer quote slice is implemented on `feature/phase-3-booking-management`.

### Customer API

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/customer/quotes` | Resolve the journey, select the rate card effective at departure, calculate and persist a quote |
| GET | `/api/v1/customer/quotes/{quoteId}` | Retrieve a quote only for its owning authenticated customer |

The create request contains a nested `journey` object using the journey-preview request contract, a `vehicleCategoryCode`, and `rentalHours` only for `LOCAL_RENTAL`. It deliberately accepts no distance, duration, coordinates as authoritative metrics, or fare amount. Place references/addresses are resolved by the server and the route adapter calculates distance and duration.

### Calculation and tariff selection

- The server selects an active rate card matching service type, operating city and active vehicle category, with an effective period covering the scheduled departure.
- The pricing engine receives only route metrics returned by the server-side journey preview service.
- Local rental requires an explicit positive package duration. Other service types reject a rental-hours value.
- A quote is valid for 15 minutes from creation.
- The quote response snapshots the resolved journey and route reference, the complete rate-card version and components, and the calculated fare breakdown.
- The database stores indexed ownership/tariff/route/amount/expiry fields plus the complete immutable JSONB response snapshot.
- Quote retrieval filters by both quote ID and authenticated customer ID. A quote belonging to another customer is returned as not found.
- No update endpoint is provided for quote snapshots. Later rate-card changes do not rewrite an existing quote.

### Provider and operational boundary

Google Maps remains disabled by default. Until a real geocoding and route provider is enabled, the journey preview adapter returns HTTP 503; quote creation consequently does not persist a quote or invent route metrics. Unit tests use mocked provider results and do not call Google APIs.

No commercial tariff values are seeded. The test rate cards used for local verification are synthetic and must be removed before operational pricing configuration.

### Migration and verification

Flyway migration `V3__fare_quotes.sql` adds `fare_quotes`, with customer/rate-card/category foreign keys, route metric and amount checks, an expiry check and indexes for customer history, expiry cleanup and rate-card tracing.

The quote service tests verify the immutable journey/rate-card/fare snapshot, the 15-minute expiry, the fare breakdown, persistence invocation and ownership filtering. Run `mvn clean verify` and validate Flyway V3 against the local PostgreSQL 18 + PostGIS database before runtime testing.


## Phase 3.4 — Customer booking lifecycle

The initial booking lifecycle is implemented in the independent `booking` Spring Modulith module.

### Customer API

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/customer/bookings` | Create a booking from an owned, unexpired quote; requires the `Idempotency-Key` header |
| GET | `/api/v1/customer/bookings` | List the authenticated customer's bookings |
| GET | `/api/v1/customer/bookings/{bookingId}` | Read one booking owned by the authenticated customer |
| POST | `/api/v1/customer/bookings/{bookingId}/cancel` | Cancel a booking while it is pending confirmation |

Create request:

```json
{
  "quoteId": "UUID-from-an-unexpired-customer-quote"
}
```

The customer identity always comes from the authenticated session. A quote ID is not accepted as proof of ownership. Both the service and a composite database foreign key require the quote to belong to the same customer who creates the booking.

### Transactional guarantees

- A quote must exist for the authenticated customer, its 15-minute validity window must not have expired, and its scheduled departure must still be in the future.
- A quote can be converted into at most one booking; the database enforces uniqueness on `quote_id`.
- A customer's `Idempotency-Key` is unique. A retry with the same key and same quote returns the original booking. Reusing the key with a different quote returns HTTP 409.
- Concurrent create attempts are arbitrated by the unique customer/idempotency and quote constraints. A losing request returns the committed booking for an identical replay or HTTP 409 for a conflicting use.
- Booking creation, the first `booking_events` row, the audit-log entry and the outbox event are written in the same database transaction.
- The booking stores the complete immutable quote snapshot. Later changes to rate cards do not change a booking's accepted fare or journey.
- Audit and outbox metadata intentionally contain booking/tariff summary fields rather than pickup/drop-off addresses or other location PII.

### Initial lifecycle policy

- A newly created booking is `PENDING_CONFIRMATION`. It is not represented as confirmed because fleet availability and dispatch confirmation are not yet implemented.
- A customer may cancel only a `PENDING_CONFIRMATION` booking.
- Repeating cancellation on an already cancelled booking is idempotent.
- Cancellation of any other state returns HTTP 409.
- Payment, cancellation fees/windows and driver dispatch are not implemented in this slice. Authorized operations confirmation/rejection and vehicle schedule allocation are added in Phase 3.5. No cancellation fee is invented.

### Persistence

Flyway migration `V4__booking_lifecycle.sql` adds:

- `bookings`: customer ownership, unique quote consumption, unique customer idempotency key, booking reference, status, summary columns and immutable JSONB snapshot.
- `booking_events`: append-only state transition history with actor and safe metadata.
- A composite foreign key from `(quote_id, customer_id)` to `fare_quotes(id, customer_id)` enforces quote ownership at the database layer.

Every create/cancel write also adds an `audit_logs` record and a pending `outbox_events` record in the same transaction. Outbox delivery/worker processing is a later infrastructure increment.

### Verification targets

- New booking from an owned and unexpired quote returns HTTP 201 and `PENDING_CONFIRMATION`.
- Same idempotency key and same quote returns the original booking; same key with a different quote returns HTTP 409.
- A quote already converted by a different key returns HTTP 409.
- Expired and foreign-owned quotes cannot be booked.
- Customer A cannot read/cancel Customer B's booking.
- Pending booking cancellation succeeds; repeat cancellation is harmless; other statuses are rejected.
- Concurrent insert constraints prevent duplicate booking per quote or customer idempotency key.
- Booking, event, audit and outbox rows commit or roll back together.

The quote provider remains configured as HTTP 503 while Google Maps is disabled. Runtime booking creation therefore needs a valid persisted quote; do not seed a fabricated route or fare merely to bypass provider configuration. A controlled test fixture can be inserted only in an isolated test database or using the test profile.


## Phase 3.5 — Operations decisions and vehicle availability

The operations decision slice is implemented on `feature/phase-3-booking-management`.

### Operations APIs

The following endpoints are restricted to authenticated users with one of these roles: `ADMIN`, `SUPER_ADMIN`, `FLEET_MANAGER` or `DISPATCHER`. POST requests retain the existing CSRF requirement.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/admin/bookings?status=PENDING_CONFIRMATION` | List bookings, optionally filtered by lifecycle status |
| GET | `/api/v1/admin/bookings/{bookingId}` | Read one booking, active vehicle allocation and decision reason |
| GET | `/api/v1/admin/bookings/{bookingId}/available-vehicles?reservedUntilAt={ISO-8601}` | Find matching vehicles not reserved during the requested window |
| POST | `/api/v1/admin/bookings/{bookingId}/confirm` | Confirm a pending booking and reserve a vehicle |
| POST | `/api/v1/admin/bookings/{bookingId}/reject` | Reject a pending booking with a required reason |

Confirmation request:

```json
{
  "vehicleId": "UUID-from-the-fleet-catalog",
  "reservedUntilAt": "2027-01-15T12:00:00+05:30"
}
```

Rejection request:

```json
{
  "reason": "No suitable vehicle is available for this time window"
}
```

### Confirmation and schedule rules

- The booking must still be `PENDING_CONFIRMATION`, and its scheduled departure must be in the future.
- The selected vehicle must exist in the active fleet catalogue, have `AVAILABLE` operational status, match the quoted vehicle category and be registered in the booking's operating city.
- The reservation begins at the departure instant accepted in the fare quote.
- For `LOCAL_RENTAL`, the reservation must cover at least the full quoted rental package duration.
- For `AIRPORT_TRANSFER` and `OUTSTATION`, the operations user supplies the reservation end time. The API enforces that the window covers at least the server-calculated route duration. The system does not invent return-empty, overnight, layover or turnaround allowances; configure those policies before automating the end time.
- The available-vehicles endpoint is an advisory search. A concurrent operator can reserve a vehicle after the search, so the confirmation transaction relies on the database constraint as the final authority.
- Confirming an already confirmed booking with the same vehicle and identical window is idempotent. A different vehicle/window returns HTTP 409.
- Rejecting requires a 5–500 character reason. Repeating the same rejection request is idempotent; changing the reason after rejection returns HTTP 409.
- Only pending bookings can be confirmed or rejected. Customer cancellation remains limited to pending bookings in this slice.

### Database enforcement and audit

Flyway migration `V5__booking_confirmation_and_vehicle_availability.sql` adds:

- `booking_vehicle_allocations` with vehicle, booking, allocating operator and reservation interval.
- A partial unique index allowing only one active vehicle allocation per booking.
- A PostgreSQL GiST exclusion constraint over vehicle ID and the half-open reservation interval `[reserved_from_at, reserved_until_at)`. Two active allocations for the same vehicle cannot overlap, including when separate API requests race.
- `bookings.decision_reason` for the operations rejection reason, with a check constraint tying it to the `REJECTED` status.

The allocation insert, booking status/snapshot update, `booking_events`, `audit_logs` and `outbox_events` writes occur in one transaction. If the database detects an overlapping window, the confirmation is rolled back and returned as HTTP 409. Vehicle allocation does not yet assign a driver or change the vehicle's operational status; the active schedule allocation is the availability ledger for future reservations.

### Verification targets

- Pending booking list/detail is available to authorized operations roles and forbidden to customer-only sessions.
- A matching `AVAILABLE` vehicle can be reserved and the booking becomes `CONFIRMED`.
- Wrong-city, wrong-category or non-AVAILABLE vehicles are rejected.
- A second booking cannot reserve the same vehicle over an overlapping time window; adjacent half-open windows are permitted.
- A repeated confirmation with the same vehicle and exact interval returns the existing decision without another allocation or event.
- Reject requires a reason and records the transition atomically; repeating the same rejection does not duplicate events.
- PostgreSQL V5 applies cleanly against the existing V1–V4 database without dropping volumes or existing customer data.
- Unit tests cover schedule rules, decision transitions, idempotent operations and the database conflict translation.

A successful live confirmation requires an actual active fleet vehicle and a genuine persisted fare quote. Do not create invented operational vehicles, routes or fares to force a local success case. The API and database conflict behavior should also be exercised with isolated PostgreSQL integration fixtures before production use.

## Phase 3.6 — PostgreSQL-backed booking decision integration tests

Phase 3.6 adds an isolated Testcontainers-backed integration suite for the operations booking decision workflow.

### Test environment

- `BookingDecisionServicePostgresIntegrationTest` starts a disposable `postgis/postgis:18-3.6` container through Testcontainers.
- Spring Boot connects only to that container through `@DynamicPropertySource`; the suite does not read the local `.env` datasource settings.
- Flyway applies the same application migrations V1–V5 before the test context is used.
- The test profile keeps Google Maps disabled. No external geocoding or routing calls are made.
- Test fixtures use generated user, rate-card, quote, booking and vehicle identifiers in the disposable database. They do not seed the Compose development database or persistent volumes.
- Testcontainers JUnit Jupiter and PostgreSQL modules use the versions managed by the Spring Boot parent.

Docker must be available to the Maven/JUnit process. The Testcontainers test class is configured to be disabled when Docker is unavailable; a skipped Testcontainers suite is not evidence that the integration assertions passed.

### Integration coverage

The PostgreSQL-backed suite verifies:

- A pending booking is confirmed against a matching, active-category, `AVAILABLE` vehicle and the active allocation is persisted.
- Repeating confirmation with the same vehicle and exact reservation window returns the original decision without duplicate allocation, booking event, audit row or outbox event.
- Rejection persists the reason and state transition; repeating the same reason is idempotent, while changing the reason returns HTTP 409.
- A vehicle already allocated to an overlapping booking is omitted from the availability query. A conflicting confirmation returns HTTP 409 and leaves the second booking pending without partial side effects.
- An injected audit-write failure rolls back the allocation, booking status/snapshot update, booking event and outbox write in the same transaction.
- Invalid rejection reasons return HTTP 400 and write no decision records.

The existing `BookingDecisionServiceTest` remains the fast mocked unit-test layer. This Testcontainers suite adds real PostgreSQL, Flyway, JDBC repository and transaction coverage. The separate manual V5 exclusion-constraint check also verified overlapping, adjacent and released intervals.

### Run and verify

From the repository root in PowerShell, run the Maven container with access to Docker Desktop's daemon socket. The Docker socket mount and host override allow Testcontainers to create its isolated sibling PostGIS container:

```powershell
docker run --rm `
  -v "$($PWD.Path):/workspace" `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -w /workspace/backend/cab-booking-api `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  maven:3.9.9-eclipse-temurin-21 `
  mvn -B -ntp clean verify
```

To run only this integration suite, use the same command and replace the final Maven arguments with:

```powershell
mvn -B -ntp -Dtest=BookingDecisionServicePostgresIntegrationTest test
```

The same Maven goals can also be run directly from the host/IDE when Java 21, Maven and Docker connectivity are configured there.

Review the Maven Surefire report for `BookingDecisionServicePostgresIntegrationTest`. Confirm that the class ran and was not skipped before marking Phase 3.6 verified.

This suite does not enable Google Maps, configure commercial tariffs, or create operational records. It is a test-only database fixture and does not demonstrate a live customer journey or production fleet assignment.

## Phase 3.7 — Customer cancellation lifecycle hardening

Phase 3.7 extends the PostgreSQL-backed lifecycle suite without changing the customer cancellation policy or adding unsupported commercial rules.

### Lifecycle policy retained

- Customers can cancel only a booking in `PENDING_CONFIRMATION`.
- Repeating cancellation for a booking already in `CANCELLED` returns the existing cancellation result and does not create another event.
- Customers cannot cancel `CONFIRMED` or `REJECTED` bookings.
- Operations cannot confirm a booking after customer cancellation.
- No cancellation fee, cancellation window, refund, driver release or confirmed-ride cancellation behavior is introduced in this increment.

### PostgreSQL-backed verification

Three additional scenarios are included in `BookingDecisionServicePostgresIntegrationTest`:

- Cancelling a pending booking persists the booking table and JSON snapshot status, cancellation timestamp, customer actor in the event, audit record and outbox record. Repeating cancellation preserves the original timestamp and does not duplicate side effects.
- Customer cancellation cannot overwrite confirmed or rejected operations decisions, and an already-cancelled booking cannot be confirmed or allocated.
- An injected audit-write failure rolls back the cancellation timestamp, booking status/snapshot, event and outbox write in the same database transaction.

These scenarios use generated fixtures in the disposable Testcontainers PostgreSQL/PostGIS database. They do not create or modify records in the local Compose database.

### Verification

Run the integration suite with Docker Desktop access:

```powershell
docker run --rm `
  -v "$($PWD.Path):/workspace" `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -w /workspace/backend/cab-booking-api `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  maven:3.9.9-eclipse-temurin-21 `
  mvn -B -ntp -Dtest=BookingDecisionServicePostgresIntegrationTest test
```

The suite now contains nine PostgreSQL integration tests. Run `mvn -B -ntp clean verify` after the targeted suite passes. A Testcontainers class skipped because Docker is unavailable must be run separately with Docker connectivity before this lifecycle slice is marked verified.


## Phase 3.8 — Driver assignment and trip lifecycle

Phase 3.8 adds operations-controlled driver assignment and an authenticated driver workflow on top of the confirmed booking and active vehicle reservation created in Phase 3.5.

### Lifecycle

The supported booking progression is:

\`PENDING_CONFIRMATION -> CONFIRMED -> ASSIGNED -> IN_PROGRESS -> COMPLETED\`

Rejected and cancelled bookings remain terminal for this workflow. Driver reassignment and cancellation after confirmation are not introduced.

### Dispatch eligibility

A driver can be assigned only when all of the following are true:

- The booking is \`CONFIRMED\` and has an active vehicle reservation.
- The driver account is active, has the \`DRIVER\` role and has a \`driver_profiles\` row.
- The driver profile is \`VERIFIED\` and currently \`AVAILABLE\`.
- The driver's operating city matches the booking's operating city.
- The driver's licence remains valid through the end date of the reserved interval.
- The driver has no overlapping assignment in the reserved interval.

Migration \`V6__driver_assignment_and_trip_lifecycle.sql\` adds nullable \`driver_profiles.operating_city\` and the \`booking_driver_assignments\` ledger. Existing driver profiles are not assigned a fabricated city; set their actual operating city before dispatch eligibility is granted.

The assignment ledger copies the active vehicle reservation's half-open interval. A PostgreSQL GiST exclusion constraint prevents two bookings from reserving the same driver over overlapping intervals, including concurrent dispatch attempts. The booking ID is unique in the ledger: reassigning an already assigned booking is deliberately not supported.

A future reservation does not immediately change the driver's presence to \`ON_TRIP\`. Starting the trip changes availability to \`ON_TRIP\`; completing it returns the driver to \`AVAILABLE\`. The assignment's reserved interval remains in the ledger after completion, so an early completion does not permit another overlapping dispatch before the reserved end time.

### Operations API

The existing operations role policy applies (\`ADMIN\`, \`SUPER_ADMIN\`, \`FLEET_MANAGER\`, \`DISPATCHER\`). POST requests continue to require CSRF validation.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | \`/api/v1/admin/bookings/{bookingId}/available-drivers\` | List verified, available drivers matching city, licence and schedule |
| POST | \`/api/v1/admin/bookings/{bookingId}/assign-driver\` | Assign a driver profile to a confirmed booking |

Assignment request:

\`\`\`json
{
  "driverProfileId": "UUID-from-an-eligible-driver-profile"
}
\`\`\`

Repeating the same assignment for the same booking and driver returns the existing assignment without duplicate event, audit or outbox records. A different driver for an already assigned booking returns HTTP 409.

### Driver API

All \`/api/v1/driver/**\` routes require the \`DRIVER\` role. The service also resolves the driver's profile from the authenticated user ID and filters every trip read by that profile, so a driver cannot read or mutate another driver's assignment.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | \`/api/v1/driver/trips\` | List the authenticated driver's assigned, in-progress and completed trips |
| GET | \`/api/v1/driver/trips/{bookingId}\` | Read one trip belonging to the authenticated driver |
| POST | \`/api/v1/driver/trips/{bookingId}/start\` | Start an assigned trip within its reserved interval |
| POST | \`/api/v1/driver/trips/{bookingId}/complete\` | Complete an in-progress trip |

Start is idempotent after the booking reaches \`IN_PROGRESS\`; completion is idempotent after \`COMPLETED\`. Completion is not permitted before trip start. Both transitions update booking status/snapshot, assignment timestamps and driver availability in the same transaction as the booking event, audit log and outbox event.

### Deliberate boundaries

- This increment uses existing \`driver_profiles\` records; a driver onboarding/profile administration UI is not included. A profile must already be provisioned, have the \`DRIVER\` role, be verified, have a current licence and have its real \`operating_city\` populated.
- No driver reassignment, cancellation after confirmation, cancellation fees, refunds, payment settlement or driver payout rules are added.
- No GPS tracking, geofencing, trip route mutation, vehicle status mutation or automatic trip completion is included.
- The active vehicle reservation remains authoritative for the vehicle's scheduled window. Driver trip completion does not release that reservation early.
- Google Maps stays disabled unless separately configured and enabled; the trip lifecycle does not fabricate route or location data.

### PostgreSQL-backed verification

The Testcontainers suite now contains 16 integration tests (the previous nine plus seven Phase 3.8 scenarios). It verifies driver eligibility, operating-city matching, licence and role enforcement, idempotent assignment, overlapping driver schedule rejection across different vehicles, driver ownership, start-window rules, trip completion, availability transitions and transactional rollback on audit failure.

Run the targeted suite from the repository root in PowerShell:

\`\`\`powershell
docker run --rm \`
  -v "$($PWD.Path):/workspace" \`
  -v /var/run/docker.sock:/var/run/docker.sock \`
  -w /workspace/backend/cab-booking-api \`
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \`
  maven:3.9.9-eclipse-temurin-21 \`
  mvn -B -ntp -Dtest=BookingDecisionServicePostgresIntegrationTest test
\`\`\`

After the targeted suite passes, run the full backend verification with the same Docker socket and host override, replacing the final Maven arguments with \`mvn -B -ntp clean verify\`. Confirm that the Testcontainers integration class ran and was not skipped.

All driver and trip fixtures in the suite are generated in the disposable PostGIS container. The migration applies through Flyway to a fresh Testcontainers database; this documentation does not claim that the user's local Compose database has been migrated or that real driver profiles exist.
