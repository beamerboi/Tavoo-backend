package org.tavoo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OrderArchiveDayResponse(
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        int orderCount,
        BigDecimal totalAmount,
        List<OrderResponse> orders
) {
}
