package org.tavoo.dto;

import org.tavoo.entity.CourseType;
import org.tavoo.entity.MenuCategory;

import java.math.BigDecimal;

public record CheckLineResponse(
        Long orderItemId,
        Long menuItemId,
        String menuItemName,
        MenuCategory category,
        CourseType courseType,
        Integer preparationPriority,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal netAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal totalAmount
) {
}
