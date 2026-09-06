package org.tavoo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tavoo.dto.AddOrderItemRequest;
import org.tavoo.dto.CreateOrderRequest;
import org.tavoo.dto.CheckResponse;
import org.tavoo.dto.OrderResponse;
import org.tavoo.dto.PaymentRequest;
import org.tavoo.dto.UpdateOrderItemRequest;
import org.tavoo.service.OrderService;

import java.security.Principal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasRole('WAITER')")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> openOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Principal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.openOrder(request, principal.getName()));
    }

    @GetMapping
    public List<OrderResponse> getMyOpenOrders(Principal principal) {
        return orderService.getOpenOrdersForWaiter(principal.getName());
    }

    @PutMapping("/{id}/items")
    public OrderResponse addItem(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AddOrderItemRequest request,
            Principal principal
    ) {
        return orderService.addItem(id, request, principal.getName());
    }

    @PatchMapping("/{orderId}/items/{itemId}")
    public OrderResponse updateItem(
            @PathVariable @Positive Long orderId,
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody UpdateOrderItemRequest request,
            Principal principal
    ) {
        return orderService.updateItem(orderId, itemId, request, principal.getName());
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @PathVariable @Positive Long orderId,
            @PathVariable @Positive Long itemId,
            Principal principal
    ) {
        orderService.removeItem(orderId, itemId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orderId}/items/{itemId}/serve")
    public OrderResponse serveItem(
            @PathVariable @Positive Long orderId,
            @PathVariable @Positive Long itemId,
            Principal principal
    ) {
        return orderService.serveItem(orderId, itemId, principal.getName());
    }

    @PostMapping("/{orderId}/courses/{preparationPriority}/delivered")
    public OrderResponse deliverCourse(
            @PathVariable @Positive Long orderId,
            @PathVariable @Positive Integer preparationPriority,
            Principal principal
    ) {
        return orderService.deliverCourse(orderId, preparationPriority, principal.getName());
    }

    @PostMapping("/{orderId}/courses/{preparationPriority}/release-next")
    public OrderResponse releaseNextCourse(
            @PathVariable @Positive Long orderId,
            @PathVariable @Positive Integer preparationPriority,
            Principal principal
    ) {
        return orderService.releaseNextCourse(orderId, preparationPriority, principal.getName());
    }

    @PostMapping("/{id}/pay")
    public CheckResponse payOrder(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PaymentRequest request,
            Principal principal
    ) {
        return orderService.payOrder(id, request.paymentMethod(), principal.getName());
    }

    @GetMapping("/{id}/check")
    public CheckResponse getCheck(
            @PathVariable @Positive Long id,
            Principal principal
    ) {
        return orderService.getCheckForWaiter(id, principal.getName());
    }

    @GetMapping("/{id}/receipt")
    public CheckResponse getReceipt(
            @PathVariable @Positive Long id,
            Principal principal
    ) {
        return orderService.getCheckForWaiter(id, principal.getName());
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(
            @PathVariable @Positive Long id,
            Principal principal
    ) {
        return orderService.getOrder(id, principal.getName());
    }
}
