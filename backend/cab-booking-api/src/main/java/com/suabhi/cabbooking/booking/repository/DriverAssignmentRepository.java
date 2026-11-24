package com.suabhi.cabbooking.booking.repository;

import com.suabhi.cabbooking.booking.api.BookingResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class DriverAssignmentRepository {
    private static final String DRIVER_PROFILE_SELECT = """
            SELECT dp.id, dp.user_id, dp.driver_code, u.full_name, dp.operating_city, dp.license_expiry,
                   dp.verification_status, dp.availability_status, u.account_status,
                   EXISTS (
                     SELECT 1
                     FROM user_roles ur
                     JOIN roles r ON r.id = ur.role_id
                     WHERE ur.user_id = dp.user_id AND r.code = 'DRIVER'
                   ) AS has_driver_role
            FROM driver_profiles dp
            JOIN app_users u ON u.id = dp.user_id
            """;

    private static final String TRIP_SELECT = """
            SELECT b.booking_snapshot::text AS booking_snapshot,
                   dp.id AS driver_profile_id, dp.user_id AS driver_user_id,
                   dp.driver_code, va.vehicle_id, v.vehicle_number,
                   a.reserved_from_at, a.reserved_until_at, a.assigned_at,
                   a.trip_started_at, a.trip_completed_at
            FROM booking_driver_assignments a
            JOIN bookings b ON b.id = a.booking_id
            JOIN driver_profiles dp ON dp.id = a.driver_profile_id
            JOIN booking_vehicle_allocations va
              ON va.booking_id = b.id AND va.allocation_status = 'ACTIVE'
            JOIN vehicles v ON v.id = va.vehicle_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DriverAssignmentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<DriverProfileRow> findAvailableDrivers(UUID bookingId) {
        return jdbcTemplate.query(DRIVER_PROFILE_SELECT + """
                JOIN user_roles ur ON ur.user_id = dp.user_id
                JOIN roles r ON r.id = ur.role_id AND r.code = 'DRIVER'
                JOIN booking_vehicle_allocations va
                  ON va.booking_id = ? AND va.allocation_status = 'ACTIVE'
                JOIN bookings b ON b.id = va.booking_id
                WHERE upper(dp.operating_city) = upper(b.operating_city)
                  AND dp.verification_status = 'VERIFIED'
                  AND dp.availability_status = 'AVAILABLE'
                  AND u.account_status = 'ACTIVE'
                  AND dp.license_expiry >=
                      (va.reserved_until_at AT TIME ZONE 'Asia/Kolkata')::date
                  AND NOT EXISTS (
                    SELECT 1
                    FROM booking_driver_assignments existing
                    WHERE existing.driver_profile_id = dp.id
                      AND tstzrange(existing.reserved_from_at, existing.reserved_until_at, '[)')
                          && tstzrange(va.reserved_from_at, va.reserved_until_at, '[)')
                  )
                ORDER BY dp.driver_code ASC
                """, this::mapDriverProfile, bookingId);
    }

    public Optional<DriverProfileRow> findDriverProfile(UUID driverProfileId) {
        return queryDriverProfile(DRIVER_PROFILE_SELECT + " WHERE dp.id = ?", driverProfileId);
    }

    public Optional<DriverProfileRow> findDriverProfileByUserId(UUID userId) {
        return queryDriverProfile(DRIVER_PROFILE_SELECT + " WHERE dp.user_id = ?", userId);
    }

    public UUID insertAssignment(UUID bookingId, UUID driverProfileId, UUID assignedBy,
                                 Instant reservedFromAt, Instant reservedUntilAt, Instant assignedAt) {
        UUID assignmentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO booking_driver_assignments
                (id, booking_id, driver_profile_id, assigned_by,
                 reserved_from_at, reserved_until_at, assigned_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                assignmentId, bookingId, driverProfileId, assignedBy,
                Timestamp.from(reservedFromAt), Timestamp.from(reservedUntilAt), Timestamp.from(assignedAt));
        return assignmentId;
    }

    public Optional<DriverTripRow> findTripForBooking(UUID bookingId) {
        return queryTrip(TRIP_SELECT + " WHERE a.booking_id = ?", bookingId);
    }

    public Optional<DriverTripRow> findTripForDriver(UUID bookingId, UUID driverUserId) {
        return queryTrip(TRIP_SELECT + """
                WHERE a.booking_id = ? AND dp.user_id = ?
                """, bookingId, driverUserId);
    }

    public List<DriverTripRow> findTripsForDriver(UUID driverUserId) {
        return jdbcTemplate.query(TRIP_SELECT + """
                WHERE dp.user_id = ?
                  AND b.status IN ('ASSIGNED','IN_PROGRESS','COMPLETED')
                ORDER BY b.departure_at DESC, a.assigned_at DESC
                """, this::mapTrip, driverUserId);
    }

    public int markTripStarted(UUID bookingId, UUID driverProfileId, Instant startedAt) {
        return jdbcTemplate.update("""
                UPDATE booking_driver_assignments
                SET trip_started_at = ?
                WHERE booking_id = ? AND driver_profile_id = ?
                  AND trip_started_at IS NULL AND trip_completed_at IS NULL
                """, Timestamp.from(startedAt), bookingId, driverProfileId);
    }

    public int markTripCompleted(UUID bookingId, UUID driverProfileId, Instant completedAt) {
        return jdbcTemplate.update("""
                UPDATE booking_driver_assignments
                SET trip_completed_at = ?
                WHERE booking_id = ? AND driver_profile_id = ?
                  AND trip_started_at IS NOT NULL AND trip_completed_at IS NULL
                """, Timestamp.from(completedAt), bookingId, driverProfileId);
    }

    public int markDriverOnTrip(UUID driverProfileId) {
        return jdbcTemplate.update("""
                UPDATE driver_profiles
                SET availability_status = 'ON_TRIP', updated_at = now()
                WHERE id = ? AND verification_status = 'VERIFIED'
                  AND availability_status = 'AVAILABLE'
                """, driverProfileId);
    }

    public int markDriverAvailable(UUID driverProfileId) {
        return jdbcTemplate.update("""
                UPDATE driver_profiles
                SET availability_status = 'AVAILABLE', updated_at = now()
                WHERE id = ? AND availability_status = 'ON_TRIP'
                """, driverProfileId);
    }

    private Optional<DriverProfileRow> queryDriverProfile(String sql, Object... args) {
        List<DriverProfileRow> rows = jdbcTemplate.query(sql, this::mapDriverProfile, args);
        return rows.stream().findFirst();
    }

    private Optional<DriverTripRow> queryTrip(String sql, Object... args) {
        List<DriverTripRow> rows = jdbcTemplate.query(sql, this::mapTrip, args);
        return rows.stream().findFirst();
    }

    private DriverProfileRow mapDriverProfile(ResultSet rs, int rowNum) throws SQLException {
        return new DriverProfileRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("driver_code"),
                rs.getString("full_name"),
                rs.getString("operating_city"),
                rs.getDate("license_expiry").toLocalDate(),
                rs.getString("verification_status"),
                rs.getString("availability_status"),
                rs.getString("account_status"),
                rs.getBoolean("has_driver_role"));
    }

    private DriverTripRow mapTrip(ResultSet rs, int rowNum) throws SQLException {
        try {
            return new DriverTripRow(
                    objectMapper.readValue(rs.getString("booking_snapshot"), BookingResponse.class),
                    rs.getObject("driver_profile_id", UUID.class),
                    rs.getObject("driver_user_id", UUID.class),
                    rs.getString("driver_code"),
                    rs.getObject("vehicle_id", UUID.class),
                    rs.getString("vehicle_number"),
                    instant(rs, "reserved_from_at"),
                    instant(rs, "reserved_until_at"),
                    instant(rs, "assigned_at"),
                    instant(rs, "trip_started_at"),
                    instant(rs, "trip_completed_at"));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read the stored driver trip snapshot.", exception);
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record DriverProfileRow(
            UUID id,
            UUID userId,
            String driverCode,
            String fullName,
            String operatingCity,
            LocalDate licenseExpiry,
            String verificationStatus,
            String availabilityStatus,
            String accountStatus,
            boolean hasDriverRole
    ) {
    }

    public record DriverTripRow(
            BookingResponse booking,
            UUID driverProfileId,
            UUID driverUserId,
            String driverCode,
            UUID vehicleId,
            String vehicleNumber,
            Instant reservedFromAt,
            Instant reservedUntilAt,
            Instant assignedAt,
            Instant startedAt,
            Instant completedAt
    ) {
    }
}
