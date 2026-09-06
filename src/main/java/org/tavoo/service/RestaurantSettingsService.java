package org.tavoo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tavoo.dto.RestaurantSettingsResponse;
import org.tavoo.entity.RestaurantSettings;
import org.tavoo.exception.ResourceNotFoundException;
import org.tavoo.repository.RestaurantSettingsRepository;

import java.math.BigDecimal;

@Service
public class RestaurantSettingsService {

    private final RestaurantSettingsRepository repository;

    public RestaurantSettingsService(RestaurantSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RestaurantSettingsResponse getSettings() {
        return toResponse(getEntity());
    }

    @Transactional(readOnly = true)
    public BigDecimal getCopertoUnitPrice() {
        return getEntity().getCopertoUnitPrice();
    }

    @Transactional
    public RestaurantSettingsResponse updateCopertoUnitPrice(BigDecimal unitPrice) {
        RestaurantSettings settings = getEntity();
        settings.updateCopertoUnitPrice(unitPrice);
        return toResponse(repository.save(settings));
    }

    private RestaurantSettings getEntity() {
        return repository.findById(RestaurantSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant settings",
                        RestaurantSettings.SINGLETON_ID
                ));
    }

    private RestaurantSettingsResponse toResponse(RestaurantSettings settings) {
        return new RestaurantSettingsResponse(
                settings.getCopertoUnitPrice(),
                settings.getVersion()
        );
    }
}
