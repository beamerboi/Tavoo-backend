package org.tavoo.dto;

import org.tavoo.entity.TableStatus;

public record RestaurantTableResponse(
        Long id,
        int tableNumber,
        int seatCount,
        LocationResponse location,
        TableStatus status
) {
}
