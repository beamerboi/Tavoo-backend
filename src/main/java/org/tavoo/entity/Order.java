package org.tavoo.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "customer_orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_table_id", nullable = false)
    private RestaurantTable table;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "waiter_id", nullable = false)
    private User waiter;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @NotNull
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "coperto_count", nullable = false)
    private int copertoCount;

    @Column(name = "coperto_unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal copertoUnitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(RestaurantTable table, User waiter) {
        this(table, waiter, 0, BigDecimal.ZERO.setScale(2));
    }

    public Order(
            RestaurantTable table,
            User waiter,
            int copertoCount,
            BigDecimal copertoUnitPrice
    ) {
        if (copertoCount < 0 || copertoUnitPrice == null
                || copertoUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Coperto count and unit price cannot be negative");
        }
        this.table = table;
        this.waiter = waiter;
        this.status = OrderStatus.OPEN;
        this.totalAmount = BigDecimal.ZERO.setScale(2);
        this.copertoCount = copertoCount;
        this.copertoUnitPrice = copertoUnitPrice.setScale(2);
        table.addOrder(this);
        waiter.addOrder(this);
    }

    public Long getId() {
        return id;
    }

    public RestaurantTable getTable() {
        return table;
    }

    public User getWaiter() {
        return waiter;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public int getCopertoCount() {
        return copertoCount;
    }

    public BigDecimal getCopertoUnitPrice() {
        return copertoUnitPrice;
    }

    public BigDecimal getCopertoTotal() {
        return copertoUnitPrice.multiply(BigDecimal.valueOf(copertoCount)).setScale(2);
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public OrderItem addItem(
            MenuItem menuItem,
            int quantity,
            String notes,
            BigDecimal taxRate,
            Integer preparationPriority
    ) {
        OrderItem item = new OrderItem(
                this,
                menuItem,
                menuItem.getPrice(),
                taxRate,
                preparationPriority,
                quantity,
                notes
        );
        items.add(item);
        return item;
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
    }

    public void markPaid(
            BigDecimal totalAmount,
            PaymentMethod paymentMethod,
            Instant paidAt
    ) {
        if (paymentMethod == null || paidAt == null) {
            throw new IllegalArgumentException("Payment method and payment time are required");
        }
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.paidAt = paidAt;
        this.status = OrderStatus.PAID;
    }
}
