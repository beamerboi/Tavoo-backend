package org.tavoo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tavoo.dto.CreateRestaurantTableRequest;
import org.tavoo.dto.LocationResponse;
import org.tavoo.dto.RestaurantTableResponse;
import org.tavoo.entity.Location;
import org.tavoo.entity.RestaurantTable;
import org.tavoo.exception.BusinessRuleException;
import org.tavoo.exception.ResourceNotFoundException;
import org.tavoo.repository.LocationRepository;
import org.tavoo.repository.RestaurantTableRepository;

import java.util.List;

@Service
public class RestaurantTableService {

    private final RestaurantTableRepository restaurantTableRepository;
    private final LocationRepository locationRepository;

    public RestaurantTableService(
            RestaurantTableRepository restaurantTableRepository,
            LocationRepository locationRepository
    ) {
        this.restaurantTableRepository = restaurantTableRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public List<RestaurantTableResponse> getTables() {
        return restaurantTableRepository.findAllByOrderByTableNumberAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RestaurantTableResponse createTable(CreateRestaurantTableRequest request) {
        if (request.tableNumber() == null || request.tableNumber() <= 0) {
            throw new BusinessRuleException("Table number must be positive");
        }
        if (request.seatCount() == null || request.seatCount() <= 0) {
            throw new BusinessRuleException("Seat count must be positive");
        }
        if (request.locationId() == null || request.locationId() <= 0) {
            throw new BusinessRuleException("Location id must be positive");
        }
        if (restaurantTableRepository.existsByTableNumber(request.tableNumber())) {
            throw new BusinessRuleException("Table number " + request.tableNumber() + " already exists");
        }

        Location location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location", request.locationId()));
        RestaurantTable table = new RestaurantTable(
                request.tableNumber(),
                request.seatCount(),
                location
        );
        return toResponse(restaurantTableRepository.save(table));
    }

    private RestaurantTableResponse toResponse(RestaurantTable table) {
        return new RestaurantTableResponse(
                table.getId(),
                table.getTableNumber(),
                table.getSeatCount(),
                new LocationResponse(
                        table.getLocation().getId(),
                        table.getLocation().getName(),
                        table.getLocation().getType()
                ),
                table.getStatus()
        );
    }
}
