package org.tavoo.dto;

import java.math.BigDecimal;

public record RestaurantSettingsResponse(
        BigDecimal copertoUnitPrice,
        long version
) {
}
