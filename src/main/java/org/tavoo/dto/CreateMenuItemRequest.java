package org.tavoo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.tavoo.entity.MenuCategory;
import org.tavoo.entity.CourseType;

import java.math.BigDecimal;

public record CreateMenuItemRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull MenuCategory category,
        @NotNull CourseType courseType,
        @NotNull Boolean available
) {
}
