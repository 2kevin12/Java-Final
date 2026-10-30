package com.restaurant.kds;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class KitchenServices {
    // Consolidated module holder: KitchenServices
}

@Service
class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final MenuItemRepository menuItemRepository;
    private final KitchenStationRepository stationRepository;
    private final OrderStateMachineService stateMachineService;
    private final KitchenEventService eventService;
    private final AuditLogRepository auditLogRepository;
    private final RestaurantSettingRepository settingRepository;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        OrderStatusHistoryRepository statusHistoryRepository,
                        PaymentRepository paymentRepository,
                        MenuItemRepository menuItemRepository,
                        KitchenStationRepository stationRepository,
                        OrderStateMachineService stateMachineService,
                        KitchenEventService eventService,
                        AuditLogRepository auditLogRepository,
                        RestaurantSettingRepository settingRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.paymentRepository = paymentRepository;
        this.menuItemRepository = menuItemRepository;
        this.stationRepository = stationRepository;
        this.stateMachineService = stateMachineService;
        this.eventService = eventService;
        this.auditLogRepository = auditLogRepository;
        this.settingRepository = settingRepository;
    }

    @Transactional
    public Order createOrder(OrderCreateRequest request, User currentUser) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessValidationException("Order must contain at least one item");
        }

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setOrderType(request.getOrderType());
        order.setTableNumber(request.getTableNumber());
        order.setCustomerName(request.getCustomerName());
        order.setCustomerPhone(request.getCustomerPhone());
        order.setSpecialInstructions(request.getSpecialInstructions());
        order.setPriority(request.getPriority() != null ? request.getPriority() : OrderPriority.NORMAL);
        order.setCreatedBy(currentUser);
        order.setStatus(OrderStatus.PLACED);
        order.setCreatedAt(LocalDateTime.now());

        // Read configured tax rate
        BigDecimal taxRate = new BigDecimal("5.00");
        Optional<RestaurantSetting> taxSetting = settingRepository.findByKey("tax_rate");
        if (taxSetting.isPresent()) {
            try {
                taxRate = new BigDecimal(taxSetting.get().getValue());
            } catch (Exception ignored) {}
        }
        order.setTaxRate(taxRate);

        for (OrderItemRequest itemReq : request.getItems()) {
            if (itemReq.getQuantity() == null || itemReq.getQuantity() < 1) {
                throw new BusinessValidationException("Quantity must be at least 1");
            }

            MenuItem menuItem = menuItemRepository.findByIdForUpdate(itemReq.getMenuItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with ID: " + itemReq.getMenuItemId()));

            if (!menuItem.isActive() || !menuItem.isAvailable() || menuItem.isOutOfStock()) {
                throw new BusinessValidationException(
                        String.format("Menu item '%s' is currently out of stock or unavailable and cannot be ordered", menuItem.getName()));
            }

            int currentStock = menuItem.getStockQuantity() != null ? menuItem.getStockQuantity() : 0;
            if (currentStock < itemReq.getQuantity()) {
                throw new BusinessValidationException(
                        String.format("Insufficient stock for '%s'. Available: %d, requested: %d",
                                menuItem.getName(), currentStock, itemReq.getQuantity()));
            }

            // Deduct stock atomically
            menuItem.setStockQuantity(currentStock - itemReq.getQuantity());
            if (menuItem.getStockQuantity() <= 0) {
                menuItem.setAvailable(false);
            }
            menuItemRepository.save(menuItem);

            OrderItem orderItem = new OrderItem(order, menuItem, itemReq.getQuantity(), itemReq.getSpecialInstructions());

            // Handle optional modifiers
            if (itemReq.getModifierNames() != null && !itemReq.getModifierNames().isEmpty()) {
                for (String modName : itemReq.getModifierNames()) {
                    if (modName != null && !modName.trim().isEmpty()) {
                        // Check if modifier has price note like (+₹30)
                        BigDecimal modPrice = BigDecimal.ZERO;
                        if (modName.contains("+₹")) {
                            try {
                                String pricePart = modName.substring(modName.indexOf("+₹") + 2).replaceAll("[^0-9.]", "");
                                if (!pricePart.isEmpty()) {
                                    modPrice = new BigDecimal(pricePart);
                                }
                            } catch (Exception ignored) {}
                        }
                        orderItem.addModifier(modName.trim(), modPrice);
                    }
                }
            }

            order.addItem(orderItem);
        }

        order.recalculateTotals();

        // Check if auto-accept is enabled in settings
        Optional<RestaurantSetting> autoAccept = settingRepository.findByKey("auto_accept_orders");
        if (autoAccept.isPresent() && "true".equalsIgnoreCase(autoAccept.get().getValue())) {
            order.setStatus(OrderStatus.ACCEPTED);
            order.setAcceptedAt(LocalDateTime.now());
        }

        Order savedOrder = orderRepository.save(order);

        // Record history
        recordHistory(savedOrder, null, savedOrder.getStatus(), currentUser, "Order created");

        // Audit log
        auditLogRepository.save(new AuditLog(
                "ORDER_CREATED",
                currentUser,
                "ORDER",
                savedOrder.getId(),
                String.format("Order #%s created with %d items. Total: ₹%s",
                        savedOrder.getOrderNumber(), savedOrder.getOrderItems().size(), savedOrder.getTotalAmount())
        ));

        // Live broadcast
        eventService.broadcastEvent("NEW_ORDER", OrderResponseDto.fromEntity(savedOrder));

        return savedOrder;
    }

    private synchronized String generateOrderNumber() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomSuffix = String.format("%04d", new Random().nextInt(10000));
        String orderNumber = "ORD-" + datePrefix + "-" + randomSuffix;
        while (orderRepository.existsByOrderNumber(orderNumber)) {
            randomSuffix = String.format("%04d", new Random().nextInt(10000));
            orderNumber = "ORD-" + datePrefix + "-" + randomSuffix;
        }
        return orderNumber;
    }

    @Transactional
    public Order acceptOrder(Long orderId, Long version, User currentUser) {
        Order order = getOrderWithVersionCheck(orderId, version);
        stateMachineService.validateOrderTransition(order, OrderStatus.ACCEPTED, null);

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.ACCEPTED);
        order.setAcceptedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        recordHistory(saved, prev, OrderStatus.ACCEPTED, currentUser, "Order accepted by kitchen");
        eventService.broadcastEvent("ORDER_STATUS_CHANGED", OrderResponseDto.fromEntity(saved));
        return saved;
    }

    @Transactional
    public Order startPreparation(Long orderId, Long version, User currentUser) {
        Order order = getOrderWithVersionCheck(orderId, version);
        stateMachineService.validateOrderTransition(order, OrderStatus.PREPARING, null);

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.PREPARING);
        order.setStartedAt(LocalDateTime.now());

        // Update pending items to preparing
        for (OrderItem item : order.getOrderItems()) {
            if (item.getPreparationStatus() == ItemPreparationStatus.PENDING) {
                item.setPreparationStatus(ItemPreparationStatus.PREPARING);
                item.setStartedAt(LocalDateTime.now());
            }
        }

        Order saved = orderRepository.save(order);
        recordHistory(saved, prev, OrderStatus.PREPARING, currentUser, "Preparation started");
        eventService.broadcastEvent("ORDER_STATUS_CHANGED", OrderResponseDto.fromEntity(saved));
        return saved;
    }

    @Transactional
    public OrderItem startOrderItemPreparation(Long itemId, User currentUser) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with ID: " + itemId));

        Order order = item.getOrder();
        if (order.getStatus().isTerminal()) {
            throw new BusinessValidationException("Cannot update item for an order in terminal state " + order.getStatus());
        }

        stateMachineService.validateItemTransition(item, ItemPreparationStatus.PREPARING);

        item.setPreparationStatus(ItemPreparationStatus.PREPARING);
        if (item.getStartedAt() == null) {
            item.setStartedAt(LocalDateTime.now());
        }

        // If parent order was ACCEPTED, advance it to PREPARING
        if (order.getStatus() == OrderStatus.ACCEPTED) {
            OrderStatus prev = order.getStatus();
            order.setStatus(OrderStatus.PREPARING);
            order.setStartedAt(LocalDateTime.now());
            orderRepository.save(order);
            recordHistory(order, prev, OrderStatus.PREPARING, currentUser, "Item preparation initiated");
            eventService.broadcastEvent("ORDER_STATUS_CHANGED", OrderResponseDto.fromEntity(order));
        }

        OrderItem savedItem = orderItemRepository.save(item);
        eventService.broadcastEvent("ITEM_STATUS_CHANGED", OrderItemResponseDto.fromEntity(savedItem));
        return savedItem;
    }

    @Transactional
    public OrderItem markOrderItemReady(Long itemId, User currentUser) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with ID: " + itemId));

        Order order = item.getOrder();
        if (order.getStatus().isTerminal()) {
            throw new BusinessValidationException("Cannot update item for an order in terminal state " + order.getStatus());
        }

        stateMachineService.validateItemTransition(item, ItemPreparationStatus.READY);

        item.setPreparationStatus(ItemPreparationStatus.READY);
        item.setReadyAt(LocalDateTime.now());
        OrderItem savedItem = orderItemRepository.save(item);

        // Check if all items in this parent order are ready!
        boolean allReady = order.getOrderItems().stream()
                .allMatch(i -> i.getPreparationStatus() == ItemPreparationStatus.READY);

        if (allReady && order.getStatus() != OrderStatus.READY && order.getStatus() != OrderStatus.SERVED) {
            OrderStatus prev = order.getStatus();
            order.setStatus(OrderStatus.READY);
            order.setReadyAt(LocalDateTime.now());
            Order savedOrder = orderRepository.save(order);
            recordHistory(savedOrder, prev, OrderStatus.READY, currentUser, "All items marked ready. Order ready to serve.");
            eventService.broadcastEvent("ORDER_STATUS_CHANGED", OrderResponseDto.fromEntity(savedOrder));
        } else {
            eventService.broadcastEvent("ITEM_STATUS_CHANGED", OrderItemResponseDto.fromEntity(savedItem));
        }

        return savedItem;
    }

    @Transactional
    public Order markOrderReady(Long orderId, Long version, User currentUser) {
        Order order = getOrderWithVersionCheck(orderId, version);
        stateMachineService.validateOrderTransition(order, OrderStatus.READY, null);

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.READY);
        order.setReadyAt(LocalDateTime.now());

        // Mark all items ready
        for (OrderItem item : order.getOrderItems()) {
            item.setPreparationStatus(ItemPreparationStatus.READY);
            if (item.getReadyAt() == null) {
                item.setReadyAt(LocalDateTime.now());
            }
        }

        Order saved = orderRepository.save(order);
        recordHistory(saved, prev, OrderStatus.READY, currentUser, "Order marked ready for serving");
        eventService.broadcastEvent("ORDER_STATUS_CHANGED", OrderResponseDto.fromEntity(saved));
        return saved;
    }

    @Transactional
    public Order serveOrder(Long orderId, Long version, User currentUser) {
        Order order = getOrderWithVersionCheck(orderId, version);
        stateMachineService.validateOrderTransition(order, OrderStatus.SERVED, null);

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.SERVED);
        order.setServedAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);
        recordHistory(saved, prev, OrderStatus.SERVED, currentUser, "Order served to customer");
        eventService.broadcastEvent("ORDER_STATUS_CHANGED", OrderResponseDto.fromEntity(saved));
        return saved;
    }

    @Transactional
    public Order cancelOrder(Long orderId, String reason, Long version, User currentUser) {
        Order order = getOrderWithVersionCheck(orderId, version);
        stateMachineService.validateOrderTransition(order, OrderStatus.CANCELLED, reason);

        restoreOrderStock(order);

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);

        Order saved = orderRepository.save(order);
        recordHistory(saved, prev, OrderStatus.CANCELLED, currentUser, "Order cancelled. Reason: " + reason);

        auditLogRepository.save(new AuditLog(
                "ORDER_CANCELLED",
                currentUser,
                "ORDER",
                saved.getId(),
                String.format("Order #%s cancelled. Reason: %s", saved.getOrderNumber(), reason)
        ));

        eventService.broadcastEvent("ORDER_STATUS_CHANGED", OrderResponseDto.fromEntity(saved));
        return saved;
    }

    @Transactional
    public Order rejectOrder(Long orderId, String reason, Long version, User currentUser) {
        Order order = getOrderWithVersionCheck(orderId, version);
        stateMachineService.validateOrderTransition(order, OrderStatus.REJECTED, reason);

        restoreOrderStock(order);

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectionReason(reason);

        Order saved = orderRepository.save(order);
        recordHistory(saved, prev, OrderStatus.REJECTED, currentUser, "Order rejected by kitchen. Reason: " + reason);

        auditLogRepository.save(new AuditLog(
                "ORDER_REJECTED",
                currentUser,
                "ORDER",
                saved.getId(),
                String.format("Order #%s rejected. Reason: %s", saved.getOrderNumber(), reason)
        ));

        eventService.broadcastEvent("ORDER_STATUS_CHANGED", OrderResponseDto.fromEntity(saved));
        return saved;
    }

    private void restoreOrderStock(Order order) {
        if (order.getOrderItems() != null) {
            for (OrderItem orderItem : order.getOrderItems()) {
                if (orderItem.getMenuItem() != null && orderItem.getQuantity() != null && orderItem.getQuantity() > 0) {
                    MenuItem item = menuItemRepository.findByIdForUpdate(orderItem.getMenuItem().getId()).orElse(null);
                    if (item != null) {
                        int current = item.getStockQuantity() != null ? item.getStockQuantity() : 0;
                        item.setStockQuantity(current + orderItem.getQuantity());
                        if (item.isActive() && item.getStockQuantity() > 0) {
                            item.setAvailable(true);
                        }
                        menuItemRepository.save(item);
                        log.info("Restored stock for item '{}' by +{}. New stock: {}", item.getName(), orderItem.getQuantity(), item.getStockQuantity());
                    }
                }
            }
        }
    }

    @Transactional
    public void clearAllOrders(User currentUser) {
        log.info("Clearing all orders, order items, status history, and payments...");
        paymentRepository.deleteAll();
        statusHistoryRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();

        auditLogRepository.save(new AuditLog(
                "ORDERS_CLEARED",
                currentUser,
                "ORDER",
                null,
                "All orders, order items, status histories, and payments were cleared for fresh demonstration."
        ));

        eventService.broadcastEvent("ORDERS_CLEARED", Map.of("message", "All orders cleared"));
    }

    @Transactional
    public Order updatePriority(Long orderId, OrderPriority newPriority, User currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        OrderPriority prev = order.getPriority();
        order.setPriority(newPriority);
        Order saved = orderRepository.save(order);

        auditLogRepository.save(new AuditLog(
                "PRIORITY_CHANGED",
                currentUser,
                "ORDER",
                saved.getId(),
                String.format("Priority of Order #%s changed from %s to %s", saved.getOrderNumber(), prev, newPriority)
        ));

        eventService.broadcastEvent("ORDER_STATUS_CHANGED", OrderResponseDto.fromEntity(saved));
        return saved;
    }

    @Transactional
    public OrderItem reassignStation(Long itemId, Long newStationId, User currentUser) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with ID: " + itemId));
        KitchenStation station = stationRepository.findById(newStationId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen station not found with ID: " + newStationId));

        KitchenStation oldStation = item.getStation();
        item.setStation(station);
        OrderItem saved = orderItemRepository.save(item);

        auditLogRepository.save(new AuditLog(
                "STATION_REASSIGNED",
                currentUser,
                "ORDER_ITEM",
                saved.getId(),
                String.format("Item '%s' for Order #%s reassigned from '%s' to '%s'",
                        saved.getItemName(), saved.getOrder().getOrderNumber(),
                        oldStation != null ? oldStation.getName() : "None", station.getName())
        ));

        eventService.broadcastEvent("ITEM_STATUS_CHANGED", OrderItemResponseDto.fromEntity(saved));
        return saved;
    }

    private Order getOrderWithVersionCheck(Long orderId, Long version) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        if (version != null && order.getVersion() != null && !version.equals(order.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "Order was modified concurrently by another user. Please refresh and try again.");
        }
        return order;
    }

    private void recordHistory(Order order, OrderStatus prev, OrderStatus next, User user, String remarks) {
        OrderStatusHistory history = new OrderStatusHistory(order, prev, next, user, remarks);
        statusHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with number: " + orderNumber));
    }

    @Transactional(readOnly = true)
    public List<Order> getActiveOrders() {
        return orderRepository.findActiveOrders();
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistory> getOrderStatusHistory(Long orderId) {
        return statusHistoryRepository.findByOrderIdOrderByTimestampAsc(orderId);
    }
}

