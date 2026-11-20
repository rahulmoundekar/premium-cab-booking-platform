package com.suabhi.cabbooking.fleet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.suabhi.cabbooking.fleet.repository.VehicleCategoryRepository;
import com.suabhi.cabbooking.fleet.repository.VehicleModelRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FleetCatalogServiceTest {
    private VehicleCategoryRepository categories;
    private VehicleModelRepository models;
    private JdbcTemplate jdbcTemplate;
    private FleetCatalogService service;

    @BeforeEach
    void setUp() {
        categories = mock(VehicleCategoryRepository.class);
        models = mock(VehicleModelRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new FleetCatalogService(categories, models, jdbcTemplate);
    }

    @Test
    void returnsEmptyListWhenThereAreNoActiveCategories() {
        when(categories.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of());
        assertThat(service.getActiveCategories()).isEmpty();
    }
}
