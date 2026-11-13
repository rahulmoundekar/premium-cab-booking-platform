package com.suabhi.cabbooking.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.pricing.domain.FareCalculationInput;
import com.suabhi.cabbooking.pricing.domain.FareCalculationResult;
import com.suabhi.cabbooking.pricing.domain.PricingCalculationType;
import com.suabhi.cabbooking.pricing.domain.PricingComponent;
import com.suabhi.cabbooking.pricing.domain.PricingComponentType;
import com.suabhi.cabbooking.pricing.domain.PricingRateCard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingEngineTest {
    private final PricingEngine engine = new PricingEngine();

    @Test
    void calculatesAirportTransferAndAppliesTaxToTaxableSubtotal() {
        PricingRateCard card = card(ServiceType.AIRPORT_TRANSFER,
                "100.00", "200.00", "10.00", "0", "0", "0", "0", "0",
                List.of(
                        component("AIRPORT_SURCHARGE", PricingComponentType.SURCHARGE,
                                PricingCalculationType.PERCENTAGE, "5", true, 10),
                        component("GST", PricingComponentType.TAX,
                                PricingCalculationType.PERCENTAGE, "5", false, 20)));

        FareCalculationResult result = engine.calculate(card,
                new FareCalculationInput(ServiceType.AIRPORT_TRANSFER, City.PUNE, 15_000, 1_800, null));

        assertThat(result.distanceKilometers()).isEqualByComparingTo("15.000");
        assertThat(result.distanceCharge()).isEqualByComparingTo("150.00");
        assertThat(result.surchargeTotal()).isEqualByComparingTo("12.50");
        assertThat(result.taxTotal()).isEqualByComparingTo("13.13");
        assertThat(result.totalAmount()).isEqualByComparingTo("275.63");
    }

    @Test
    void appliesMinimumFareWhenCoreChargesAreLowerThanMinimum() {
        PricingRateCard card = card(ServiceType.AIRPORT_TRANSFER,
                "100.00", "500.00", "2.00", "0", "0", "0", "0", "0", List.of());

        FareCalculationResult result = engine.calculate(card,
                new FareCalculationInput(ServiceType.AIRPORT_TRANSFER, City.PUNE, 10_000, 900, null));

        assertThat(result.minimumFareAdjustment()).isEqualByComparingTo("380.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void calculatesOutstationPerDayAndDriverAllowanceUsingCeilingDays() {
        PricingRateCard card = card(ServiceType.OUTSTATION,
                "1000.00", "0.00", "12.00", "0", "500.00", "100.000", "0", "400.00", List.of());

        FareCalculationResult result = engine.calculate(card,
                new FareCalculationInput(ServiceType.OUTSTATION, City.PUNE, 250_000, 97_200, null));

        assertThat(result.billableKilometers()).isEqualByComparingTo("150.000");
        assertThat(result.perDayCharge()).isEqualByComparingTo("1000.00");
        assertThat(result.driverAllowance()).isEqualByComparingTo("800.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("4600.00");
    }

    @Test
    void calculatesLocalRentalHoursBeyondIncludedPackageHours() {
        PricingRateCard card = card(ServiceType.LOCAL_RENTAL,
                "500.00", "0.00", "10.00", "200.00", "0", "20.000", "4.00", "0", List.of());

        FareCalculationResult result = engine.calculate(card,
                new FareCalculationInput(ServiceType.LOCAL_RENTAL, City.MUMBAI, 45_000, 7_200, 8));

        assertThat(result.billableKilometers()).isEqualByComparingTo("25.000");
        assertThat(result.distanceCharge()).isEqualByComparingTo("250.00");
        assertThat(result.usageCharge()).isEqualByComparingTo("800.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("1550.00");
    }

    @Test
    void rejectsRateCardForDifferentServiceOrCity() {
        PricingRateCard card = card(ServiceType.AIRPORT_TRANSFER,
                "100.00", "100.00", "10.00", "0", "0", "0", "0", "0", List.of());

        assertThatThrownBy(() -> engine.calculate(card,
                new FareCalculationInput(ServiceType.OUTSTATION, City.PUNE, 10_000, 900, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsInactiveRateCard() {
        PricingRateCard active = card(ServiceType.AIRPORT_TRANSFER,
                "100.00", "100.00", "10.00", "0", "0", "0", "0", "0", List.of());
        PricingRateCard inactive = new PricingRateCard(active.id(), active.code(), active.version(),
                active.serviceType(), active.operatingCity(), active.vehicleCategoryId(),
                active.vehicleCategoryCode(), active.currency(), active.baseFare(), active.minimumFare(),
                active.perKilometerRate(), active.perHourRate(), active.perDayRate(),
                active.includedKilometers(), active.includedHours(), active.driverAllowancePerDay(),
                active.effectiveFrom(), active.effectiveUntil(), false, active.components());

        assertThatThrownBy(() -> engine.calculate(inactive,
                new FareCalculationInput(ServiceType.AIRPORT_TRANSFER, City.PUNE, 10_000, 900, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }

    private static PricingComponent component(String code, PricingComponentType type,
                                               PricingCalculationType calculationType, String amount,
                                               boolean taxable, int order) {
        return new PricingComponent(code, code, type, calculationType,
                new BigDecimal(amount), taxable, order);
    }

    private static PricingRateCard card(ServiceType serviceType, String base, String minimum,
                                        String perKm, String perHour, String perDay,
                                        String includedKm, String includedHours, String driverAllowance,
                                        List<PricingComponent> components) {
        return new PricingRateCard(
                java.util.UUID.randomUUID(), "TEST_RATE_CARD", 1, serviceType, City.PUNE,
                java.util.UUID.randomUUID(), "EXECUTIVE_SEDAN", "INR",
                new BigDecimal(base), new BigDecimal(minimum), new BigDecimal(perKm),
                new BigDecimal(perHour), new BigDecimal(perDay), new BigDecimal(includedKm),
                new BigDecimal(includedHours), new BigDecimal(driverAllowance),
                Instant.parse("2026-01-01T00:00:00Z"), null, true, components);
    }
}
