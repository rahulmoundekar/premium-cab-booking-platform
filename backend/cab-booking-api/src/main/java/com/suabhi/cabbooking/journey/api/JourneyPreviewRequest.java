package com.suabhi.cabbooking.journey.api;

import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.GeocodingProvider;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

@org.springframework.modulith.NamedInterface("preview")
public record JourneyPreviewRequest(
        @NotNull ServiceType serviceType,
        @NotNull City originCity,
        @NotNull @Future OffsetDateTime departureAt,
        @NotNull @Valid PlaceRequest pickup,
        @NotNull @Valid PlaceRequest dropOff,
        @Size(max = 8) List<@NotNull @Valid PlaceRequest> stops
) {
    public JourneyPreviewRequest {
        stops = stops == null ? List.of() : List.copyOf(stops);
    }

    public record PlaceRequest(
            @NotBlank @Size(max = 500) String formattedAddress,
            @Size(max = 255) String providerReference,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
    ) {
        @AssertTrue(message = "Latitude and longitude must be provided together.")
        public boolean isCoordinatePairValid() {
            return (latitude == null) == (longitude == null);
        }

        public GeocodingProvider.PlaceInput toProviderInput() {
            return new GeocodingProvider.PlaceInput(
                    formattedAddress, providerReference, latitude, longitude);
        }
    }
}
