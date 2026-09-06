package org.tavoo.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDomainTest {

    @Test
    void kitchenItemFollowsItsPreparationLifecycle() {
        OrderItem item = openOrder().addItem(
                menuItem("Pasta", "12.50", MenuCategory.FOOD, CourseType.PRIMO),
                2,
                "No cheese",
                new BigDecimal("0.1000"),
                1
        );

        assertThat(item.getPreparationStatus()).isEqualTo(PreparationStatus.ON_HOLD);

        item.releaseForPreparation();
        item.startPreparation();
        item.markReady();
        item.markServed();

        assertThat(item.getPreparationStatus()).isEqualTo(PreparationStatus.SERVED);
    }

    @Test
    void beverageBypassesTheKitchenQueue() {
        OrderItem item = openOrder().addItem(
                menuItem("Water", "3.00", MenuCategory.BEVERAGE, CourseType.BEVERAGE),
                1,
                null,
                new BigDecimal("0.1000"),
                null
        );

        assertThat(item.getPreparationPriority()).isNull();
        assertThat(item.getPreparationStatus()).isEqualTo(PreparationStatus.READY);
    }

    @Test
    void rejectsAnOutOfOrderPreparationTransition() {
        OrderItem item = openOrder().addItem(
                menuItem("Pasta", "12.50", MenuCategory.FOOD, CourseType.PRIMO),
                1,
                null,
                new BigDecimal("0.1000"),
                1
        );

        assertThatThrownBy(item::markReady)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ON_HOLD")
                .hasMessageContaining("READY");
    }

    @Test
    void paymentStoresTheFinancialSnapshot() {
        Order order = new Order(table(), waiter(), 3, new BigDecimal("2.50"));
        Instant paidAt = Instant.parse("2026-09-02T10:00:00Z");

        order.markPaid(new BigDecimal("31.50"), PaymentMethod.POS, paidAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("31.50");
        assertThat(order.getCopertoTotal()).isEqualByComparingTo("7.50");
        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.POS);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    void rejectsIncompletePaymentData() {
        Order order = openOrder();

        assertThatThrownBy(() -> order.markPaid(new BigDecimal("10.00"), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment method and payment time are required");
    }

    private static Order openOrder() {
        return new Order(table(), waiter());
    }

    private static RestaurantTable table() {
        return new RestaurantTable(1, 4, new Location("Main Hall", LocationType.INSIDE));
    }

    private static User waiter() {
        return new User("waiter", "encoded-password", Role.WAITER);
    }

    private static MenuItem menuItem(
            String name,
            String price,
            MenuCategory category,
            CourseType courseType
    ) {
        return new MenuItem(name, new BigDecimal(price), category, courseType, true);
    }
}
