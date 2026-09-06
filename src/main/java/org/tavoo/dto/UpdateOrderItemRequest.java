package org.tavoo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateOrderItemRequest(
        @NotNull @Positive Integer quantity,
        @Size(max = 500) String notes
) {
}
