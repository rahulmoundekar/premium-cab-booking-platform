package com.suabhi.cabbooking.pricing.repository;

import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.pricing.domain.PricingCalculationType;
import com.suabhi.cabbooking.pricing.domain.PricingComponent;
import com.suabhi.cabbooking.pricing.domain.PricingComponentType;
import com.suabhi.cabbooking.pricing.domain.PricingRateCard;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PricingRateCardRepository {
    private static final String SELECT_RATE_CARD = """
            SELECT rc.id, rc.code, rc.version, rc.service_type, rc.operating_city,
                   rc.vehicle_category_id, vc.code AS vehicle_category_code, rc.currency,
                   rc.base_fare, rc.minimum_fare, rc.per_km_rate, rc.per_hour_rate,
                   rc.per_day_rate, rc.included_kilometers, rc.included_hours,
                   rc.driver_allowance_per_day, rc.effective_from, rc.effective_until, rc.active
            FROM pricing_rate_cards rc
            JOIN vehicle_categories vc ON vc.id = rc.vehicle_category_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public PricingRateCardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID create(PricingRateCard card) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO pricing_rate_cards
                (id, code, version, service_type, operating_city, vehicle_category_id, currency,
                 base_fare, minimum_fare, per_km_rate, per_hour_rate, per_day_rate,
                 included_kilometers, included_hours, driver_allowance_per_day,
                 effective_from, effective_until, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, now(), now())
                """,
                id, card.code(), card.version(), card.serviceType().name(), card.operatingCity().name(),
                card.vehicleCategoryId(), card.currency(), card.baseFare(), card.minimumFare(),
                card.perKilometerRate(), card.perHourRate(), card.perDayRate(),
                card.includedKilometers(), card.includedHours(), card.driverAllowancePerDay(),
                Timestamp.from(card.effectiveFrom()),
                card.effectiveUntil() == null ? null : Timestamp.from(card.effectiveUntil()));

        for (PricingComponent component : card.components()) {
            jdbcTemplate.update("""
                    INSERT INTO pricing_rate_card_components
                    (id, rate_card_id, component_code, display_name, component_type,
                     calculation_type, amount, taxable, display_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(), id, component.code(), component.displayName(),
                    component.componentType().name(), component.calculationType().name(),
                    component.amount(), component.taxable(), component.displayOrder());
        }
        return id;
    }

    public List<PricingRateCard> findAll() {
        return jdbcTemplate.query(SELECT_RATE_CARD + " ORDER BY rc.created_at DESC, rc.code, rc.version",
                (rs, rowNum) -> mapRateCard(rs)).stream()
                .map(this::withComponents)
                .toList();
    }

    public Optional<PricingRateCard> findById(UUID id) {
        List<PricingRateCard> cards = jdbcTemplate.query(
                SELECT_RATE_CARD + " WHERE rc.id = ?",
                (rs, rowNum) -> mapRateCard(rs), id);
        return cards.stream().findFirst().map(this::withComponents);
    }

    public Optional<PricingRateCard> findEffectiveActive(
            ServiceType serviceType, City city, UUID vehicleCategoryId, Instant effectiveAt) {
        List<PricingRateCard> cards = jdbcTemplate.query(
                SELECT_RATE_CARD + """
                 WHERE rc.service_type = ?
                   AND rc.operating_city = ?
                   AND rc.vehicle_category_id = ?
                   AND rc.active = TRUE
                   AND rc.effective_from <= ?
                   AND (rc.effective_until IS NULL OR rc.effective_until > ?)
                 ORDER BY rc.effective_from DESC, rc.version DESC
                 LIMIT 1
                """,
                (rs, rowNum) -> mapRateCard(rs),
                serviceType.name(), city.name(), vehicleCategoryId,
                Timestamp.from(effectiveAt), Timestamp.from(effectiveAt));
        return cards.stream().findFirst().map(this::withComponents);
    }

    public boolean hasOverlappingActiveRateCard(PricingRateCard card) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pricing_rate_cards
                    WHERE active = TRUE
                      AND service_type = ?
                      AND operating_city = ?
                      AND vehicle_category_id = ?
                      AND id <> ?
                      AND effective_from < COALESCE(CAST(? AS TIMESTAMPTZ), 'infinity'::timestamptz)
                      AND COALESCE(effective_until, 'infinity'::timestamptz) > ?
                )
                """, Boolean.class,
                card.serviceType().name(), card.operatingCity().name(), card.vehicleCategoryId(), card.id(),
                card.effectiveUntil() == null ? null : Timestamp.from(card.effectiveUntil()),
                Timestamp.from(card.effectiveFrom()));
        return Boolean.TRUE.equals(exists);
    }

    public void setActive(UUID id, boolean active) {
        jdbcTemplate.update("UPDATE pricing_rate_cards SET active = ?, updated_at = now() WHERE id = ?",
                active, id);
    }

    private PricingRateCard withComponents(PricingRateCard card) {
        List<PricingComponent> components = jdbcTemplate.query("""
                SELECT component_code, display_name, component_type, calculation_type,
                       amount, taxable, display_order
                FROM pricing_rate_card_components
                WHERE rate_card_id = ?
                ORDER BY display_order, component_code
                """, (rs, rowNum) -> new PricingComponent(
                rs.getString("component_code"),
                rs.getString("display_name"),
                PricingComponentType.valueOf(rs.getString("component_type")),
                PricingCalculationType.valueOf(rs.getString("calculation_type")),
                rs.getBigDecimal("amount"),
                rs.getBoolean("taxable"),
                rs.getInt("display_order")), card.id());
        return new PricingRateCard(
                card.id(), card.code(), card.version(), card.serviceType(), card.operatingCity(),
                card.vehicleCategoryId(), card.vehicleCategoryCode(), card.currency(),
                card.baseFare(), card.minimumFare(), card.perKilometerRate(), card.perHourRate(),
                card.perDayRate(), card.includedKilometers(), card.includedHours(),
                card.driverAllowancePerDay(), card.effectiveFrom(), card.effectiveUntil(),
                card.active(), components);
    }

    private static PricingRateCard mapRateCard(ResultSet rs) throws SQLException {
        Timestamp effectiveFrom = rs.getTimestamp("effective_from");
        Timestamp effectiveUntil = rs.getTimestamp("effective_until");
        return new PricingRateCard(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getInt("version"),
                ServiceType.valueOf(rs.getString("service_type")),
                City.valueOf(rs.getString("operating_city")),
                rs.getObject("vehicle_category_id", UUID.class),
                rs.getString("vehicle_category_code"),
                rs.getString("currency").trim(),
                rs.getBigDecimal("base_fare"),
                rs.getBigDecimal("minimum_fare"),
                rs.getBigDecimal("per_km_rate"),
                rs.getBigDecimal("per_hour_rate"),
                rs.getBigDecimal("per_day_rate"),
                rs.getBigDecimal("included_kilometers"),
                rs.getBigDecimal("included_hours"),
                rs.getBigDecimal("driver_allowance_per_day"),
                effectiveFrom == null ? null : effectiveFrom.toInstant(),
                effectiveUntil == null ? null : effectiveUntil.toInstant(),
                rs.getBoolean("active"),
                List.of());
    }
}
