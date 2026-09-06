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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "restaurant_tables")
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Positive
    @Column(name = "table_number", nullable = false, unique = true)
    private int tableNumber;

    @Positive
    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TableStatus status;

    @OneToMany(mappedBy = "table")
    private List<Order> orders = new ArrayList<>();

    protected RestaurantTable() {
    }

    public RestaurantTable(int tableNumber, int seatCount, Location location) {
        this.tableNumber = tableNumber;
        this.seatCount = seatCount;
        this.location = location;
        this.status = TableStatus.FREE;
    }

    public Long getId() {
        return id;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public int getSeatCount() {
        return seatCount;
    }

    public Location getLocation() {
        return location;
    }

    public TableStatus getStatus() {
        return status;
    }

    public List<Order> getOrders() {
        return Collections.unmodifiableList(orders);
    }

    public void occupy() {
        status = TableStatus.OCCUPIED;
    }

    public void free() {
        status = TableStatus.FREE;
    }

    public void addOrder(Order order) {
        orders.add(order);
    }
}