@Service
class OrderStateMachineService {

    // Valid transitions map
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            OrderStatus.PLACED, EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.REJECTED, OrderStatus.CANCELLED),
            OrderStatus.ACCEPTED, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
            OrderStatus.PREPARING, EnumSet.of(OrderStatus.READY, OrderStatus.CANCELLED),
            OrderStatus.READY, EnumSet.of(OrderStatus.SERVED, OrderStatus.CANCELLED),
            OrderStatus.SERVED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

    public void validateOrderTransition(Order order, OrderStatus targetStatus, String reason) {
        if (order == null) {
            throw new BusinessValidationException("Order cannot be null");
        }

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == targetStatus) {
            throw new InvalidOrderStateTransitionException(
                    String.format("Order #%s is already in status '%s'", order.getOrderNumber(), currentStatus));
        }

        if (currentStatus.isTerminal()) {
            throw new InvalidOrderStateTransitionException(
                    String.format("Cannot modify order #%s because it is in terminal state '%s'",
                            order.getOrderNumber(), currentStatus.name()));
        }

        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(OrderStatus.class));
        if (!allowed.contains(targetStatus)) {
            throw new InvalidOrderStateTransitionException(currentStatus, targetStatus);
        }

        if (targetStatus == OrderStatus.REJECTED) {
            if (reason == null || reason.trim().isBlank()) {
                throw new BusinessValidationException("Rejection reason is required when rejecting an order");
            }
        }

        if (targetStatus == OrderStatus.CANCELLED) {
            if (reason == null || reason.trim().isBlank()) {
                throw new BusinessValidationException("Cancellation reason is required when cancelling an order");
            }
        }

        if (targetStatus == OrderStatus.SERVED) {
            // Cannot serve order until all items are READY
            boolean allItemsReady = order.getOrderItems().stream()
                    .allMatch(item -> item.getPreparationStatus() == ItemPreparationStatus.READY);
            if (!allItemsReady) {
                throw new BusinessValidationException(
                        "Cannot mark order as SERVED until all order items have been prepared and marked READY");
            }
        }
    }

    public void validateItemTransition(OrderItem item, ItemPreparationStatus targetStatus) {
        if (item == null) {
            throw new BusinessValidationException("Order item cannot be null");
        }

        ItemPreparationStatus current = item.getPreparationStatus();
        if (current == targetStatus) {
            return; // no-op
        }

        if (current == ItemPreparationStatus.PENDING && targetStatus == ItemPreparationStatus.READY) {
            // Direct jump from PENDING to READY is allowed (e.g., drinks or pre-made desserts)
            return;
        }

        if (current == ItemPreparationStatus.PENDING && targetStatus == ItemPreparationStatus.PREPARING) {
            return;
        }

        if (current == ItemPreparationStatus.PREPARING && targetStatus == ItemPreparationStatus.READY) {
            return;
        }

        if (current == ItemPreparationStatus.READY && targetStatus != ItemPreparationStatus.READY) {
            throw new BusinessValidationException(
                    String.format("Item '%s' is already marked READY and cannot revert to '%s'",
                            item.getItemName(), targetStatus));
        }
    }
}

