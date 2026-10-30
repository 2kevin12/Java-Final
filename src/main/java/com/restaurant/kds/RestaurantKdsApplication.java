package com.restaurant.kds;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.boot.autoconfigure.SpringBootApplication
public class RestaurantKdsApplication {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(RestaurantKdsApplication.class, args);
    }
}

@Component
class DatabaseMigrationConfig implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationConfig.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                log.info("Checking database schema for required column upgrades...");
                try {
                    stmt.execute("ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS stock_quantity INT DEFAULT 50");
                    log.info("Database schema migration successful: stock_quantity column verified.");
                } catch (Exception ex) {
                    // Table might not exist yet if Hibernate creates it later
                    log.debug("Schema migration notice (table may be created by Hibernate): {}", ex.getMessage());
                }
            } catch (Exception e) {
                log.warn("Database connection notice during migration check: {}", e.getMessage());
            }
        }
        return bean;
    }
}

@Component
class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final KitchenStationRepository stationRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final PaymentRepository paymentRepository;
    private final RestaurantSettingRepository settingRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           KitchenStationRepository stationRepository,
                           MenuCategoryRepository categoryRepository,
                           MenuItemRepository menuItemRepository,
                           OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           OrderStatusHistoryRepository historyRepository,
                           PaymentRepository paymentRepository,
                           RestaurantSettingRepository settingRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.stationRepository = stationRepository;
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.historyRepository = historyRepository;
        this.paymentRepository = paymentRepository;
        this.settingRepository = settingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        initUsers();
        initSettings();
        initStationsAndMenu();
        ensureMenuItemStock();
        clearLegacyOrdersOnce();
        log.info("Restaurant KDS Data Initializer finished setup.");
    }

    private void ensureMenuItemStock() {
        List<MenuItem> items = menuItemRepository.findAll();
        for (MenuItem item : items) {
            if (item.getStockQuantity() == null || item.getStockQuantity() <= 0) {
                item.setStockQuantity(50);
                if (item.isActive()) {
                    item.setAvailable(true);
                }
                menuItemRepository.save(item);
            }
        }
    }

    private void clearLegacyOrdersOnce() {
        if (settingRepository.findByKey("sample_orders_cleared").isEmpty()) {
            paymentRepository.deleteAll();
            historyRepository.deleteAll();
            orderItemRepository.deleteAll();
            orderRepository.deleteAll();
            settingRepository.save(new RestaurantSetting("sample_orders_cleared", "true", "Sample Orders Cleared Flag"));
            log.info("Cleared all pre-existing demonstration orders safely for fresh start.");
        }
    }

    private void initUsers() {
        if (userRepository.count() > 0) return;

        User admin = new User("admin", passwordEncoder.encode("admin123"), "Executive Admin", "admin@restaurant.com", UserRole.ROLE_ADMIN);
        User cashier = new User("cashier", passwordEncoder.encode("cashier123"), "Frontdesk Cashier", "cashier@restaurant.com", UserRole.ROLE_CASHIER);
        User kitchen = new User("kitchen", passwordEncoder.encode("kitchen123"), "Head Chef Mario", "kitchen@restaurant.com", UserRole.ROLE_KITCHEN_STAFF);
        User manager = new User("manager", passwordEncoder.encode("manager123"), "Floor Manager Priya", "manager@restaurant.com", UserRole.ROLE_MANAGER);

        userRepository.saveAll(List.of(admin, cashier, kitchen, manager));
        log.info("Initialized default users: admin, cashier, kitchen, manager");
    }

    private void initSettings() {
        if (settingRepository.count() > 0) return;

        settingRepository.save(new RestaurantSetting("restaurant_name", "Spice & Sizzle Kitchen", "Restaurant Name"));
        settingRepository.save(new RestaurantSetting("tax_rate", "5.00", "GST / Tax Percentage (%)"));
        settingRepository.save(new RestaurantSetting("currency_symbol", "₹", "Currency Symbol"));
        settingRepository.save(new RestaurantSetting("overdue_warning_minutes", "15", "Overdue Ticket Warning Threshold (minutes)"));
        settingRepository.save(new RestaurantSetting("auto_accept_orders", "false", "Automatically accept incoming orders"));
        settingRepository.save(new RestaurantSetting("contact_phone", "+91 98765 43210", "Restaurant Contact Number"));
        settingRepository.save(new RestaurantSetting("address", "124 Gourmet Boulevard, Bengaluru, Karnataka", "Restaurant Address"));
    }

    private void initStationsAndMenu() {
        if (categoryRepository.count() > 0) return;

        // Stations
        KitchenStation grill = stationRepository.save(new KitchenStation("Grill Station", "GRILL", "Burgers, steaks, tikkas and barbecues", "#e63946", 1));
        KitchenStation fryer = stationRepository.save(new KitchenStation("Fryer Station", "FRYER", "French fries, fritters, fried chicken", "#f4a261", 2));
        KitchenStation main = stationRepository.save(new KitchenStation("Main Kitchen", "MAIN", "Curries, breads, rice dishes, gravies", "#2a9d8f", 3));
        KitchenStation bev = stationRepository.save(new KitchenStation("Beverage Counter", "BEV", "Cold and hot beverages, mocktails, coffee", "#457b9d", 4));
        KitchenStation dessert = stationRepository.save(new KitchenStation("Dessert Station", "DESSERT", "Pastries, ice creams, traditional sweets", "#e76f51", 5));

        // Categories
        MenuCategory catStarters = categoryRepository.save(new MenuCategory("Starters", "Appetizers and quick bites", 1));
        MenuCategory catMain = categoryRepository.save(new MenuCategory("Main Course", "Traditional curries and hearty meals", 2));
        MenuCategory catFastFood = categoryRepository.save(new MenuCategory("Fast Food", "Burgers, sandwiches, and crispy sides", 3));
        MenuCategory catBev = categoryRepository.save(new MenuCategory("Beverages", "Refreshing drinks and coffees", 4));
        MenuCategory catDesserts = categoryRepository.save(new MenuCategory("Desserts", "Sweet delicacies and desserts", 5));

        // Menu Items
        menuItemRepository.save(new MenuItem("Chicken Burger", "Juicy grilled chicken patty with fresh lettuce, mayo, and pickles",
                catFastFood, new BigDecimal("180.00"), 12, grill, false, "Extra Cheese (+₹30), Spicy Mayo, No Onion"));

        menuItemRepository.save(new MenuItem("Crispy French Fries", "Golden salted crispy potato fries served with dip",
                catFastFood, new BigDecimal("110.00"), 8, fryer, true, "Peri-Peri Seasoning, Cheesy Dip (+₹25)"));

        menuItemRepository.save(new MenuItem("Paneer Butter Masala", "Cottage cheese cubes in rich tomato cashew butter gravy",
                catMain, new BigDecimal("260.00"), 15, main, true, "Less Spicy, Extra Butter, Gluten-Free"));

        menuItemRepository.save(new MenuItem("Butter Naan", "Tandoor baked soft flatbread brushed with butter",
                catMain, new BigDecimal("45.00"), 5, main, true, "Garlic Flavor (+₹15), Extra Butter"));

        menuItemRepository.save(new MenuItem("Grilled Chicken Tikka", "Spiced boneless chicken chunks charred to perfection in clay oven",
                catStarters, new BigDecimal("280.00"), 16, grill, false, "Mint Chutney, Extra Spicy, Lemon Wedges"));

        menuItemRepository.save(new MenuItem("Crispy Veg Spring Rolls", "Shredded veggies wrapped in crunchy golden skin with sweet chili sauce",
                catStarters, new BigDecimal("150.00"), 10, fryer, true, "Sweet Chili Sauce, Extra Dip"));

        menuItemRepository.save(new MenuItem("Cold Coffee with Ice Cream", "Chilled whipped coffee topped with vanilla ice cream",
                catBev, new BigDecimal("120.00"), 6, bev, true, "Less Sugar, Extra Scoop (+₹35), Strong Coffee"));

        menuItemRepository.save(new MenuItem("Mango Mint Cooler", "Refreshing raw mango pulp infused with fresh mint and soda",
                catBev, new BigDecimal("110.00"), 5, bev, true, "Extra Ice, No Sugar"));

        menuItemRepository.save(new MenuItem("Sizzling Chocolate Brownie", "Warm chocolate walnut brownie served on a sizzler plate with ice cream",
                catDesserts, new BigDecimal("190.00"), 8, dessert, true, "Extra Fudge (+₹20), Nuts on Top"));

        menuItemRepository.save(new MenuItem("Gulab Jamun (2 Pcs)", "Traditional warm milk solid dumplings soaked in cardamom sugar syrup",
                catDesserts, new BigDecimal("80.00"), 4, dessert, true, "Serve Warm, Pistachio Garnish"));

        log.info("Initialized menu items across 5 categories and 5 kitchen stations");
    }

    private void initSampleOrders() {
        if (orderRepository.count() > 0) return;

        User cashier = userRepository.findByUsername("cashier").orElse(null);
        MenuItem burger = menuItemRepository.findAll().stream().filter(m -> m.getName().contains("Burger")).findFirst().orElse(null);
        MenuItem fries = menuItemRepository.findAll().stream().filter(m -> m.getName().contains("Fries")).findFirst().orElse(null);
        MenuItem paneer = menuItemRepository.findAll().stream().filter(m -> m.getName().contains("Paneer")).findFirst().orElse(null);
        MenuItem naan = menuItemRepository.findAll().stream().filter(m -> m.getName().contains("Naan")).findFirst().orElse(null);
        MenuItem coffee = menuItemRepository.findAll().stream().filter(m -> m.getName().contains("Coffee")).findFirst().orElse(null);
        MenuItem brownie = menuItemRepository.findAll().stream().filter(m -> m.getName().contains("Brownie")).findFirst().orElse(null);

        if (burger == null || fries == null) return;

        // Order 1: PLACED (New Order Ticket)
        Order o1 = new Order();
        o1.setOrderNumber("ORD-SAMPLE-101");
        o1.setOrderType(OrderType.DINE_IN);
        o1.setTableNumber("T-04");
        o1.setCustomerName("Rahul Sharma");
        o1.setCustomerPhone("9876543210");
        o1.setCreatedBy(cashier);
        o1.setStatus(OrderStatus.PLACED);
        o1.setPriority(OrderPriority.NORMAL);
        o1.setCreatedAt(LocalDateTime.now().minusMinutes(4));
        OrderItem item1 = new OrderItem(o1, burger, 2, "Less spicy please");
        item1.addModifier("Extra Cheese", new BigDecimal("30.00"));
        OrderItem item2 = new OrderItem(o1, fries, 1, "Crispy fried");
        o1.addItem(item1);
        o1.addItem(item2);
        o1.recalculateTotals();
        orderRepository.save(o1);
        historyRepository.save(new OrderStatusHistory(o1, null, OrderStatus.PLACED, cashier, "Order placed"));

        // Order 2: PREPARING (In kitchen progress)
        Order o2 = new Order();
        o2.setOrderNumber("ORD-SAMPLE-102");
        o2.setOrderType(OrderType.TAKEAWAY);
        o2.setTableNumber("TA-08");
        o2.setCustomerName("Ananya Sen");
        o2.setCreatedBy(cashier);
        o2.setStatus(OrderStatus.PREPARING);
        o2.setPriority(OrderPriority.HIGH);
        o2.setCreatedAt(LocalDateTime.now().minusMinutes(12));
        o2.setAcceptedAt(LocalDateTime.now().minusMinutes(10));
        o2.setStartedAt(LocalDateTime.now().minusMinutes(8));
        OrderItem item3 = new OrderItem(o2, paneer, 1, "Medium spice");
        item3.setPreparationStatus(ItemPreparationStatus.PREPARING);
        item3.setStartedAt(LocalDateTime.now().minusMinutes(8));
        OrderItem item4 = new OrderItem(o2, naan, 3, "Crisp butter naan");
        item4.setPreparationStatus(ItemPreparationStatus.READY);
        item4.setStartedAt(LocalDateTime.now().minusMinutes(8));
        item4.setReadyAt(LocalDateTime.now().minusMinutes(2));
        o2.addItem(item3);
        o2.addItem(item4);
        o2.recalculateTotals();
        orderRepository.save(o2);
        historyRepository.save(new OrderStatusHistory(o2, null, OrderStatus.PLACED, cashier, "Order placed"));
        historyRepository.save(new OrderStatusHistory(o2, OrderStatus.PLACED, OrderStatus.ACCEPTED, cashier, "Accepted"));
        historyRepository.save(new OrderStatusHistory(o2, OrderStatus.ACCEPTED, OrderStatus.PREPARING, cashier, "Started prep"));

        // Order 3: READY (Ready to serve)
        Order o3 = new Order();
        o3.setOrderNumber("ORD-SAMPLE-103");
        o3.setOrderType(OrderType.DINE_IN);
        o3.setTableNumber("T-12");
        o3.setCustomerName("Vikram Verma");
        o3.setCreatedBy(cashier);
        o3.setStatus(OrderStatus.READY);
        o3.setPriority(OrderPriority.NORMAL);
        o3.setCreatedAt(LocalDateTime.now().minusMinutes(18));
        o3.setAcceptedAt(LocalDateTime.now().minusMinutes(16));
        o3.setStartedAt(LocalDateTime.now().minusMinutes(14));
        o3.setReadyAt(LocalDateTime.now().minusMinutes(2));
        OrderItem item5 = new OrderItem(o3, coffee, 2, "Less sugar");
        item5.setPreparationStatus(ItemPreparationStatus.READY);
        item5.setStartedAt(LocalDateTime.now().minusMinutes(14));
        item5.setReadyAt(LocalDateTime.now().minusMinutes(3));
        OrderItem item6 = new OrderItem(o3, brownie, 1, "Warm fudge");
        item6.setPreparationStatus(ItemPreparationStatus.READY);
        item6.setStartedAt(LocalDateTime.now().minusMinutes(14));
        item6.setReadyAt(LocalDateTime.now().minusMinutes(2));
        o3.addItem(item5);
        o3.addItem(item6);
        o3.recalculateTotals();
        orderRepository.save(o3);
        historyRepository.save(new OrderStatusHistory(o3, null, OrderStatus.PLACED, cashier, "Order placed"));
        historyRepository.save(new OrderStatusHistory(o3, OrderStatus.PLACED, OrderStatus.ACCEPTED, cashier, "Accepted"));
        historyRepository.save(new OrderStatusHistory(o3, OrderStatus.ACCEPTED, OrderStatus.PREPARING, cashier, "Started"));
        historyRepository.save(new OrderStatusHistory(o3, OrderStatus.PREPARING, OrderStatus.READY, cashier, "Ready to serve"));

        log.info("Initialized 3 sample demonstration orders in PLACED, PREPARING, and READY states");
    }
}

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            User user = userRepository.findByUsername(username.toLowerCase().trim())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            if (!user.isActive()) {
                throw new UsernameNotFoundException("User account is deactivated: " + username);
            }

            return new org.springframework.security.core.userdetails.User(
                    user.getUsername(),
                    user.getPassword(),
                    user.isActive(),
                    true, true, true,
                    Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
            );
        };
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return (request, response, authentication) -> {
            boolean isKitchenOnly = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_KITCHEN_STAFF"));
            boolean isCashier = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_CASHIER"));

            if (isKitchenOnly) {
                response.sendRedirect("/kds");
            } else if (isCashier) {
                response.sendRedirect("/orders/new");
            } else {
                response.sendRedirect("/dashboard");
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**") // Disable CSRF for REST APIs
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/sounds/**", "/images/**", "/error").permitAll()
                // Kitchen Display & Simulation
                .requestMatchers("/kds/**", "/api/kitchen/**", "/api/simulation/**").hasAnyRole("ADMIN", "KITCHEN_STAFF", "MANAGER")
                // Orders & Billing
                .requestMatchers("/orders/new", "/orders/**", "/billing/**", "/api/orders/**", "/api/order-items/**", "/api/billing/**").hasAnyRole("ADMIN", "CASHIER", "MANAGER", "KITCHEN_STAFF")
                // Menu and Stations Read APIs
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/menu/**", "/api/stations/**").hasAnyRole("ADMIN", "CASHIER", "MANAGER", "KITCHEN_STAFF")
                // Menu, Stations, Settings, Users, Audit
                .requestMatchers("/menu/**", "/stations/**", "/users/**", "/settings/**", "/audit/**", "/api/menu/**", "/api/stations/**").hasRole("ADMIN")
                // Dashboard & Reports
                .requestMatchers("/dashboard", "/reports/**", "/api/dashboard/**", "/api/reports/**").hasAnyRole("ADMIN", "MANAGER")
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(customSuccessHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/error?accessDenied=true")
            );

        return http.build();
    }
}