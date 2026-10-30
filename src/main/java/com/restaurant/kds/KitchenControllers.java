package com.restaurant.kds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class KitchenControllers {
    // Consolidated module holder: KitchenControllers
}

@RestController
@RequestMapping("/api")
class OrderApiController {

    private final OrderService orderService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public OrderApiController(OrderService orderService, UserService userService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    private User getAuthenticatedUser(UserDetails userDetails) {
        if (userDetails != null) {
            return userService.getUserByUsername(userDetails.getUsername());
        }
        return null;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponseDto> createOrder(@Valid @RequestBody OrderCreateRequest request,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        Order order = orderService.createOrder(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponseDto.fromEntity(order));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponseDto>> getActiveOrders() {
        List<OrderResponseDto> dtos = orderService.getActiveOrders().stream()
                .map(OrderResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/orders/active")
    public ResponseEntity<List<OrderResponseDto>> getActiveOrdersSnapshot() {
        List<OrderResponseDto> dtos = orderService.getActiveOrders().stream()
                .map(OrderResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderResponseDto> getOrderById(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        return ResponseEntity.ok(OrderResponseDto.fromEntity(order));
    }

    private StatusUpdateRequest parseStatusUpdateRequest(String rawBody, Long queryVersion, String queryReason) {
        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setVersion(queryVersion);
        request.setReason(queryReason);
        if (rawBody != null && !rawBody.trim().isEmpty()) {
            try {
                JsonNode node = objectMapper.readTree(rawBody);
                if (node.has("version") && !node.get("version").isNull()) {
                    request.setVersion(node.get("version").asLong());
                }
                if (node.has("reason") && !node.get("reason").isNull()) {
                    request.setReason(node.get("reason").asText());
                }
            } catch (Exception ignored) {
            }
        }
        return request;
    }

    @PostMapping("/orders/{id}/accept")
    public ResponseEntity<OrderResponseDto> acceptOrder(@PathVariable Long id,
                                                        @RequestBody(required = false) String rawBody,
                                                        @RequestParam(value = "version", required = false) Long version,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        StatusUpdateRequest req = parseStatusUpdateRequest(rawBody, version, null);
        Order order = orderService.acceptOrder(id, req.getVersion(), getAuthenticatedUser(userDetails));
        return ResponseEntity.ok(OrderResponseDto.fromEntity(order));
    }

    @PostMapping("/orders/{id}/start")
    public ResponseEntity<OrderResponseDto> startOrderPreparation(@PathVariable Long id,
                                                                  @RequestBody(required = false) String rawBody,
                                                                  @RequestParam(value = "version", required = false) Long version,
                                                                  @AuthenticationPrincipal UserDetails userDetails) {
        StatusUpdateRequest req = parseStatusUpdateRequest(rawBody, version, null);
        Order order = orderService.startPreparation(id, req.getVersion(), getAuthenticatedUser(userDetails));
        return ResponseEntity.ok(OrderResponseDto.fromEntity(order));
    }

    @PostMapping("/orders/{id}/ready")
    public ResponseEntity<OrderResponseDto> markOrderReady(@PathVariable Long id,
                                                           @RequestBody(required = false) String rawBody,
                                                           @RequestParam(value = "version", required = false) Long version,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        StatusUpdateRequest req = parseStatusUpdateRequest(rawBody, version, null);
        Order order = orderService.markOrderReady(id, req.getVersion(), getAuthenticatedUser(userDetails));
        return ResponseEntity.ok(OrderResponseDto.fromEntity(order));
    }

    @PostMapping("/orders/{id}/serve")
    public ResponseEntity<OrderResponseDto> serveOrder(@PathVariable Long id,
                                                       @RequestBody(required = false) String rawBody,
                                                       @RequestParam(value = "version", required = false) Long version,
                                                       @AuthenticationPrincipal UserDetails userDetails) {
        StatusUpdateRequest req = parseStatusUpdateRequest(rawBody, version, null);
        Order order = orderService.serveOrder(id, req.getVersion(), getAuthenticatedUser(userDetails));
        return ResponseEntity.ok(OrderResponseDto.fromEntity(order));
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<OrderResponseDto> cancelOrder(@PathVariable Long id,
                                                        @RequestBody(required = false) String rawBody,
                                                        @RequestParam(value = "reason", required = false) String reason,
                                                        @RequestParam(value = "version", required = false) Long version,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        StatusUpdateRequest req = parseStatusUpdateRequest(rawBody, version, reason);
        Order order = orderService.cancelOrder(id, req.getReason(), req.getVersion(), getAuthenticatedUser(userDetails));
        return ResponseEntity.ok(OrderResponseDto.fromEntity(order));
    }

    @PostMapping("/orders/{id}/reject")
    public ResponseEntity<OrderResponseDto> rejectOrder(@PathVariable Long id,
                                                        @RequestBody(required = false) String rawBody,
                                                        @RequestParam(value = "reason", required = false) String reason,
                                                        @RequestParam(value = "version", required = false) Long version,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        StatusUpdateRequest req = parseStatusUpdateRequest(rawBody, version, reason);
        Order order = orderService.rejectOrder(id, req.getReason(), req.getVersion(), getAuthenticatedUser(userDetails));
        return ResponseEntity.ok(OrderResponseDto.fromEntity(order));
    }

    @PostMapping("/orders/{id}/priority")
    public ResponseEntity<OrderResponseDto> setPriority(@PathVariable Long id,
                                                        @RequestParam("priority") OrderPriority priority,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        Order order = orderService.updatePriority(id, priority, getAuthenticatedUser(userDetails));
        return ResponseEntity.ok(OrderResponseDto.fromEntity(order));
    }

    @GetMapping("/orders/{id}/history")
    public ResponseEntity<List<OrderStatusHistory>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderStatusHistory(id));
    }

    @PostMapping("/order-items/{id}/start")
    public ResponseEntity<OrderItemResponseDto> startOrderItem(@PathVariable Long id,
                                                               @AuthenticationPrincipal UserDetails userDetails) {
        OrderItem item = orderService.startOrderItemPreparation(id, getAuthenticatedUser(userDetails));
        return ResponseEntity.ok(OrderItemResponseDto.fromEntity(item));
    }

    @PostMapping("/order-items/{id}/ready")
    public ResponseEntity<OrderItemResponseDto> readyOrderItem(@PathVariable Long id,
                                                               @AuthenticationPrincipal UserDetails userDetails) {
        OrderItem item = orderService.markOrderItemReady(id, getAuthenticatedUser(userDetails));
        return ResponseEntity.ok(OrderItemResponseDto.fromEntity(item));
    }

    @PostMapping("/orders/clear-all")
    public ResponseEntity<Void> clearAllOrders(@AuthenticationPrincipal UserDetails userDetails) {
        orderService.clearAllOrders(getAuthenticatedUser(userDetails));
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/api/menu")
class MenuApiController {

    private final MenuService menuService;
    private final UserService userService;

    public MenuApiController(MenuService menuService, UserService userService) {
        this.menuService = menuService;
        this.userService = userService;
    }

    private User getAuthenticatedUser(UserDetails userDetails) {
        if (userDetails != null) {
            return userService.getUserByUsername(userDetails.getUsername());
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<List<MenuItemDto>> getMenu(@RequestParam(required = false) Long categoryId,
                                                     @RequestParam(required = false) Long stationId,
                                                     @RequestParam(required = false) Boolean available,
                                                     @RequestParam(required = false) String search) {
        List<MenuItemDto> items = menuService.searchMenuItems(categoryId, stationId, available, search)
                .stream().map(MenuItemDto::fromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(items);
    }

    @PostMapping
    public ResponseEntity<MenuItemDto> createMenuItem(@Valid @RequestBody MenuItemDto dto,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        MenuItem item = menuService.createMenuItem(dto, getAuthenticatedUser(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED).body(MenuItemDto.fromEntity(item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuItemDto> updateMenuItem(@PathVariable Long id,
                                                      @Valid @RequestBody MenuItemDto dto,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        MenuItem item = menuService.updateMenuItem(id, dto, getAuthenticatedUser(userDetails));
        return ResponseEntity.ok(MenuItemDto.fromEntity(item));
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<MenuItemDto> updateStock(@PathVariable Long id,
                                                   @RequestParam("stockQuantity") int stockQuantity,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        MenuItem item = menuService.updateStock(id, stockQuantity, getAuthenticatedUser(userDetails));
        return ResponseEntity.ok(MenuItemDto.fromEntity(item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenuItem(@PathVariable Long id,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        menuService.deleteMenuItem(id, getAuthenticatedUser(userDetails));
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/api/stations")
class StationApiController {

    private final KitchenStationService stationService;
    private final UserService userService;

    public StationApiController(KitchenStationService stationService, UserService userService) {
        this.stationService = stationService;
        this.userService = userService;
    }

    private User getAuthenticatedUser(UserDetails userDetails) {
        if (userDetails != null) {
            return userService.getUserByUsername(userDetails.getUsername());
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<List<KitchenStationDto>> getAllStations() {
        List<KitchenStationDto> dtos = stationService.getActiveStations().stream()
                .map(KitchenStationDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<KitchenStationDto> createStation(@Valid @RequestBody KitchenStationDto dto,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        KitchenStation station = stationService.createStation(dto, getAuthenticatedUser(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED).body(KitchenStationDto.fromEntity(station));
    }
}

@RestController
@RequestMapping("/api/billing")
class BillingApiController {

    private final BillingService billingService;
    private final OrderService orderService;
    private final UserService userService;

    public BillingApiController(BillingService billingService,
                                OrderService orderService,
                                UserService userService) {
        this.billingService = billingService;
        this.orderService = orderService;
        this.userService = userService;
    }

    private User getAuthenticatedUser(UserDetails userDetails) {
        if (userDetails != null) {
            return userService.getUserByUsername(userDetails.getUsername());
        }
        return null;
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrderBillingDetails(@PathVariable Long orderId) {
        Order order = orderService.getOrderById(orderId);
        List<Payment> payments = billingService.getPaymentsForOrder(orderId);

        BigDecimal totalPaid = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = order.getTotalAmount().subtract(totalPaid);
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            balance = BigDecimal.ZERO;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getId());
        response.put("orderNumber", order.getOrderNumber());
        response.put("subtotal", order.getSubtotal());
        response.put("taxAmount", order.getTaxAmount());
        response.put("discountAmount", order.getDiscountAmount());
        response.put("totalAmount", order.getTotalAmount());
        response.put("paymentStatus", order.getPaymentStatus());
        response.put("totalPaid", totalPaid);
        response.put("balanceDue", balance);
        response.put("payments", payments);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders/{orderId}/pay")
    public ResponseEntity<Map<String, Object>> processPayment(@PathVariable Long orderId,
                                                              @Valid @RequestBody PaymentRequest request,
                                                              @AuthenticationPrincipal UserDetails userDetails) {
        request.setOrderId(orderId);
        User currentUser = getAuthenticatedUser(userDetails);
        Payment payment = billingService.processPayment(request, currentUser);
        Order updatedOrder = orderService.getOrderById(orderId);

        Map<String, Object> response = new HashMap<>();
        response.put("paymentId", payment.getId());
        response.put("orderId", updatedOrder.getId());
        response.put("orderNumber", updatedOrder.getOrderNumber());
        response.put("paidAmount", payment.getAmount());
        response.put("paymentMethod", payment.getPaymentMethod());
        response.put("paymentStatus", updatedOrder.getPaymentStatus());
        response.put("transactionReference", payment.getTransactionReference());
        response.put("timestamp", payment.getPaymentTime());

        return ResponseEntity.ok(response);
    }
}

@RestController
@RequestMapping("/api/reports")
class ReportApiController {

    private final ReportService reportService;

    public ReportApiController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily")
    public ResponseEntity<List<OrderResponseDto>> getDailyReport(@ModelAttribute ReportFilterDto filter) {
        List<OrderResponseDto> dtos = reportService.getOrdersByFilter(filter).stream()
                .map(OrderResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/kitchen-performance")
    public ResponseEntity<DashboardSummaryDto> getKitchenPerformance() {
        return ResponseEntity.ok(reportService.getDashboardSummary());
    }

    @GetMapping("/export")
    public void exportCsv(@ModelAttribute ReportFilterDto filter, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"kds-orders-report-" + LocalDate.now() + ".csv\"");
        List<Order> orders = reportService.getOrdersByFilter(filter);
        reportService.writeOrdersCsv(orders, response.getWriter());
    }
}

@RestController
@RequestMapping("/api/dashboard")
class DashboardApiController {

    private final ReportService reportService;

    public DashboardApiController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDto> getSummary() {
        return ResponseEntity.ok(reportService.getDashboardSummary());
    }
}

@RestController
@RequestMapping("/api/simulation/multithreading")
class KitchenSimulationApiController {

    private final KitchenSimulationService simulationService;

    public KitchenSimulationApiController(KitchenSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runRubricSimulation(
            @RequestParam(defaultValue = "150") long chefCookTimeMs,
            @RequestParam(defaultValue = "15") long waiterDelayMs) {
        Map<String, Object> report = simulationService.executeStandardRubricSimulation(chefCookTimeMs, waiterDelayMs);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/overload")
    public ResponseEntity<Map<String, Object>> runOverloadSimulation() {
        Map<String, Object> report = simulationService.executeEmergencyOverloadSimulation();
        return ResponseEntity.ok(report);
    }
}

@RestController
@RequestMapping("/api/kitchen")
class KitchenSseApiController {

    private final KitchenEventService eventService;

    public KitchenSseApiController(KitchenEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamKitchenEvents() {
        return eventService.registerEmitter();
    }
}


@org.springframework.stereotype.Controller
class WebViewController {
    @org.springframework.web.bind.annotation.GetMapping({
        "/", "/dashboard", "/kds", "/kds/**",
        "/orders", "/orders/**",
        "/menu", "/menu/**",
        "/stations", "/stations/**",
        "/billing", "/billing/**",
        "/reports", "/reports/**",
        "/settings", "/settings/**",
        "/users", "/users/**",
        "/audit", "/audit/**",
        "/login", "/error"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
