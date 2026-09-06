package org.tavoo.dto;

import org.tavoo.entity.OrderStatus;
import org.tavoo.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long tableId,
        int tableNumber,
        Long waiterId,
        String waiterUsername,
        OrderStatus status,
        BigDecimal totalAmount,
        int copertoCount,
        BigDecimal copertoUnitPrice,
        BigDecimal copertoTotal,
        PaymentMethod paymentMethod,
        Instant paidAt,
        List<OrderItemResponse> items
) {
}
