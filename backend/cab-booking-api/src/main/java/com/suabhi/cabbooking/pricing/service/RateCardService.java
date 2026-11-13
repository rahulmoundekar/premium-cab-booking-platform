package com.suabhi.cabbooking.pricing.service;

import com.suabhi.cabbooking.common.exception.ResourceNotFoundException;
import com.suabhi.cabbooking.fleet.FleetCategoryCatalog;
import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.pricing.api.RateCardWriteRequest;
import com.suabhi.cabbooking.pricing.domain.PricingCalculationType;
import com.suabhi.cabbooking.pricing.domain.PricingComponent;
import com.suabhi.cabbooking.pricing.domain.PricingComponentType;
import com.suabhi.cabbooking.pricing.domain.PricingRateCard;
import com.suabhi.cabbooking.pricing.repository.PricingRateCardRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RateCardService {
    private final PricingRateCardRepository rateCardRepository;
    private final FleetCategoryCatalog fleetCategoryCatalog;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public RateCardService(PricingRateCardRepository rateCardRepository,
                           FleetCategoryCatalog fleetCategoryCatalog) {
        this(rateCardRepository, fleetCategoryCatalog, Clock.systemUTC());
    }

    RateCardService(PricingRateCardRepository rateCardRepository,
                    FleetCategoryCatalog fleetCategoryCatalog, Clock clock) {
        this.rateCardRepository = rateCardRepository;
        this.fleetCategoryCatalog = fleetCategoryCatalog;
        this.clock = clock;
    }

    public PricingRateCard create(RateCardWriteRequest request) {
        String categoryCode = request.vehicleCategoryCode().strip().toUpperCase(Locale.ROOT);
        FleetCategoryCatalog.CategoryReference category = fleetCategoryCatalog.findActiveCategory(categoryCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active vehicle category not found: " + categoryCode));

        List<PricingComponent> components = request.components().stream()
                .map(component -> new PricingComponent(
                        component.code().strip().toUpperCase(Locale.ROOT),
                        component.displayName().strip(),
                        component.componentType(),
                        component.calculationType(),
                        component.amount(),
                        component.taxable(),
                        component.displayOrder()))
                .toList();

        PricingRateCard card = new PricingRateCard(
                null,
                request.code().strip().toUpperCase(Locale.ROOT),
                request.version(),
                request.serviceType(),
                request.operatingCity(),
                category.id(),
                category.code(),
                request.currency().strip().toUpperCase(Locale.ROOT),
                request.baseFare(),
                request.minimumFare(),
                request.perKilometerRate(),
                request.perHourRate(),
                request.perDayRate(),
                request.includedKilometers(),
                request.includedHours(),
                request.driverAllowancePerDay(),
                request.effectiveFrom().toInstant(),
                request.effectiveUntil() == null ? null : request.effectiveUntil().toInstant(),
                false,
                components
        );

        try {
            UUID id = rateCardRepository.create(card);
            return rateCardRepository.findById(id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A rate card with this code/version or component code already exists.", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<PricingRateCard> findAll() {
        return rateCardRepository.findAll();
    }

    public PricingRateCard activate(UUID id) {
        PricingRateCard card = rateCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rate card was not found."));
        Instant now = clock.instant();
        if (card.effectiveUntil() != null && !now.isBefore(card.effectiveUntil())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An expired rate card cannot be activated.");
        }
        if (rateCardRepository.hasOverlappingActiveRateCard(card)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another active rate card overlaps this service, city, vehicle category and effective period.");
        }
        try {
            rateCardRepository.setActive(id, true);
        } catch (DataIntegrityViolationException exception) {
            // The database exclusion constraint is the final guard against concurrent activation races.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another active rate card overlaps this service, city, vehicle category and effective period.",
                    exception);
        }
        return rateCardRepository.findById(id).orElseThrow();
    }

    public PricingRateCard deactivate(UUID id) {
        rateCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rate card was not found."));
        rateCardRepository.setActive(id, false);
        return rateCardRepository.findById(id).orElseThrow();
    }
}
