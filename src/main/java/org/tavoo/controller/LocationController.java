package org.tavoo.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tavoo.dto.CreateLocationRequest;
import org.tavoo.dto.LocationResponse;
import org.tavoo.service.LocationService;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WAITER')")
    public List<LocationResponse> getLocations() {
        return locationService.getLocations();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LocationResponse> createLocation(
            @Valid @RequestBody CreateLocationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(locationService.createLocation(request));
    }
}
