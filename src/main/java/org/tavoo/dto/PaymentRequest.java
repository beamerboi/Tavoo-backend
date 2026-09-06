package org.tavoo.dto;

import jakarta.validation.constraints.NotNull;
import org.tavoo.entity.PaymentMethod;

public record PaymentRequest(
        @NotNull PaymentMethod paymentMethod
) {
}
