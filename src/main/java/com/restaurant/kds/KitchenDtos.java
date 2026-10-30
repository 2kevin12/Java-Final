package com.restaurant.kds;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;

public class KitchenDtos {
    // Consolidated module holder: KitchenDtos
}

class OrderCreateRequest {

    @NotNull(message = "Order type is required")
    private OrderType orderType = OrderType.DINE_IN;

    private String tableNumber;

    private String customerName;

    private String customerPhone;

    private String specialInstructions;

    private OrderPriority priority = OrderPriority.NORMAL;

    @NotEmpty(message = "At least one item must be added to the order")
    @Valid
    private List<OrderItemRequest> items = new ArrayList<>();

    public OrderCreateRequest() {}

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    public OrderPriority getPriority() {
        return priority;
    }

    public void setPriority(OrderPriority priority) {
        this.priority = priority;
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }
}

class OrderItemRequest {

    @NotNull(message = "Menu item ID is required")
    private Long menuItemId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity = 1;

    private String specialInstructions;

    private List<String> modifierNames = new ArrayList<>();

    public OrderItemRequest() {}

    public OrderItemRequest(Long menuItemId, Integer quantity, String specialInstructions) {
        this.menuItemId = menuItemId;
        this.quantity = quantity;
        this.specialInstructions = specialInstructions;
    }

    public Long getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(Long menuItemId) {
        this.menuItemId = menuItemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    public List<String> getModifierNames() {
        return modifierNames;
    }

    public void setModifierNames(List<String> modifierNames) {
        this.modifierNames = modifierNames;
    }
}

class OrderResponseDto {

    private Long id;
    private String orderNumber;
    private OrderType orderType;
    private String orderTypeDisplayName;
    private String orderTypeIcon;
    private String tableNumber;
    private String customerName;
    private String customerPhone;
    private String createdByUsername;
    private OrderStatus status;
    private String statusDisplayName;
    private String statusBadgeClass;
    private OrderPriority priority;
    private String priorityDisplayName;
    private String priorityBadgeClass;
    private BigDecimal subtotal;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private String paymentStatusDisplayName;
    private String paymentStatusBadgeClass;
    private String specialInstructions;
    private String rejectionReason;
    private String cancellationReason;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime readyAt;
    private LocalDateTime servedAt;
    private Integer estimatedPrepMinutes;
    private LocalDateTime estimatedCompletionTime;
    private long elapsedMinutes;
    private long elapsedSeconds;
    private boolean overdue;
    private Long version;
    private List<OrderItemResponseDto> items = new ArrayList<>();

    public OrderResponseDto() {}

