package org.tavoo.dto;

import org.tavoo.entity.MenuCategory;
import org.tavoo.entity.CourseType;

import java.math.BigDecimal;

public record MenuItemResponse(
        Long id,
        String name,
        BigDecimal price,
        MenuCategory category,
        CourseType courseType,
        boolean available
) {
}
