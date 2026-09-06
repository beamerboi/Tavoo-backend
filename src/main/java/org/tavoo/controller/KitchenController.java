package org.tavoo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tavoo.dto.OrderResponse;
import org.tavoo.dto.UpdatePreparationStatusRequest;
import org.tavoo.service.OrderService;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/kitchen/orders")
@PreAuthorize("hasAnyRole('KITCHEN', 'ADMIN')")
public class KitchenController {

    private final OrderService orderService;

    public KitchenController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> getOpenOrders() {
        return orderService.getOpenOrdersForKitchen();
    }

    @PatchMapping("/{orderId}/items/{itemId}/status")
    public OrderResponse updatePreparationStatus(
            @PathVariable @Positive Long orderId,
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody UpdatePreparationStatusRequest request
    ) {
        return orderService.updatePreparationStatus(orderId, itemId, request.status());
    }

    @PostMapping("/{orderId}/courses/{preparationPriority}/start")
    public OrderResponse startCourse(
            @PathVariable @Positive Long orderId,
            @PathVariable @Positive Integer preparationPriority
    ) {
        return orderService.startCourse(orderId, preparationPriority);
    }

    @PostMapping("/{orderId}/courses/{preparationPriority}/ready")
    public OrderResponse markCourseReady(
            @PathVariable @Positive Long orderId,
            @PathVariable @Positive Integer preparationPriority
    ) {
        return orderService.markCourseReady(orderId, preparationPriority);
    }

    @PostMapping("/{orderId}/ready")
    public OrderResponse markOrderReady(@PathVariable @Positive Long orderId) {
        return orderService.markOrderReady(orderId);
    }
}