@Service
class KitchenEventService {

    private static final Logger log = LoggerFactory.getLogger(KitchenEventService.class);
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter registerEmitter() {
        // 30 minute timeout
        SseEmitter emitter = new SseEmitter(1800000L);

        emitter.onCompletion(() -> {
            log.debug("SSE emitter completed");
            emitters.remove(emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out");
            emitter.complete();
            emitters.remove(emitter);
        });

        emitter.onError(e -> {
            log.debug("SSE emitter error: {}", e.getMessage());
            emitters.remove(emitter);
        });

        emitters.add(emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("Connected to KDS Live Stream"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcastEvent(String eventName, Object data) {
        log.info("Broadcasting kitchen event: {} to {} clients", eventName, emitters.size());
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }
}

@Service
class KitchenSimulationService {

    private static final Logger log = LoggerFactory.getLogger(KitchenSimulationService.class);

    private final KitchenSimulationRunner runner = new KitchenSimulationRunner();

    public Map<String, Object> executeStandardRubricSimulation(long chefCookTimeMs, long waiterDelayMs) {
        log.info("Executing Multithreaded Producer-Consumer Kitchen Simulation via Service...");
        try {
            KitchenSimulationRunner.SimulationReport report = runner.runStandardRubricSimulation(
                    chefCookTimeMs > 0 ? chefCookTimeMs : 150L,
                    waiterDelayMs > 0 ? waiterDelayMs : 15L
            );

            Map<String, Object> response = new HashMap<>();
            response.put("simulationName", report.getSimulationName());
            response.put("status", report.isSuccess() ? "SUCCESS" : "FAILED");
            response.put("carouselCapacity", report.getCarouselCapacity());
            response.put("ordersPlacedCount", report.getOrdersPlaced().size());
            response.put("ordersCookedCount", report.getOrdersCooked().size());
            response.put("ordersPlaced", report.getOrdersPlaced());
            response.put("ordersCooked", report.getOrdersCooked());
            response.put("logs", report.getLogs());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simulation interrupted", e);
        }
    }

    public Map<String, Object> executeEmergencyOverloadSimulation() {
        log.info("Executing Emergency Kitchen Overload Simulation via Service...");
        KitchenSimulationRunner.SimulationReport report = runner.runEmergencyOverloadSimulation();

        Map<String, Object> response = new HashMap<>();
        response.put("simulationName", report.getSimulationName());
        response.put("status", report.isSuccess() ? "OVERLOAD_CAUGHT_AS_EXPECTED" : "FAILED");
        response.put("carouselCapacity", report.getCarouselCapacity());
        response.put("ordersPlacedCount", report.getOrdersPlaced().size());
        response.put("logs", report.getLogs());
        return response;
    }
}

@Service
class MenuService {

    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository categoryRepository;
    private final KitchenStationRepository stationRepository;
    private final OrderItemRepository orderItemRepository;
    private final AuditLogRepository auditLogRepository;

    public MenuService(MenuItemRepository menuItemRepository,
                       MenuCategoryRepository categoryRepository,
                       KitchenStationRepository stationRepository,
                       OrderItemRepository orderItemRepository,
                       AuditLogRepository auditLogRepository) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
        this.stationRepository = stationRepository;
        this.orderItemRepository = orderItemRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<MenuItem> getAllActiveMenuItems() {
        return menuItemRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<MenuItem> getAvailableMenuItems() {
        return menuItemRepository.findByActiveTrueAndAvailableTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<MenuCategory> getAllActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<MenuItem> searchMenuItems(Long categoryId, Long stationId, Boolean available, String search) {
        return menuItemRepository.searchMenu(categoryId, stationId, available, search);
    }

    @Transactional(readOnly = true)
    public MenuItem getMenuItemById(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with ID: " + id));
    }

    @Transactional
    public MenuItem createMenuItem(MenuItemDto dto, User currentUser) {
        MenuCategory category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + dto.getCategoryId()));

        KitchenStation station = stationRepository.findById(dto.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with ID: " + dto.getStationId()));

        MenuItem item = new MenuItem();
        item.setName(dto.getName());
        item.setDescription(dto.getDescription());
        item.setCategory(category);
        item.setPrice(dto.getPrice());
        item.setPrepTimeMinutes(dto.getPrepTimeMinutes());
        item.setStation(station);
        item.setVegetarian(dto.isVegetarian());
        item.setDefaultModifiers(dto.getDefaultModifiers());
        item.setImageUrl(dto.getImageUrl());
        int stock = dto.getStockQuantity() != null ? dto.getStockQuantity() : 50;
        item.setStockQuantity(stock);
        item.setAvailable(stock > 0 && dto.isAvailable());
        item.setActive(true);

        MenuItem saved = menuItemRepository.save(item);

        auditLogRepository.save(new AuditLog(
                "MENU_ITEM_CREATED",
                currentUser,
                "MENU_ITEM",
                saved.getId(),
                "Created menu item: " + saved.getName() + " (₹" + saved.getPrice() + ", Stock: " + saved.getStockQuantity() + ")"
        ));

        return saved;
    }

    @Transactional
    public MenuItem updateMenuItem(Long id, MenuItemDto dto, User currentUser) {
        MenuItem item = getMenuItemById(id);

        MenuCategory category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + dto.getCategoryId()));

        KitchenStation station = stationRepository.findById(dto.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with ID: " + dto.getStationId()));

        item.setName(dto.getName());
        item.setDescription(dto.getDescription());
        item.setCategory(category);
        item.setPrice(dto.getPrice());
        item.setPrepTimeMinutes(dto.getPrepTimeMinutes());
        item.setStation(station);
        item.setVegetarian(dto.isVegetarian());
        item.setDefaultModifiers(dto.getDefaultModifiers());
        item.setImageUrl(dto.getImageUrl());
        if (dto.getStockQuantity() != null) {
            item.setStockQuantity(dto.getStockQuantity());
        }
        item.setAvailable(item.getStockQuantity() > 0 && dto.isAvailable());

        MenuItem saved = menuItemRepository.save(item);

        auditLogRepository.save(new AuditLog(
                "MENU_ITEM_UPDATED",
                currentUser,
                "MENU_ITEM",
                saved.getId(),
                "Updated menu item: " + saved.getName() + " (Stock: " + saved.getStockQuantity() + ")"
        ));

        return saved;
    }

    @Transactional
    public MenuItem updateStock(Long id, int newStock, User currentUser) {
        MenuItem item = getMenuItemById(id);
        item.setStockQuantity(Math.max(0, newStock));
        item.setAvailable(item.getStockQuantity() > 0);
        MenuItem saved = menuItemRepository.save(item);

        auditLogRepository.save(new AuditLog(
                "MENU_ITEM_STOCK_UPDATED",
                currentUser,
                "MENU_ITEM",
                saved.getId(),
                "Stock updated for " + saved.getName() + " to " + saved.getStockQuantity()
        ));

        return saved;
    }

    @Transactional
    public MenuItem toggleAvailability(Long id, User currentUser) {
        MenuItem item = getMenuItemById(id);
        boolean newAvail = !item.isAvailable();
        if (newAvail && item.isOutOfStock()) {
            throw new BusinessValidationException("Cannot make item available when stock is zero. Please replenish stock first.");
        }
        item.setAvailable(newAvail);
        MenuItem saved = menuItemRepository.save(item);

        auditLogRepository.save(new AuditLog(
                "MENU_ITEM_AVAILABILITY",
                currentUser,
                "MENU_ITEM",
                saved.getId(),
                "Toggled availability for " + saved.getName() + " to " + saved.isAvailable()
        ));

        return saved;
    }

    @Transactional
    public void deleteMenuItem(Long id, User currentUser) {
        MenuItem item = getMenuItemById(id);
        boolean hasOrders = orderItemRepository.existsByMenuItemId(id);

        if (hasOrders) {
            // Soft-delete to preserve historical orders and financial reports
            item.setActive(false);
            item.setAvailable(false);
            menuItemRepository.save(item);

            auditLogRepository.save(new AuditLog(
                    "MENU_ITEM_DEACTIVATED",
                    currentUser,
                    "MENU_ITEM",
                    item.getId(),
                    "Deactivated menu item (retained for historical orders): " + item.getName()
            ));
        } else {
            // Safe hard delete if never ordered
            menuItemRepository.delete(item);

            auditLogRepository.save(new AuditLog(
                    "MENU_ITEM_DELETED",
                    currentUser,
                    "MENU_ITEM",
                    id,
                    "Deleted unreferenced menu item: " + item.getName()
            ));
        }
    }

    @Transactional
    public MenuCategory createCategory(String name, String description, int displayOrder, User currentUser) {
        if (categoryRepository.existsByName(name)) {
            throw new BusinessValidationException("Category already exists with name: " + name);
        }
        MenuCategory cat = new MenuCategory(name, description, displayOrder);
        MenuCategory saved = categoryRepository.save(cat);

        auditLogRepository.save(new AuditLog(
                "CATEGORY_CREATED",
                currentUser,
                "CATEGORY",
                saved.getId(),
                "Created menu category: " + saved.getName()
        ));
        return saved;
    }
}

@Service
class KitchenStationService {

    private final KitchenStationRepository stationRepository;
    private final AuditLogRepository auditLogRepository;

    public KitchenStationService(KitchenStationRepository stationRepository, AuditLogRepository auditLogRepository) {
        this.stationRepository = stationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<KitchenStation> getAllStations() {
        return stationRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<KitchenStation> getActiveStations() {
        return stationRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public KitchenStation getStationById(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen station not found with ID: " + id));
    }

    @Transactional
    public KitchenStation createStation(KitchenStationDto dto, User currentUser) {
        if (stationRepository.existsByCode(dto.getCode())) {
            throw new BusinessValidationException("Station code '" + dto.getCode() + "' is already in use");
        }
        if (stationRepository.existsByName(dto.getName())) {
            throw new BusinessValidationException("Station name '" + dto.getName() + "' is already in use");
        }

        KitchenStation station = new KitchenStation();
        station.setName(dto.getName());
        station.setCode(dto.getCode().toUpperCase().trim());
        station.setDescription(dto.getDescription());
        station.setColorCode(dto.getColorCode() != null ? dto.getColorCode() : "#0d6efd");
        station.setDisplayOrder(dto.getDisplayOrder());
        station.setActive(true);

        KitchenStation saved = stationRepository.save(station);

        auditLogRepository.save(new AuditLog(
                "STATION_CREATED",
                currentUser,
                "STATION",
                saved.getId(),
                "Created kitchen station: " + saved.getName() + " (" + saved.getCode() + ")"
        ));

        return saved;
    }

    @Transactional
    public KitchenStation updateStation(Long id, KitchenStationDto dto, User currentUser) {
        KitchenStation station = getStationById(id);

        station.setName(dto.getName());
        station.setDescription(dto.getDescription());
        station.setColorCode(dto.getColorCode());
        station.setDisplayOrder(dto.getDisplayOrder());
        station.setActive(dto.isActive());

        KitchenStation saved = stationRepository.save(station);

        auditLogRepository.save(new AuditLog(
                "STATION_UPDATED",
                currentUser,
                "STATION",
                saved.getId(),
                "Updated kitchen station: " + saved.getName()
        ));

        return saved;
    }

    @Transactional
    public void toggleStationActive(Long id, User currentUser) {
        KitchenStation station = getStationById(id);
        station.setActive(!station.isActive());
        stationRepository.save(station);

        auditLogRepository.save(new AuditLog(
                "STATION_STATUS_TOGGLED",
                currentUser,
                "STATION",
                station.getId(),
                "Station " + station.getName() + " active status set to " + station.isActive()
        ));
    }
}

@Service
class BillingService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogRepository auditLogRepository;

    public BillingService(OrderRepository orderRepository,
                          PaymentRepository paymentRepository,
                          AuditLogRepository auditLogRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public Payment processPayment(PaymentRequest request, User currentUser) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + request.getOrderId()));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessValidationException("Order #" + order.getOrderNumber() + " is already fully paid");
        }

        BigDecimal payAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessValidationException("Payment amount must be greater than zero");
        }

        Payment payment = new Payment(
                order,
                payAmount,
                request.getPaymentMethod(),
                request.getTransactionReference(),
                currentUser,
                request.getNotes()
        );
        Payment savedPayment = paymentRepository.save(payment);

        // Calculate total payments made on this order so far
        List<Payment> allPayments = paymentRepository.findByOrderIdOrderByPaymentTimeAsc(order.getId());
        BigDecimal totalPaid = allPayments.stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPaid.compareTo(order.getTotalAmount()) >= 0) {
            order.setPaymentStatus(PaymentStatus.PAID);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
        }

        // NOTE: Strictly retain existing order.status (e.g. PREPARING, READY, etc.)
        // A payment update must NOT mark an order as prepared or served.
        orderRepository.save(order);

        auditLogRepository.save(new AuditLog(
                "PAYMENT_RECORDED",
                currentUser,
                "ORDER",
                order.getId(),
                String.format("Payment of ₹%s received via %s for Order #%s. Payment status: %s",
                        payAmount, request.getPaymentMethod(), order.getOrderNumber(), order.getPaymentStatus())
        ));

        return savedPayment;
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsForOrder(Long orderId) {
        return paymentRepository.findByOrderIdOrderByPaymentTimeAsc(orderId);
    }

    @Transactional(readOnly = true)
    public List<Payment> getRecentPayments(LocalDateTime start, LocalDateTime end) {
        return paymentRepository.findPaymentsBetween(start, end);
    }
}

@Service
class ReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    public ReportService(OrderRepository orderRepository,
                         OrderItemRepository orderItemRepository,
                         PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboardSummary() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        DashboardSummaryDto summary = new DashboardSummaryDto();

        List<Order> todayOrders = orderRepository.findOrdersBetween(startOfDay, endOfDay);
        summary.setTotalOrdersToday(todayOrders.size());

        BigDecimal totalSales = todayOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED && o.getStatus() != OrderStatus.REJECTED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setTotalSalesToday(totalSales);

        long waiting = 0;
        long inPrep = 0;
        long ready = 0;
        long served = 0;
        long cancelled = 0;
        long overdueCount = 0;
        long activeCount = 0;
        long totalPrepMinutes = 0;
        long completedWithPrepCount = 0;
        long withinTargetCount = 0;

        Map<Integer, Long> hourlyMap = new HashMap<>();
        for (int h = 0; h < 24; h++) {
            hourlyMap.put(h, 0L);
        }

        Map<String, Long> typeMap = new HashMap<>();

        for (Order o : todayOrders) {
            if (o.getCreatedAt() != null) {
                int hour = o.getCreatedAt().getHour();
                hourlyMap.put(hour, hourlyMap.getOrDefault(hour, 0L) + 1);
            }

            String typeName = o.getOrderType() != null ? o.getOrderType().getDisplayName() : "Unknown";
            typeMap.put(typeName, typeMap.getOrDefault(typeName, 0L) + 1);

            if (o.getStatus() == OrderStatus.PLACED) {
                waiting++;
                activeCount++;
            } else if (o.getStatus() == OrderStatus.ACCEPTED || o.getStatus() == OrderStatus.PREPARING) {
                inPrep++;
                activeCount++;
            } else if (o.getStatus() == OrderStatus.READY) {
                ready++;
                activeCount++;
            } else if (o.getStatus() == OrderStatus.SERVED) {
                served++;
            } else if (o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.REJECTED) {
                cancelled++;
            }

            if (o.isOverdue()) {
                overdueCount++;
            }

            if (o.getStatus() == OrderStatus.SERVED || o.getStatus() == OrderStatus.READY) {
                long elapsed = o.getElapsedMinutes();
                totalPrepMinutes += elapsed;
                completedWithPrepCount++;
                if (elapsed <= (o.getEstimatedPrepMinutes() != null ? o.getEstimatedPrepMinutes() : 15)) {
                    withinTargetCount++;
                }
            }
        }

        summary.setOrdersWaitingAcceptance(waiting);
        summary.setOrdersInPreparation(inPrep);
        summary.setOrdersReadyToServe(ready);
        summary.setCompletedOrdersToday(served);
        summary.setCancelledOrdersToday(cancelled);
        summary.setActiveOrders(activeCount);
        summary.setOverdueOrdersCount(overdueCount);

        double overduePct = todayOrders.isEmpty() ? 0.0 : ((double) overdueCount / todayOrders.size()) * 100.0;
        summary.setOverdueOrdersPercentage(Math.round(overduePct * 10.0) / 10.0);

        double avgPrep = completedWithPrepCount == 0 ? 0.0 : ((double) totalPrepMinutes / completedWithPrepCount);
        summary.setAveragePrepTimeMinutes(Math.round(avgPrep * 10.0) / 10.0);

        double withinTargetPct = completedWithPrepCount == 0 ? 100.0 : ((double) withinTargetCount / completedWithPrepCount) * 100.0;
        summary.setWithinTargetPercentage(Math.round(withinTargetPct * 10.0) / 10.0);

        summary.setHourlyVolume(hourlyMap);
        summary.setOrdersByType(typeMap);

        // Top selling items
        List<Object[]> topItemsData = orderItemRepository.findTopSellingItems(startOfDay, endOfDay);
        List<DashboardSummaryDto.TopItemDto> topItems = new ArrayList<>();
        int count = 0;
        for (Object[] row : topItemsData) {
            if (count++ >= 5) break;
            String name = (String) row[1];
            long qty = ((Number) row[2]).longValue();
            BigDecimal rev = (BigDecimal) row[3];
            topItems.add(new DashboardSummaryDto.TopItemDto(name, qty, rev != null ? rev : BigDecimal.ZERO));
        }
        summary.setTopSellingItems(topItems);

        // Station workload
        List<Object[]> stationData = orderItemRepository.getStationPerformance(startOfDay, endOfDay);
        List<DashboardSummaryDto.StationMetricDto> stationMetrics = new ArrayList<>();
        for (Object[] row : stationData) {
            String stationName = (String) row[0];
            long total = ((Number) row[1]).longValue();
            long readyItems = ((Number) row[2]).longValue();
            long prepItems = ((Number) row[3]).longValue();
            long pendingItems = ((Number) row[4]).longValue();
            stationMetrics.add(new DashboardSummaryDto.StationMetricDto(stationName, total, readyItems, prepItems, pendingItems));
        }
        summary.setStationWorkload(stationMetrics);

        return summary;
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByFilter(ReportFilterDto filter) {
        LocalDate start = filter.getStartDate() != null ? filter.getStartDate() : LocalDate.now();
        LocalDate end = filter.getEndDate() != null ? filter.getEndDate() : LocalDate.now();

        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(LocalTime.MAX);

        return orderRepository.searchOrders(
                filter.getStatus(),
                filter.getOrderType(),
                filter.getSearch(),
                startDt,
                endDt
        );
    }

    public void writeOrdersCsv(List<Order> orders, PrintWriter writer) {
        writer.println("Order Number,Order Type,Table / Ref,Customer Name,Status,Priority,Items Count,Created At,Elapsed (Mins),Subtotal,Tax,Discount,Total Amount,Payment Status");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (Order o : orders) {
            String orderNum = escapeCsv(o.getOrderNumber());
            String type = o.getOrderType() != null ? o.getOrderType().getDisplayName() : "";
            String table = escapeCsv(o.getTableNumber() != null ? o.getTableNumber() : "-");
            String customer = escapeCsv(o.getCustomerName() != null ? o.getCustomerName() : "-");
            String status = o.getStatus() != null ? o.getStatus().getDisplayName() : "";
            String priority = o.getPriority() != null ? o.getPriority().getDisplayName() : "";
            int itemsCount = o.getOrderItems() != null ? o.getOrderItems().size() : 0;
            String created = o.getCreatedAt() != null ? o.getCreatedAt().format(dtf) : "";
            long elapsed = o.getElapsedMinutes();
            BigDecimal subtotal = o.getSubtotal() != null ? o.getSubtotal() : BigDecimal.ZERO;
            BigDecimal tax = o.getTaxAmount() != null ? o.getTaxAmount() : BigDecimal.ZERO;
            BigDecimal discount = o.getDiscountAmount() != null ? o.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal total = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
            String payStatus = o.getPaymentStatus() != null ? o.getPaymentStatus().getDisplayName() : "";

            writer.println(String.format("%s,%s,%s,%s,%s,%s,%d,%s,%d,%.2f,%.2f,%.2f,%.2f,%s",
                    orderNum, type, table, customer, status, priority, itemsCount,
                    created, elapsed, subtotal, tax, discount, total, payStatus));
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

@Service
class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    @Transactional
    public User createUser(String username, String rawPassword, String fullName, String email, UserRole role, User currentUser) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessValidationException("Username '" + username + "' is already taken");
        }

        if (rawPassword == null || rawPassword.length() < 6) {
            throw new BusinessValidationException("Password must be at least 6 characters long");
        }

        User user = new User(
                username.trim().toLowerCase(),
                passwordEncoder.encode(rawPassword),
                fullName.trim(),
                email != null ? email.trim() : null,
                role
        );

        User saved = userRepository.save(user);

        auditLogRepository.save(new AuditLog(
                "USER_CREATED",
                currentUser,
                "USER",
                saved.getId(),
                "Created staff account: " + saved.getUsername() + " (" + saved.getRole() + ")"
        ));

        return saved;
    }

    @Transactional
    public User updateUser(Long id, String fullName, String email, UserRole role, String newPassword, User currentUser) {
        User user = getUserById(id);

        user.setFullName(fullName.trim());
        user.setEmail(email != null ? email.trim() : null);
        user.setRole(role);

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            if (newPassword.trim().length() < 6) {
                throw new BusinessValidationException("Password must be at least 6 characters long");
            }
            user.setPassword(passwordEncoder.encode(newPassword.trim()));
        }

        User saved = userRepository.save(user);

        auditLogRepository.save(new AuditLog(
                "USER_UPDATED",
                currentUser,
                "USER",
                saved.getId(),
                "Updated staff account: " + saved.getUsername()
        ));

        return saved;
    }

    @Transactional
    public void toggleUserActive(Long id, User currentUser) {
        User user = getUserById(id);
        if (currentUser != null && currentUser.getId().equals(user.getId())) {
            throw new BusinessValidationException("You cannot deactivate your own account");
        }

        user.setActive(!user.isActive());
        userRepository.save(user);

        auditLogRepository.save(new AuditLog(
                "USER_STATUS_TOGGLED",
                currentUser,
                "USER",
                user.getId(),
                "User " + user.getUsername() + " active status set to " + user.isActive()
        ));
    }
}

@Service
class RestaurantSettingService {

