package org.tavoo.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tavoo.entity.CourseType;
import org.tavoo.entity.Location;
import org.tavoo.entity.LocationType;
import org.tavoo.entity.MenuCategory;
import org.tavoo.entity.MenuItem;
import org.tavoo.entity.Order;
import org.tavoo.entity.OrderStatus;
import org.tavoo.entity.PaymentMethod;
import org.tavoo.entity.PreparationStatus;
import org.tavoo.entity.RestaurantSettings;
import org.tavoo.entity.RestaurantTable;
import org.tavoo.entity.Role;
import org.tavoo.entity.User;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PersistenceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RestaurantSettingsRepository settingsRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void flywayMigrationsAndJpaMappingsPersistAnOrderAggregate() {
        RestaurantSettings settings = settingsRepository
                .findById(RestaurantSettings.SINGLETON_ID)
                .orElseThrow();
        assertThat(settings.getCopertoUnitPrice()).isEqualByComparingTo("2.50");

        Location location = locationRepository.save(new Location("Terrace", LocationType.OUTSIDE));
        RestaurantTable table = tableRepository.save(new RestaurantTable(7, 4, location));
        User waiter = userRepository.save(new User("integration-waiter", "encoded", Role.WAITER));
        MenuItem menuItem = menuItemRepository.save(new MenuItem(
                "Test pasta",
                new BigDecimal("12.50"),
                MenuCategory.FOOD,
                CourseType.PRIMO,
                true
        ));

        Order order = new Order(table, waiter, 2, settings.getCopertoUnitPrice());
        order.addItem(menuItem, 2, "No cheese", new BigDecimal("0.1000"), 1)
                .releaseForPreparation();
        order.markPaid(
                new BigDecimal("30.00"),
                PaymentMethod.POS,
                Instant.parse("2026-09-02T10:15:30Z")
        );
        orderRepository.saveAndFlush(order);
        entityManager.clear();

        Order persisted = orderRepository.findByIdForUpdate(order.getId()).orElseThrow();
        assertThat(persisted.getTable().getLocation().getName()).isEqualTo("Terrace");
        assertThat(persisted.getWaiter().getUsername()).isEqualTo("integration-waiter");
        assertThat(persisted.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMenuItem().getName()).isEqualTo("Test pasta");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("12.50");
            assertThat(item.getTaxRate()).isEqualByComparingTo("0.1000");
            assertThat(item.getPreparationStatus()).isEqualTo(PreparationStatus.ORDERED);
        });

        assertThat(orderRepository
                .findByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDescIdDesc(
                        OrderStatus.PAID,
                        Instant.parse("2026-09-02T00:00:00Z"),
                        Instant.parse("2026-09-03T00:00:00Z")
                ))
                .extracting(Order::getId)
                .containsExactly(order.getId());
    }
}
