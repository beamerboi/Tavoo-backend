package org.tavoo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddOrderItemRequest(
        @NotNull @Positive Long menuItemId,
        @Positive int quantity,
        @Size(max = 500) String notes,
        @JsonAlias("serviceSequence") @Positive Integer preparationPriority,
        boolean serveFirst
) {
    public AddOrderItemRequest(Long menuItemId, int quantity, String notes) {
        this(menuItemId, quantity, notes, null, false);
    }
}
