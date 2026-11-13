package com.suabhi.cabbooking.pricing.service;

import com.suabhi.cabbooking.common.exception.ResourceNotFoundException;
import com.suabhi.cabbooking.journey.api.JourneyPreviewResponse;
import com.suabhi.cabbooking.journey.service.JourneyPreviewService;
import com.suabhi.cabbooking.pricing.api.FareQuoteRequest;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse;
import com.suabhi.cabbooking.pricing.domain.FareCalculationInput;
import com.suabhi.cabbooking.pricing.domain.FareCalculationResult;
import com.suabhi.cabbooking.pricing.domain.PricingRateCard;
import com.suabhi.cabbooking.pricing.repository.FareQuoteRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FareQuoteService {
    private static final Duration QUOTE_VALIDITY = Duration.ofMinutes(15);

    private final JourneyPreviewService journeyPreviewService;
    private final RateCardService rateCardService;
    private final PricingEngine pricingEngine;
    private final FareQuoteRepository fareQuoteRepository;
    private final Clock clock;

    @Autowired
    public FareQuoteService(JourneyPreviewService journeyPreviewService,
                            RateCardService rateCardService,
                            PricingEngine pricingEngine,
                            FareQuoteRepository fareQuoteRepository) {
        this(journeyPreviewService, rateCardService, pricingEngine, fareQuoteRepository, Clock.systemUTC());
    }

    FareQuoteService(JourneyPreviewService journeyPreviewService,
                     RateCardService rateCardService,
                     PricingEngine pricingEngine,
                     FareQuoteRepository fareQuoteRepository,
                     Clock clock) {
        this.journeyPreviewService = journeyPreviewService;
        this.rateCardService = rateCardService;
        this.pricingEngine = pricingEngine;
        this.fareQuoteRepository = fareQuoteRepository;
        this.clock = clock;
    }

    public FareQuoteResponse create(UUID customerId, FareQuoteRequest request) {
        if (customerId == null) {
            throw new IllegalArgumentException("An authenticated customer is required to create a fare quote.");
        }

        // Route metrics and resolved place coordinates are obtained only from the server-side journey provider.
        JourneyPreviewResponse journey = journeyPreviewService.preview(request.journey());

        // Tariff effective dates are evaluated against the scheduled departure, not client-supplied prices.
        PricingRateCard rateCard = rateCardService.findEffectiveActive(
                journey.serviceType(),
                com.suabhi.cabbooking.journey.provider.City.valueOf(journey.originCity()),
                request.vehicleCategoryCode(),
                journey.departureAt());

        FareCalculationInput input = new FareCalculationInput(
                journey.serviceType(),
                com.suabhi.cabbooking.journey.provider.City.valueOf(journey.originCity()),
                journey.distanceMeters(),
                journey.durationSeconds(),
                request.rentalHours());
        FareCalculationResult fare = pricingEngine.calculate(rateCard, input);

        Instant createdAt = clock.instant();
        FareQuoteResponse response = new FareQuoteResponse(
                UUID.randomUUID(),
                createdAt,
                createdAt.plus(QUOTE_VALIDITY),
                journey,
                FareQuoteResponse.RateCardSnapshot.from(rateCard),
                fare);

        fareQuoteRepository.save(customerId, rateCard, response);
        return response;
    }

    @Transactional(readOnly = true)
    public FareQuoteResponse findOwnedQuote(UUID customerId, UUID quoteId) {
        return fareQuoteRepository.findByIdAndCustomerId(quoteId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Fare quote was not found."));
    }
}
