package com.suabhi.cabbooking.pricing.api;

import com.suabhi.cabbooking.pricing.domain.PricingRateCard;
import com.suabhi.cabbooking.pricing.service.RateCardService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/pricing/rate-cards")
public class RateCardAdminController {
    private final RateCardService rateCardService;

    public RateCardAdminController(RateCardService rateCardService) {
        this.rateCardService = rateCardService;
    }

    @GetMapping
    public List<PricingRateCard> list() {
        return rateCardService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PricingRateCard create(@Valid @RequestBody RateCardWriteRequest request) {
        return rateCardService.create(request);
    }

    @PostMapping("/{id}/activate")
    public PricingRateCard activate(@PathVariable UUID id) {
        return rateCardService.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    public PricingRateCard deactivate(@PathVariable UUID id) {
        return rateCardService.deactivate(id);
    }
}
