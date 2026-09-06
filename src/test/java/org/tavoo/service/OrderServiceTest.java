package org.tavoo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.tavoo.dto.AddOrderItemRequest;
import org.tavoo.dto.CreateOrderRequest;
import org.tavoo.dto.CheckResponse;
import org.tavoo.dto.OrderArchiveDayResponse;
import org.tavoo.dto.OrderResponse;
import org.tavoo.entity.CourseType;
import org.tavoo.entity.Location;
import org.tavoo.entity.LocationType;
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
import org.tavoo.exception.TableOccupiedException;
import org.tavoo.repository.MenuItemRepository;
import org.tavoo.repository.OrderRepository;
import org.tavoo.repository.RestaurantTableRepository;
import org.tavoo.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long TABLE_ID = 1L;
    private static final Long ORDER_ID = 3L;
    private static final Long MENU_ITEM_ID = 4L;
    private static final Long ORDER_ITEM_ID = 5L;
    private static final String WAITER_USERNAME = "waiter";
    private static final Instant NOW = Instant.parse("2026-08-22T10:15:30Z");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Rome");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestaurantTableRepository restaurantTableRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private RestaurantSettingsService restaurantSettingsService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(restaurantSettingsService.getCopertoUnitPrice())
                .thenReturn(new BigDecimal("2.50"));
        orderService = new OrderService(
                orderRepository,
                restaurantTableRepository,
                userRepository,
                menuItemRepository,
                Clock.fixed(NOW, BUSINESS_ZONE),
                restaurantSettingsService
        );
    }

    @Test
    void opensOrderOnFreeTableWithAuthenticatedWaiterAndItems() {
        RestaurantTable table = table();
        MenuItem item = menuItem("Pasta", "12.50", MenuCategory.FOOD, CourseType.PRIMO);
        when(userRepository.findByUsernameIgnoreCase(WAITER_USERNAME)).thenReturn(Optional.of(waiter()));
        when(restaurantTableRepository.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.of(table));
        when(menuItemRepository.findById(MENU_ITEM_ID)).thenReturn(Optional.of(item));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.openOrder(
                new CreateOrderRequest(
                        TABLE_ID,
                        List.of(new AddOrderItemRequest(MENU_ITEM_ID, 2, "No cheese"))
                ),
                WAITER_USERNAME
        );

        assertThat(response.status()).isEqualTo(OrderStatus.OPEN);
        assertThat(response.waiterUsername()).isEqualTo(WAITER_USERNAME);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).courseType()).isEqualTo(CourseType.PRIMO);
        assertThat(response.items().get(0).preparationStatus()).isEqualTo(PreparationStatus.ORDERED);
        assertThat(response.copertoCount()).isEqualTo(1);
        assertThat(response.copertoUnitPrice()).isEqualByComparingTo("2.50");
        assertThat(response.copertoTotal()).isEqualByComparingTo("2.50");
        assertThat(table.getStatus()).isEqualTo(TableStatus.OCCUPIED);
    }

    @Test
    void openingOrderReleasesOnlyFirstPreparationPriority() {
        RestaurantTable table = table();
        MenuItem antipasto = menuItem(
                "Bruschetta",
                "8.00",
                MenuCategory.FOOD,
                CourseType.ANTIPASTO
        );
        MenuItem pasta = menuItem("Pasta", "12.50", MenuCategory.FOOD, CourseType.PRIMO);
        Long secondMenuItemId = 6L;
        when(userRepository.findByUsernameIgnoreCase(WAITER_USERNAME)).thenReturn(Optional.of(waiter()));
        when(restaurantTableRepository.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.of(table));
        when(menuItemRepository.findById(MENU_ITEM_ID)).thenReturn(Optional.of(antipasto));
        when(menuItemRepository.findById(secondMenuItemId)).thenReturn(Optional.of(pasta));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.openOrder(
                new CreateOrderRequest(
                        TABLE_ID,
                        List.of(
                                new AddOrderItemRequest(MENU_ITEM_ID, 1, null, 1, true),
                                new AddOrderItemRequest(secondMenuItemId, 1, null, 2, false)
                        )
                ),
                WAITER_USERNAME
        );

        assertThat(response.items())
                .filteredOn(item -> item.preparationPriority() == 1)
                .allMatch(item -> item.preparationStatus() == PreparationStatus.ORDERED);
        assertThat(response.items())
                .filteredOn(item -> item.preparationPriority() == 2)
                .allMatch(item -> item.preparationStatus() == PreparationStatus.ON_HOLD);
    }

    @Test
    void rejectsOrderOnOccupiedTable() {
        RestaurantTable table = table();
        table.occupy();
        when(userRepository.findByUsernameIgnoreCase(WAITER_USERNAME)).thenReturn(Optional.of(waiter()));
        when(restaurantTableRepository.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> orderService.openOrder(
                new CreateOrderRequest(
                        TABLE_ID,
                        List.of(new AddOrderItemRequest(MENU_ITEM_ID, 1, null))
                ),
                WAITER_USERNAME
        )).isInstanceOf(TableOccupiedException.class);

        verifyNoInteractions(menuItemRepository, orderRepository);
    }

    @Test
    void addsItemToOwnedOpenOrder() {
        Order order = openOrder();
        MenuItem item = menuItem("Pasta", "12.50", MenuCategory.FOOD, CourseType.PRIMO);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(menuItemRepository.findById(MENU_ITEM_ID)).thenReturn(Optional.of(item));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.addItem(
                ORDER_ID,
                new AddOrderItemRequest(MENU_ITEM_ID, 2, "No cheese"),
                WAITER_USERNAME
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        verify(orderRepository).save(order);
    }

    @Test
    void rejectsItemAdditionToPaidOrder() {
        Order order = openOrder();
        order.markPaid(new BigDecimal("10.00"), PaymentMethod.CHECK, NOW);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.addItem(
                ORDER_ID,
                new AddOrderItemRequest(MENU_ITEM_ID, 1, null),
                WAITER_USERNAME
        )).isInstanceOf(InvalidOrderStateException.class);

        verifyNoInteractions(menuItemRepository);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void rejectsInvalidQuantityBeforeLoadingOrder() {
        assertThatThrownBy(() -> orderService.addItem(
                ORDER_ID,
                new AddOrderItemRequest(MENU_ITEM_ID, 0, null),
                WAITER_USERNAME
        )).isInstanceOf(BusinessRuleException.class)
                .hasMessage("Quantity must be positive");

        verifyNoInteractions(orderRepository, menuItemRepository);
    }

    @Test
    void calculatesStandardTaxForFoodAndDessert() {
        Order order = openOrder();
        order.addItem(menuItem("Pasta", "20.00", MenuCategory.FOOD, CourseType.PRIMO), 2, null, OrderService.STANDARD_TAX_RATE, 1);
        order.addItem(menuItem("Tiramisu", "10.00", MenuCategory.DESSERT, CourseType.DESSERT), 1, null, OrderService.STANDARD_TAX_RATE, 2);
        stubOrderForPayment(order);

        CheckResponse response = orderService.payOrder(ORDER_ID, PaymentMethod.POS, WAITER_USERNAME);

        assertThat(response.totalAmount()).isEqualByComparingTo("55.00");
    }

    @Test
    void calculatesAlcoholTaxAtFifteenPercent() {
        Order order = openOrder();
        order.addItem(menuItem("Wine", "20.00", MenuCategory.ALCOHOL, CourseType.BEVERAGE), 2, null, OrderService.ALCOHOL_TAX_RATE, 1);
        stubOrderForPayment(order);

        CheckResponse response = orderService.payOrder(ORDER_ID, PaymentMethod.CHECK, WAITER_USERNAME);

        assertThat(response.totalAmount()).isEqualByComparingTo("46.00");
    }

    @Test
    void kitchenProgressesItemAndWaiterServesIt() {
        Order order = openOrder();
        OrderItem item = order.addItem(
                menuItem("Steak", "25.00", MenuCategory.FOOD, CourseType.STEAK),
                1,
                null,
                OrderService.STANDARD_TAX_RATE,
                1
        );
        item.releaseForPreparation();
        ReflectionTestUtils.setField(item, "id", ORDER_ITEM_ID);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse makingResponse = orderService.updatePreparationStatus(
                ORDER_ID,
                ORDER_ITEM_ID,
                PreparationStatus.IN_PREPARATION
        );
        assertThat(makingResponse.items().get(0).preparationStatus())
                .isEqualTo(PreparationStatus.IN_PREPARATION);
        orderService.updatePreparationStatus(ORDER_ID, ORDER_ITEM_ID, PreparationStatus.READY);
        OrderResponse response = orderService.serveItem(
                ORDER_ID,
                ORDER_ITEM_ID,
                WAITER_USERNAME
        );

        assertThat(response.items().get(0).preparationStatus()).isEqualTo(PreparationStatus.SERVED);
    }

    @Test
    void waiterCannotReadAnotherWaitersOrder() {
        Order order = openOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(ORDER_ID, "someone-else"))
                .isInstanceOf(org.tavoo.exception.ResourceNotFoundException.class);
    }

    @Test
    void payingOrderChangesStatusAndFreesTable() {
        Order order = openOrder();
        order.addItem(menuItem("Pasta", "10.00", MenuCategory.FOOD, CourseType.PRIMO), 1, null, OrderService.STANDARD_TAX_RATE, 1);
        stubOrderForPayment(order);

        CheckResponse response = orderService.payOrder(ORDER_ID, PaymentMethod.POS, WAITER_USERNAME);

        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.POS);
        assertThat(response.paidAt()).isEqualTo(NOW);
        assertThat(order.getTable().getStatus()).isEqualTo(TableStatus.FREE);
    }

    @Test
    void checkContainsFrontendReceiptDataAndSnapshottedPrices() {
        Order order = openOrder();
        MenuItem pasta = menuItem("Pasta", "12.50", MenuCategory.FOOD, CourseType.PRIMO);
        order.addItem(pasta, 2, null, OrderService.STANDARD_TAX_RATE, 1);
        ReflectionTestUtils.setField(pasta, "price", new BigDecimal("99.00"));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        CheckResponse response = orderService.getCheckForWaiter(ORDER_ID, WAITER_USERNAME);

        assertThat(response.waiterUsername()).isEqualTo(WAITER_USERNAME);
        assertThat(response.tableNumber()).isEqualTo(7);
        assertThat(response.subtotal()).isEqualByComparingTo("25.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("2.50");
        assertThat(response.totalAmount()).isEqualByComparingTo("27.50");
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("12.50");
    }

    @Test
    void receiptIncludesCopertoCountAndAmount() {
        RestaurantTable table = table();
        table.occupy();
        Order order = new Order(table, waiter(), 3, new BigDecimal("2.50"));
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.addItem(
                menuItem("Pasta", "10.00", MenuCategory.FOOD, CourseType.PRIMO),
                1,
                null,
                OrderService.STANDARD_TAX_RATE,
                1
        );
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        CheckResponse response = orderService.getCheckForWaiter(ORDER_ID, WAITER_USERNAME);

        assertThat(response.copertoCount()).isEqualTo(3);
        assertThat(response.copertoUnitPrice()).isEqualByComparingTo("2.50");
        assertThat(response.copertoTotal()).isEqualByComparingTo("7.50");
        assertThat(response.totalAmount()).isEqualByComparingTo("18.50");
    }

    @Test
    void adminArchiveGroupsPaidOrdersByBusinessDay() {
        Order order = openOrder();
        order.addItem(
                menuItem("Pasta", "10.00", MenuCategory.FOOD, CourseType.PRIMO),
                1,
                null,
                OrderService.STANDARD_TAX_RATE,
                1
        );
        order.markPaid(new BigDecimal("11.00"), PaymentMethod.CHECK, NOW);
        LocalDate day = LocalDate.of(2026, 8, 22);
        Instant start = day.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        when(orderRepository
                .findByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDescIdDesc(
                        OrderStatus.PAID,
                        start,
                        end
                )).thenReturn(List.of(order));

        List<OrderArchiveDayResponse> archive = orderService.getPaidOrderArchive(day, day);

        assertThat(archive).hasSize(1);
        assertThat(archive.get(0).date()).isEqualTo(day);
        assertThat(archive.get(0).orderCount()).isEqualTo(1);
        assertThat(archive.get(0).totalAmount()).isEqualByComparingTo("11.00");
        assertThat(archive.get(0).orders().get(0).paymentMethod()).isEqualTo(PaymentMethod.CHECK);
    }

    @Test
    void waiterReleasesNextCourseAfterDeliveredCourse() {
        Order order = openOrder();
        OrderItem firstCourse = order.addItem(
                menuItem("Antipasto", "8.00", MenuCategory.FOOD, CourseType.ANTIPASTO),
                1,
                null,
                OrderService.STANDARD_TAX_RATE,
                1
        );
        OrderItem secondCourse = order.addItem(
                menuItem("Pasta", "12.00", MenuCategory.FOOD, CourseType.PRIMO),
                1,
                null,
                OrderService.STANDARD_TAX_RATE,
                2
        );
        firstCourse.releaseForPreparation();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.startCourse(ORDER_ID, 1);
        orderService.markCourseReady(ORDER_ID, 1);
        orderService.deliverCourse(ORDER_ID, 1, WAITER_USERNAME);
        OrderResponse response = orderService.releaseNextCourse(ORDER_ID, 1, WAITER_USERNAME);

        assertThat(firstCourse.getPreparationStatus()).isEqualTo(PreparationStatus.SERVED);
        assertThat(secondCourse.getPreparationStatus()).isEqualTo(PreparationStatus.ORDERED);
        assertThat(response.items())
                .filteredOn(item -> item.preparationPriority() == 2)
                .allMatch(item -> item.preparationStatus() == PreparationStatus.ORDERED);
    }

    @Test
    void serveFirstReleasesItemAsCourseOne() {
        Order order = openOrder();
        MenuItem dessert = menuItem("Tiramisu", "8.00", MenuCategory.DESSERT, CourseType.DESSERT);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(menuItemRepository.findById(MENU_ITEM_ID)).thenReturn(Optional.of(dessert));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.addItem(
                ORDER_ID,
                new AddOrderItemRequest(MENU_ITEM_ID, 1, null, null, true),
                WAITER_USERNAME
        );

        assertThat(response.items().get(0).preparationPriority()).isEqualTo(1);
        assertThat(response.items().get(0).preparationStatus()).isEqualTo(PreparationStatus.ORDERED);
    }

    @Test
    void beveragesHaveNoPriorityAndAreExcludedFromKitchenTickets() {
        Order order = openOrder();
        OrderItem wine = order.addItem(
                menuItem("Wine", "6.00", MenuCategory.ALCOHOL, CourseType.BEVERAGE),
                2,
                null,
                OrderService.ALCOHOL_TAX_RATE,
                null
        );
        OrderItem pasta = order.addItem(
                menuItem("Pasta", "12.00", MenuCategory.FOOD, CourseType.PRIMO),
                1,
                null,
                OrderService.STANDARD_TAX_RATE,
                1
        );
        pasta.releaseForPreparation();
        when(orderRepository.findByStatusOrderByIdAsc(OrderStatus.OPEN))
                .thenReturn(List.of(order));

        List<OrderResponse> kitchenOrders = orderService.getOpenOrdersForKitchen();

        assertThat(wine.getPreparationPriority()).isNull();
        assertThat(wine.getPreparationStatus()).isEqualTo(PreparationStatus.READY);
        assertThat(kitchenOrders).hasSize(1);
        assertThat(kitchenOrders.get(0).items())
                .extracting(item -> item.menuItemName())
                .containsExactly("Pasta");
    }

    @Test
    void orderContainingOnlyBeveragesDoesNotCreateKitchenTicket() {
        Order order = openOrder();
        order.addItem(
                menuItem("Water", "3.00", MenuCategory.BEVERAGE, CourseType.BEVERAGE),
                1,
                null,
                OrderService.STANDARD_TAX_RATE,
                null
        );
        when(orderRepository.findByStatusOrderByIdAsc(OrderStatus.OPEN))
                .thenReturn(List.of(order));

        assertThat(orderService.getOpenOrdersForKitchen()).isEmpty();
    }

    private void stubOrderForPayment(Order order) {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
    }

    private Order openOrder() {
        RestaurantTable table = table();
        table.occupy();
        Order order = new Order(table, waiter());
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private RestaurantTable table() {
        return new RestaurantTable(7, 4, new Location("Main Hall", LocationType.INSIDE));
    }

    private User waiter() {
        return new User(WAITER_USERNAME, "encoded-password", Role.WAITER);
    }

    private MenuItem menuItem(
            String name,
            String price,
            MenuCategory category,
            CourseType courseType
    ) {
        return new MenuItem(name, new BigDecimal(price), category, courseType, true);
    }
}
