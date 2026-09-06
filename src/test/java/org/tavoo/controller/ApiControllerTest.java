package org.tavoo.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tavoo.dto.AddOrderItemRequest;
import org.tavoo.dto.CreateLocationRequest;
import org.tavoo.dto.CreateMenuItemRequest;
import org.tavoo.dto.CreateOrderRequest;
import org.tavoo.dto.CreateRestaurantTableRequest;
import org.tavoo.dto.CheckResponse;
import org.tavoo.dto.LocationResponse;
import org.tavoo.dto.MenuItemResponse;
import org.tavoo.dto.OrderResponse;
import org.tavoo.dto.OrderArchiveDayResponse;
import org.tavoo.dto.RestaurantTableResponse;
import org.tavoo.entity.MenuCategory;
import org.tavoo.entity.CourseType;
import org.tavoo.entity.LocationType;
import org.tavoo.entity.OrderStatus;
import org.tavoo.entity.PaymentMethod;
import org.tavoo.entity.TableStatus;
import org.tavoo.exception.GlobalExceptionHandler;
import org.tavoo.exception.TableOccupiedException;
import org.tavoo.service.LocationService;
import org.tavoo.service.MenuService;
import org.tavoo.service.OrderService;
import org.tavoo.service.RestaurantTableService;
import org.tavoo.service.RestaurantSettingsService;
import org.tavoo.dto.RestaurantSettingsResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiControllerTest {

    @Mock
    private MenuService menuService;

    @Mock
    private RestaurantTableService restaurantTableService;

    @Mock
    private LocationService locationService;

    @Mock
    private OrderService orderService;

    @Mock
    private RestaurantSettingsService restaurantSettingsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MenuController(menuService),
                        new LocationController(locationService),
                        new RestaurantTableController(restaurantTableService),
                        new OrderController(orderService),
                        new KitchenController(orderService),
                        new AdminOrderController(orderService),
                        new AdminRestaurantSettingsController(restaurantSettingsService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getsAvailableMenuItems() throws Exception {
        when(menuService.getAvailableMenuItems()).thenReturn(List.of(menuResponse()));

        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Pasta"));
    }

    @Test
    void createsMenuItemWithCreatedStatus() throws Exception {
        when(menuService.createMenuItem(any(CreateMenuItemRequest.class))).thenReturn(menuResponse());

        mockMvc.perform(post("/api/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pasta",
                                  "price": 12.50,
                                  "category": "FOOD",
                                  "courseType": "PRIMO",
                                  "available": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("FOOD"));
    }

    @Test
    void rejectsInvalidMenuRequest() throws Exception {
        mockMvc.perform(post("/api/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "price": 0,
                                  "category": "FOOD",
                                  "courseType": "PRIMO",
                                  "available": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.price").exists());
    }

    @Test
    void getsRestaurantTableStates() throws Exception {
        when(restaurantTableService.getTables())
                .thenReturn(List.of(new RestaurantTableResponse(
                        1L, 4, 6,
                        new LocationResponse(2L, "Main Hall", LocationType.INSIDE),
                        TableStatus.FREE
                )));

        mockMvc.perform(get("/api/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].seatCount").value(6))
                .andExpect(jsonPath("$[0].location.name").value("Main Hall"))
                .andExpect(jsonPath("$[0].location.type").value("INSIDE"))
                .andExpect(jsonPath("$[0].status").value("FREE"));
    }

    @Test
    void createsRestaurantTableWithCreatedStatus() throws Exception {
        when(restaurantTableService.createTable(any(CreateRestaurantTableRequest.class)))
                .thenReturn(new RestaurantTableResponse(
                        1L, 4, 6,
                        new LocationResponse(3L, "Terrace", LocationType.OUTSIDE),
                        TableStatus.FREE
                ));

        mockMvc.perform(post("/api/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableNumber": 4, "seatCount": 6, "locationId": 3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableNumber").value(4))
                .andExpect(jsonPath("$.seatCount").value(6))
                .andExpect(jsonPath("$.location.id").value(3))
                .andExpect(jsonPath("$.location.type").value("OUTSIDE"))
                .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    void rejectsInvalidRestaurantTableNumber() throws Exception {
        mockMvc.perform(post("/api/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableNumber": 0, "seatCount": 0, "locationId": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.tableNumber").exists())
                .andExpect(jsonPath("$.validationErrors.seatCount").exists())
                .andExpect(jsonPath("$.validationErrors.locationId").exists());
    }

    @Test
    void createsLocationWithCreatedStatus() throws Exception {
        when(locationService.createLocation(any(CreateLocationRequest.class)))
                .thenReturn(new LocationResponse(3L, "Terrace", LocationType.OUTSIDE));

        mockMvc.perform(post("/api/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Terrace", "type": "OUTSIDE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Terrace"))
                .andExpect(jsonPath("$.type").value("OUTSIDE"));
    }

    @Test
    void getsLocations() throws Exception {
        when(locationService.getLocations())
                .thenReturn(List.of(new LocationResponse(2L, "Main Hall", LocationType.INSIDE)));

        mockMvc.perform(get("/api/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Main Hall"));
    }

    @Test
    void rejectsUnsupportedLocationType() throws Exception {
        mockMvc.perform(post("/api/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Roof", "type": "ROOFTOP"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void opensOrderWithCreatedStatus() throws Exception {
        when(orderService.openOrder(any(CreateOrderRequest.class), anyString())).thenReturn(orderResponse());

        mockMvc.perform(post("/api/orders")
                        .principal(() -> "waiter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tableId": 1,
                                  "copertoCount": 2,
                                  "items": [{"menuItemId": 1, "quantity": 2, "notes": null}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void mapsOccupiedTableToConflict() throws Exception {
        when(orderService.openOrder(any(CreateOrderRequest.class), anyString()))
                .thenThrow(new TableOccupiedException(1L));

        mockMvc.perform(post("/api/orders")
                        .principal(() -> "waiter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tableId": 1,
                                  "copertoCount": 2,
                                  "items": [{"menuItemId": 1, "quantity": 1, "notes": null}]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void addsOrderItem() throws Exception {
        when(orderService.addItem(any(Long.class), any(AddOrderItemRequest.class), anyString()))
                .thenReturn(orderResponse());

        mockMvc.perform(put("/api/orders/3/items")
                        .principal(() -> "waiter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"menuItemId": 1, "quantity": 2, "notes": "No cheese"}
                                """))
                .andExpect(status().isOk());

        verify(orderService).addItem(any(Long.class), any(AddOrderItemRequest.class), anyString());
    }

    @Test
    void paysOrder() throws Exception {
        when(orderService.payOrder(3L, PaymentMethod.POS, "waiter")).thenReturn(checkResponse());

        mockMvc.perform(post("/api/orders/3/pay")
                        .principal(() -> "waiter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod": "POS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("POS"));

        verify(orderService).payOrder(3L, PaymentMethod.POS, "waiter");
    }

    @Test
    void rejectsPaymentWithoutMethod() throws Exception {
        mockMvc.perform(post("/api/orders/3/pay")
                        .principal(() -> "waiter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.paymentMethod").exists());
    }

    @Test
    void getsAdminArchiveGroupedByDay() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 22);
        when(orderService.getPaidOrderArchive(date, date)).thenReturn(List.of(
                new OrderArchiveDayResponse(
                        date,
                        1,
                        new BigDecimal("11.00"),
                        List.of(orderResponse())
                )
        ));

        mockMvc.perform(get("/api/admin/orders/archive")
                        .param("from", "2026-08-22")
                        .param("to", "2026-08-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-08-22"))
                .andExpect(jsonPath("$[0].orderCount").value(1))
                .andExpect(jsonPath("$[0].totalAmount").value(11.00));
    }

    @Test
    void updatesCopertoPrice() throws Exception {
        when(restaurantSettingsService.updateCopertoUnitPrice(new BigDecimal("3.00")))
                .thenReturn(new RestaurantSettingsResponse(new BigDecimal("3.00"), 1));

        mockMvc.perform(put("/api/admin/settings/coperto-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"unitPrice": 3.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copertoUnitPrice").value(3.00));
    }

    @Test
    void getsOrder() throws Exception {
        when(orderService.getOrder(3L, "waiter")).thenReturn(orderResponse());

        mockMvc.perform(get("/api/orders/3").principal(() -> "waiter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNumber").value(4));
    }

    @Test
    void kitchenStartsPreparationPriorityGroup() throws Exception {
        when(orderService.startCourse(3L, 1)).thenReturn(orderResponse());

        mockMvc.perform(post("/api/kitchen/orders/3/courses/1/start"))
                .andExpect(status().isOk());

        verify(orderService).startCourse(3L, 1);
    }

    @Test
    void waiterMarksGroupDeliveredAndReleasesNext() throws Exception {
        when(orderService.deliverCourse(3L, 1, "waiter")).thenReturn(orderResponse());
        when(orderService.releaseNextCourse(3L, 1, "waiter")).thenReturn(orderResponse());

        mockMvc.perform(post("/api/orders/3/courses/1/delivered")
                        .principal(() -> "waiter"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/3/courses/1/release-next")
                        .principal(() -> "waiter"))
                .andExpect(status().isOk());

        verify(orderService).deliverCourse(3L, 1, "waiter");
        verify(orderService).releaseNextCourse(3L, 1, "waiter");
    }

    @Test
    void rejectsNonNumericOrderIdAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/orders/not-a-number").principal(() -> "waiter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private MenuItemResponse menuResponse() {
        return new MenuItemResponse(
                1L,
                "Pasta",
                new BigDecimal("12.50"),
                MenuCategory.FOOD,
                CourseType.PRIMO,
                true
        );
    }

    private OrderResponse orderResponse() {
        return new OrderResponse(
                3L,
                1L,
                4,
                2L,
                "server",
                OrderStatus.OPEN,
                new BigDecimal("0.00"),
                2,
                new BigDecimal("2.50"),
                new BigDecimal("5.00"),
                null,
                null,
                List.of()
        );
    }

    private CheckResponse checkResponse() {
        Instant paidAt = Instant.parse("2026-08-22T10:15:30Z");
        return new CheckResponse(
                3L,
                OrderStatus.PAID,
                1L,
                4,
                2L,
                "waiter",
                paidAt,
                paidAt,
                PaymentMethod.POS,
                new BigDecimal("10.00"),
                new BigDecimal("1.00"),
                2,
                new BigDecimal("2.50"),
                new BigDecimal("5.00"),
                new BigDecimal("11.00"),
                List.of()
        );
    }
}
