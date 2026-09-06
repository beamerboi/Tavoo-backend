package org.tavoo.entity;

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
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @NotNull
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @NotNull
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Positive
    @Column(nullable = false)
    private int quantity;

    @Positive
    @Column(name = "preparation_priority")
    private Integer preparationPriority;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "preparation_status", nullable = false, length = 20)
    private PreparationStatus preparationStatus;

    protected OrderItem() {
    }

    OrderItem(
            Order order,
            MenuItem menuItem,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            Integer preparationPriority,
            int quantity,
            String notes
    ) {
        this.order = order;
        this.menuItem = menuItem;
        this.unitPrice = unitPrice;
        this.taxRate = taxRate;
        this.preparationPriority = preparationPriority;
        this.quantity = quantity;
        this.notes = notes;
        this.preparationStatus = preparationPriority == null
                ? PreparationStatus.READY
                : PreparationStatus.ON_HOLD;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public int getQuantity() {
        return quantity;
    }

    public Integer getPreparationPriority() {
        return preparationPriority;
    }

    public String getNotes() {
        return notes;
    }

    public PreparationStatus getPreparationStatus() {
        return preparationStatus;
    }

    public void update(int quantity, String notes) {
        this.quantity = quantity;
        this.notes = notes;
    }

    public void startPreparation() {
        transition(PreparationStatus.ORDERED, PreparationStatus.IN_PREPARATION);
    }

    public void releaseForPreparation() {
        transition(PreparationStatus.ON_HOLD, PreparationStatus.ORDERED);
    }

    public void markReady() {
        transition(PreparationStatus.IN_PREPARATION, PreparationStatus.READY);
    }

    public void markServed() {
        transition(PreparationStatus.READY, PreparationStatus.SERVED);
    }

    private void transition(PreparationStatus expected, PreparationStatus target) {
        if (preparationStatus != expected) {
            throw new IllegalStateException(
                    "Cannot change preparation status from " + preparationStatus + " to " + target
            );
        }
        preparationStatus = target;
    }
}