    private final RestaurantSettingRepository settingRepository;
    private final AuditLogRepository auditLogRepository;

    public RestaurantSettingService(RestaurantSettingRepository settingRepository, AuditLogRepository auditLogRepository) {
        this.settingRepository = settingRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAllSettingsAsMap() {
        List<RestaurantSetting> settings = settingRepository.findAll();
        Map<String, String> map = new HashMap<>();
        for (RestaurantSetting s : settings) {
            map.put(s.getKey(), s.getValue());
        }
        return map;
    }

    @Transactional(readOnly = true)
    public String getSetting(String key, String defaultValue) {
        return settingRepository.findByKey(key)
                .map(RestaurantSetting::getValue)
                .orElse(defaultValue);
    }

    @Transactional
    public void saveSettings(Map<String, String> newSettings, User currentUser) {
        for (Map.Entry<String, String> entry : newSettings.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            RestaurantSetting setting = settingRepository.findByKey(key)
                    .orElse(new RestaurantSetting(key, value, key.replace("_", " ")));

            setting.setValue(value);
            settingRepository.save(setting);
        }

        auditLogRepository.save(new AuditLog(
                "SETTINGS_UPDATED",
                currentUser,
                "SETTINGS",
                0L,
                "Updated restaurant operational settings"
        ));
    }
}

@Service
class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> searchLogs(String action, String targetType, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = (startDate != null ? startDate : LocalDate.now().minusDays(7)).atStartOfDay();
        LocalDateTime end = (endDate != null ? endDate : LocalDate.now()).atTime(LocalTime.MAX);

        return auditLogRepository.searchLogs(action, targetType, start, end);
    }
}