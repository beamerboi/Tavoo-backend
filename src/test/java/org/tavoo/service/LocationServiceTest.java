package org.tavoo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tavoo.dto.CreateLocationRequest;
import org.tavoo.entity.Location;
import org.tavoo.entity.LocationType;
import org.tavoo.exception.BusinessRuleException;
import org.tavoo.repository.LocationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private LocationService locationService;

    @Test
    void createsNamedLocation() {
        when(locationRepository.save(any(Location.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = locationService.createLocation(
                new CreateLocationRequest("  Terrace  ", LocationType.OUTSIDE)
        );

        assertThat(response.name()).isEqualTo("Terrace");
        assertThat(response.type()).isEqualTo(LocationType.OUTSIDE);
    }

    @Test
    void rejectsDuplicateLocationName() {
        when(locationRepository.existsByNameIgnoreCase("Main Hall")).thenReturn(true);

        var request = new CreateLocationRequest("Main Hall", LocationType.INSIDE);

        assertThatThrownBy(() -> locationService.createLocation(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");

        verify(locationRepository, never()).save(any(Location.class));
    }
}
