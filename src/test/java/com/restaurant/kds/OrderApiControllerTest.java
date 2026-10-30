package com.restaurant.kds;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public @WebMvcTest(OrderApiController.class)
@AutoConfigureMockMvc
class OrderApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private UserService userService;

    @Test
    @WithMockUser(username = "kitchen", roles = {"KITCHEN_STAFF"})
    @DisplayName("Requirement 1 & API: Kitchen staff can retrieve active orders snapshot")
    void testGetActiveOrdersSnapshot() throws Exception {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORD-API-01");
        order.setStatus(OrderStatus.PLACED);
        order.setOrderType(OrderType.DINE_IN);
        order.setTotalAmount(new BigDecimal("250.00"));
        order.setCreatedAt(LocalDateTime.now());

        when(orderService.getActiveOrders()).thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders/active")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-API-01"))
                .andExpect(jsonPath("$[0].status").value("PLACED"));
    }

    @Test
    @WithMockUser(username = "kitchen", roles = {"KITCHEN_STAFF"})
    @DisplayName("API: Accept order endpoint transitions order status")
    void testAcceptOrderEndpoint() throws Exception {
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-API-10");
        order.setStatus(OrderStatus.ACCEPTED);
        order.setOrderType(OrderType.DINE_IN);
        order.setTotalAmount(new BigDecimal("180.00"));
        order.setCreatedAt(LocalDateTime.now());

        when(orderService.acceptOrder(eq(10L), any(), any())).thenReturn(order);

        StatusUpdateRequest req = new StatusUpdateRequest();
        req.setVersion(1L);

        mockMvc.perform(post("/api/orders/10/accept")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-API-10"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("Requirement 1: Unauthenticated request to /api/orders should be rejected")
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/orders/active"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("API: Admin can clear all orders")
    void testClearAllOrdersEndpoint() throws Exception {
        mockMvc.perform(post("/api/orders/clear-all")
                .with(csrf()))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(orderService).clearAllOrders(any());
    }
}