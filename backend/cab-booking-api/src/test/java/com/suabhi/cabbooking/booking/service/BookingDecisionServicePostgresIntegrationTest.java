package com.suabhi.cabbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.suabhi.cabbooking.booking.api.BookingResponse;
import com.suabhi.cabbooking.booking.api.DriverOptionResponse;
import com.suabhi.cabbooking.booking.domain.BookingStatus;
import com.suabhi.cabbooking.common.exception.ResourceNotFoundException;
import com.suabhi.cabbooking.journey.api.JourneyPreviewResponse;
import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse.RateCardSnapshot;
import com.suabhi.cabbooking.pricing.domain.FareCalculationResult;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * PostgreSQL-backed integration tests for booking decisions and customer lifecycle persistence.
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
    private static final UUID DRIVER_ROLE_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("suabhi_booking_it")
            .withUsername("suabhi_it")
            .withPassword("suabhi_it_test_password");

    @Autowired
    private BookingDecisionService bookingDecisionService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private DriverAssignmentService driverAssignmentService;

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
        assertThat(count("SELECT count(*) FROM booking_vehicle_allocations "
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

        var availableForOverlappingWindow = bookingDecisionService.findAvailableVehicles(
                second.bookingId(), reservationEnd);
        assertThat(availableForOverlappingWindow)
                .extracting(vehicle -> vehicle.vehicleId())
                .doesNotContain(first.vehicleId());

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
                .extracting(vehicle -> vehicle.vehicleId())
                .contains(first.vehicleId());

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


    @Test
    void cancelsPendingBookingOnceAndPersistsEventAuditAndOutbox() throws Exception {
        Fixture fixture = createFixture();

        BookingResponse firstResult = bookingService.cancel(fixture.customerId(), fixture.bookingId());
        BookingResponse replayResult = bookingService.cancel(fixture.customerId(), fixture.bookingId());

        assertThat(firstResult.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(firstResult.cancelledAt()).isNotNull();
        assertThat(replayResult.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(replayResult.cancelledAt()).isEqualTo(firstResult.cancelledAt());

        assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("CANCELLED");
        assertThat(readBookingSnapshotStatus(fixture.bookingId())).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cancelled_at IS NOT NULL FROM bookings WHERE id = ?", Boolean.class, fixture.bookingId()))
                .isTrue();
        assertThat(count("SELECT count(*) FROM booking_events "
                + "WHERE booking_id = ? AND event_type = 'BOOKING_CANCELLED' "
                + "AND from_status = 'PENDING_CONFIRMATION' AND to_status = 'CANCELLED'",
                fixture.bookingId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actor_user_id FROM booking_events "
                        + "WHERE booking_id = ? AND event_type = 'BOOKING_CANCELLED'",
                UUID.class, fixture.bookingId())).isEqualTo(fixture.customerId());
        assertThat(count("SELECT count(*) FROM audit_logs "
                + "WHERE aggregate_type = 'BOOKING' AND aggregate_id = ? AND action = 'BOOKING_CANCELLED'",
                fixture.bookingId().toString())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_events "
                + "WHERE aggregate_type = 'BOOKING' AND aggregate_id = ? AND event_type = 'BOOKING_CANCELLED'",
                fixture.bookingId().toString())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM booking_vehicle_allocations "
                + "WHERE booking_id = ? AND allocation_status = 'ACTIVE'", fixture.bookingId())).isZero();
    }

    @Test
    void customerCancellationCannotOverwriteAnOperationsDecisionAndCancelledBookingCannotBeConfirmed() throws Exception {
        Fixture confirmed = createFixture();
        Instant confirmedUntil = confirmed.departureAt().plusSeconds(7_200);
        bookingDecisionService.confirm(
                confirmed.operationsUserId(), confirmed.bookingId(), confirmed.vehicleId(),
                OffsetDateTime.ofInstant(confirmedUntil, ZoneOffset.UTC));

        assertThatThrownBy(() -> bookingService.cancel(confirmed.customerId(), confirmed.bookingId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(readBookingStatus(confirmed.bookingId())).isEqualTo("CONFIRMED");
        assertThat(count("SELECT count(*) FROM booking_vehicle_allocations "
                + "WHERE booking_id = ? AND allocation_status = 'ACTIVE'", confirmed.bookingId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM booking_events "
                + "WHERE booking_id = ? AND event_type = 'BOOKING_CANCELLED'", confirmed.bookingId())).isZero();

        Fixture rejected = createFixture();
        bookingDecisionService.reject(
                rejected.operationsUserId(), rejected.bookingId(), "No eligible vehicle for this service window");
        assertThatThrownBy(() -> bookingService.cancel(rejected.customerId(), rejected.bookingId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(readBookingStatus(rejected.bookingId())).isEqualTo("REJECTED");
        assertThat(count("SELECT count(*) FROM booking_events "
                + "WHERE booking_id = ? AND event_type = 'BOOKING_CANCELLED'", rejected.bookingId())).isZero();

        Fixture cancelled = createFixture();
        bookingService.cancel(cancelled.customerId(), cancelled.bookingId());
        Instant cancelledUntil = cancelled.departureAt().plusSeconds(7_200);
        assertThatThrownBy(() -> bookingDecisionService.confirm(
                cancelled.operationsUserId(), cancelled.bookingId(), cancelled.vehicleId(),
                OffsetDateTime.ofInstant(cancelledUntil, ZoneOffset.UTC)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(readBookingStatus(cancelled.bookingId())).isEqualTo("CANCELLED");
        assertThat(count("SELECT count(*) FROM booking_vehicle_allocations WHERE booking_id = ?",
                cancelled.bookingId())).isZero();
    }

    @Test
    void rollsBackCancellationStatusAndLifecycleRecordsWhenAuditWriteFails() throws Exception {
        Fixture fixture = createFixture();

        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION it_fail_booking_cancellation_audit()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $body$
                BEGIN
                    IF NEW.action = 'BOOKING_CANCELLED' THEN
                        RAISE EXCEPTION 'forced Phase 3.7 cancellation audit failure';
                    END IF;
                    RETURN NEW;
                END;
                $body$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER it_fail_booking_cancellation_audit_trigger
                BEFORE INSERT ON audit_logs
                FOR EACH ROW
                EXECUTE FUNCTION it_fail_booking_cancellation_audit()
                """);

        try {
            assertThatThrownBy(() -> bookingService.cancel(fixture.customerId(), fixture.bookingId()))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbcTemplate.execute("""
                    DROP TRIGGER IF EXISTS it_fail_booking_cancellation_audit_trigger ON audit_logs
                    """);
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS it_fail_booking_cancellation_audit()");
        }

        assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("PENDING_CONFIRMATION");
        assertThat(readBookingSnapshotStatus(fixture.bookingId())).isEqualTo("PENDING_CONFIRMATION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cancelled_at IS NULL FROM bookings WHERE id = ?", Boolean.class, fixture.bookingId()))
                .isTrue();
        assertThat(count("SELECT count(*) FROM booking_events WHERE booking_id = ?", fixture.bookingId())).isZero();
        assertThat(count("SELECT count(*) FROM audit_logs WHERE aggregate_type = 'BOOKING' AND aggregate_id = ?",
                fixture.bookingId().toString())).isZero();
        assertThat(count("SELECT count(*) FROM outbox_events WHERE aggregate_type = 'BOOKING' AND aggregate_id = ?",
                fixture.bookingId().toString())).isZero();
    }


    @Test
    void listsOnlyVerifiedAvailableLicensedDriversInTheBookingCity() throws Exception {
        Fixture fixture = createConfirmedFixture();
        LocalDate validExpiry = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(1);
        DriverFixture eligible = createDriver("VERIFIED", "AVAILABLE", validExpiry, "PUNE", true);
        DriverFixture pending = createDriver("PENDING", "AVAILABLE", validExpiry, "PUNE", true);
        DriverFixture offline = createDriver("VERIFIED", "OFFLINE", validExpiry, "PUNE", true);
        DriverFixture expired = createDriver("VERIFIED", "AVAILABLE",
                LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1), "PUNE", true);
        DriverFixture wrongCity = createDriver("VERIFIED", "AVAILABLE", validExpiry, "MUMBAI", true);
        DriverFixture noDriverRole = createDriver("VERIFIED", "AVAILABLE", validExpiry, "PUNE", false);

        List<DriverOptionResponse> options = driverAssignmentService.findAvailableDrivers(fixture.bookingId());

        assertThat(options).extracting(DriverOptionResponse::driverProfileId).contains(eligible.profileId());
        assertThat(options).extracting(DriverOptionResponse::driverProfileId)
                .doesNotContain(pending.profileId(), offline.profileId(), expired.profileId(),
                        wrongCity.profileId(), noDriverRole.profileId());
        assertThat(options.stream().filter(option -> option.driverProfileId().equals(eligible.profileId()))
                .findFirst().orElseThrow().fullName()).isEqualTo(eligible.fullName());
    }

    @Test
    void assignsDriverOnceAndPersistsAssignmentEventAuditAndOutbox() throws Exception {
        Fixture fixture = createConfirmedFixture();
        DriverFixture driver = createDriver("VERIFIED", "AVAILABLE",
                LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(1), "PUNE", true);

        var firstResult = driverAssignmentService.assignDriver(
                fixture.operationsUserId(), fixture.bookingId(), driver.profileId());
        var replayResult = driverAssignmentService.assignDriver(
                fixture.operationsUserId(), fixture.bookingId(), driver.profileId());

        assertThat(firstResult.booking().status()).isEqualTo(BookingStatus.ASSIGNED);
        assertThat(firstResult.driverProfileId()).isEqualTo(driver.profileId());
        assertThat(firstResult.vehicleId()).isEqualTo(fixture.vehicleId());
        assertThat(replayResult.booking().status()).isEqualTo(BookingStatus.ASSIGNED);
        assertThat(replayResult.driverProfileId()).isEqualTo(driver.profileId());
        assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("ASSIGNED");
        assertThat(readBookingSnapshotStatus(fixture.bookingId())).isEqualTo("ASSIGNED");
        assertThat(count("SELECT count(*) FROM booking_driver_assignments WHERE booking_id = ?",
                fixture.bookingId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM booking_events "
                + "WHERE booking_id = ? AND event_type = 'BOOKING_DRIVER_ASSIGNED'",
                fixture.bookingId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs "
                + "WHERE aggregate_type = 'BOOKING' AND aggregate_id = ? "
                + "AND action = 'BOOKING_DRIVER_ASSIGNED'", fixture.bookingId().toString())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_events "
                + "WHERE aggregate_type = 'BOOKING' AND aggregate_id = ? "
                + "AND event_type = 'BOOKING_DRIVER_ASSIGNED'", fixture.bookingId().toString())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT availability_status FROM driver_profiles WHERE id = ?",
                String.class, driver.profileId())).isEqualTo("AVAILABLE");
        assertThat(driverAssignmentService.findTrips(driver.userId()))
                .extracting(trip -> trip.booking().id()).contains(fixture.bookingId());
    }

    @Test
    void preventsOverlappingDriverAssignmentsAcrossDifferentVehicles() throws Exception {
        Fixture first = createConfirmedFixture();
        Fixture second = createConfirmedFixture();
        DriverFixture driver = createDriver("VERIFIED", "AVAILABLE",
                LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(1), "PUNE", true);

        driverAssignmentService.assignDriver(first.operationsUserId(), first.bookingId(), driver.profileId());

        assertThat(driverAssignmentService.findAvailableDrivers(second.bookingId()))
                .extracting(DriverOptionResponse::driverProfileId)
                .doesNotContain(driver.profileId());
        assertThatThrownBy(() -> driverAssignmentService.assignDriver(
                second.operationsUserId(), second.bookingId(), driver.profileId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(readBookingStatus(second.bookingId())).isEqualTo("CONFIRMED");
        assertThat(count("SELECT count(*) FROM booking_driver_assignments WHERE driver_profile_id = ?",
                driver.profileId())).isEqualTo(1);
    }

    @Test
    void rejectsDriversWithInvalidVerificationAvailabilityLicenseCityOrRole() throws Exception {
        Fixture fixture = createConfirmedFixture();
        LocalDate validExpiry = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(1);
        List<DriverFixture> invalidDrivers = List.of(
                createDriver("PENDING", "AVAILABLE", validExpiry, "PUNE", true),
                createDriver("VERIFIED", "OFFLINE", validExpiry, "PUNE", true),
                createDriver("VERIFIED", "AVAILABLE",
                        LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1), "PUNE", true),
                createDriver("VERIFIED", "AVAILABLE", validExpiry, "MUMBAI", true),
                createDriver("VERIFIED", "AVAILABLE", validExpiry, "PUNE", false));

        for (DriverFixture driver : invalidDrivers) {
            assertThatThrownBy(() -> driverAssignmentService.assignDriver(
                    fixture.operationsUserId(), fixture.bookingId(), driver.profileId()))
                    .isInstanceOf(RuntimeException.class);
            assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("CONFIRMED");
            assertThat(count("SELECT count(*) FROM booking_driver_assignments WHERE booking_id = ?",
                    fixture.bookingId())).isZero();
        }
    }

    @Test
    void driverCanStartAndCompleteOnlyTheirOwnAssignedTrip() throws Exception {
        Fixture fixture = createConfirmedFixture();
        DriverFixture driver = createDriver("VERIFIED", "AVAILABLE",
                LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(1), "PUNE", true);
        DriverFixture otherDriver = createDriver("VERIFIED", "AVAILABLE",
                LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(1), "PUNE", true);
        driverAssignmentService.assignDriver(fixture.operationsUserId(), fixture.bookingId(), driver.profileId());

        assertThatThrownBy(() -> driverAssignmentService.findTrip(otherDriver.userId(), fixture.bookingId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> driverAssignmentService.startTrip(otherDriver.userId(), fixture.bookingId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> driverAssignmentService.completeTrip(driver.userId(), fixture.bookingId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> driverAssignmentService.startTrip(driver.userId(), fixture.bookingId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        Instant scheduleNow = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbcTemplate.update("""
                UPDATE booking_driver_assignments
                SET reserved_from_at = ?, reserved_until_at = ?
                WHERE booking_id = ?
                """, Timestamp.from(scheduleNow.minusSeconds(60)),
                Timestamp.from(scheduleNow.plusSeconds(3_600)), fixture.bookingId());

        var started = driverAssignmentService.startTrip(driver.userId(), fixture.bookingId());
        var startReplay = driverAssignmentService.startTrip(driver.userId(), fixture.bookingId());
        assertThat(started.booking().status()).isEqualTo(BookingStatus.IN_PROGRESS);
        assertThat(started.startedAt()).isNotNull();
        assertThat(startReplay.startedAt()).isEqualTo(started.startedAt());
        assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT availability_status FROM driver_profiles WHERE id = ?",
                String.class, driver.profileId())).isEqualTo("ON_TRIP");
        assertThat(count("SELECT count(*) FROM booking_events "
                + "WHERE booking_id = ? AND event_type = 'BOOKING_TRIP_STARTED'",
                fixture.bookingId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs "
                + "WHERE aggregate_id = ? AND action = 'BOOKING_TRIP_STARTED'",
                fixture.bookingId().toString())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_events "
                + "WHERE aggregate_id = ? AND event_type = 'BOOKING_TRIP_STARTED'",
                fixture.bookingId().toString())).isEqualTo(1);

        var completed = driverAssignmentService.completeTrip(driver.userId(), fixture.bookingId());
        var completionReplay = driverAssignmentService.completeTrip(driver.userId(), fixture.bookingId());
        assertThat(completed.booking().status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completionReplay.completedAt()).isEqualTo(completed.completedAt());
        assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT availability_status FROM driver_profiles WHERE id = ?",
                String.class, driver.profileId())).isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM booking_events "
                + "WHERE booking_id = ? AND event_type = 'BOOKING_TRIP_COMPLETED'",
                fixture.bookingId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs "
                + "WHERE aggregate_id = ? AND action = 'BOOKING_TRIP_COMPLETED'",
                fixture.bookingId().toString())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_events "
                + "WHERE aggregate_id = ? AND event_type = 'BOOKING_TRIP_COMPLETED'",
                fixture.bookingId().toString())).isEqualTo(1);
        assertThat(driverAssignmentService.findTrips(driver.userId()))
                .extracting(trip -> trip.booking().id()).contains(fixture.bookingId());
    }

    @Test
    void rollsBackDriverAssignmentWhenAuditWriteFails() throws Exception {
        Fixture fixture = createConfirmedFixture();
        DriverFixture driver = createDriver("VERIFIED", "AVAILABLE",
                LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(1), "PUNE", true);

        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION it_fail_driver_assignment_audit()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $body$
                BEGIN
                    IF NEW.action = 'BOOKING_DRIVER_ASSIGNED' THEN
                        RAISE EXCEPTION 'forced Phase 3.8 assignment audit failure';
                    END IF;
                    RETURN NEW;
                END;
                $body$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER it_fail_driver_assignment_audit_trigger
                BEFORE INSERT ON audit_logs
                FOR EACH ROW
                EXECUTE FUNCTION it_fail_driver_assignment_audit()
                """);

        try {
            assertThatThrownBy(() -> driverAssignmentService.assignDriver(
                    fixture.operationsUserId(), fixture.bookingId(), driver.profileId()))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS it_fail_driver_assignment_audit_trigger ON audit_logs");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS it_fail_driver_assignment_audit()");
        }

        assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("CONFIRMED");
        assertThat(readBookingSnapshotStatus(fixture.bookingId())).isEqualTo("CONFIRMED");
        assertThat(count("SELECT count(*) FROM booking_driver_assignments WHERE booking_id = ?",
                fixture.bookingId())).isZero();
        assertThat(count("SELECT count(*) FROM booking_events WHERE booking_id = ?",
                fixture.bookingId())).isEqualTo(1); // the earlier booking confirmation remains intact
        assertThat(count("SELECT count(*) FROM audit_logs WHERE aggregate_id = ? AND action = 'BOOKING_DRIVER_ASSIGNED'",
                fixture.bookingId().toString())).isZero();
        assertThat(count("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? "
                + "AND event_type = 'BOOKING_DRIVER_ASSIGNED'", fixture.bookingId().toString())).isZero();
    }

    @Test
    void rollsBackDriverStartAndAvailabilityWhenTripAuditWriteFails() throws Exception {
        Fixture fixture = createConfirmedFixture();
        DriverFixture driver = createDriver("VERIFIED", "AVAILABLE",
                LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(1), "PUNE", true);
        driverAssignmentService.assignDriver(fixture.operationsUserId(), fixture.bookingId(), driver.profileId());

        Instant scheduleNow = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbcTemplate.update("""
                UPDATE booking_driver_assignments
                SET reserved_from_at = ?, reserved_until_at = ?
                WHERE booking_id = ?
                """, Timestamp.from(scheduleNow.minusSeconds(60)),
                Timestamp.from(scheduleNow.plusSeconds(3_600)), fixture.bookingId());

        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION it_fail_driver_trip_start_audit()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $body$
                BEGIN
                    IF NEW.action = 'BOOKING_TRIP_STARTED' THEN
                        RAISE EXCEPTION 'forced Phase 3.8 trip-start audit failure';
                    END IF;
                    RETURN NEW;
                END;
                $body$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER it_fail_driver_trip_start_audit_trigger
                BEFORE INSERT ON audit_logs
                FOR EACH ROW
                EXECUTE FUNCTION it_fail_driver_trip_start_audit()
                """);

        try {
            assertThatThrownBy(() -> driverAssignmentService.startTrip(driver.userId(), fixture.bookingId()))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS it_fail_driver_trip_start_audit_trigger ON audit_logs");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS it_fail_driver_trip_start_audit()");
        }

        assertThat(readBookingStatus(fixture.bookingId())).isEqualTo("ASSIGNED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT availability_status FROM driver_profiles WHERE id = ?",
                String.class, driver.profileId())).isEqualTo("AVAILABLE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT trip_started_at IS NULL FROM booking_driver_assignments WHERE booking_id = ?",
                Boolean.class, fixture.bookingId())).isTrue();
        assertThat(count("SELECT count(*) FROM booking_events "
                + "WHERE booking_id = ? AND event_type = 'BOOKING_TRIP_STARTED'",
                fixture.bookingId())).isZero();
        assertThat(count("SELECT count(*) FROM outbox_events "
                + "WHERE aggregate_id = ? AND event_type = 'BOOKING_TRIP_STARTED'",
                fixture.bookingId().toString())).isZero();
    }


    private Fixture createConfirmedFixture() throws Exception {
        Fixture fixture = createFixture();
        Instant reservedUntil = fixture.departureAt().plusSeconds(7_200);
        bookingDecisionService.confirm(
                fixture.operationsUserId(), fixture.bookingId(), fixture.vehicleId(),
                OffsetDateTime.ofInstant(reservedUntil, ZoneOffset.UTC));
        return fixture;
    }

    private DriverFixture createDriver(String verificationStatus, String availabilityStatus,
                                       LocalDate licenseExpiry, String operatingCity,
                                       boolean assignDriverRole) {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String driverCode = "DRV" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String licenseNumber = "LIC" + UUID.randomUUID().toString().replace("-", "");
        insertUser(userId, "driver");
        if (assignDriverRole) {
            jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                    userId, DRIVER_ROLE_ID);
        }
        jdbcTemplate.update("""
                INSERT INTO driver_profiles
                (id, user_id, driver_code, license_number, license_expiry,
                 verification_status, availability_status, operating_city)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                profileId, userId, driverCode, licenseNumber, licenseExpiry,
                verificationStatus, availabilityStatus, operatingCity);
        return new DriverFixture(profileId, userId, driverCode, "Phase 3.6 driver");
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

    private record DriverFixture(UUID profileId, UUID userId, String driverCode, String fullName) {
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
