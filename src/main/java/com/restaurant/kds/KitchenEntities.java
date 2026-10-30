package com.restaurant.kds;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class KitchenEntities {
    // Consolidated module holder: KitchenEntities
}

enum OrderStatus {
    PLACED("Placed", "bg-primary"),
    ACCEPTED("Accepted", "bg-info text-dark"),
    PREPARING("Preparing", "bg-warning text-dark"),
    READY("Ready to Serve", "bg-success"),
    SERVED("Served", "bg-secondary"),
    REJECTED("Rejected", "bg-danger"),
    CANCELLED("Cancelled", "bg-dark");

    private final String displayName;
    private final String badgeClass;

    OrderStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public boolean isTerminal() {
        return this == SERVED || this == REJECTED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == PLACED || this == ACCEPTED || this == PREPARING || this == READY;
    }
}

enum OrderPriority {
    NORMAL("Normal", "badge-priority-normal", 1),
    HIGH("High", "badge-priority-high", 2),
    URGENT("Urgent", "badge-priority-urgent", 3);

    private final String displayName;
    private final String badgeClass;
    private final int level;

    OrderPriority(String displayName, String badgeClass, int level) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public int getLevel() {
        return level;
    }
}

enum OrderType {
    DINE_IN("Dine-In", "fas fa-utensils"),
    TAKEAWAY("Takeaway", "fas fa-shopping-bag"),
    DELIVERY("Delivery", "fas fa-motorcycle");

    private final String displayName;
    private final String iconClass;

    OrderType(String displayName, String iconClass) {
        this.displayName = displayName;
        this.iconClass = iconClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconClass() {
        return iconClass;
    }
}

enum ItemPreparationStatus {
    PENDING("Pending", "badge-pending"),
    PREPARING("Preparing", "badge-preparing"),
    READY("Ready", "badge-ready");

    private final String displayName;
    private final String badgeClass;

    ItemPreparationStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}

enum PaymentMethod {
    CASH("Cash"),
    CARD("Credit/Debit Card"),
    UPI("UPI / Digital Wallet"),
    OTHER("Other");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

enum PaymentStatus {
    UNPAID("Unpaid", "badge-unpaid"),
    PARTIALLY_PAID("Partially Paid", "badge-partial"),
    PAID("Paid", "badge-paid"),
    REFUNDED("Refunded", "badge-refunded");

    private final String displayName;
    private final String badgeClass;

    PaymentStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}

enum UserRole {
    ROLE_ADMIN("Admin", "Full system access & configuration"),
    ROLE_CASHIER("Cashier / Waiter", "Order entry, tables, billing"),
    ROLE_KITCHEN_STAFF("Kitchen Staff", "Kitchen display, food preparation"),
    ROLE_MANAGER("Manager", "Operations oversight, analytics & reports");

    private final String displayName;
    private final String description;

    UserRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_number", columnList = "order_number", unique = true),
    @Index(name = "idx_order_status", columnList = "status"),
    @Index(name = "idx_order_created_at", columnList = "created_at"),
    @Index(name = "idx_order_type", columnList = "order_type"),
    @Index(name = "idx_order_payment_status", columnList = "payment_status")
})
class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 32)
    private String orderNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType = OrderType.DINE_IN;

    @Column(name = "table_number", length = 30)
    private String tableNumber;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_phone", length = 30)
    private String customerPhone;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PLACED;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderPriority priority = OrderPriority.NORMAL;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = new BigDecimal("5.00"); // e.g. 5% GST

    @NotNull
    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "special_instructions", length = 500)
    private String specialInstructions;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "served_at")
    private LocalDateTime servedAt;

    @Column(name = "estimated_prep_minutes")
    private Integer estimatedPrepMinutes = 15;

    @Column(name = "estimated_completion_time")
    private LocalDateTime estimatedCompletionTime;

    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC")
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("paymentTime ASC")
    private List<Payment> payments = new ArrayList<>();

    public Order() {}

    public void addItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
        recalculateTotals();
    }

    public void removeItem(OrderItem item) {
        orderItems.remove(item);
        item.setOrder(null);
        recalculateTotals();
    }

    public void recalculateTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        int maxPrepTime = 0;
        for (OrderItem it : orderItems) {
            it.recalculateTotal();
            if (it.getTotalPrice() != null) {
                sum = sum.add(it.getTotalPrice());
            }
            if (it.getMenuItem() != null && it.getMenuItem().getPrepTimeMinutes() != null) {
                maxPrepTime = Math.max(maxPrepTime, it.getMenuItem().getPrepTimeMinutes());
            }
        }
        this.subtotal = sum.setScale(2, RoundingMode.HALF_UP);
        if (this.taxRate == null) {
            this.taxRate = new BigDecimal("5.00");
        }
        this.taxAmount = this.subtotal.multiply(this.taxRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        if (this.discountAmount == null) {
            this.discountAmount = BigDecimal.ZERO;
        }

        BigDecimal netTotal = this.subtotal.add(this.taxAmount).subtract(this.discountAmount);
        this.totalAmount = netTotal.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : netTotal.setScale(2, RoundingMode.HALF_UP);

        // Estimate prep time based on maximum single item prep time plus 2 mins per additional item
        int extraItems = Math.max(0, orderItems.size() - 1);
        this.estimatedPrepMinutes = Math.max(5, maxPrepTime + (extraItems * 2));
        if (this.createdAt != null && this.estimatedCompletionTime == null) {
            this.estimatedCompletionTime = this.createdAt.plusMinutes(this.estimatedPrepMinutes);
        }
    }

    public long getElapsedMinutes() {
        LocalDateTime start = (createdAt != null) ? createdAt : LocalDateTime.now();
        LocalDateTime end = (readyAt != null) ? readyAt : (servedAt != null ? servedAt : LocalDateTime.now());
        return Math.max(0, Duration.between(start, end).toMinutes());
    }

    public long getElapsedSeconds() {
        LocalDateTime start = (createdAt != null) ? createdAt : LocalDateTime.now();
        LocalDateTime end = (readyAt != null) ? readyAt : (servedAt != null ? servedAt : LocalDateTime.now());
        return Math.max(0, Duration.between(start, end).toSeconds());
    }

    public boolean isOverdue() {
        if (status.isTerminal()) return false;
        long elapsed = getElapsedMinutes();
        int estimated = (estimatedPrepMinutes != null && estimatedPrepMinutes > 0) ? estimatedPrepMinutes : 15;
        return elapsed > estimated;
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

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public OrderPriority getPriority() {
        return priority;
    }

    public void setPriority(OrderPriority priority) {
        this.priority = priority;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public List<OrderStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<OrderStatusHistory> statusHistory) {
        this.statusHistory = statusHistory;
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public void setPayments(List<Payment> payments) {
        this.payments = payments;
    }
}

@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "idx_order_item_order", columnList = "order_id"),
    @Index(name = "idx_order_item_station", columnList = "station_id"),
    @Index(name = "idx_order_item_status", columnList = "preparation_status")
})
class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer quantity = 1;

    @NotNull
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @NotNull
    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "special_instructions", length = 300)
    private String specialInstructions;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private KitchenStation station;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "preparation_status", nullable = false, length = 30)
    private ItemPreparationStatus preparationStatus = ItemPreparationStatus.PENDING;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItemModifier> modifiers = new ArrayList<>();

    public OrderItem() {}

    public OrderItem(Order order, MenuItem menuItem, Integer quantity, String specialInstructions) {
        this.order = order;
        this.menuItem = menuItem;
        this.itemName = menuItem.getName();
        this.quantity = quantity;
        this.unitPrice = menuItem.getPrice();
        this.station = menuItem.getStation();
        this.specialInstructions = specialInstructions;
        this.preparationStatus = ItemPreparationStatus.PENDING;
        recalculateTotal();
    }

    public void recalculateTotal() {
        BigDecimal modTotal = BigDecimal.ZERO;
        if (modifiers != null) {
            for (OrderItemModifier mod : modifiers) {
                if (mod.getPrice() != null) {
                    modTotal = modTotal.add(mod.getPrice());
                }
            }
        }
        BigDecimal singlePrice = (unitPrice != null ? unitPrice : BigDecimal.ZERO).add(modTotal);
        this.totalPrice = singlePrice.multiply(BigDecimal.valueOf(quantity != null ? quantity : 1));
    }

    public void addModifier(String name, BigDecimal price) {
        OrderItemModifier mod = new OrderItemModifier(this, name, price);
        this.modifiers.add(mod);
        recalculateTotal();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public void setMenuItem(MenuItem menuItem) {
        this.menuItem = menuItem;
        if (menuItem != null && (this.itemName == null || this.itemName.isBlank())) {
            this.itemName = menuItem.getName();
        }
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
        recalculateTotal();
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        recalculateTotal();
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

    public KitchenStation getStation() {
        return station;
    }

    public void setStation(KitchenStation station) {
        this.station = station;
    }

    public ItemPreparationStatus getPreparationStatus() {
        return preparationStatus;
    }

    public void setPreparationStatus(ItemPreparationStatus preparationStatus) {
        this.preparationStatus = preparationStatus;
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

    public List<OrderItemModifier> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<OrderItemModifier> modifiers) {
        this.modifiers = modifiers;
        recalculateTotal();
    }
}

@Entity
@Table(name = "order_item_modifiers")
class OrderItemModifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    public OrderItemModifier() {}

    public OrderItemModifier(OrderItem orderItem, String name, BigDecimal price) {
        this.orderItem = orderItem;
        this.name = name;
        this.price = price != null ? price : BigDecimal.ZERO;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public void setOrderItem(OrderItem orderItem) {
        this.orderItem = orderItem;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}

@Entity
@Table(name = "order_status_history", indexes = {
    @Index(name = "idx_history_order", columnList = "order_id"),
    @Index(name = "idx_history_timestamp", columnList = "timestamp")
})
class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private OrderStatus previousStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private OrderStatus newStatus;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "changed_by_user_id")
    private User changedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(length = 500)
    private String remarks;

    public OrderStatusHistory() {}

    public OrderStatusHistory(Order order, OrderStatus previousStatus, OrderStatus newStatus, User changedBy, String remarks) {
        this.order = order;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.remarks = remarks;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public OrderStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(OrderStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(OrderStatus newStatus) {
        this.newStatus = newStatus;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(User changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}

@Entity
@Table(name = "menu_items", indexes = {
    @Index(name = "idx_item_category", columnList = "category_id"),
    @Index(name = "idx_item_station", columnList = "station_id"),
    @Index(name = "idx_item_available", columnList = "available")
})
class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private MenuCategory category;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @NotNull
    @Min(1)
    @Column(name = "prep_time_minutes", nullable = false)
    private Integer prepTimeMinutes = 10;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private KitchenStation station;

    @Column(nullable = false)
    private boolean available = true;

    @NotNull
    @Min(0)
    @org.hibernate.annotations.ColumnDefault("50")
    @Column(name = "stock_quantity", columnDefinition = "integer default 50")
    private Integer stockQuantity = 50;

    @Column(name = "image_url", length = 300)
    private String imageUrl;

    @Column(name = "is_vegetarian", nullable = false)
    private boolean vegetarian = false;

    @Column(name = "default_modifiers", length = 300)
    private String defaultModifiers; // Comma-separated or descriptor, e.g., "Extra Cheese (+₹30), Less Spicy, No Onion"

    @Column(nullable = false)
    private boolean active = true;

    public MenuItem() {}

    public MenuItem(String name, String description, MenuCategory category, BigDecimal price,
                    Integer prepTimeMinutes, KitchenStation station, boolean vegetarian, String defaultModifiers) {
        this(name, description, category, price, prepTimeMinutes, station, vegetarian, defaultModifiers, 50);
    }

    public MenuItem(String name, String description, MenuCategory category, BigDecimal price,
                    Integer prepTimeMinutes, KitchenStation station, boolean vegetarian, String defaultModifiers, Integer stockQuantity) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.prepTimeMinutes = prepTimeMinutes;
        this.station = station;
        this.vegetarian = vegetarian;
        this.defaultModifiers = defaultModifiers;
        this.stockQuantity = stockQuantity != null ? stockQuantity : 50;
        this.available = this.stockQuantity > 0;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MenuCategory getCategory() {
        return category;
    }

    public void setCategory(MenuCategory category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getPrepTimeMinutes() {
        return prepTimeMinutes;
    }

    public void setPrepTimeMinutes(Integer prepTimeMinutes) {
        this.prepTimeMinutes = prepTimeMinutes;
    }

    public KitchenStation getStation() {
        return station;
    }

    public void setStation(KitchenStation station) {
        this.station = station;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isVegetarian() {
        return vegetarian;
    }

    public void setVegetarian(boolean vegetarian) {
        this.vegetarian = vegetarian;
    }

    public String getDefaultModifiers() {
        return defaultModifiers;
    }

    public void setDefaultModifiers(String defaultModifiers) {
        this.defaultModifiers = defaultModifiers;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
        if (this.stockQuantity != null && this.stockQuantity <= 0) {
            this.available = false;
        }
    }

    public boolean isOutOfStock() {
        return stockQuantity != null && stockQuantity <= 0;
    }

    public boolean isLowStock() {
        return stockQuantity != null && stockQuantity > 0 && stockQuantity <= 5;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

@Entity
@Table(name = "menu_categories", indexes = {
    @Index(name = "idx_category_name", columnList = "name", unique = true)
})
class MenuCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "display_order")
    private int displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    public MenuCategory() {}

    public MenuCategory(String name, String description, int displayOrder) {
        this.name = name;
        this.description = description;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

@Entity
@Table(name = "kitchen_stations", indexes = {
    @Index(name = "idx_station_code", columnList = "code", unique = true)
})
class KitchenStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(length = 255)
    private String description;

    @Column(name = "color_code", length = 20)
    private String colorCode = "#0d6efd";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order")
    private int displayOrder = 0;

    public KitchenStation() {}

    public KitchenStation(String name, String code, String description, String colorCode, int displayOrder) {
        this.name = name;
        this.code = code;
        this.description = description;
        this.colorCode = colorCode;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColorCode() {
        return colorCode;
    }

    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true)
})
class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 3, max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank
    @Column(nullable = false)
    private String password;

    @NotBlank
    @Size(max = 100)
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 100)
    private String email;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public User() {}

    public User(String username, String password, String fullName, String email, UserRole role) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.active = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_order", columnList = "order_id"),
    @Index(name = "idx_payment_time", columnList = "payment_time")
})
class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.PAID;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "processed_by_user_id")
    private User processedBy;

    @Column(name = "payment_time", nullable = false)
    private LocalDateTime paymentTime = LocalDateTime.now();

    @Column(length = 255)
    private String notes;

    public Payment() {}

    public Payment(Order order, BigDecimal amount, PaymentMethod paymentMethod,
                   String transactionReference, User processedBy, String notes) {
        this.order = order;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
        this.processedBy = processedBy;
        this.notes = notes;
        this.paymentStatus = PaymentStatus.PAID;
        this.paymentTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
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

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public User getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(User processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getPaymentTime() {
        return paymentTime;
    }

    public void setPaymentTime(LocalDateTime paymentTime) {
        this.paymentTime = paymentTime;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

@Entity
@Table(name = "restaurant_settings", indexes = {
    @Index(name = "idx_setting_key", columnList = "setting_key", unique = true)
})
class RestaurantSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "setting_key", nullable = false, unique = true, length = 80)
    private String key;

    @Column(name = "setting_value", length = 500)
    private String value;

    @Column(length = 255)
    private String description;

    public RestaurantSetting() {}

    public RestaurantSetting(String key, String value, String description) {
        this.key = key;
        this.value = value;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_target", columnList = "target_type, target_id")
})
class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 60)
    private String action;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User performedBy;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public AuditLog() {}

    public AuditLog(String action, User performedBy, String targetType, Long targetId, String details) {
        this.action = action;
        this.performedBy = performedBy;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(User performedBy) {
        this.performedBy = performedBy;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}