package com.restaurant.kds;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

public class KitchenRepositories {
    // Consolidated module holder: KitchenRepositories
}

@Repository
interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    @Query("SELECT o FROM Order o WHERE o.status IN :statuses ORDER BY o.createdAt ASC")
    List<Order> findByStatusInOrderByCreatedAtAsc(@Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT o FROM Order o WHERE o.status IN ('PLACED', 'ACCEPTED', 'PREPARING', 'READY') ORDER BY o.createdAt ASC")
    List<Order> findActiveOrders();

    @Query("SELECT o FROM Order o WHERE o.createdAt >= :startDate AND o.createdAt <= :endDate ORDER BY o.createdAt DESC")
    List<Order> findOrdersBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT o FROM Order o WHERE " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:orderType IS NULL OR o.orderType = :orderType) AND " +
           "(:search IS NULL OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(o.tableNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(o.customerName) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "o.createdAt >= :startDate AND o.createdAt <= :endDate " +
           "ORDER BY o.createdAt DESC")
    List<Order> searchOrders(@Param("status") OrderStatus status,
                             @Param("orderType") OrderType orderType,
                             @Param("search") String search,
                             @Param("startDate") LocalDateTime startDate,
                             @Param("endDate") LocalDateTime endDate);

    long countByStatus(OrderStatus status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :start AND o.createdAt <= :end")
    long countOrdersInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :start AND o.createdAt <= :end AND o.status = :status")
    long countOrdersInPeriodByStatus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("status") OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.createdAt >= :start AND o.createdAt <= :end AND o.status != 'CANCELLED' AND o.status != 'REJECTED'")
    java.math.BigDecimal sumTotalSalesInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o.orderType, COUNT(o) FROM Order o WHERE o.createdAt >= :start AND o.createdAt <= :end GROUP BY o.orderType")
    List<Object[]> countByOrderTypeInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

@Repository
interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);
    boolean existsByMenuItemId(Long menuItemId);

    @Query("SELECT oi FROM OrderItem oi WHERE oi.station.id = :stationId AND oi.order.status IN ('PLACED', 'ACCEPTED', 'PREPARING')")
    List<OrderItem> findActiveItemsByStation(@Param("stationId") Long stationId);

    @Query("SELECT oi.menuItem.id, oi.itemName, SUM(oi.quantity), SUM(oi.totalPrice) " +
           "FROM OrderItem oi " +
           "WHERE oi.order.createdAt >= :start AND oi.order.createdAt <= :end AND oi.order.status != 'CANCELLED' " +
           "GROUP BY oi.menuItem.id, oi.itemName " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopSellingItems(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT oi.station.name, COUNT(oi), " +
           "SUM(CASE WHEN oi.preparationStatus = 'READY' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN oi.preparationStatus = 'PREPARING' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN oi.preparationStatus = 'PENDING' THEN 1 ELSE 0 END) " +
           "FROM OrderItem oi " +
           "WHERE oi.order.createdAt >= :start AND oi.order.createdAt <= :end " +
           "GROUP BY oi.station.name")
    List<Object[]> getStationPerformance(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

@Repository
interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    List<OrderStatusHistory> findByOrderIdOrderByTimestampAsc(Long orderId);
}

@Repository
interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByActiveTrueOrderByNameAsc();
    List<MenuItem> findByActiveTrueAndAvailableTrueOrderByNameAsc();
    List<MenuItem> findByCategoryIdAndActiveTrue(Long categoryId);
    List<MenuItem> findByStationIdAndActiveTrue(Long stationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MenuItem m WHERE m.id = :id")
    Optional<MenuItem> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT m FROM MenuItem m WHERE m.active = true AND " +
           "(:categoryId IS NULL OR m.category.id = :categoryId) AND " +
           "(:stationId IS NULL OR m.station.id = :stationId) AND " +
           "(:available IS NULL OR m.available = :available) AND " +
           "(:search IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<MenuItem> searchMenu(@Param("categoryId") Long categoryId,
                              @Param("stationId") Long stationId,
                              @Param("available") Boolean available,
                              @Param("search") String search);
}

@Repository
interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {
    Optional<MenuCategory> findByName(String name);
    boolean existsByName(String name);
    List<MenuCategory> findByActiveTrueOrderByDisplayOrderAsc();
    List<MenuCategory> findAllByOrderByDisplayOrderAsc();
}

@Repository
interface KitchenStationRepository extends JpaRepository<KitchenStation, Long> {
    Optional<KitchenStation> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByName(String name);
    List<KitchenStation> findByActiveTrueOrderByDisplayOrderAsc();
    List<KitchenStation> findAllByOrderByDisplayOrderAsc();
}

@Repository
interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByRole(UserRole role);
    List<UserByRoleProjection> findAllByOrderByFullNameAsc();

    interface UserByRoleProjection {
        Long getId();
        String getUsername();
        String getFullName();
        UserRole getRole();
        boolean isActive();
    }
}

@Repository
interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderIdOrderByPaymentTimeAsc(Long orderId);

    @Query("SELECT p FROM Payment p WHERE p.paymentTime >= :start AND p.paymentTime <= :end ORDER BY p.paymentTime DESC")
    List<Payment> findPaymentsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.paymentTime >= :start AND p.paymentTime <= :end AND p.paymentStatus = 'PAID'")
    BigDecimal sumTotalPaidInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT p.paymentMethod, COUNT(p), SUM(p.amount) FROM Payment p WHERE p.paymentTime >= :start AND p.paymentTime <= :end AND p.paymentStatus = 'PAID' GROUP BY p.paymentMethod")
    List<Object[]> getPaymentMethodSummary(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

@Repository
interface RestaurantSettingRepository extends JpaRepository<RestaurantSetting, Long> {
    Optional<RestaurantSetting> findByKey(String key);
}

@Repository
interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop100ByOrderByTimestampDesc();

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:targetType IS NULL OR a.targetType = :targetType) AND " +
           "a.timestamp >= :start AND a.timestamp <= :end " +
           "ORDER BY a.timestamp DESC")
    List<AuditLog> searchLogs(@Param("action") String action,
                             @Param("targetType") String targetType,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);
}