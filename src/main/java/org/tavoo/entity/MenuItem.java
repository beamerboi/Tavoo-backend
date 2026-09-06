package org.tavoo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuCategory category;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "course_type", nullable = false, length = 20)
    private CourseType courseType;

    @OneToMany(mappedBy = "menuItem")
    private List<OrderItem> orderItems = new ArrayList<>();

    protected MenuItem() {
    }

    public MenuItem(
            String name,
            BigDecimal price,
            MenuCategory category,
            CourseType courseType,
            boolean available
    ) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.courseType = courseType;
        this.available = available;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public MenuCategory getCategory() {
        return category;
    }

    public boolean isAvailable() {
        return available;
    }

    public CourseType getCourseType() {
        return courseType;
    }

    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }
}
