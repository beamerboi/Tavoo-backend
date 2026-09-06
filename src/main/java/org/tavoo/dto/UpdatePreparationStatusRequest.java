package org.tavoo.dto;

import jakarta.validation.constraints.NotNull;
import org.tavoo.entity.PreparationStatus;

public record UpdatePreparationStatusRequest(
        @NotNull PreparationStatus status
) {
}
