package com.suabhi.cabbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.suabhi.cabbooking.booking.api.BookingResponse;
import com.suabhi.cabbooking.booking.domain.BookingStatus;
import com.suabhi.cabbooking.journey.api.JourneyPreviewResponse;
import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse.RateCardSnapshot;
import com.suabhi.cabbooking.pricing.domain.FareCalculationResult;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * PostgreSQL-backed integration tests for the Phase 3.6 booking decision workflow.
 *
 * <p>The container runs the same PostGIS major/image family as local development.
 * Flyway applies the production V1-V5 migrations to this disposable database.
 * No application Compose datasource or persistent volume is used by this suite.</p>
 */
@SpringBootTest(properties = "app.maps.google.enabled=false")
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD)
class BookingDecisionServicePostgresIntegrationTest {

    private static final UUID SEDAN_CATEGORY_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DZIRE_MODEL_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("suabhi_booking_it")
            .withUsername("suabhi_it")
            .withPassword("suabhi_it_test_password");

    @Autowired
    private BookingDecisionService bookingDecisionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.maps.google.enabled", () -> false);
    }

    @Test
    void confirmsBookingOnceAndPersistsAllocationEventAuditAndOutbox() throws Exception {
        Fixture fixture = createFixture();
        Instant reservedUntil = fixture.departureAt().plusSeconds(7_200);
        OffsetDateTime reservationEnd = OffsetDateTime.ofInstant(reservedUntil, ZoneOffset.UTC);

        var firstResult = bookingDecisionService.confirm(
                fixture.operationsUserId(), fixture.bookingId(), fixture.vehicleId(), reservationEnd);
        var replayResult = bookingDecisionService.confirm(
                fixture.operationsUserId(), fixture.bookingId(), fixture.vehicleId(), reservationEnd);

        assertThat(firstResult.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(firstResult.allocation()).isNotNull();
        assertThat(firstResult.allocation().vehicleId()).isEqualTo(fixture.vehicleId());
        assertThat(replayResult.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(replayResult.allocation().vehicleId()).isEqualTo(fixture.vehicleId());

        assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("CONFIRMED");
        assertThat(readBookingSnapshotStatus(fixture.bookingId())).isEqualTo("CONFIRMED");
        assertThat(count("SELECT count(*) FROM booking_vehicle_allocations 
                + "WHERE booking_id = ? AND allocation_status = 'ACTIVE'", fixture.bookingId())).isEqualTo(1);
        assertConfirmationSideEffects(fixture.bookingId(), 1);

        String eventVehicleId = jdbcTemplate.queryForObject("""
                SELECT metadata ->> 'vehicleId'
                FROM booking_events
                WHERE booking_id = ? AND event_type = 'BOOKING_CONFIRMED'
                """, String.class, fixture.bookingId());
        assertThat(eventVehicleId).isEqualTo(fixture.vehicleId().toString());
    }

    @Test
    void rejectsBookingWithReasonAndReplaysSameDecisionWithoutDuplicateSideEffects() throws Exception {
        Fixture fixture = createFixture();
        String reason = "Vehicle is unavailable for this service window.";

        var firstResult = bookingDecisionService.reject(
                fixture.operationsUserId(), fixture.bookingId(), reason);
        var replayResult = bookingDecisionService.reject(
                fixture.operationsUserId(), fixture.bookingId(), reason);

        assertThat(firstResult.booking().status()).isEqualTo(BookingStatus.REJECTED);
        assertThat(firstResult.decisionReason()).isEqualTo(reason);
        assertThat(replayResult.booking().status()).isEqualTo(BookingStatus.REJECTED);
        assertThat(replayResult.decisionReason()).isEqualTo(reason);
        assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("REJECTED");
        assertThat(readBookingSnapshotStatus(fixture.bookingId())).isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT decision_reason FROM bookings WHERE id = ?", String.class, fixture.bookingId()))
                .isEqualTo(reason);
        assertThat(count("SELECT count(*) FROM booking_vehicle_allocations WHERE booking_id = ?",
                fixture.bookingId())).isZero();
        assertThat(count("SELECT count(*) FROM booking_events "
                + "WHERE booking_id = ? AND event_type = 'BOOKING_REJECTED'", fixture.bookingId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs "
                + "WHERE aggregate_type = 'BOOKING' AND aggregate_id = ? AND action = 'BOOKING_REJECTED'",
                fixture.bookingId().toString())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_events "
                + "WHERE aggregate_type = 'BOOKING' AND aggregate_id = ? AND event_type = 'BOOKING_REJECTED'",
                fixture.bookingId().toString())).isEqualTo(1);

        assertThatThrownBy(() -> bookingDecisionService.reject(
                fixture.operationsUserId(), fixture.bookingId(), "A different rejection reason"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(count("SELECT count(*) FROM booking_events "
                + "WHERE booking_id = ? AND event_type = 'BOOKING_REJECTED'", fixture.bookingId())).isEqualTo(1);
    }

    @Test
    void databaseOverlapConstraintRejectsSecondConfirmationAndAvailabilityQueryHidesVehicle() throws Exception {
        Fixture first = createFixture();
        Fixture second = createAnotherBookingForSameVehicle(first);
        Instant reservedUntil = first.departureAt().plusSeconds(7_200);
        OffsetDateTime reservationEnd = OffsetDateTime.ofInstant(reservedUntil, ZoneOffset.UTC);

        bookingDecisionService.confirm(
                first.operationsUserId(), first.bookingId(), first.vehicleId(), reservationEnd);

        assertThat(bookingDecisionService.findAvailableVehicles(second.bookingId(), reservationEnd))
                .isEmpty();

        assertThatThrownBy(() -> bookingDecisionService.confirm(
                second.operationsUserId(), second.bookingId(), second.vehicleId(), reservationEnd))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(readBookingStatus(first.bookingId())).isEqualTo("CONFIRMED");
        assertThat(readBookingStatus(second.bookingId())).isEqualTo("PENDING_CONFIRMATION");
        assertThat(count("SELECT count(*) FROM booking_vehicle_allocations "
                + "WHERE vehicle_id = ? AND allocation_status = 'ACTIVE'", first.vehicleId())).isEqualTo(1);
        assertConfirmationSideEffects(first.bookingId(), 1);
        assertConfirmationSideEffects(second.bookingId(), 0);
    }

    @Test
    void allowsAdjacentHalfOpenReservationsForTheSameVehicle() throws Exception {
        Fixture first = createFixture();
        Fixture adjacent = createBookingForVehicle(first, first.departureAt().plusSeconds(7_200));
        Instant firstEnd = first.departureAt().plusSeconds(7_200);
        Instant adjacentEnd = adjacent.departureAt().plusSeconds(7_200);

        bookingDecisionService.confirm(
                first.operationsUserId(), first.bookingId(), first.vehicleId(),
                OffsetDateTime.ofInstant(firstEnd, ZoneOffset.UTC));

        var availableForAdjacentWindow = bookingDecisionService.findAvailableVehicles(
                adjacent.bookingId(), OffsetDateTime.ofInstant(adjacentEnd, ZoneOffset.UTC));
        assertThat(availableForAdjacentWindow)
                .extracting("vehicleId")
                .containsExactly(first.vehicleId());

        var secondResult = bookingDecisionService.confirm(
                adjacent.operationsUserId(), adjacent.bookingId(), adjacent.vehicleId(),
                OffsetDateTime.ofInstant(adjacentEnd, ZoneOffset.UTC));

        assertThat(secondResult.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(count("SELECT count(*) FROM booking_vehicle_allocations "
                + "WHERE vehicle_id = ? AND allocation_status = 'ACTIVE'", first.vehicleId())).isEqualTo(2);
        assertConfirmationSideEffects(first.bookingId(), 1);
        assertConfirmationSideEffects(adjacent.bookingId(), 1);
    }

    @Test
    void rollsBackAllocationBookingEventAndOutboxWhenAuditWriteFails() throws Exception {
        Fixture fixture = createFixture();
        Instant reservedUntil = fixture.departureAt().plusSeconds(7_200);
        OffsetDateTime reservationEnd = OffsetDateTime.ofInstant(reservedUntil, ZoneOffset.UTC);

        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION it_fail_booking_confirmation_audit()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $body$
                BEGIN
                    IF NEW.action = 'BOOKING_CONFIRMED' THEN
                        RAISE EXCEPTION 'forced Phase 3.6 audit failure';
                    END IF;
                    RETURN NEW;
                END;
                $body$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER it_fail_booking_confirmation_audit_trigger
                BEFORE INSERT ON audit_logs
                FOR EACH ROW
                EXECUTE FUNCTION it_fail_booking_confirmation_audit()
                """);

        try {
            assertThatThrownBy(() -> bookingDecisionService.confirm(
                    fixture.operationsUserId(), fixture.bookingId(), fixture.vehicleId(), reservationEnd))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbcTemplate.execute("""
                    DROP TRIGGER IF EXISTS it_fail_booking_confirmation_audit_trigger ON audit_logs
                    """);
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS it_fail_booking_confirmation_audit()");
        }

        assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("PENDING_CONFIRMATION");
        assertThat(readBookingSnapshotStatus(fixture.bookingId())).isEqualTo("PENDING_CONFIRMATION");
        assertThat(count("SELECT count(*) FROM booking_vehicle_allocations WHERE booking_id = ?",
                fixture.bookingId())).isZero();
        assertThat(count("SELECT count(*) FROM booking_events WHERE booking_id = ?",
                fixture.bookingId())).isZero();
        assertThat(count("SELECT count(*) FROM audit_logs "
                + "WHERE aggregate_type = 'BOOKING' AND aggregate_id = ?",
                fixture.bookingId().toString())).isZero();
        assertThat(count("SELECT count(*) FROM outbox_events "
                + "WHERE aggregate_type = 'BOOKING' AND aggregate_id = ?",
                fixture.bookingId().toString())).isZero();
    }

    @Test
    void doesNotWriteAnyDecisionWhenRejectionReasonIsInvalid() throws Exception {
        Fixture fixture = createFixture();

        assertThatThrownBy(() -> bookingDecisionService.reject(
                fixture.operationsUserId(), fixture.bookingId(), "bad"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("PENDING_CONFIRMATION");
        assertThat(count("SELECT count(*) FROM booking_events WHERE booking_id = ?", fixture.bookingId())).isZero();
        assertThat(count("SELECT count(*) FROM audit_logs WHERE aggregate_id = ?",
                fixture.bookingId().toString())).isZero();
        assertThat(count("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?",
                fixture.bookingId().toString())).isZero();
    }

    private Fixture createFixture() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID operationsUserId = UUID.randomUUID();
        UUID rateCardId = UUID.randomUUID();
        int rateCardVersion = nextRateCardVersion();
        UUID vehicleId = UUID.randomUUID();
        String vehicleNumber = "IT" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String rateCardCode = "IT_AIRPORT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        insertUser(customerId, "customer");
        insertUser(operationsUserId, "operations");

        Instant effectiveFrom = Instant.now().minus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        jdbcTemplate.update("""
                INSERT INTO pricing_rate_cards
                (id, code, version, service_type, operating_city, vehicle_category_id,
                 currency, base_fare, minimum_fare, per_km_rate, per_hour_rate,
                 per_day_rate, included_kilometers, included_hours,
                 driver_allowance_per_day, effective_from, active)
                VALUES (?, ?, ?, 'AIRPORT_TRANSFER', 'PUNE', ?, 'INR',
                        100, 150, 10, 0, 0, 0, 0, 0, ?, FALSE)
                """, rateCardId, rateCardCode, rateCardVersion,
                SEDAN_CATEGORY_ID, Timestamp.from(effectiveFrom));

        jdbcTemplate.update("""
                INSERT INTO vehicles
                (id, vehicle_number, category_id, model_id, passenger_capacity,
                 luggage_capacity, operational_city, status)
                VALUES (?, ?, ?, ?, 4, 2, 'PUNE', 'AVAILABLE')
                """, vehicleId, vehicleNumber, SEDAN_CATEGORY_ID, DZIRE_MODEL_ID);

        Fixture base = new Fixture(customerId, operationsUserId, rateCardId, rateCardCode,
                rateCardVersion, effectiveFrom, vehicleId, vehicleNumber, null, null, null);
        return createBookingForVehicle(base, Instant.now().plus(1, ChronoUnit.DAYS));
    }

    private Fixture createAnotherBookingForSameVehicle(Fixture existing) throws Exception {
        return createBookingForVehicle(existing, existing.departureAt());
    }

    private Fixture createBookingForVehicle(Fixture base, Instant departureAt) throws Exception {
        UUID quoteId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = Instant.now().plusSeconds(900).truncatedTo(ChronoUnit.MICROS);
        Instant normalizedDeparture = departureAt.truncatedTo(ChronoUnit.MICROS);

        jdbcTemplate.update("""
                INSERT INTO fare_quotes
                (id, customer_id, rate_card_id, rate_card_code, rate_card_version,
                 service_type, operating_city, vehicle_category_id, vehicle_category_code,
                 departure_at, rental_hours, currency, distance_meters, duration_seconds,
                 total_amount, created_at, expires_at, response_snapshot)
                VALUES (?, ?, ?, ?, ?, 'AIRPORT_TRANSFER', 'PUNE', ?, 'EXECUTIVE_SEDAN',
                        ?, NULL, 'INR', 10000, 1800, 220.50, ?, ?, '{}'::jsonb)
                """,
                quoteId, base.customerId(), base.rateCardId(), base.rateCardCode(),
                base.rateCardVersion(), SEDAN_CATEGORY_ID, Timestamp.from(normalizedDeparture),
                Timestamp.from(createdAt), Timestamp.from(expiresAt));

        RateCardSnapshot rateCard = new RateCardSnapshot(
                base.rateCardId(), base.rateCardCode(), base.rateCardVersion(), ServiceType.AIRPORT_TRANSFER,
                City.PUNE, SEDAN_CATEGORY_ID, "EXECUTIVE_SEDAN", "INR",
                new BigDecimal("100"), new BigDecimal("150"), new BigDecimal("10"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, base.rateCardEffectiveFrom(), null, List.of());
        JourneyPreviewResponse journey = new JourneyPreviewResponse(
                ServiceType.AIRPORT_TRANSFER, "PUNE", normalizedDeparture, List.of(),
                10_000, 1_800, "it-route-polyline", "it-route-reference");
        FareCalculationResult fare = new FareCalculationResult(
                "INR", new BigDecimal("10.000"), new BigDecimal("10.000"),
                new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10.00"), new BigDecimal("10.50"),
                new BigDecimal("220.50"), List.of());
        FareQuoteResponse quote = new FareQuoteResponse(
                quoteId, createdAt, expiresAt, null, journey, rateCard, fare);

        BookingResponse booking = new BookingResponse(
                bookingId,
                "IT-" + bookingId.toString().replace("-", "").substring(0, 16),
                BookingStatus.PENDING_CONFIRMATION, createdAt, createdAt, null, quote);

        jdbcTemplate.update("""
                INSERT INTO bookings
                (id, booking_reference, customer_id, quote_id, idempotency_key,
                 request_hash, status, service_type, operating_city,
                 vehicle_category_code, departure_at, currency, total_amount, booking_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING_CONFIRMATION', 'AIRPORT_TRANSFER',
                        'PUNE', 'EXECUTIVE_SEDAN', ?, 'INR', 220.50, CAST(? AS JSONB))
                """,
                bookingId, booking.bookingReference(), base.customerId(), quoteId,
                "it-key-" + bookingId, "it-hash-" + bookingId,
                Timestamp.from(normalizedDeparture), objectMapper.writeValueAsString(booking));

        return new Fixture(base.customerId(), base.operationsUserId(), base.rateCardId(),
                base.rateCardCode(), base.rateCardVersion(), base.rateCardEffectiveFrom(),
                base.vehicleId(), base.vehicleNumber(),
                bookingId, quoteId, normalizedDeparture);
    }

    private int nextRateCardVersion() {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version), 0) + 1
                FROM pricing_rate_cards
                WHERE service_type = 'AIRPORT_TRANSFER'
                  AND operating_city = 'PUNE'
                  AND vehicle_category_id = ?
                """, Integer.class, SEDAN_CATEGORY_ID);
    }

    private void insertUser(UUID userId, String type) {
        jdbcTemplate.update("""
                INSERT INTO app_users (id, email, full_name, account_status, email_verified)
                VALUES (?, ?, ?, 'ACTIVE', TRUE)
                """, userId, type + "-" + userId + "@phase36.suabhi.test",
                "Phase 3.6 " + type);
    }

    private String readBookingStatus(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    private String readBookingSnapshotStatus(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT booking_snapshot ->> 'status' FROM bookings WHERE id = ?",
                String.class, bookingId);
    }

    private long count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Long.class, arguments);
    }

    private void assertConfirmationSideEffects(UUID bookingId, long expectedCount) {
        assertThat(count("SELECT count(*) FROM booking_events "
                + "WHERE booking_id = ? AND event_type = 'BOOKING_CONFIRMED'", bookingId))
                .isEqualTo(expectedCount);
        assertThat(count("SELECT count(*) FROM audit_logs "
                + "WHERE aggregate_type = 'BOOKING' AND aggregate_id = ? AND action = 'BOOKING_CONFIRMED'",
                bookingId.toString())).isEqualTo(expectedCount);
        assertThat(count("SELECT count(*) FROM outbox_events "
                + "WHERE aggregate_type = 'BOOKING' AND aggregate_id = ? AND event_type = 'BOOKING_CONFIRMED'",
                bookingId.toString())).isEqualTo(expectedCount);
    }

    private record Fixture(
            UUID customerId,
            UUID operationsUserId,
            UUID rateCardId,
            String rateCardCode,
            int rateCardVersion,
            Instant rateCardEffectiveFrom,
            UUID vehicleId,
            String vehicleNumber,
            UUID bookingId,
            UUID quoteId,
            Instant departureAt
    ) {
    }
}
