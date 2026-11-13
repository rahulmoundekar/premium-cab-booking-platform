package com.suabhi.cabbooking.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.suabhi.cabbooking.fleet.FleetCategoryCatalog;
import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.pricing.domain.PricingRateCard;
import com.suabhi.cabbooking.pricing.repository.PricingRateCardRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RateCardServiceTest {
    private PricingRateCardRepository rateCards;
    private FleetCategoryCatalog fleetCategoryCatalog;
    private RateCardService service;

    @BeforeEach
    void setUp() {
        rateCards = mock(PricingRateCardRepository.class);
        fleetCategoryCatalog = mock(FleetCategoryCatalog.class);
        service = new RateCardService(
                rateCards,
                fleetCategoryCatalog,
                Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void activatesRateCardWhenThereIsNoOverlappingActiveVersion() {
        UUID id = UUID.randomUUID();
        PricingRateCard inactive = rateCard(id, false, null);
        PricingRateCard active = rateCard(id, true, null);
        when(rateCards.findById(id)).thenReturn(Optional.of(inactive), Optional.of(active));
        when(rateCards.hasOverlappingActiveRateCard(inactive)).thenReturn(false);

        PricingRateCard result = service.activate(id);

        assertThat(result.active()).isTrue();
        verify(rateCards).setActive(id, true);
    }

    @Test
    void rejectsActivationWhenAnActiveEffectivePeriodOverlaps() {
        UUID id = UUID.randomUUID();
        PricingRateCard card = rateCard(id, false, null);
        when(rateCards.findById(id)).thenReturn(Optional.of(card));
        when(rateCards.hasOverlappingActiveRateCard(card)).thenReturn(true);

        assertThatThrownBy(() -> service.activate(id))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(rateCards, never()).setActive(id, true);
    }

    @Test
    void rejectsExpiredRateCardActivation() {
        UUID id = UUID.randomUUID();
        PricingRateCard expired = rateCard(id, false, Instant.parse("2026-11-01T00:00:00Z"));
        when(rateCards.findById(id)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.activate(id))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(rateCards, never()).setActive(id, true);
    }

    @Test
    void findsActiveRateCardEffectiveAtScheduledDeparture() {
        UUID categoryId = UUID.randomUUID();
        Instant departure = Instant.parse("2026-11-14T05:00:00Z");
        PricingRateCard card = rateCard(UUID.randomUUID(), true, null);
        when(fleetCategoryCatalog.findActiveCategory("EXECUTIVE_SEDAN"))
                .thenReturn(Optional.of(new FleetCategoryCatalog.CategoryReference(categoryId, "EXECUTIVE_SEDAN")));
        when(rateCards.findEffectiveActive(ServiceType.AIRPORT_TRANSFER, City.PUNE, categoryId, departure))
                .thenReturn(Optional.of(card));

        PricingRateCard result = service.findEffectiveActive(
                ServiceType.AIRPORT_TRANSFER, City.PUNE, " executive_sedan ", departure);

        assertThat(result).isSameAs(card);
        verify(rateCards).findEffectiveActive(ServiceType.AIRPORT_TRANSFER, City.PUNE, categoryId, departure);
    }

    @Test
    void rejectsQuoteWhenNoEffectiveActiveRateCardExists() {
        UUID categoryId = UUID.randomUUID();
        Instant departure = Instant.parse("2026-11-14T05:00:00Z");
        when(fleetCategoryCatalog.findActiveCategory("EXECUTIVE_SEDAN"))
                .thenReturn(Optional.of(new FleetCategoryCatalog.CategoryReference(categoryId, "EXECUTIVE_SEDAN")));
        when(rateCards.findEffectiveActive(ServiceType.AIRPORT_TRANSFER, City.PUNE, categoryId, departure))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findEffectiveActive(
                ServiceType.AIRPORT_TRANSFER, City.PUNE, "EXECUTIVE_SEDAN", departure))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private static PricingRateCard rateCard(UUID id, boolean active, Instant effectiveUntil) {
        return new PricingRateCard(
                id, "PUNE_EXECUTIVE_AIRPORT", 1, ServiceType.AIRPORT_TRANSFER, City.PUNE,
                UUID.randomUUID(), "EXECUTIVE_SEDAN", "INR",
                new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.000"),
                new BigDecimal("0.00"), new BigDecimal("0.00"),
                Instant.parse("2026-10-01T00:00:00Z"), effectiveUntil, active, List.of());
    }
}
