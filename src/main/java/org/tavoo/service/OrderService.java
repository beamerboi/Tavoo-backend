package org.tavoo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tavoo.dto.AddOrderItemRequest;
import org.tavoo.dto.CreateOrderRequest;
import org.tavoo.dto.CheckLineResponse;
import org.tavoo.dto.CheckResponse;
import org.tavoo.dto.OrderArchiveDayResponse;
import org.tavoo.dto.OrderItemResponse;
import org.tavoo.dto.OrderResponse;
import org.tavoo.dto.UpdateOrderItemRequest;
import org.tavoo.entity.MenuCategory;
import org.tavoo.entity.MenuItem;
import org.tavoo.entity.Order;
import org.tavoo.entity.OrderItem;
import org.tavoo.entity.OrderStatus;
import org.tavoo.entity.PaymentMethod;
import org.tavoo.entity.PreparationStatus;
import org.tavoo.entity.RestaurantTable;
import org.tavoo.entity.Role;
import org.tavoo.entity.TableStatus;
import org.tavoo.entity.User;
import org.tavoo.exception.BusinessRuleException;
import org.tavoo.exception.InvalidOrderStateException;
import org.tavoo.exception.ResourceNotFoundException;
import org.tavoo.exception.TableOccupiedException;
import org.tavoo.repository.MenuItemRepository;
import org.tavoo.repository.OrderRepository;
import org.tavoo.repository.RestaurantTableRepository;
import org.tavoo.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    static final BigDecimal STANDARD_TAX_RATE = new BigDecimal("0.10");
    static final BigDecimal ALCOHOL_TAX_RATE = new BigDecimal("0.15");

    private final OrderRepository orderRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;
    private final Clock clock;
    private final RestaurantSettingsService restaurantSettingsService;

    public OrderService(
            OrderRepository orderRepository,
            RestaurantTableRepository restaurantTableRepository,
            UserRepository userRepository,
            MenuItemRepository menuItemRepository,
            Clock clock,
            RestaurantSettingsService restaurantSettingsService
    ) {
        this.orderRepository = orderRepository;
        this.restaurantTableRepository = restaurantTableRepository;
        this.userRepository = userRepository;
        this.menuItemRepository = menuItemRepository;
        this.clock = clock;
        this.restaurantSettingsService = restaurantSettingsService;
    }

    @Transactional
    public OrderResponse openOrder(CreateOrderRequest request, String waiterUsername) {
        User waiter = findWaiter(waiterUsername);
        RestaurantTable table = restaurantTableRepository.findByIdForUpdate(request.tableId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant table", request.tableId()));

        if (table.getStatus() == TableStatus.OCCUPIED) {
            throw new TableOccupiedException(request.tableId());
        }

        table.occupy();
        Order order = new Order(
                table,
                waiter,
                request.copertoCount(),
                restaurantSettingsService.getCopertoUnitPrice()
        );
        for (AddOrderItemRequest itemRequest : request.items()) {
            addItemToOrder(order, itemRequest);
        }
        releaseFirstHeldCourse(order);
        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOpenOrdersForWaiter(String waiterUsername) {
        return orderRepository
                .findByWaiterUsernameIgnoreCaseAndStatusOrderByIdAsc(waiterUsername, OrderStatus.OPEN)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOpenOrdersForKitchen() {
        return orderRepository.findByStatusOrderByIdAsc(OrderStatus.OPEN)
                .stream()
                .filter(order -> order.getItems().stream().anyMatch(this::isKitchenItem))
                .map(this::toKitchenResponse)
                .toList();
    }

    @Transactional
    public OrderResponse addItem(
            Long orderId,
            AddOrderItemRequest request,
            String waiterUsername
    ) {
        validateQuantity(request.quantity());
        Order order = findOwnedOpenOrderForUpdate(orderId, waiterUsername, "add items");
        OrderItem addedItem = addItemToOrder(order, request);
        releaseAddedItemIfCurrent(order, addedItem);
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse updateItem(
            Long orderId,
            Long itemId,
            UpdateOrderItemRequest request,
            String waiterUsername
    ) {
        validateQuantity(request.quantity());
        Order order = findOwnedOpenOrderForUpdate(orderId, waiterUsername, "update items");
        OrderItem item = findOrderItem(order, itemId);
        requireEditable(item, "Only items that have not entered preparation can be changed");
        item.update(request.quantity(), normalizeNotes(request.notes()));
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public void removeItem(Long orderId, Long itemId, String waiterUsername) {
        Order order = findOwnedOpenOrderForUpdate(orderId, waiterUsername, "remove items");
        OrderItem item = findOrderItem(order, itemId);
        requireEditable(item, "Only items that have not entered preparation can be removed");
        order.removeItem(item);
        releaseLowestHeldCourseIfNoneActive(order);
        orderRepository.save(order);
    }

    @Transactional
    public OrderResponse updatePreparationStatus(
            Long orderId,
            Long itemId,
            PreparationStatus targetStatus
    ) {
        Order order = findOpenOrderForUpdate(orderId, "update preparation status");
        OrderItem item = findOrderItem(order, itemId);
        try {
            switch (targetStatus) {
                case IN_PREPARATION -> item.startPreparation();
                case READY -> item.markReady();
                default -> throw new BusinessRuleException(
                        "Kitchen may only set IN_PREPARATION or READY"
                );
            }
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse serveItem(Long orderId, Long itemId, String waiterUsername) {
        Order order = findOwnedOpenOrderForUpdate(orderId, waiterUsername, "serve items");
        OrderItem item = findOrderItem(order, itemId);
        try {
            item.markServed();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse startCourse(Long orderId, int preparationPriority) {
        Order order = findOpenOrderForUpdate(orderId, "start a course");
        List<OrderItem> courseItems = findCourseItems(order, preparationPriority);
        for (OrderItem item : courseItems) {
            try {
                item.startPreparation();
            } catch (IllegalStateException exception) {
                throw new BusinessRuleException(
                        "Every item in preparation group " + preparationPriority
                                + " must be ORDERED before it starts"
                );
            }
        }
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse markCourseReady(Long orderId, int preparationPriority) {
        Order order = findOpenOrderForUpdate(orderId, "mark a course ready");
        List<OrderItem> courseItems = findCourseItems(order, preparationPriority);
        for (OrderItem item : courseItems) {
            try {
                item.markReady();
            } catch (IllegalStateException exception) {
                throw new BusinessRuleException(
                        "Every item in preparation group " + preparationPriority
                                + " must be IN_PREPARATION before it is ready"
                );
            }
        }
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse markOrderReady(Long orderId) {
        Order order = findOpenOrderForUpdate(orderId, "mark the order ready");
        if (order.getItems().stream()
                .anyMatch(item -> item.getPreparationStatus() == PreparationStatus.ON_HOLD)) {
            throw new BusinessRuleException(
                    "An order with held future courses cannot be marked ready as a whole"
            );
        }
        for (OrderItem item : order.getItems()) {
            if (item.getPreparationStatus() == PreparationStatus.IN_PREPARATION) {
                item.markReady();
            } else if (item.getPreparationStatus() != PreparationStatus.READY) {
                throw new BusinessRuleException(
                        "Every order item must be IN_PREPARATION or READY"
                );
            }
        }
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse deliverCourse(
            Long orderId,
            int preparationPriority,
            String waiterUsername
    ) {
        Order order = findOwnedOpenOrderForUpdate(orderId, waiterUsername, "deliver a course");
        List<OrderItem> courseItems = findCourseItems(order, preparationPriority);
        for (OrderItem item : courseItems) {
            try {
                item.markServed();
            } catch (IllegalStateException exception) {
                throw new BusinessRuleException(
                        "Every item in preparation group " + preparationPriority
                                + " must be READY before delivery"
                );
            }
        }
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse releaseNextCourse(
            Long orderId,
            int completedSequence,
            String waiterUsername
    ) {
        Order order = findOwnedOpenOrderForUpdate(
                orderId,
                waiterUsername,
                "release the next course"
        );
        List<OrderItem> completedCourse = findCourseItems(order, completedSequence);
        if (completedCourse.stream()
                .anyMatch(item -> item.getPreparationStatus() != PreparationStatus.SERVED)) {
            throw new BusinessRuleException(
                    "Course " + completedSequence + " must be delivered before releasing the next course"
            );
        }

        int nextSequence = order.getItems().stream()
                .filter(item -> item.getPreparationPriority() != null)
                .filter(item -> item.getPreparationPriority() > completedSequence)
                .filter(item -> item.getPreparationStatus() == PreparationStatus.ON_HOLD)
                .mapToInt(OrderItem::getPreparationPriority)
                .min()
                .orElseThrow(() -> new BusinessRuleException("There is no held next course"));
        findCourseItems(order, nextSequence).stream()
                .filter(item -> item.getPreparationStatus() == PreparationStatus.ON_HOLD)
                .forEach(OrderItem::releaseForPreparation);
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public CheckResponse payOrder(
            Long orderId,
            PaymentMethod paymentMethod,
            String waiterUsername
    ) {
        if (paymentMethod == null) {
            throw new BusinessRuleException("Payment method is required");
        }
        Order order = findOwnedOpenOrderForUpdate(orderId, waiterUsername, "pay");
        BigDecimal total = calculateFinalTotal(order);
        Instant paidAt = clock.instant();

        order.markPaid(total, paymentMethod, paidAt);
        order.getTable().free();

        return toCheckResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public CheckResponse getCheckForWaiter(Long orderId, String waiterUsername) {
        Order order = findOrder(orderId);
        requireOwnership(order, waiterUsername);
        return toCheckResponse(order);
    }

    @Transactional(readOnly = true)
    public CheckResponse getArchivedCheck(Long orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessRuleException("Only paid orders are available in the archive");
        }
        return toCheckResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderArchiveDayResponse> getPaidOrderArchive(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from == null ? LocalDate.now(clock) : from;
        LocalDate effectiveTo = to == null ? effectiveFrom : to;
        if (effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessRuleException("Archive 'to' date must not be before 'from' date");
        }

        ZoneId zone = clock.getZone();
        Instant rangeStart = effectiveFrom.atStartOfDay(zone).toInstant();
        Instant rangeEnd = effectiveTo.plusDays(1).atStartOfDay(zone).toInstant();
        List<Order> paidOrders = orderRepository
                .findByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDescIdDesc(
                        OrderStatus.PAID,
                        rangeStart,
                        rangeEnd
                );

        Map<LocalDate, List<Order>> ordersByDay = new LinkedHashMap<>();
        for (Order order : paidOrders) {
            LocalDate day = order.getPaidAt().atZone(zone).toLocalDate();
            ordersByDay.computeIfAbsent(day, ignored -> new ArrayList<>()).add(order);
        }

        return ordersByDay.entrySet().stream()
                .map(entry -> toArchiveDayResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, String waiterUsername) {
        Order order = findOrder(orderId);
        requireOwnership(order, waiterUsername);
        return toResponse(order);
    }

    private User findWaiter(String username) {
        User waiter = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new BusinessRuleException("Authenticated waiter does not exist"));
        if (waiter.getRole() != Role.WAITER) {
            throw new BusinessRuleException("Authenticated user does not have the WAITER role");
        }
        return waiter;
    }

    private OrderItem addItemToOrder(Order order, AddOrderItemRequest request) {
        validateQuantity(request.quantity());
        MenuItem menuItem = menuItemRepository.findById(request.menuItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item", request.menuItemId()));
        validateMenuItemForOrdering(menuItem);
        return order.addItem(
                menuItem,
                request.quantity(),
                normalizeNotes(request.notes()),
                taxRateFor(menuItem),
                resolveServiceSequence(request, menuItem)
        );
    }

    private Integer resolveServiceSequence(AddOrderItemRequest request, MenuItem menuItem) {
        if (!isKitchenItem(menuItem)) {
            if (request.serveFirst() || request.preparationPriority() != null) {
                throw new BusinessRuleException(
                        "Beverages do not use a preparation priority"
                );
            }
            return null;
        }
        if (request.serveFirst()) {
            if (request.preparationPriority() != null && request.preparationPriority() != 1) {
                throw new BusinessRuleException(
                        "An item marked serveFirst cannot use a preparation priority other than 1"
                );
            }
            return 1;
        }
        if (request.preparationPriority() != null) {
            return request.preparationPriority();
        }
        return switch (menuItem.getCourseType()) {
            case BEVERAGE, ANTIPASTO -> 1;
            case PRIMO -> 2;
            case SECONDO, STEAK -> 3;
            case DESSERT -> 4;
        };
    }

    private void releaseFirstHeldCourse(Order order) {
        Integer firstSequence = order.getItems().stream()
                .map(OrderItem::getPreparationPriority)
                .filter(java.util.Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
        if (firstSequence == null) {
            return;
        }
        order.getItems().stream()
                .filter(item -> firstSequence.equals(item.getPreparationPriority()))
                .forEach(OrderItem::releaseForPreparation);
    }

    private void releaseAddedItemIfCurrent(Order order, OrderItem addedItem) {
        if (addedItem.getPreparationPriority() == null) {
            return;
        }
        List<OrderItem> existingItems = order.getItems().stream()
                .filter(item -> item != addedItem)
                .toList();
        int activeSequence = existingItems.stream()
                .filter(item -> item.getPreparationPriority() != null)
                .filter(item -> item.getPreparationStatus() != PreparationStatus.ON_HOLD)
                .filter(item -> item.getPreparationStatus() != PreparationStatus.SERVED)
                .mapToInt(OrderItem::getPreparationPriority)
                .min()
                .orElse(Integer.MAX_VALUE);
        boolean hasHeldExistingCourse = existingItems.stream()
                .anyMatch(item -> item.getPreparationStatus() == PreparationStatus.ON_HOLD);
        if (activeSequence != Integer.MAX_VALUE
                && addedItem.getPreparationPriority() <= activeSequence) {
            addedItem.releaseForPreparation();
        } else if (activeSequence == Integer.MAX_VALUE && !hasHeldExistingCourse) {
            addedItem.releaseForPreparation();
        }
    }

    private void releaseLowestHeldCourseIfNoneActive(Order order) {
        boolean hasActiveKitchenItem = order.getItems().stream()
                .filter(this::isKitchenItem)
                .anyMatch(item -> item.getPreparationStatus() != PreparationStatus.ON_HOLD
                        && item.getPreparationStatus() != PreparationStatus.SERVED);
        if (hasActiveKitchenItem) {
            return;
        }
        Integer nextPriority = order.getItems().stream()
                .filter(item -> item.getPreparationStatus() == PreparationStatus.ON_HOLD)
                .map(OrderItem::getPreparationPriority)
                .filter(java.util.Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
        if (nextPriority != null) {
            order.getItems().stream()
                    .filter(item -> nextPriority.equals(item.getPreparationPriority()))
                    .filter(item -> item.getPreparationStatus() == PreparationStatus.ON_HOLD)
                    .forEach(OrderItem::releaseForPreparation);
        }
    }

    private List<OrderItem> findCourseItems(Order order, int preparationPriority) {
        if (preparationPriority <= 0) {
            throw new BusinessRuleException("Preparation priority must be positive");
        }
        List<OrderItem> items = order.getItems().stream()
                .filter(item -> Integer.valueOf(preparationPriority)
                        .equals(item.getPreparationPriority()))
                .toList();
        if (items.isEmpty()) {
            throw new BusinessRuleException(
                    "Order " + order.getId() + " has no preparation group " + preparationPriority
            );
        }
        return items;
    }

    private Order findOwnedOpenOrderForUpdate(
            Long orderId,
            String waiterUsername,
            String operation
    ) {
        Order order = findOpenOrderForUpdate(orderId, operation);
        requireOwnership(order, waiterUsername);
        return order;
    }

    private Order findOpenOrderForUpdate(Long orderId, String operation) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new InvalidOrderStateException(orderId, operation);
        }
        return order;
    }

    private void requireOwnership(Order order, String waiterUsername) {
        if (!order.getWaiter().getUsername().equalsIgnoreCase(waiterUsername)) {
            throw new ResourceNotFoundException("Order", order.getId());
        }
    }

    private OrderItem findOrderItem(Order order, Long itemId) {
        return order.getItems().stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item", itemId));
    }

    private void requireEditable(OrderItem item, String message) {
        boolean kitchenItemIsEditable = isKitchenItem(item)
                && (item.getPreparationStatus() == PreparationStatus.ORDERED
                || item.getPreparationStatus() == PreparationStatus.ON_HOLD);
        boolean beverageIsEditable = !isKitchenItem(item)
                && item.getPreparationStatus() != PreparationStatus.SERVED;
        if (!kitchenItemIsEditable && !beverageIsEditable) {
            throw new BusinessRuleException(message);
        }
    }

    private BigDecimal calculateFinalTotal(Order order) {
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            BigDecimal subtotal = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = subtotal.multiply(item.getTaxRate())
                    .setScale(2, RoundingMode.HALF_UP);
            total = total.add(subtotal).add(tax);
        }

        return total.add(order.getCopertoTotal()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal taxRateFor(MenuItem menuItem) {
        return menuItem.getCategory() == MenuCategory.ALCOHOL
                ? ALCOHOL_TAX_RATE
                : STANDARD_TAX_RATE;
    }

    private boolean isKitchenItem(OrderItem item) {
        return isKitchenItem(item.getMenuItem());
    }

    private boolean isKitchenItem(MenuItem menuItem) {
        return menuItem.getCategory() != MenuCategory.BEVERAGE
                && menuItem.getCategory() != MenuCategory.ALCOHOL;
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private void validateMenuItemForOrdering(MenuItem menuItem) {
        if (!menuItem.isAvailable()) {
            throw new BusinessRuleException("Menu item " + menuItem.getId() + " is not available");
        }
        validatePrice(menuItem);
    }

    private void validatePrice(MenuItem menuItem) {
        if (menuItem.getPrice() == null || menuItem.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Menu item " + menuItem.getId() + " has an invalid price");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleException("Quantity must be positive");
        }
    }

    private String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        return notes.trim();
    }

    private OrderResponse toResponse(Order order) {
        return toResponse(order, false);
    }

    private OrderResponse toKitchenResponse(Order order) {
        return toResponse(order, true);
    }

    private OrderResponse toResponse(Order order, boolean kitchenOnly) {
        List<OrderItemResponse> items = order.getItems().stream()
                .filter(item -> !kitchenOnly || isKitchenItem(item))
                .sorted(Comparator
                        .comparing(
                                OrderItem::getPreparationPriority,
                                Comparator.nullsLast(Integer::compareTo)
                        )
                        .thenComparing(item -> item.getMenuItem().getCourseType())
                        .thenComparing(OrderItem::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getTable().getId(),
                order.getTable().getTableNumber(),
                order.getWaiter().getId(),
                order.getWaiter().getUsername(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCopertoCount(),
                order.getCopertoUnitPrice(),
                order.getCopertoTotal(),
                order.getPaymentMethod(),
                order.getPaidAt(),
                items
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        MenuItem menuItem = item.getMenuItem();
        return new OrderItemResponse(
                item.getId(),
                menuItem.getId(),
                menuItem.getName(),
                item.getUnitPrice(),
                menuItem.getCategory(),
                menuItem.getCourseType(),
                item.getPreparationPriority(),
                item.getQuantity(),
                item.getNotes(),
                item.getPreparationStatus()
        );
    }

    private CheckResponse toCheckResponse(Order order) {
        List<CheckLineResponse> lines = order.getItems().stream()
                .sorted(Comparator
                        .comparing(
                                OrderItem::getPreparationPriority,
                                Comparator.nullsLast(Integer::compareTo)
                        )
                        .thenComparing(item -> item.getMenuItem().getCourseType())
                        .thenComparing(OrderItem::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::toCheckLineResponse)
                .toList();
        BigDecimal subtotal = lines.stream()
                .map(CheckLineResponse::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = lines.stream()
                .map(CheckLineResponse::taxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal calculatedTotal = subtotal
                .add(taxAmount)
                .add(order.getCopertoTotal())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = order.getStatus() == OrderStatus.PAID
                ? order.getTotalAmount()
                : calculatedTotal;

        return new CheckResponse(
                order.getId(),
                order.getStatus(),
                order.getTable().getId(),
                order.getTable().getTableNumber(),
                order.getWaiter().getId(),
                order.getWaiter().getUsername(),
                clock.instant(),
                order.getPaidAt(),
                order.getPaymentMethod(),
                subtotal,
                taxAmount,
                order.getCopertoCount(),
                order.getCopertoUnitPrice(),
                order.getCopertoTotal(),
                total,
                lines
        );
    }

    private CheckLineResponse toCheckLineResponse(OrderItem item) {
        MenuItem menuItem = item.getMenuItem();
        BigDecimal netAmount = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = netAmount.multiply(item.getTaxRate())
                .setScale(2, RoundingMode.HALF_UP);
        return new CheckLineResponse(
                item.getId(),
                menuItem.getId(),
                menuItem.getName(),
                menuItem.getCategory(),
                menuItem.getCourseType(),
                item.getPreparationPriority(),
                item.getQuantity(),
                item.getUnitPrice(),
                netAmount,
                item.getTaxRate(),
                taxAmount,
                netAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private OrderArchiveDayResponse toArchiveDayResponse(LocalDate date, List<Order> orders) {
        List<OrderResponse> responses = orders.stream().map(this::toResponse).toList();
        BigDecimal dailyTotal = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new OrderArchiveDayResponse(date, orders.size(), dailyTotal, responses);
    }
}
