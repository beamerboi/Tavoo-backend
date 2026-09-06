package org.tavoo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.tavoo.entity.LocationType;

public record CreateLocationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull LocationType type
) {
}
