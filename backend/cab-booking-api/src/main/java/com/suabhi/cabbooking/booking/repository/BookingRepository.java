package com.suabhi.cabbooking.booking.repository;

import com.suabhi.cabbooking.booking.api.BookingResponse;
import com.suabhi.cabbooking.booking.domain.BookingStatus;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse;
import java.sql.Timestamp;
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

    public void recordCreated(UUID actorId, BookingResponse booking) {
        recordEvent(actorId, booking, "BOOKING_CREATED", null);
    }

    public void recordCancelled(UUID actorId, BookingResponse booking, BookingStatus previousStatus) {
        recordEvent(actorId, booking, "BOOKING_CANCELLED", previousStatus);
    }

    private void recordEvent(UUID actorId, BookingResponse booking, String eventType,
                             BookingStatus previousStatus) {
        String metadata = writeValue(Map.of(
                "bookingReference", booking.bookingReference(),
                "quoteId", booking.quote().id().toString()));
        String snapshot = writeSnapshot(booking);
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
                (id, actor_user_id, action, aggregate_type, aggregate_id, after_state, occurred_at)
                VALUES (?, ?, ?, 'BOOKING', ?, CAST(? AS JSONB), ?)
                """,
                UUID.randomUUID(), actorId, eventType, booking.id().toString(), snapshot, occurredAt);

        jdbcTemplate.update("""
                INSERT INTO outbox_events
                (id, aggregate_type, aggregate_id, event_type, payload, event_status, occurred_at, next_attempt_at)
                VALUES (?, 'BOOKING', ?, ?, CAST(? AS JSONB), 'PENDING', ?, ?)
                """,
                UUID.randomUUID(), booking.id().toString(), eventType, snapshot, occurredAt, occurredAt);
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
}
