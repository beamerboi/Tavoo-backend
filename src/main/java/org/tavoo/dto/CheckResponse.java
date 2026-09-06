package org.tavoo.dto;

import org.tavoo.entity.OrderStatus;
import org.tavoo.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CheckResponse(
        Long orderId,
        OrderStatus status,
        Long tableId,
        int tableNumber,
        Long waiterId,
        String waiterUsername,
        Instant generatedAt,
        Instant paidAt,
        PaymentMethod paymentMethod,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        int copertoCount,
        BigDecimal copertoUnitPrice,
        BigDecimal copertoTotal,
        BigDecimal totalAmount,
        List<CheckLineResponse> items
) {
}
