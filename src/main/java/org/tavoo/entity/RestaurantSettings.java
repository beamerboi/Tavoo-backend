package org.tavoo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "restaurant_settings")
public class RestaurantSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "coperto_unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal copertoUnitPrice;

    @Version
    @Column(nullable = false)
    private long version;

    protected RestaurantSettings() {
    }

    public RestaurantSettings(BigDecimal copertoUnitPrice) {
        this.id = SINGLETON_ID;
        updateCopertoUnitPrice(copertoUnitPrice);
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getCopertoUnitPrice() {
        return copertoUnitPrice;
    }

    public long getVersion() {
        return version;
    }

    public void updateCopertoUnitPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Coperto unit price cannot be negative");
        }
        this.copertoUnitPrice = price.setScale(2);
    }
}
