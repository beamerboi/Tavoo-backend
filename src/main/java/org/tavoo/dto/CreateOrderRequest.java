package org.tavoo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotNull @Positive Long tableId,
        @Positive int copertoCount,
        @NotEmpty List<@Valid AddOrderItemRequest> items
) {
    public CreateOrderRequest(Long tableId, List<AddOrderItemRequest> items) {
        this(tableId, 1, items);
    }
}
