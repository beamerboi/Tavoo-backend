package org.tavoo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.tavoo.config.SecurityConfig;
import org.tavoo.repository.UserRepository;
import org.tavoo.service.MenuService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MenuController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties =
        "tavoo.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuService menuService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/menu")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void waiterCanReadMenu() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void kitchenCannotUseWaiterMenuApi() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void waiterCannotCreateMenuItems() throws Exception {
        mockMvc.perform(post("/api/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Tiramisu",
                                  "price": 8.00,
                                  "category": "DESSERT",
                                  "courseType": "DESSERT",
                                  "available": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
