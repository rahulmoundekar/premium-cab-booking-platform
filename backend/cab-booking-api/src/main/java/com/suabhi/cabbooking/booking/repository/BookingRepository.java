package com.suabhi.cabbooking.booking.repository;

import com.suabhi.cabbooking.booking.api.BookingResponse;
import com.suabhi.cabbooking.booking.domain.BookingStatus;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class BookingRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BookingRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Unique constraints on (customer_id, idempotency_key) and quote_id arbitrate concurrent retries.
     * The caller handles a zero row count by loading the winning record and comparing its request hash.
     */
    public boolean insertIfAbsent(UUID customerId, String idempotencyKey, String requestHash,
                                  FareQuoteResponse quote, BookingResponse booking) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO bookings
                (id, booking_reference, customer_id, quote_id, idempotency_key, request_hash,
                 status, service_type, operating_city, vehicle_category_code, departure_at,
                 currency, total_amount, booking_snapshot, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?)
                ON CONFLICT DO NOTHING
                """,
                booking.id(), booking.bookingReference(), customerId, quote.id(),
                idempotencyKey, requestHash, booking.status().name(),
                quote.journey().serviceType().name(), quote.journey().originCity(),
                quote.rateCard().vehicleCategoryCode(), Timestamp.from(quote.journey().departureAt()),
                quote.fare().currency(), quote.fare().totalAmount(),
                writeSnapshot(booking), Timestamp.from(booking.createdAt()), Timestamp.from(booking.updatedAt()));
        return inserted == 1;
    }

    public Optional<BookingResponse> findByCustomerAndIdempotencyKey(UUID customerId, String idempotencyKey) {
        return findOne("""
                SELECT booking_snapshot::text
                FROM bookings
                WHERE customer_id = ? AND idempotency_key = ?
                """, customerId, idempotencyKey);
    }

    public String findRequestHash(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT request_hash FROM bookings WHERE id = ?",
                String.class, bookingId);
    }

    public Optional<BookingResponse> findByQuoteId(UUID quoteId) {
        return findOne("""
                SELECT booking_snapshot::text
                FROM bookings
                WHERE quote_id = ?
                """, quoteId);
    }

    public Optional<BookingResponse> findOwned(UUID bookingId, UUID customerId) {
        return findOne("""
                SELECT booking_snapshot::text
                FROM bookings
                WHERE id = ? AND customer_id = ?
                """, bookingId, customerId);
    }

    public Optional<BookingResponse> findOwnedForUpdate(UUID bookingId, UUID customerId) {
        return findOne("""
                SELECT booking_snapshot::text
                FROM bookings
                WHERE id = ? AND customer_id = ?
                FOR UPDATE
                """, bookingId, customerId);
    }

    public List<BookingResponse> findAllOwned(UUID customerId) {
        return jdbcTemplate.query("""
                SELECT booking_snapshot::text
                FROM bookings
                WHERE customer_id = ?
                ORDER BY created_at DESC, id DESC
                """, (rs, rowNum) -> readSnapshot(rs.getString(1)), customerId);
    }

    public List<BookingResponse> findAllForManagement(BookingStatus status) {
        String sql = """
                SELECT booking_snapshot::text
                FROM bookings
                ORDER BY departure_at ASC, created_at ASC, id ASC
                """;
        if (status != null) {
            sql = """
                    SELECT booking_snapshot::text
                    FROM bookings
                    WHERE status = ?
                    ORDER BY departure_at ASC, created_at ASC, id ASC
                    """;
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> readSnapshot(rs.getString(1)), status.name());
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> readSnapshot(rs.getString(1)));
    }

    public Optional<BookingResponse> findForManagement(UUID bookingId) {
        return findOne("""
                SELECT booking_snapshot::text
                FROM bookings
                WHERE id = ?
                """, bookingId);
    }

    public Optional<BookingResponse> findForManagementForUpdate(UUID bookingId) {
        return findOne("""
                SELECT booking_snapshot::text
                FROM bookings
                WHERE id = ?
                FOR UPDATE
                """, bookingId);
    }

    public String findDecisionReason(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT decision_reason FROM bookings WHERE id = ?",
                String.class, bookingId);
    }

    public Optional<AllocationRow> findActiveAllocation(UUID bookingId) {
        List<AllocationRow> rows = jdbcTemplate.query("""
                SELECT a.id, a.booking_id, a.vehicle_id, v.vehicle_number,
                       a.reserved_from_at, a.reserved_until_at
                FROM booking_vehicle_allocations a
                JOIN vehicles v ON v.id = a.vehicle_id
                WHERE a.booking_id = ? AND a.allocation_status = 'ACTIVE'
                """, (rs, rowNum) -> new AllocationRow(
                rs.getObject("id", UUID.class),
                rs.getObject("booking_id", UUID.class),
                rs.getObject("vehicle_id", UUID.class),
                rs.getString("vehicle_number"),
                rs.getTimestamp("reserved_from_at").toInstant(),
                rs.getTimestamp("reserved_until_at").toInstant()), bookingId);
        return rows.stream().findFirst();
    }

    public List<AvailableVehicle> findAvailableVehicles(UUID categoryId, String operatingCity,
                                                         Instant reservedFromAt, Instant reservedUntilAt) {
        return jdbcTemplate.query("""
                SELECT v.id, v.vehicle_number, v.operational_city
                FROM vehicles v
                JOIN vehicle_categories c ON c.id = v.category_id
                WHERE v.category_id = ?
                  AND c.active = TRUE
                  AND v.status = 'AVAILABLE'
                  AND upper(v.operational_city) = upper(?)
                  AND NOT EXISTS (
                    SELECT 1
                    FROM booking_vehicle_allocations a
                    WHERE a.vehicle_id = v.id
                      AND a.allocation_status = 'ACTIVE'
                      AND tstzrange(a.reserved_from_at, a.reserved_until_at, '[)')
                          && tstzrange(?::timestamptz, ?::timestamptz, '[)')
                  )
                ORDER BY v.vehicle_number ASC
                """, (rs, rowNum) -> new AvailableVehicle(
                rs.getObject("id", UUID.class),
                rs.getString("vehicle_number"),
                rs.getString("operational_city")),
                categoryId, operatingCity, Timestamp.from(reservedFromAt), Timestamp.from(reservedUntilAt));
    }

    public AllocationRow insertActiveAllocation(UUID bookingId, UUID vehicleId, String vehicleNumber,
                                                 UUID allocatedBy, Instant reservedFromAt,
                                                 Instant reservedUntilAt) {
        UUID allocationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO booking_vehicle_allocations
                (id, booking_id, vehicle_id, reserved_from_at, reserved_until_at,
                 allocation_status, allocated_by, created_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, now())
                """,
                allocationId, bookingId, vehicleId,
                Timestamp.from(reservedFromAt), Timestamp.from(reservedUntilAt), allocatedBy);
        return new AllocationRow(allocationId, bookingId, vehicleId, vehicleNumber,
                reservedFromAt, reservedUntilAt);
    }

    public void updateStatus(UUID bookingId, UUID customerId, BookingResponse updated) {
        jdbcTemplate.update("""
                UPDATE bookings
                SET status = ?, updated_at = ?, cancelled_at = ?, booking_snapshot = CAST(? AS JSONB)
                WHERE id = ? AND customer_id = ?
                """,
                updated.status().name(), Timestamp.from(updated.updatedAt()),
                updated.cancelledAt() == null ? null : Timestamp.from(updated.cancelledAt()),
                writeSnapshot(updated), bookingId, customerId);
    }

    public void updateManagementStatus(UUID bookingId, BookingResponse updated, String decisionReason) {
        jdbcTemplate.update("""
                UPDATE bookings
                SET status = ?, updated_at = ?, booking_snapshot = CAST(? AS JSONB), decision_reason = ?
                WHERE id = ?
                """,
                updated.status().name(), Timestamp.from(updated.updatedAt()),
                writeSnapshot(updated), decisionReason, bookingId);
    }

    public void recordCreated(UUID actorId, BookingResponse booking) {
        recordEvent(actorId, booking, "BOOKING_CREATED", null, Map.of());
    }

    public void recordCancelled(UUID actorId, BookingResponse booking, BookingStatus previousStatus) {
        recordEvent(actorId, booking, "BOOKING_CANCELLED", previousStatus, Map.of());
    }

    public void recordConfirmed(UUID actorId, BookingResponse booking, UUID vehicleId,
                                String vehicleNumber, Instant reservedFromAt, Instant reservedUntilAt) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("vehicleId", vehicleId.toString());
        metadata.put("vehicleNumber", vehicleNumber);
        metadata.put("reservedFromAt", reservedFromAt.toString());
        metadata.put("reservedUntilAt", reservedUntilAt.toString());
        recordEvent(actorId, booking, "BOOKING_CONFIRMED", BookingStatus.PENDING_CONFIRMATION, metadata);
    }

    public void recordRejected(UUID actorId, BookingResponse booking, String reason) {
        recordEvent(actorId, booking, "BOOKING_REJECTED", BookingStatus.PENDING_CONFIRMATION,
                Map.of("decisionReason", reason));
    }

    private void recordEvent(UUID actorId, BookingResponse booking, String eventType,
                             BookingStatus previousStatus, Map<String, Object> additionalMetadata) {
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("bookingReference", booking.bookingReference());
        eventData.put("quoteId", booking.quote().id().toString());
        eventData.put("status", booking.status().name());
        eventData.put("serviceType", booking.quote().journey().serviceType().name());
        eventData.put("operatingCity", booking.quote().journey().originCity());
        eventData.put("currency", booking.quote().fare().currency());
        eventData.put("totalAmount", booking.quote().fare().totalAmount());
        eventData.putAll(additionalMetadata);

        String metadata = writeValue(eventData);
        String beforeState = previousStatus == null ? null
                : writeValue(Map.of("status", previousStatus.name()));
        String afterState = metadata;
        Timestamp occurredAt = Timestamp.from(booking.updatedAt());

        jdbcTemplate.update("""
                INSERT INTO booking_events
                (id, booking_id, event_type, from_status, to_status, actor_user_id, metadata, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                """,
                UUID.randomUUID(), booking.id(), eventType,
                previousStatus == null ? null : previousStatus.name(),
                booking.status().name(), actorId, metadata, occurredAt);

        jdbcTemplate.update("""
                INSERT INTO audit_logs
                (id, actor_user_id, action, aggregate_type, aggregate_id, before_state, after_state, occurred_at)
                VALUES (?, ?, ?, 'BOOKING', ?, CAST(? AS JSONB), CAST(? AS JSONB), ?)
                """,
                UUID.randomUUID(), actorId, eventType, booking.id().toString(), beforeState, afterState, occurredAt);

        jdbcTemplate.update("""
                INSERT INTO outbox_events
                (id, aggregate_type, aggregate_id, event_type, payload, event_status, occurred_at, next_attempt_at)
                VALUES (?, 'BOOKING', ?, ?, CAST(? AS JSONB), 'PENDING', ?, ?)
                """,
                UUID.randomUUID(), booking.id().toString(), eventType, metadata, occurredAt, occurredAt);
    }

    private Optional<BookingResponse> findOne(String sql, Object... args) {
        List<BookingResponse> results = jdbcTemplate.query(
                sql, (rs, rowNum) -> readSnapshot(rs.getString(1)), args);
        return results.stream().findFirst();
    }

    private String writeSnapshot(BookingResponse booking) {
        return writeValue(booking);
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize the booking snapshot.", exception);
        }
    }

    private BookingResponse readSnapshot(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, BookingResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read the stored booking snapshot.", exception);
        }
    }

    public record AllocationRow(
            UUID id,
            UUID bookingId,
            UUID vehicleId,
            String vehicleNumber,
            Instant reservedFromAt,
            Instant reservedUntilAt
    ) {
    }

    public record AvailableVehicle(UUID id, String vehicleNumber, String operatingCity) {
    }
}
