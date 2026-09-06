package org.tavoo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tavoo.dto.CreateLocationRequest;
import org.tavoo.dto.LocationResponse;
import org.tavoo.entity.Location;
import org.tavoo.exception.BusinessRuleException;
import org.tavoo.repository.LocationRepository;

import java.util.List;

@Service
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> getLocations() {
        return locationRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LocationResponse createLocation(CreateLocationRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessRuleException("Location name is required");
        }
        if (request.type() == null) {
            throw new BusinessRuleException("Location type is required");
        }

        String normalizedName = request.name().trim();
        if (locationRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new BusinessRuleException("Location named '" + normalizedName + "' already exists");
        }

        return toResponse(locationRepository.save(new Location(normalizedName, request.type())));
    }

    private LocationResponse toResponse(Location location) {
        return new LocationResponse(location.getId(), location.getName(), location.getType());
    }
}
