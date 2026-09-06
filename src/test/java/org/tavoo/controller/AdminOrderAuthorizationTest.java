package org.tavoo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.tavoo.config.SecurityConfig;
import org.tavoo.repository.UserRepository;
import org.tavoo.service.OrderService;
import org.tavoo.service.RestaurantSettingsService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminOrderController.class, AdminRestaurantSettingsController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties =
        "tavoo.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class AdminOrderAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private RestaurantSettingsService restaurantSettingsService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void archiveRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/orders/archive"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void waiterCannotReadAdminArchive() throws Exception {
        mockMvc.perform(get("/api/admin/orders/archive"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadArchive() throws Exception {
        mockMvc.perform(get("/api/admin/orders/archive"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void waiterCannotManageCopertoPrice() throws Exception {
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadRestaurantSettings() throws Exception {
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk());
    }
}
