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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({OrderController.class, KitchenController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties =
        "tavoo.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class CourseWorkflowAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(username = "waiter", roles = "WAITER")
    void waiterCanDeliverOwnedCourse() throws Exception {
        mockMvc.perform(post("/api/orders/3/courses/1/delivered"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void kitchenCannotUseWaiterDeliveryApi() throws Exception {
        mockMvc.perform(post("/api/orders/3/courses/1/delivered"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void kitchenCanStartPreparationGroup() throws Exception {
        mockMvc.perform(post("/api/kitchen/orders/3/courses/1/start"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void waiterCannotStartKitchenPreparationGroup() throws Exception {
        mockMvc.perform(post("/api/kitchen/orders/3/courses/1/start"))
                .andExpect(status().isForbidden());
    }
}
