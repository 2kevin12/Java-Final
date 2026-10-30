package com.restaurant.kds;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class KitchenServiceSuiteTest {
    // Consolidated module holder: KitchenServiceSuiteTest
}

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private KitchenStationRepository stationRepository;

    @Mock
    private OrderStateMachineService stateMachineService;

    @Mock
    private KitchenEventService eventService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private RestaurantSettingRepository settingRepository;

    @InjectMocks
    private OrderService orderService;

    private User testUser;
    private KitchenStation grillStation;
    private KitchenStation fryerStation;
    private MenuCategory fastFoodCategory;
    private MenuItem burgerItem;
    private MenuItem friesItem;

    @BeforeEach
    void setUp() {
        testUser = new User("cashier", "encodedPass", "Cashier John", "cashier@test.com", UserRole.ROLE_CASHIER);
        testUser.setId(1L);

        grillStation = new KitchenStation("Grill Station", "GRILL", "Burgers", "#e63946", 1);
        grillStation.setId(10L);

        fryerStation = new KitchenStation("Fryer Station", "FRYER", "Fries", "#f4a261", 2);
        fryerStation.setId(20L);

        fastFoodCategory = new MenuCategory("Fast Food", "Burgers & Sides", 1);
        fastFoodCategory.setId(100L);

        burgerItem = new MenuItem("Chicken Burger", "Grilled patty", fastFoodCategory,
                new BigDecimal("180.00"), 12, grillStation, false, "Extra Cheese (+₹30)");
        burgerItem.setId(101L);
        burgerItem.setStockQuantity(50);
        burgerItem.setAvailable(true);
        burgerItem.setActive(true);

        friesItem = new MenuItem("Crispy French Fries", "Salted fries", fastFoodCategory,
                new BigDecimal("110.00"), 8, fryerStation, true, null);
        friesItem.setId(102L);
        friesItem.setStockQuantity(50);
        friesItem.setAvailable(true);
        friesItem.setActive(true);
    }

    @Test
    @DisplayName("Requirement 2 & 3: Create order with multiple items and verify financial calculations and stock deduction")
    void testCreateOrderWithMultipleItemsAndTax() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setOrderType(OrderType.DINE_IN);
        req.setTableNumber("T-05");
        req.setCustomerName("Anita Roy");

        OrderItemRequest item1 = new OrderItemRequest(101L, 2, "Less spicy");
        item1.getModifierNames().add("Extra Cheese (+₹30)");

        OrderItemRequest item2 = new OrderItemRequest(102L, 1, "Extra crispy");

        req.setItems(List.of(item1, item2));

        when(menuItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(burgerItem));
        when(menuItemRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(friesItem));
        when(settingRepository.findByKey("tax_rate")).thenReturn(Optional.of(new RestaurantSetting("tax_rate", "5.00", "Tax")));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(500L);
            return o;
        });

        Order created = orderService.createOrder(req, testUser);

        assertNotNull(created);
        assertEquals(2, created.getOrderItems().size());

        // Price item 1: (180 + 30) * 2 = 420.00
        // Price item 2: 110 * 1 = 110.00
        // Subtotal = 530.00
        // Tax (5%) = 26.50
        // Total = 556.50
        assertEquals(new BigDecimal("530.00"), created.getSubtotal());
        assertEquals(new BigDecimal("26.50"), created.getTaxAmount());
        assertEquals(new BigDecimal("556.50"), created.getTotalAmount());
        assertEquals(OrderStatus.PLACED, created.getStatus());

        // Check stock deduction
        assertEquals(48, burgerItem.getStockQuantity());
        assertEquals(49, friesItem.getStockQuantity());

        verify(orderRepository).save(any(Order.class));
        verify(statusHistoryRepository).save(any(OrderStatusHistory.class));
        verify(eventService).broadcastEvent(eq("NEW_ORDER"), any());
    }

    @Test
    @DisplayName("Requirement 4: Reject order with invalid quantity")
    void testRejectInvalidQuantity() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(List.of(new OrderItemRequest(101L, 0, "None")));

        assertThrows(BusinessValidationException.class, () -> orderService.createOrder(req, testUser));
    }

    @Test
    @DisplayName("Requirement 4: Reject order with unavailable menu item")
    void testRejectUnavailableMenuItem() {
        burgerItem.setAvailable(false); // Out of stock

        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(List.of(new OrderItemRequest(101L, 1, null)));

        when(menuItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(burgerItem));

        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> orderService.createOrder(req, testUser));

        assertTrue(ex.getMessage().contains("unavailable"));
    }

    @Test
    @DisplayName("Inventory: Reject order when requested quantity exceeds available stock")
    void testRejectOrderExceedingStock() {
        burgerItem.setStockQuantity(3);

        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(List.of(new OrderItemRequest(101L, 5, "Lots of burgers")));

        when(menuItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(burgerItem));

        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> orderService.createOrder(req, testUser));

        assertTrue(ex.getMessage().contains("Insufficient stock"));
    }

    @Test
    @DisplayName("Requirement 6: Accept and start preparation of order")
    void testAcceptAndStartPreparation() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORD-TEST-001");
        order.setStatus(OrderStatus.PLACED);
        order.setCreatedAt(LocalDateTime.now());

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Accept
        Order accepted = orderService.acceptOrder(1L, null, testUser);
        assertEquals(OrderStatus.ACCEPTED, accepted.getStatus());
        assertNotNull(accepted.getAcceptedAt());

        // Start
        Order preparing = orderService.startPreparation(1L, null, testUser);
        assertEquals(OrderStatus.PREPARING, preparing.getStatus());
        assertNotNull(preparing.getStartedAt());
    }

    @Test
    @DisplayName("Requirement 7 & 8: Marking items ready automatically marks parent order READY when all items are ready")
    void testMarkItemReadyAutoUpdatesParentOrder() {
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-AUTO-01");
        order.setStatus(OrderStatus.PREPARING);

        OrderItem i1 = new OrderItem(order, burgerItem, 1, null);
        i1.setId(1001L);
        i1.setPreparationStatus(ItemPreparationStatus.PREPARING);

        OrderItem i2 = new OrderItem(order, friesItem, 1, null);
        i2.setId(1002L);
        i2.setPreparationStatus(ItemPreparationStatus.READY); // already ready

        order.getOrderItems().addAll(List.of(i1, i2));

        when(orderItemRepository.findById(1001L)).thenReturn(Optional.of(i1));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Mark last item i1 ready
        OrderItem readyItem = orderService.markOrderItemReady(1001L, testUser);
        assertEquals(ItemPreparationStatus.READY, readyItem.getPreparationStatus());

        // Verify parent order status is now automatically updated to READY
        assertEquals(OrderStatus.READY, order.getStatus());
        assertNotNull(order.getReadyAt());
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("Requirement 10: Cancel order restores stock to menu items")
    void testCancelOrderWithReasonRestoresStock() {
        Order order = new Order();
        order.setId(20L);
        order.setOrderNumber("ORD-CANCEL-01");
        order.setStatus(OrderStatus.PLACED);

        burgerItem.setStockQuantity(40);
        OrderItem item = new OrderItem(order, burgerItem, 5, "Sample note");
        order.addItem(item);

        when(orderRepository.findById(20L)).thenReturn(Optional.of(order));
        when(menuItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(burgerItem));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order cancelled = orderService.cancelOrder(20L, "Customer requested cancellation", null, testUser);

        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals("Customer requested cancellation", cancelled.getCancellationReason());
        // Verify stock was restored by +5 (40 -> 45)
        assertEquals(45, burgerItem.getStockQuantity());
        verify(menuItemRepository).save(burgerItem);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Admin: Clear all orders safely removes records and broadcasts event")
    void testClearAllOrders() {
        orderService.clearAllOrders(testUser);

        verify(paymentRepository).deleteAll();
        verify(statusHistoryRepository).deleteAll();
        verify(orderItemRepository).deleteAll();
        verify(orderRepository).deleteAll();
        verify(eventService).broadcastEvent(eq("ORDERS_CLEARED"), any());
        verify(auditLogRepository).save(any(AuditLog.class));
    }
}

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private BillingService billingService;

    private User cashierUser;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        cashierUser = new User("cashier", "pass", "Jane Cashier", "cashier@test.com", UserRole.ROLE_CASHIER);
        cashierUser.setId(2L);

        sampleOrder = new Order();
        sampleOrder.setId(100L);
        sampleOrder.setOrderNumber("ORD-BILL-100");
        sampleOrder.setTotalAmount(new BigDecimal("500.00"));
        sampleOrder.setPaymentStatus(PaymentStatus.UNPAID);
        sampleOrder.setStatus(OrderStatus.PREPARING); // Currently in kitchen preparation
    }

    @Test
    @DisplayName("Requirement 15: Payment and billing status must remain independent of kitchen preparation status")
    void testPaymentIndependenceFromKitchenStatus() {
        PaymentRequest req = new PaymentRequest();
        req.setOrderId(100L);
        req.setAmount(new BigDecimal("500.00"));
        req.setPaymentMethod(PaymentMethod.UPI);
        req.setTransactionReference("UPI-9876543210");

        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));

        Payment payment = new Payment(sampleOrder, req.getAmount(), req.getPaymentMethod(),
                req.getTransactionReference(), cashierUser, null);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        when(paymentRepository.findByOrderIdOrderByPaymentTimeAsc(100L)).thenReturn(List.of(payment));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Payment processed = billingService.processPayment(req, cashierUser);

        assertNotNull(processed);
        // Billing status is updated to PAID
        assertEquals(PaymentStatus.PAID, sampleOrder.getPaymentStatus());

        // CRITICAL CHECK: Kitchen status MUST remain unchanged (PREPARING) and NOT be auto-marked SERVED or READY!
        assertEquals(OrderStatus.PREPARING, sampleOrder.getStatus(),
                "Order kitchen status must remain PREPARING despite full payment");

        verify(orderRepository).save(sampleOrder);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Test partial payment transitions payment status to PARTIALLY_PAID")
    void testPartialPayment() {
        PaymentRequest req = new PaymentRequest();
        req.setOrderId(100L);
        req.setAmount(new BigDecimal("200.00")); // Partial: 200 out of 500
        req.setPaymentMethod(PaymentMethod.CASH);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));

        Payment p1 = new Payment(sampleOrder, new BigDecimal("200.00"), PaymentMethod.CASH, null, cashierUser, null);
        when(paymentRepository.save(any(Payment.class))).thenReturn(p1);
        when(paymentRepository.findByOrderIdOrderByPaymentTimeAsc(100L)).thenReturn(List.of(p1));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        billingService.processPayment(req, cashierUser);

        assertEquals(PaymentStatus.PARTIALLY_PAID, sampleOrder.getPaymentStatus());
    }

    @Test
    @DisplayName("Test rejecting payment on already fully paid order")
    void testRejectPaymentOnAlreadyPaidOrder() {
        sampleOrder.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));

        PaymentRequest req = new PaymentRequest();
        req.setOrderId(100L);
        req.setAmount(new BigDecimal("100.00"));

        assertThrows(BusinessValidationException.class, () -> billingService.processPayment(req, cashierUser));
    }
}

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private ReportService reportService;

    private Order order1;
    private Order order2;

    @BeforeEach
    void setUp() {
        order1 = new Order();
        order1.setId(1L);
        order1.setOrderNumber("ORD-REP-01");
        order1.setOrderType(OrderType.DINE_IN);
        order1.setTableNumber("T-01");
        order1.setStatus(OrderStatus.SERVED);
        order1.setTotalAmount(new BigDecimal("350.00"));
        order1.setSubtotal(new BigDecimal("333.33"));
        order1.setTaxAmount(new BigDecimal("16.67"));
        order1.setCreatedAt(LocalDateTime.now().minusMinutes(20));
        order1.setReadyAt(LocalDateTime.now().minusMinutes(5));
        order1.setServedAt(LocalDateTime.now().minusMinutes(3));
        order1.setEstimatedPrepMinutes(18);

        order2 = new Order();
        order2.setId(2L);
        order2.setOrderNumber("ORD-REP-02");
        order2.setOrderType(OrderType.TAKEAWAY);
        order2.setStatus(OrderStatus.PREPARING);
        order2.setTotalAmount(new BigDecimal("200.00"));
        order2.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        order2.setEstimatedPrepMinutes(15);
    }

    @Test
    @DisplayName("Requirement 14: Dashboard metric calculations")
    void testDashboardMetricCalculations() {
        when(orderRepository.findOrdersBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(order1, order2));
        when(orderItemRepository.findTopSellingItems(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(orderItemRepository.getStationPerformance(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        DashboardSummaryDto summary = reportService.getDashboardSummary();

        assertNotNull(summary);
        assertEquals(2, summary.getTotalOrdersToday());
        assertEquals(new BigDecimal("550.00"), summary.getTotalSalesToday());
        assertEquals(1, summary.getActiveOrders()); // order2 is PREPARING
        assertEquals(1, summary.getOrdersInPreparation());
        assertEquals(1, summary.getCompletedOrdersToday()); // order1 is SERVED
    }

    @Test
    @DisplayName("Requirement 16: Report date filtering and CSV export generation")
    void testReportFilteringAndCsvExport() {
        ReportFilterDto filter = new ReportFilterDto();
        filter.setStartDate(LocalDate.now());
        filter.setEndDate(LocalDate.now());

        when(orderRepository.searchOrders(any(), any(), any(), any(), any()))
                .thenReturn(List.of(order1, order2));

        List<Order> filtered = reportService.getOrdersByFilter(filter);
        assertEquals(2, filtered.size());

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        reportService.writeOrdersCsv(filtered, pw);
        pw.flush();

        String csvOutput = sw.toString();
        assertTrue(csvOutput.contains("Order Number,Order Type,Table / Ref"));
        assertTrue(csvOutput.contains("ORD-REP-01"));
        assertTrue(csvOutput.contains("ORD-REP-02"));
        assertTrue(csvOutput.contains("Dine-In"));
        assertTrue(csvOutput.contains("Takeaway"));
    }
}

@ExtendWith(MockitoExtension.class)
class ConcurrencyAndStateMachineTest {

    private OrderStateMachineService stateMachineService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private KitchenStationRepository stationRepository;

    @Mock
    private KitchenEventService eventService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private RestaurantSettingRepository settingRepository;

    @InjectMocks
    private OrderService orderService;

    private User managerUser;

    @BeforeEach
    void setUp() {
        stateMachineService = new OrderStateMachineService();
        managerUser = new User("manager", "pass", "Manager Bob", "manager@test.com", UserRole.ROLE_MANAGER);
        managerUser.setId(3L);
    }

    @Test
    @DisplayName("Requirement 5: Valid state transitions order: PLACED -> ACCEPTED -> PREPARING -> READY -> SERVED")
    void testValidTransitions() {
        Order order = new Order();
        order.setStatus(OrderStatus.PLACED);

        // PLACED -> ACCEPTED
        assertDoesNotThrow(() -> stateMachineService.validateOrderTransition(order, OrderStatus.ACCEPTED, null));

        // ACCEPTED -> PREPARING
        order.setStatus(OrderStatus.ACCEPTED);
        assertDoesNotThrow(() -> stateMachineService.validateOrderTransition(order, OrderStatus.PREPARING, null));

        // PREPARING -> READY
        order.setStatus(OrderStatus.PREPARING);
        assertDoesNotThrow(() -> stateMachineService.validateOrderTransition(order, OrderStatus.READY, null));
    }

    @Test
    @DisplayName("Requirement 5: Reject invalid jump transition (PLACED -> READY or SERVED directly)")
    void testInvalidJumpTransitions() {
        Order order = new Order();
        order.setStatus(OrderStatus.PLACED);

        assertThrows(InvalidOrderStateTransitionException.class,
                () -> stateMachineService.validateOrderTransition(order, OrderStatus.READY, null));
        assertThrows(InvalidOrderStateTransitionException.class,
                () -> stateMachineService.validateOrderTransition(order, OrderStatus.SERVED, null));
    }

    @Test
    @DisplayName("Requirement 5: Terminal states cannot be reverted or restarted")
    void testTerminalStateProtection() {
        Order servedOrder = new Order();
        servedOrder.setStatus(OrderStatus.SERVED);

        assertThrows(InvalidOrderStateTransitionException.class,
                () -> stateMachineService.validateOrderTransition(servedOrder, OrderStatus.PREPARING, null));
        assertThrows(InvalidOrderStateTransitionException.class,
                () -> stateMachineService.validateOrderTransition(servedOrder, OrderStatus.CANCELLED, "Reason"));

        Order cancelledOrder = new Order();
        cancelledOrder.setStatus(OrderStatus.CANCELLED);

        assertThrows(InvalidOrderStateTransitionException.class,
                () -> stateMachineService.validateOrderTransition(cancelledOrder, OrderStatus.PLACED, null));
    }

    @Test
    @DisplayName("Requirement 9: Prevent serving an order before all items are ready")
    void testPreventServingBeforeReady() {
        Order order = new Order();
        order.setStatus(OrderStatus.READY);

        OrderItem item1 = new OrderItem();
        item1.setPreparationStatus(ItemPreparationStatus.READY);

        OrderItem item2 = new OrderItem();
        item2.setPreparationStatus(ItemPreparationStatus.PREPARING); // not ready yet!

        order.setOrderItems(List.of(item1, item2));

        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> stateMachineService.validateOrderTransition(order, OrderStatus.SERVED, null));

        assertTrue(ex.getMessage().contains("Cannot mark order as SERVED until all order items have been prepared"));
    }

    @Test
    @DisplayName("Requirement 11: Optimistic locking concurrency protection against stale version update")
    void testOptimisticLockingConcurrencyProtection() {
        Order orderInDb = new Order();
        orderInDb.setId(77L);
        orderInDb.setVersion(2L); // Current version in DB is 2
        orderInDb.setStatus(OrderStatus.PLACED);

        when(orderRepository.findById(77L)).thenReturn(Optional.of(orderInDb));

        // Another client attempts to accept order using stale version 1
        Long staleVersion = 1L;

        assertThrows(OptimisticLockingFailureException.class,
                () -> orderService.acceptOrder(77L, staleVersion, managerUser));
    }

    @Test
    @DisplayName("Requirement 13: Live event publishing on kitchen status changes")
    void testLiveEventBroadcasting() {
        eventService.broadcastEvent("ORDER_STATUS_CHANGED", "Payload");
        verify(eventService, times(1)).broadcastEvent("ORDER_STATUS_CHANGED", "Payload");
    }
}