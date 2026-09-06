package org.tavoo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateRestaurantTableRequest(
        @NotNull @Positive Integer tableNumber,
        @NotNull @Positive Integer seatCount,
        @NotNull @Positive Long locationId
) {
}
