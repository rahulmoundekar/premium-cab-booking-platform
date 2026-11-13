package com.suabhi.cabbooking.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.suabhi.cabbooking.common.exception.ResourceNotFoundException;
import com.suabhi.cabbooking.journey.api.JourneyPreviewRequest;
import com.suabhi.cabbooking.journey.api.JourneyPreviewResponse;
import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.journey.service.JourneyPreviewService;
import com.suabhi.cabbooking.pricing.api.FareQuoteRequest;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse;
import com.suabhi.cabbooking.pricing.domain.PricingCalculationType;
import com.suabhi.cabbooking.pricing.domain.PricingComponent;
import com.suabhi.cabbooking.pricing.domain.PricingComponentType;
import com.suabhi.cabbooking.pricing.domain.PricingRateCard;
import com.suabhi.cabbooking.pricing.repository.FareQuoteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FareQuoteServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    private JourneyPreviewService journeyPreviewService;
    private RateCardService rateCardService;
    private FareQuoteRepository quoteRepository;
    private FareQuoteService service;

    @BeforeEach
    void setUp() {
        journeyPreviewService = mock(JourneyPreviewService.class);
        rateCardService = mock(RateCardService.class);
        quoteRepository = mock(FareQuoteRepository.class);
        service = new FareQuoteService(
                journeyPreviewService,
                rateCardService,
                new PricingEngine(),
                quoteRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAndPersistsQuoteFromServerCalculatedRouteAndEffectiveRateCard() {
        UUID customerId = UUID.randomUUID();
        PricingRateCard rateCard = activeAirportRateCard();
        JourneyPreviewRequest journeyRequest = journeyRequest();
        JourneyPreviewResponse route = new JourneyPreviewResponse(
                ServiceType.AIRPORT_TRANSFER,
                City.PUNE.name(),
                journeyRequest.departureAt().toInstant(),
                List.of(
                        new JourneyPreviewResponse.Place("Pune Airport", "pickup-ref", 18.5793, 73.9089),
                        new JourneyPreviewResponse.Place("Koregaon Park, Pune", "drop-ref", 18.5362, 73.8939)),
                10_000,
                1_800,
                "encoded-route",
                "server-route-reference");
        when(journeyPreviewService.preview(journeyRequest)).thenReturn(route);
        when(rateCardService.findEffectiveActive(
                ServiceType.AIRPORT_TRANSFER, City.PUNE, "EXECUTIVE_SEDAN",
                journeyRequest.departureAt().toInstant())).thenReturn(rateCard);

        FareQuoteResponse response = service.create(
                customerId, new FareQuoteRequest(journeyRequest, "EXECUTIVE_SEDAN", null));

        assertThat(response.id()).isNotNull();
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(response.rentalHours()).isNull();
        assertThat(response.journey().distanceMeters()).isEqualTo(10_000);
        assertThat(response.journey().durationSeconds()).isEqualTo(1_800);
        assertThat(response.rateCard().id()).isEqualTo(rateCard.id());
        assertThat(response.rateCard().version()).isEqualTo(2);
        assertThat(response.fare().totalAmount()).isEqualByComparingTo("220.50");
        assertThat(response.fare().surchargeTotal()).isEqualByComparingTo("10.00");
        assertThat(response.fare().taxTotal()).isEqualByComparingTo("10.50");

        verify(quoteRepository).save(customerId, rateCard, response);
    }

    @Test
    void snapshotsTheSelectedLocalRentalPackageDuration() {
        UUID customerId = UUID.randomUUID();
        PricingRateCard rateCard = localRentalRateCard();
        JourneyPreviewRequest journeyRequest = new JourneyPreviewRequest(
                ServiceType.LOCAL_RENTAL,
                City.PUNE,
                OffsetDateTime.parse("2026-11-14T10:00:00+05:30"),
                new JourneyPreviewRequest.PlaceRequest("Koregaon Park, Pune", "pickup-ref", null, null),
                new JourneyPreviewRequest.PlaceRequest("Hinjawadi, Pune", "drop-ref", null, null),
                List.of());
        JourneyPreviewResponse route = new JourneyPreviewResponse(
                ServiceType.LOCAL_RENTAL,
                City.PUNE.name(),
                journeyRequest.departureAt().toInstant(),
                List.of(
                        new JourneyPreviewResponse.Place("Koregaon Park, Pune", "pickup-ref", 18.5362, 73.8939),
                        new JourneyPreviewResponse.Place("Hinjawadi, Pune", "drop-ref", 18.5913, 73.7389)),
                10_000,
                1_800,
                null,
                "server-route-reference");
        when(journeyPreviewService.preview(journeyRequest)).thenReturn(route);
        when(rateCardService.findEffectiveActive(
                ServiceType.LOCAL_RENTAL, City.PUNE, "EXECUTIVE_SEDAN",
                journeyRequest.departureAt().toInstant())).thenReturn(rateCard);

        FareQuoteResponse response = service.create(
                customerId, new FareQuoteRequest(journeyRequest, "EXECUTIVE_SEDAN", 12));

        assertThat(response.rentalHours()).isEqualTo(12);
        assertThat(response.fare().usageCharge()).isEqualByComparingTo("400.00");
        assertThat(response.fare().totalAmount()).isEqualByComparingTo("700.00");
        verify(quoteRepository).save(customerId, rateCard, response);
    }

    @Test
    void hidesQuotesThatDoNotBelongToTheAuthenticatedCustomer() {
        UUID customerId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        when(quoteRepository.findByIdAndCustomerId(quoteId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOwnedQuote(customerId, quoteId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static JourneyPreviewRequest journeyRequest() {
        return new JourneyPreviewRequest(
                ServiceType.AIRPORT_TRANSFER,
                City.PUNE,
                OffsetDateTime.parse("2026-11-14T10:00:00+05:30"),
                new JourneyPreviewRequest.PlaceRequest("Pune Airport", "pickup-ref", null, null),
                new JourneyPreviewRequest.PlaceRequest("Koregaon Park, Pune", "drop-ref", null, null),
                List.of());
    }

    private static PricingRateCard localRentalRateCard() {
        return new PricingRateCard(
                UUID.randomUUID(),
                "TEST_PUNE_LOCAL_SEDAN",
                1,
                ServiceType.LOCAL_RENTAL,
                City.PUNE,
                UUID.randomUUID(),
                "EXECUTIVE_SEDAN",
                "INR",
                new BigDecimal("300.00"),
                new BigDecimal("300.00"),
                new BigDecimal("10.0000"),
                new BigDecimal("100.0000"),
                new BigDecimal("0.00"),
                new BigDecimal("10.000"),
                new BigDecimal("8.00"),
                new BigDecimal("0.00"),
                Instant.parse("2026-11-13T11:00:00Z"),
                null,
                true,
                List.of());
    }

    private static PricingRateCard activeAirportRateCard() {
        return new PricingRateCard(
                UUID.randomUUID(),
                "TEST_PUNE_AIRPORT_SEDAN",
                2,
                ServiceType.AIRPORT_TRANSFER,
                City.PUNE,
                UUID.randomUUID(),
                "EXECUTIVE_SEDAN",
                "INR",
                new BigDecimal("100.00"),
                new BigDecimal("150.00"),
                new BigDecimal("10.0000"),
                new BigDecimal("0.0000"),
                new BigDecimal("0.00"),
                new BigDecimal("0.000"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                Instant.parse("2026-11-13T11:00:00Z"),
                null,
                true,
                List.of(
                        new PricingComponent("TEST_SURCHARGE", "Test Fixed Surcharge",
                                PricingComponentType.SURCHARGE, PricingCalculationType.FIXED,
                                new BigDecimal("10.00"), true, 1),
                        new PricingComponent("TEST_TAX", "Test Tax",
                                PricingComponentType.TAX, PricingCalculationType.PERCENTAGE,
                                new BigDecimal("5.00"), false, 2)));
    }
}
