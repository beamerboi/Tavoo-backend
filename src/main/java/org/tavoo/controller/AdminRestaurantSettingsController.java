package org.tavoo.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tavoo.dto.RestaurantSettingsResponse;
import org.tavoo.dto.UpdateCopertoPriceRequest;
import org.tavoo.service.RestaurantSettingsService;

@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRestaurantSettingsController {

    private final RestaurantSettingsService settingsService;

    public AdminRestaurantSettingsController(RestaurantSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public RestaurantSettingsResponse getSettings() {
        return settingsService.getSettings();
    }

    @PutMapping("/coperto-price")
    public RestaurantSettingsResponse updateCopertoPrice(
            @Valid @RequestBody UpdateCopertoPriceRequest request
    ) {
        return settingsService.updateCopertoUnitPrice(request.unitPrice());
    }
}
