package com.suabhi.cabbooking.journey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.suabhi.cabbooking.journey.api.JourneyPreviewRequest;
import com.suabhi.cabbooking.journey.api.JourneyPreviewResponse;
import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.GeocodingProvider;
import com.suabhi.cabbooking.journey.provider.RouteProvider;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JourneyPreviewServiceTest {
    private GeocodingProvider geocodingProvider;
    private RouteProvider routeProvider;
    private JourneyPreviewService service;

    @BeforeEach
    void setUp() {
        geocodingProvider = mock(GeocodingProvider.class);
        routeProvider = mock(RouteProvider.class);
        service = new JourneyPreviewService(geocodingProvider, routeProvider);
    }

    @Test
    void resolvesPickupStopsAndDropoffBeforeRouting() {
        JourneyPreviewRequest.PlaceRequest pickup =
                new JourneyPreviewRequest.PlaceRequest("Pune Airport", "pickup-ref", null, null);
        JourneyPreviewRequest.PlaceRequest stop =
                new JourneyPreviewRequest.PlaceRequest("Shivajinagar, Pune", "stop-ref", null, null);
        JourneyPreviewRequest.PlaceRequest dropOff =
                new JourneyPreviewRequest.PlaceRequest("Koregaon Park, Pune", "drop-ref", null, null);

        GeocodingProvider.ResolvedPlace resolvedPickup =
                new GeocodingProvider.ResolvedPlace("Pune Airport", "pickup-ref", 18.5793, 73.9089);
        GeocodingProvider.ResolvedPlace resolvedStop =
                new GeocodingProvider.ResolvedPlace("Shivajinagar, Pune", "stop-ref", 18.5308, 73.8475);
        GeocodingProvider.ResolvedPlace resolvedDropOff =
                new GeocodingProvider.ResolvedPlace("Koregaon Park, Pune", "drop-ref", 18.5362, 73.8939);

        when(geocodingProvider.resolve(any())).thenReturn(
                resolvedPickup, resolvedStop, resolvedDropOff);
        when(routeProvider.calculate(any())).thenReturn(
                new RouteProvider.RouteResult(12_400, 2_100, null, "route-ref"));

        JourneyPreviewRequest request = new JourneyPreviewRequest(
                ServiceType.LOCAL_RENTAL,
                City.PUNE,
                OffsetDateTime.now().plusDays(1),
                pickup,
                dropOff,
                List.of(stop)
        );

        JourneyPreviewResponse response = service.preview(request);

        assertThat(response.locations()).hasSize(3);
        assertThat(response.locations().getFirst().formattedAddress()).isEqualTo("Pune Airport");
        assertThat(response.locations().get(1).formattedAddress()).isEqualTo("Shivajinagar, Pune");
        assertThat(response.locations().getLast().formattedAddress()).isEqualTo("Koregaon Park, Pune");
        assertThat(response.distanceMeters()).isEqualTo(12_400);
        assertThat(response.durationSeconds()).isEqualTo(2_100);

        ArgumentCaptor<RouteProvider.RouteRequest> captor =
                ArgumentCaptor.forClass(RouteProvider.RouteRequest.class);
        verify(routeProvider).calculate(captor.capture());
        assertThat(captor.getValue().locations()).containsExactly(
                resolvedPickup, resolvedStop, resolvedDropOff);
        assertThat(captor.getValue().originCity()).isEqualTo(City.PUNE);
        assertThat(captor.getValue().serviceType()).isEqualTo(ServiceType.LOCAL_RENTAL);
    }

    @Test
    void trimsPlaceSearchQueryBeforeCallingProvider() {
        when(geocodingProvider.search("Pune Airport", City.PUNE)).thenReturn(List.of());

        assertThat(service.searchPlaces("  Pune Airport  ", City.PUNE)).isEmpty();

        verify(geocodingProvider).search("Pune Airport", City.PUNE);
    }
}
