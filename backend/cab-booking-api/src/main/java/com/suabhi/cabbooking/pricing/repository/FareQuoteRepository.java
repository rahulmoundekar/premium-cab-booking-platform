package com.suabhi.cabbooking.pricing.repository;

import com.suabhi.cabbooking.pricing.api.FareQuoteResponse;
import com.suabhi.cabbooking.pricing.domain.PricingRateCard;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FareQuoteRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FareQuoteRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(UUID customerId, PricingRateCard rateCard, FareQuoteResponse response) {
        String snapshot = writeSnapshot(response);
        jdbcTemplate.update("""
                INSERT INTO fare_quotes
                (id, customer_id, rate_card_id, rate_card_code, rate_card_version,
                 service_type, operating_city, vehicle_category_id, vehicle_category_code,
                 departure_at, currency, distance_meters, duration_seconds, total_amount,
                 created_at, expires_at, response_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
                """,
                response.id(), customerId, rateCard.id(), rateCard.code(), rateCard.version(),
                rateCard.serviceType().name(), rateCard.operatingCity().name(),
                rateCard.vehicleCategoryId(), rateCard.vehicleCategoryCode(),
                Timestamp.from(response.journey().departureAt()), rateCard.currency(),
                response.journey().distanceMeters(), response.journey().durationSeconds(),
                response.fare().totalAmount(), Timestamp.from(response.createdAt()),
                Timestamp.from(response.expiresAt()), snapshot);
    }

    public Optional<FareQuoteResponse> findByIdAndCustomerId(UUID quoteId, UUID customerId) {
        List<FareQuoteResponse> quotes = jdbcTemplate.query("""
                SELECT response_snapshot::text
                FROM fare_quotes
                WHERE id = ? AND customer_id = ?
                """, (rs, rowNum) -> readSnapshot(rs.getString(1)), quoteId, customerId);
        return quotes.stream().findFirst();
    }

    private String writeSnapshot(FareQuoteResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize the fare quote snapshot.", exception);
        }
    }

    private FareQuoteResponse readSnapshot(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, FareQuoteResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read the stored fare quote snapshot.", exception);
        }
    }
}
