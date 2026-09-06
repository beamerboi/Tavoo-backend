package org.tavoo.dto;

import org.tavoo.entity.CourseType;
import org.tavoo.entity.MenuCategory;
import org.tavoo.entity.PreparationStatus;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long menuItemId,
        String menuItemName,
        BigDecimal unitPrice,
        MenuCategory category,
        CourseType courseType,
        Integer preparationPriority,
        int quantity,
        String notes,
        PreparationStatus preparationStatus
) {
}
