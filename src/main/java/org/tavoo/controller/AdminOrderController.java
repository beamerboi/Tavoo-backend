package org.tavoo.controller;

import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tavoo.dto.CheckResponse;
import org.tavoo.dto.OrderArchiveDayResponse;
import org.tavoo.service.OrderService;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/archive")
    public List<OrderArchiveDayResponse> getArchive(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return orderService.getPaidOrderArchive(from, to);
    }

    @GetMapping("/archive/{id}/check")
    public CheckResponse getArchivedCheck(@PathVariable @Positive Long id) {
        return orderService.getArchivedCheck(id);
    }

    @GetMapping("/archive/{id}/receipt")
    public CheckResponse getArchivedReceipt(@PathVariable @Positive Long id) {
        return orderService.getArchivedCheck(id);
    }
}