    public static OrderResponseDto fromEntity(Order order) {
        OrderResponseDto dto = new OrderResponseDto();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setOrderType(order.getOrderType());
        if (order.getOrderType() != null) {
            dto.setOrderTypeDisplayName(order.getOrderType().getDisplayName());
            dto.setOrderTypeIcon(order.getOrderType().getIconClass());
        }
        dto.setTableNumber(order.getTableNumber());
        dto.setCustomerName(order.getCustomerName());
        dto.setCustomerPhone(order.getCustomerPhone());
        if (order.getCreatedBy() != null) {
            dto.setCreatedByUsername(order.getCreatedBy().getUsername());
        }
        dto.setStatus(order.getStatus());
        if (order.getStatus() != null) {
            dto.setStatusDisplayName(order.getStatus().getDisplayName());
            dto.setStatusBadgeClass(order.getStatus().getBadgeClass());
        }
        dto.setPriority(order.getPriority());
        if (order.getPriority() != null) {
            dto.setPriorityDisplayName(order.getPriority().getDisplayName());
            dto.setPriorityBadgeClass(order.getPriority().getBadgeClass());
        }
        dto.setSubtotal(order.getSubtotal());
        dto.setTaxRate(order.getTaxRate());
        dto.setTaxAmount(order.getTaxAmount());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPaymentStatus(order.getPaymentStatus());
        if (order.getPaymentStatus() != null) {
            dto.setPaymentStatusDisplayName(order.getPaymentStatus().getDisplayName());
            dto.setPaymentStatusBadgeClass(order.getPaymentStatus().getBadgeClass());
        }
        dto.setSpecialInstructions(order.getSpecialInstructions());
        dto.setRejectionReason(order.getRejectionReason());
        dto.setCancellationReason(order.getCancellationReason());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setAcceptedAt(order.getAcceptedAt());
        dto.setStartedAt(order.getStartedAt());
        dto.setReadyAt(order.getReadyAt());
        dto.setServedAt(order.getServedAt());
        dto.setEstimatedPrepMinutes(order.getEstimatedPrepMinutes());
        dto.setEstimatedCompletionTime(order.getEstimatedCompletionTime());
        dto.setElapsedMinutes(order.getElapsedMinutes());
        dto.setElapsedSeconds(order.getElapsedSeconds());
        dto.setOverdue(order.isOverdue());
        dto.setVersion(order.getVersion());

        if (order.getOrderItems() != null) {
            dto.setItems(order.getOrderItems().stream()
                    .map(OrderItemResponseDto::fromEntity)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public String getOrderTypeDisplayName() {
        return orderTypeDisplayName;
    }

    public void setOrderTypeDisplayName(String orderTypeDisplayName) {
        this.orderTypeDisplayName = orderTypeDisplayName;
    }

    public String getOrderTypeIcon() {
        return orderTypeIcon;
    }

    public void setOrderTypeIcon(String orderTypeIcon) {
        this.orderTypeIcon = orderTypeIcon;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public void setCreatedByUsername(String createdByUsername) {
        this.createdByUsername = createdByUsername;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getStatusDisplayName() {
        return statusDisplayName;
    }

    public void setStatusDisplayName(String statusDisplayName) {
        this.statusDisplayName = statusDisplayName;
    }

    public String getStatusBadgeClass() {
        return statusBadgeClass;
    }

    public void setStatusBadgeClass(String statusBadgeClass) {
        this.statusBadgeClass = statusBadgeClass;
    }

    public OrderPriority getPriority() {
        return priority;
    }

    public void setPriority(OrderPriority priority) {
        this.priority = priority;
    }

    public String getPriorityDisplayName() {
        return priorityDisplayName;
    }

    public void setPriorityDisplayName(String priorityDisplayName) {
        this.priorityDisplayName = priorityDisplayName;
    }

    public String getPriorityBadgeClass() {
        return priorityBadgeClass;
    }

    public void setPriorityBadgeClass(String priorityBadgeClass) {
        this.priorityBadgeClass = priorityBadgeClass;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getPaymentStatusDisplayName() {
        return paymentStatusDisplayName;
    }

    public void setPaymentStatusDisplayName(String paymentStatusDisplayName) {
        this.paymentStatusDisplayName = paymentStatusDisplayName;
    }

    public String getPaymentStatusBadgeClass() {
        return paymentStatusBadgeClass;
    }

    public void setPaymentStatusBadgeClass(String paymentStatusBadgeClass) {
        this.paymentStatusBadgeClass = paymentStatusBadgeClass;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(LocalDateTime readyAt) {
        this.readyAt = readyAt;
    }

    public LocalDateTime getServedAt() {
        return servedAt;
    }

    public void setServedAt(LocalDateTime servedAt) {
        this.servedAt = servedAt;
    }

    public Integer getEstimatedPrepMinutes() {
        return estimatedPrepMinutes;
    }

    public void setEstimatedPrepMinutes(Integer estimatedPrepMinutes) {
        this.estimatedPrepMinutes = estimatedPrepMinutes;
    }

    public LocalDateTime getEstimatedCompletionTime() {
        return estimatedCompletionTime;
    }

    public void setEstimatedCompletionTime(LocalDateTime estimatedCompletionTime) {
        this.estimatedCompletionTime = estimatedCompletionTime;
    }

    public long getElapsedMinutes() {
        return elapsedMinutes;
    }

    public void setElapsedMinutes(long elapsedMinutes) {
        this.elapsedMinutes = elapsedMinutes;
    }

    public long getElapsedSeconds() {
        return elapsedSeconds;
    }

    public void setElapsedSeconds(long elapsedSeconds) {
        this.elapsedSeconds = elapsedSeconds;
    }

    public boolean isOverdue() {
        return overdue;
    }

    public void setOverdue(boolean overdue) {
        this.overdue = overdue;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<OrderItemResponseDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemResponseDto> items) {
        this.items = items;
    }
}

class OrderItemResponseDto {

    private Long id;
    private Long menuItemId;
    private String itemName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String specialInstructions;
    private Long stationId;
    private String stationName;
    private String stationCode;
    private String stationColor;
    private ItemPreparationStatus preparationStatus;
    private String statusDisplayName;
    private String statusBadgeClass;
    private LocalDateTime startedAt;
    private LocalDateTime readyAt;
    private List<String> modifiers;

    public OrderItemResponseDto() {}

    public static OrderItemResponseDto fromEntity(OrderItem item) {
        OrderItemResponseDto dto = new OrderItemResponseDto();
        dto.setId(item.getId());
        if (item.getMenuItem() != null) {
            dto.setMenuItemId(item.getMenuItem().getId());
        }
        dto.setItemName(item.getItemName());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setTotalPrice(item.getTotalPrice());
        dto.setSpecialInstructions(item.getSpecialInstructions());
        if (item.getStation() != null) {
            dto.setStationId(item.getStation().getId());
            dto.setStationName(item.getStation().getName());
            dto.setStationCode(item.getStation().getCode());
            dto.setStationColor(item.getStation().getColorCode());
        }
        dto.setPreparationStatus(item.getPreparationStatus());
        dto.setStatusDisplayName(item.getPreparationStatus() != null ? item.getPreparationStatus().getDisplayName() : "");
        dto.setStatusBadgeClass(item.getPreparationStatus() != null ? item.getPreparationStatus().getBadgeClass() : "");
        dto.setStartedAt(item.getStartedAt());
        dto.setReadyAt(item.getReadyAt());
        if (item.getModifiers() != null) {
            dto.setModifiers(item.getModifiers().stream()
                    .map(m -> m.getName() + (m.getPrice().compareTo(BigDecimal.ZERO) > 0 ? " (+₹" + m.getPrice() + ")" : ""))
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(Long menuItemId) {
        this.menuItemId = menuItemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    public Long getStationId() {
        return stationId;
    }

    public void setStationId(Long stationId) {
        this.stationId = stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getStationColor() {
        return stationColor;
    }

    public void setStationColor(String stationColor) {
        this.stationColor = stationColor;
    }

    public ItemPreparationStatus getPreparationStatus() {
        return preparationStatus;
    }

    public void setPreparationStatus(ItemPreparationStatus preparationStatus) {
        this.preparationStatus = preparationStatus;
    }

    public String getStatusDisplayName() {
        return statusDisplayName;
    }

    public void setStatusDisplayName(String statusDisplayName) {
        this.statusDisplayName = statusDisplayName;
    }

    public String getStatusBadgeClass() {
        return statusBadgeClass;
    }

    public void setStatusBadgeClass(String statusBadgeClass) {
        this.statusBadgeClass = statusBadgeClass;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(LocalDateTime readyAt) {
        this.readyAt = readyAt;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<String> modifiers) {
        this.modifiers = modifiers;
    }
}

class StatusUpdateRequest {
    private OrderStatus status;
    private String reason;
    private Long version;

    public StatusUpdateRequest() {}

    public StatusUpdateRequest(OrderStatus status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

class PaymentRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    private String transactionReference;

    private String notes;

    public PaymentRequest() {}

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

class DashboardSummaryDto {

    private long totalOrdersToday;
    private BigDecimal totalSalesToday = BigDecimal.ZERO;
    private long activeOrders;
    private long ordersWaitingAcceptance; // PLACED
    private long ordersInPreparation;    // ACCEPTED + PREPARING
    private long ordersReadyToServe;      // READY
    private long completedOrdersToday;    // SERVED
    private long cancelledOrdersToday;    // CANCELLED + REJECTED
    private double averagePrepTimeMinutes;
    private long overdueOrdersCount;
    private double overdueOrdersPercentage;
    private double withinTargetPercentage;

    private Map<Integer, Long> hourlyVolume = new HashMap<>();
    private Map<String, Long> ordersByType = new HashMap<>();
    private List<TopItemDto> topSellingItems = new ArrayList<>();
    private List<StationMetricDto> stationWorkload = new ArrayList<>();

    public DashboardSummaryDto() {}

    public static class TopItemDto {
        private String itemName;
        private long quantitySold;
        private BigDecimal totalRevenue;

        public TopItemDto(String itemName, long quantitySold, BigDecimal totalRevenue) {
            this.itemName = itemName;
            this.quantitySold = quantitySold;
            this.totalRevenue = totalRevenue;
        }

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public long getQuantitySold() { return quantitySold; }
        public void setQuantitySold(long quantitySold) { this.quantitySold = quantitySold; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    }

    public static class StationMetricDto {
        private String stationName;
        private long totalItems;
        private long readyItems;
        private long preparingItems;
        private long pendingItems;

        public StationMetricDto(String stationName, long totalItems, long readyItems, long preparingItems, long pendingItems) {
            this.stationName = stationName;
            this.totalItems = totalItems;
            this.readyItems = readyItems;
            this.preparingItems = preparingItems;
            this.pendingItems = pendingItems;
        }

        public String getStationName() { return stationName; }
        public long getTotalItems() { return totalItems; }
        public long getReadyItems() { return readyItems; }
        public long getPreparingItems() { return preparingItems; }
        public long getPendingItems() { return pendingItems; }
    }

    // Getters and Setters
    public long getTotalOrdersToday() { return totalOrdersToday; }
    public void setTotalOrdersToday(long totalOrdersToday) { this.totalOrdersToday = totalOrdersToday; }

    public BigDecimal getTotalSalesToday() { return totalSalesToday; }
    public void setTotalSalesToday(BigDecimal totalSalesToday) { this.totalSalesToday = totalSalesToday; }

    public long getActiveOrders() { return activeOrders; }
    public void setActiveOrders(long activeOrders) { this.activeOrders = activeOrders; }

    public long getOrdersWaitingAcceptance() { return ordersWaitingAcceptance; }
    public void setOrdersWaitingAcceptance(long ordersWaitingAcceptance) { this.ordersWaitingAcceptance = ordersWaitingAcceptance; }

    public long getOrdersInPreparation() { return ordersInPreparation; }
    public void setOrdersInPreparation(long ordersInPreparation) { this.ordersInPreparation = ordersInPreparation; }

    public long getOrdersReadyToServe() { return ordersReadyToServe; }
    public void setOrdersReadyToServe(long ordersReadyToServe) { this.ordersReadyToServe = ordersReadyToServe; }

    public long getCompletedOrdersToday() { return completedOrdersToday; }
    public void setCompletedOrdersToday(long completedOrdersToday) { this.completedOrdersToday = completedOrdersToday; }

    public long getCancelledOrdersToday() { return cancelledOrdersToday; }
    public void setCancelledOrdersToday(long cancelledOrdersToday) { this.cancelledOrdersToday = cancelledOrdersToday; }

    public double getAveragePrepTimeMinutes() { return averagePrepTimeMinutes; }
    public void setAveragePrepTimeMinutes(double averagePrepTimeMinutes) { this.averagePrepTimeMinutes = averagePrepTimeMinutes; }

    public long getOverdueOrdersCount() { return overdueOrdersCount; }
    public void setOverdueOrdersCount(long overdueOrdersCount) { this.overdueOrdersCount = overdueOrdersCount; }

    public double getOverdueOrdersPercentage() { return overdueOrdersPercentage; }
    public void setOverdueOrdersPercentage(double overdueOrdersPercentage) { this.overdueOrdersPercentage = overdueOrdersPercentage; }

    public double getWithinTargetPercentage() { return withinTargetPercentage; }
    public void setWithinTargetPercentage(double withinTargetPercentage) { this.withinTargetPercentage = withinTargetPercentage; }

    public Map<Integer, Long> getHourlyVolume() { return hourlyVolume; }
    public void setHourlyVolume(Map<Integer, Long> hourlyVolume) { this.hourlyVolume = hourlyVolume; }

    public Map<String, Long> getOrdersByType() { return ordersByType; }
    public void setOrdersByType(Map<String, Long> ordersByType) { this.ordersByType = ordersByType; }

    public List<TopItemDto> getTopSellingItems() { return topSellingItems; }
    public void setTopSellingItems(List<TopItemDto> topSellingItems) { this.topSellingItems = topSellingItems; }

    public List<StationMetricDto> getStationWorkload() { return stationWorkload; }
    public void setStationWorkload(List<StationMetricDto> stationWorkload) { this.stationWorkload = stationWorkload; }
}

class KitchenStationDto {

    private Long id;

    @NotBlank(message = "Station name is required")
    @Size(max = 50)
    private String name;

    @NotBlank(message = "Station code is required")
    @Size(max = 20)
    private String code;

    private String description;
    private String colorCode = "#0d6efd";
    private boolean active = true;
    private int displayOrder = 0;

    public KitchenStationDto() {}

    public static KitchenStationDto fromEntity(KitchenStation station) {
        KitchenStationDto dto = new KitchenStationDto();
        dto.setId(station.getId());
        dto.setName(station.getName());
        dto.setCode(station.getCode());
        dto.setDescription(station.getDescription());
        dto.setColorCode(station.getColorCode());
        dto.setActive(station.isActive());
        dto.setDisplayOrder(station.getDisplayOrder());
        return dto;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}

class MenuItemDto {

    private Long id;

    @NotBlank(message = "Item name is required")
    private String name;

    private String description;

    @NotNull(message = "Category is required")
    private Long categoryId;
    private String categoryName;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    private BigDecimal price;

    @NotNull(message = "Estimated prep time is required")
    @Min(value = 1, message = "Prep time must be at least 1 minute")
    private Integer prepTimeMinutes = 10;

    @NotNull(message = "Kitchen station is required")
    private Long stationId;
    private String stationName;

    private boolean available = true;
    private Integer stockQuantity = 50;
    private String imageUrl;
    private boolean vegetarian = false;
    private String defaultModifiers;
    private boolean active = true;

    public MenuItemDto() {}

    public static MenuItemDto fromEntity(MenuItem item) {
        MenuItemDto dto = new MenuItemDto();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setDescription(item.getDescription());
        if (item.getCategory() != null) {
            dto.setCategoryId(item.getCategory().getId());
            dto.setCategoryName(item.getCategory().getName());
        }
        dto.setPrice(item.getPrice());
        dto.setPrepTimeMinutes(item.getPrepTimeMinutes());
        if (item.getStation() != null) {
            dto.setStationId(item.getStation().getId());
            dto.setStationName(item.getStation().getName());
        }
        dto.setAvailable(item.isAvailable());
        dto.setStockQuantity(item.getStockQuantity());
        dto.setImageUrl(item.getImageUrl());
        dto.setVegetarian(item.isVegetarian());
        dto.setDefaultModifiers(item.getDefaultModifiers());
        dto.setActive(item.isActive());
        return dto;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getPrepTimeMinutes() { return prepTimeMinutes; }
    public void setPrepTimeMinutes(Integer prepTimeMinutes) { this.prepTimeMinutes = prepTimeMinutes; }
    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public boolean isVegetarian() { return vegetarian; }
    public void setVegetarian(boolean vegetarian) { this.vegetarian = vegetarian; }
    public String getDefaultModifiers() { return defaultModifiers; }
    public void setDefaultModifiers(String defaultModifiers) { this.defaultModifiers = defaultModifiers; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

class ReportFilterDto {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private OrderStatus status;

    private OrderType orderType;

    private Long stationId;

    private String search;

    public ReportFilterDto() {
        this.startDate = LocalDate.now();
        this.endDate = LocalDate.now();
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public Long getStationId() {
        return stationId;
    }

    public void setStationId(Long stationId) {
        this.stationId = stationId;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }
}