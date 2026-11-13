package com.suabhi.cabbooking.journey.api;

import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.GeocodingProvider;
import com.suabhi.cabbooking.journey.service.JourneyPreviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/journeys")
public class JourneyController {
    private final JourneyPreviewService journeyPreviewService;

    public JourneyController(JourneyPreviewService journeyPreviewService) {
        this.journeyPreviewService = journeyPreviewService;
    }

    @GetMapping("/places")
    public List<GeocodingProvider.PlaceSuggestion> searchPlaces(
            @RequestParam String query,
            @RequestParam City city
    ) {
        return journeyPreviewService.searchPlaces(query, city);
    }

    @PostMapping("/preview")
    public ResponseEntity<JourneyPreviewResponse> preview(
            @Valid @RequestBody JourneyPreviewRequest request
    ) {
        return ResponseEntity.ok(journeyPreviewService.preview(request));
    }
}
