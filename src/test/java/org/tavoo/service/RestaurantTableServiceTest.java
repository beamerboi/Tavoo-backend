package org.tavoo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tavoo.dto.CreateRestaurantTableRequest;
import org.tavoo.entity.Location;
import org.tavoo.entity.LocationType;
import org.tavoo.entity.RestaurantTable;
import org.tavoo.entity.TableStatus;
import org.tavoo.exception.BusinessRuleException;
import org.tavoo.repository.LocationRepository;
import org.tavoo.repository.RestaurantTableRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantTableServiceTest {

    @Mock
    private RestaurantTableRepository restaurantTableRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private RestaurantTableService restaurantTableService;

    @Test
    void createsFreeTable() {
        when(locationRepository.findById(1L))
                .thenReturn(Optional.of(new Location("Terrace", LocationType.OUTSIDE)));
        when(restaurantTableRepository.save(any(RestaurantTable.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = restaurantTableService.createTable(new CreateRestaurantTableRequest(4, 6, 1L));

        assertThat(response.tableNumber()).isEqualTo(4);
        assertThat(response.seatCount()).isEqualTo(6);
        assertThat(response.location().name()).isEqualTo("Terrace");
        assertThat(response.location().type()).isEqualTo(LocationType.OUTSIDE);
        assertThat(response.status()).isEqualTo(TableStatus.FREE);
    }

    @Test
    void rejectsDuplicateTableNumber() {
        when(restaurantTableRepository.existsByTableNumber(4)).thenReturn(true);

        var request = new CreateRestaurantTableRequest(4, 6, 1L);

        assertThatThrownBy(() -> restaurantTableService.createTable(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");

        verify(restaurantTableRepository, never()).save(any(RestaurantTable.class));
    }
}
