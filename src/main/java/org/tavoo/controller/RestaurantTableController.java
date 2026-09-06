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
import org.tavoo.dto.CreateRestaurantTableRequest;
import org.tavoo.dto.RestaurantTableResponse;
import org.tavoo.service.RestaurantTableService;

import java.util.List;

@RestController
@RequestMapping("/api/tables")
public class RestaurantTableController {

    private final RestaurantTableService restaurantTableService;

    public RestaurantTableController(RestaurantTableService restaurantTableService) {
        this.restaurantTableService = restaurantTableService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WAITER', 'KITCHEN', 'BAR')")
    public List<RestaurantTableResponse> getTables() {
        return restaurantTableService.getTables();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestaurantTableResponse> createTable(
            @Valid @RequestBody CreateRestaurantTableRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantTableService.createTable(request));
    }
}
