package org.tavoo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tavoo.dto.RestaurantSettingsResponse;
import org.tavoo.entity.RestaurantSettings;
import org.tavoo.repository.RestaurantSettingsRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantSettingsServiceTest {

    @Mock
    private RestaurantSettingsRepository repository;

    @Test
    void adminUpdatesPersistentCopertoPrice() {
        RestaurantSettings settings = new RestaurantSettings(new BigDecimal("2.50"));
        when(repository.findById(RestaurantSettings.SINGLETON_ID))
                .thenReturn(Optional.of(settings));
        when(repository.save(settings)).thenReturn(settings);
        RestaurantSettingsService service = new RestaurantSettingsService(repository);

        RestaurantSettingsResponse response = service.updateCopertoUnitPrice(
                new BigDecimal("3.00")
        );

        assertThat(response.copertoUnitPrice()).isEqualByComparingTo("3.00");
        verify(repository).save(settings);
    }
}
