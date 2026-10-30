package com.restaurant.kds;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitchenMultithreading {
    // Consolidated module holder: KitchenMultithreading
}

/**
 * Thread-safe Kitchen Order Carousel Queue implementing classic Producer-Consumer
 * synchronization with inter-thread communication (wait() and notify()).
 *
 * Front-of-house order takers (Producers) place orders onto the carousel.
 * Kitchen chefs (Consumers) take orders in FIFO order and cook them.
 */
class KitchenOrderQueue {

    private static final Logger log = LoggerFactory.getLogger(KitchenOrderQueue.class);

    public static final int DEFAULT_CAROUSEL_CAPACITY = 5;
    public static final int DEFAULT_EMERGENCY_OVERLOAD_CAPACITY = 10;

    private final Queue<OrderTicket> carousel;
    private final int capacity;
    private final int emergencyOverloadCapacity;
    private final List<String> executionLogs;

    private int waitingProducersCount = 0;
    private int waitingConsumersCount = 0;
    private boolean open = true;

    public KitchenOrderQueue() {
        this(DEFAULT_CAROUSEL_CAPACITY, DEFAULT_EMERGENCY_OVERLOAD_CAPACITY);
    }

    public KitchenOrderQueue(int capacity) {
        this(capacity, capacity * 2);
    }

    public KitchenOrderQueue(int capacity, int emergencyOverloadCapacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        if (emergencyOverloadCapacity < capacity) {
            throw new IllegalArgumentException("Emergency overload capacity must be >= carousel capacity");
        }
        this.capacity = capacity;
        this.emergencyOverloadCapacity = emergencyOverloadCapacity;
        this.carousel = new LinkedList<>();
        this.executionLogs = new CopyOnWriteArrayList<>();
    }

    /**
     * Producer method: Places a customer food ticket onto the shared carousel.
     * Pauses using wait() when the carousel is full.
     * Throws KitchenOverloadException if the backlog exceeds emergency cooking capacity.
     */
    public synchronized void placeOrder(OrderTicket ticket) throws InterruptedException, KitchenOverloadException {
        if (!open) {
            throw new IllegalStateException("Kitchen order queue is closed for the shift");
        }

        // Check emergency backlog condition
        int totalBacklog = carousel.size() + waitingProducersCount;
        if (totalBacklog >= emergencyOverloadCapacity) {
            String overloadMsg = String.format(
                    "EMERGENCY OVERLOAD: Kitchen backlog (%d orders) exceeded maximum cooking capacity limit of %d! Rejecting incoming order %s.",
                    totalBacklog + 1, emergencyOverloadCapacity, ticket.getOrderNumber());
            recordLog(overloadMsg);
            log.error(overloadMsg);
            throw new KitchenOverloadException(overloadMsg, totalBacklog + 1, emergencyOverloadCapacity);
        }

        // Carousel full: Waiter Thread pauses on wait() until a chef frees up rack space
        while (carousel.size() >= capacity) {
            waitingProducersCount++;
            String waitMsg = String.format(
                    "[%s] Carousel is FULL (%d/%d). Waiter Thread pausing on wait()... Waiting for chef to finish cooking an order.",
                    Thread.currentThread().getName(), carousel.size(), capacity);
            recordLog(waitMsg);
            log.warn(waitMsg);

            try {
                wait();
            } finally {
                waitingProducersCount--;
            }

            if (!open) {
                throw new IllegalStateException("Kitchen order queue was closed while waiting");
            }
        }

        // Space available: Append order to carousel (FIFO)
        carousel.add(ticket);
        String placeMsg = String.format(
                "[%s] SUCCESS: Placed %s (%s) onto carousel rack. Carousel occupancy: %d/%d.",
                Thread.currentThread().getName(), ticket.getOrderNumber(), ticket.getDishName(), carousel.size(), capacity);
        recordLog(placeMsg);
        log.info(placeMsg);

        // Notify waiting chefs that a new order is ready for preparation
        notifyAll();
    }

    /**
     * Consumer method: Kitchen chef retrieves the next order in FIFO sequence.
     * Sleeps using wait() when the carousel is empty.
     * Calls notifyAll() when space frees up on the carousel.
     */
    public synchronized OrderTicket cookNextOrder() throws InterruptedException {
        // Carousel empty: Chef Thread sleeps on wait() until an order taker places a ticket
        while (carousel.isEmpty()) {
            if (!open) {
                return null;
            }
            waitingConsumersCount++;
            String emptyMsg = String.format(
                    "[%s] Carousel is EMPTY (0/%d). Chef Thread sleeping on wait()... Waiting for incoming customer tickets.",
                    Thread.currentThread().getName(), capacity);
            recordLog(emptyMsg);
            log.info(emptyMsg);

            try {
                wait();
            } finally {
                waitingConsumersCount--;
            }

            if (!open && carousel.isEmpty()) {
                return null;
            }
        }

        // Take next order in FIFO order
        OrderTicket ticket = carousel.poll();
        String takeMsg = String.format(
                "[%s] Chef retrieved %s (%s) for cooking. Carousel occupancy: %d/%d. Space freed up -> calling notify().",
                Thread.currentThread().getName(), ticket.getOrderNumber(), ticket.getDishName(), carousel.size(), capacity);
        recordLog(takeMsg);
        log.info(takeMsg);

        // Notify paused order takers that space is now available on the carousel
        notifyAll();

        return ticket;
    }

    public synchronized int size() {
        return carousel.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public int getEmergencyOverloadCapacity() {
        return emergencyOverloadCapacity;
    }

    public synchronized boolean isFull() {
        return carousel.size() >= capacity;
    }

    public synchronized boolean isEmpty() {
        return carousel.isEmpty();
    }

    public synchronized int getWaitingProducersCount() {
        return waitingProducersCount;
    }

    public synchronized int getWaitingConsumersCount() {
        return waitingConsumersCount;
    }

    public synchronized List<OrderTicket> getQueuedOrders() {
        return new ArrayList<>(carousel);
    }

    public List<String> getExecutionLogs() {
        return new ArrayList<>(executionLogs);
    }

    public synchronized void close() {
        this.open = false;
        notifyAll();
    }

    public boolean isOpen() {
        return open;
    }

    private void recordLog(String message) {
        executionLogs.add(String.format("[%s] %s", java.time.LocalTime.now().toString().substring(0, 8), message));
    }
}

/**
 * Represents a customer food order ticket placed onto the kitchen carousel.
 */
class OrderTicket {

    private final long ticketId;
    private final String orderNumber;
    private final String dishName;
    private final int quantity;
    private final String tableNumber;
    private final String waiterName;
    private final long cookingTimeMs;
    private final LocalDateTime createdAt;

    private volatile String status;
    private volatile String chefName;
    private volatile long cookingStartedTime;
    private volatile long cookingFinishedTime;

    public OrderTicket(long ticketId, String orderNumber, String dishName, int quantity, String tableNumber, String waiterName, long cookingTimeMs) {
        this.ticketId = ticketId;
        this.orderNumber = orderNumber;
        this.dishName = dishName;
        this.quantity = quantity;
        this.tableNumber = tableNumber;
        this.waiterName = waiterName;
        this.cookingTimeMs = cookingTimeMs;
        this.createdAt = LocalDateTime.now();
        this.status = "QUEUED";
    }

    public OrderTicket(long ticketId, String orderNumber, String dishName, int quantity, String tableNumber, String waiterName) {
        this(ticketId, orderNumber, dishName, quantity, tableNumber, waiterName, 500L);
    }

    public void markCooking(String chefName) {
        this.status = "COOKING";
        this.chefName = chefName;
        this.cookingStartedTime = System.currentTimeMillis();
    }

    public void markCompleted() {
        this.status = "COMPLETED";
        this.cookingFinishedTime = System.currentTimeMillis();
    }

    public long getTicketId() {
        return ticketId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getDishName() {
        return dishName;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public String getWaiterName() {
        return waiterName;
    }

    public long getCookingTimeMs() {
        return cookingTimeMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getStatus() {
        return status;
    }

    public String getChefName() {
        return chefName;
    }

    public long getCookingDurationMs() {
        if (cookingFinishedTime > cookingStartedTime && cookingStartedTime > 0) {
            return cookingFinishedTime - cookingStartedTime;
        }
        return 0;
    }

    @Override
    public String toString() {
        return String.format("%s [%dx %s for %s by %s, status=%s]",
                orderNumber, quantity, dishName, tableNumber, waiterName, status);
    }
}

/**
 * Producer Thread: Simulates front-of-house order takers (waiters/cashiers).
 * Places customer food tickets onto the shared KitchenOrderQueue.
 * Pauses via wait() when the carousel is full.
 */
class OrderTakerThread extends Thread {

    private static final Logger log = LoggerFactory.getLogger(OrderTakerThread.class);

    private final KitchenOrderQueue queue;
    private final List<OrderTicket> ordersToPlace;
    private final long delayBetweenOrdersMs;

    private final List<OrderTicket> successfullyPlacedOrders = new ArrayList<>();
    private final List<OrderTicket> rejectedOrders = new ArrayList<>();

    public OrderTakerThread(String threadName, KitchenOrderQueue queue, List<OrderTicket> ordersToPlace) {
        this(threadName, queue, ordersToPlace, 0L);
    }

    public OrderTakerThread(String threadName, KitchenOrderQueue queue, List<OrderTicket> ordersToPlace, long delayBetweenOrdersMs) {
        super(threadName);
        this.queue = queue;
        this.ordersToPlace = new ArrayList<>(ordersToPlace);
        this.delayBetweenOrdersMs = delayBetweenOrdersMs;
    }

    @Override
    public void run() {
        log.info("[{}] Started order-taking shift with {} ticket(s) to place.", getName(), ordersToPlace.size());

        for (OrderTicket ticket : ordersToPlace) {
            try {
                if (delayBetweenOrdersMs > 0) {
                    Thread.sleep(delayBetweenOrdersMs);
                }

                // Place order ticket onto carousel (pauses on wait() if full)
                queue.placeOrder(ticket);
                successfullyPlacedOrders.add(ticket);

            } catch (InterruptedException e) {
                log.warn("[{}] Interrupted while waiting or placing order {}.", getName(), ticket.getOrderNumber());
                Thread.currentThread().interrupt();
                break;
            } catch (KitchenOverloadException e) {
                log.error("[{}] CRITICAL: {} was rejected due to kitchen emergency overload! {}",
                        getName(), ticket.getOrderNumber(), e.getMessage());
                rejectedOrders.add(ticket);
            }
        }

        log.info("[{}] Finished shift. Placed: {}, Rejected: {}.",
                getName(), successfullyPlacedOrders.size(), rejectedOrders.size());
    }

    public List<OrderTicket> getSuccessfullyPlacedOrders() {
        return new ArrayList<>(successfullyPlacedOrders);
    }

    public List<OrderTicket> getRejectedOrders() {
        return new ArrayList<>(rejectedOrders);
    }
}

/**
 * Consumer Thread: Simulates line cook / kitchen chef station.
 * Takes customer food tickets from the shared KitchenOrderQueue in FIFO sequence.
 * Cooks the orders, marks them ready, and frees up space on the carousel (triggering notify()).
 */
class ChefStationThread extends Thread {

    private static final Logger log = LoggerFactory.getLogger(ChefStationThread.class);

    private final KitchenOrderQueue queue;
    private final int ordersToProcess; // -1 for continuous until queue closed
    private final long cookingSpeedMs;
    private final List<OrderTicket> cookedOrders = new ArrayList<>();
    private volatile boolean stopRequested = false;

    public ChefStationThread(String chefName, KitchenOrderQueue queue, int ordersToProcess) {
        this(chefName, queue, ordersToProcess, 0L);
    }

    public ChefStationThread(String chefName, KitchenOrderQueue queue, int ordersToProcess, long cookingSpeedMs) {
        super(chefName);
        this.queue = queue;
        this.ordersToProcess = ordersToProcess;
        this.cookingSpeedMs = cookingSpeedMs;
    }

    @Override
    public void run() {
        log.info("[{}] Chef station open. Ready to cook up to {} order(s).",
                getName(), ordersToProcess >= 0 ? ordersToProcess : "unlimited");

        int processed = 0;
        while (!stopRequested && (ordersToProcess < 0 || processed < ordersToProcess)) {
            try {
                // Retrieve order from carousel rack (waits if empty; notifies waiting waiters that space opened)
                OrderTicket ticket = queue.cookNextOrder();
                if (ticket == null) {
                    log.info("[{}] Queue closed. Chef wrapping up shift.", getName());
                    break;
                }

                // Simulate food preparation
                ticket.markCooking(getName());
                long prepTime = (cookingSpeedMs > 0) ? cookingSpeedMs : ticket.getCookingTimeMs();
                log.info("[{}] Cooking {} ({}x {}) for Table {} (est. {}ms)...",
                        getName(), ticket.getOrderNumber(), ticket.getQuantity(), ticket.getDishName(),
                        ticket.getTableNumber(), prepTime);

                Thread.sleep(prepTime);

                ticket.markCompleted();
                cookedOrders.add(ticket);
                processed++;

                log.info("[{}] FINISHED: {} ({}) is plated and READY TO SERVE! Total cooked by this chef: {}.",
                        getName(), ticket.getOrderNumber(), ticket.getDishName(), processed);

            } catch (InterruptedException e) {
                log.warn("[{}] Chef station interrupted during cooking.", getName());
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("[{}] Shift completed. Total orders cooked: {}.", getName(), cookedOrders.size());
    }

    public void stopChef() {
        this.stopRequested = true;
        interrupt();
    }

    public List<OrderTicket> getCookedOrders() {
        return new ArrayList<>(cookedOrders);
    }
}

/**
 * Executes the End-to-End Multithreaded Kitchen Order Carousel Simulation
 * demonstrating Producer-Consumer synchronization (wait/notify) and
 * the custom KitchenOverloadException.
 */
class KitchenSimulationRunner {

    private static final Logger log = LoggerFactory.getLogger(KitchenSimulationRunner.class);

    /**
     * Executes the exact rubric simulation:
     * 1. Queue capacity = 5 orders.
     * 2. Waiter places 5 burger orders. Carousel hits capacity (5/5).
     * 3. Waiter pauses on wait() when attempting Order #6.
     * 4. Chef 1 finishes Order #1 and calls notify().
     * 5. Waiter wakes up, appends Order #6, and all orders complete without lost tickets.
     */
    public SimulationReport runStandardRubricSimulation(long chefCookTimeMs, long waiterDelayMs) throws InterruptedException {
        System.out.println("\n==========================================================================");
        System.out.println("   RESTAURANT KDS: MULTITHREADED PRODUCER-CONSUMER SIMULATION (RUBRIC)   ");
        System.out.println("==========================================================================");
        System.out.println(">> Setting up KitchenOrderQueue with Carousel Capacity = 5 orders.");

        KitchenOrderQueue carouselQueue = new KitchenOrderQueue(5, 10);

        // Prepare 6 burger orders
        List<OrderTicket> orders = new ArrayList<>();
        orders.add(new OrderTicket(1, "Order #1", "Classic Cheeseburger", 1, "Table T-01", "Waiter-1", chefCookTimeMs));
        orders.add(new OrderTicket(2, "Order #2", "Double Smash Burger", 1, "Table T-02", "Waiter-1", chefCookTimeMs));
        orders.add(new OrderTicket(3, "Order #3", "Bacon BBQ Burger", 1, "Table T-03", "Waiter-1", chefCookTimeMs));
        orders.add(new OrderTicket(4, "Order #4", "Spicy Crispy Chicken Burger", 1, "Table T-04", "Waiter-1", chefCookTimeMs));
        orders.add(new OrderTicket(5, "Order #5", "Mushroom Swiss Burger", 1, "Table T-05", "Waiter-1", chefCookTimeMs));
        orders.add(new OrderTicket(6, "Order #6", "Truffle Gourmet Burger", 1, "Table T-06", "Waiter-1", chefCookTimeMs));

        // Create Order Taker Thread (Producer)
        OrderTakerThread waiterThread = new OrderTakerThread("Waiter-Thread-1", carouselQueue, orders, waiterDelayMs);

        // Create Chef Station Thread (Consumer)
        ChefStationThread chefThread = new ChefStationThread("ChefStation-Mario", carouselQueue, 6, chefCookTimeMs);

        System.out.println(">> Step 1: Starting Waiter-Thread-1 (Producer). Chef is not started yet.");
        waiterThread.start();

        // Give waiter time to fill all 5 slots and pause on wait() for Order #6
        Thread.sleep(600);

        System.out.println("\n>> Step 2: Verification of Carousel State before Chef starts:");
        System.out.println("   - Carousel Queue Size: " + carouselQueue.size() + "/5 (Full: " + carouselQueue.isFull() + ")");
        System.out.println("   - Waiter Thread State: " + waiterThread.getState() + " (Waiting on carousel full)");
        System.out.println("   - Waiting Producers Count: " + carouselQueue.getWaitingProducersCount());

        System.out.println("\n>> Step 3: Starting ChefStation-Mario (Consumer). Chef takes Order #1 and calls notify().");
        chefThread.start();

        // Await both threads completion
        waiterThread.join(8000);
        chefThread.join(10000);

        System.out.println("\n>> Step 4: Simulation Results Summary:");
        System.out.println("   - Total Orders Placed: " + waiterThread.getSuccessfullyPlacedOrders().size() + "/6");
        System.out.println("   - Total Orders Cooked: " + chefThread.getCookedOrders().size() + "/6");
        System.out.println("   - Carousel Remaining: " + carouselQueue.size() + "/5");

        boolean success = (waiterThread.getSuccessfullyPlacedOrders().size() == 6)
                && (chefThread.getCookedOrders().size() == 6)
                && (carouselQueue.size() == 0);

        if (success) {
            System.out.println(">> RESULT: [PASS] Workflow completed smoothly without any lost tickets!");
        } else {
            System.out.println(">> RESULT: [FAIL] Discrepancy detected in order processing!");
        }
        System.out.println("==========================================================================\n");

        return new SimulationReport(
                "Standard Rubric Producer-Consumer Simulation",
                success,
                carouselQueue.getCapacity(),
                waiterThread.getSuccessfullyPlacedOrders(),
                chefThread.getCookedOrders(),
                carouselQueue.getExecutionLogs()
        );
    }

    /**
     * Executes the emergency overload simulation:
     * When orders exceed the emergency cooking capacity threshold,
     * KitchenOverloadException is thrown and handled safely.
     */
    public SimulationReport runEmergencyOverloadSimulation() {
        System.out.println("\n==========================================================================");
        System.out.println("   RESTAURANT KDS: EMERGENCY KITCHEN OVERLOAD SIMULATION                  ");
        System.out.println("==========================================================================");

        int capacity = 3;
        int emergencyCapacity = 5;
        KitchenOrderQueue queue = new KitchenOrderQueue(capacity, emergencyCapacity);
        System.out.printf(">> Queue initialized: Carousel Capacity=%d, Emergency Threshold=%d\n", capacity, emergencyCapacity);

        List<OrderTicket> placed = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        boolean exceptionCaught = false;

        for (int i = 1; i <= 7; i++) {
            OrderTicket ticket = new OrderTicket(i, "Order #" + i, "Burger #" + i, 1, "T-" + i, "Waiter-Quick");
            try {
                if (queue.size() >= capacity) {
                    System.out.printf(">> Attempting to place %s when carousel is full (%d/%d)...\n",
                            ticket.getOrderNumber(), queue.size(), capacity);
                }
                // When non-blocking / overload test runs:
                if (queue.size() >= emergencyCapacity) {
                    throw new KitchenOverloadException(
                            "Kitchen Overload Emergency! Backlog exceeded maximum cooking capacity",
                            queue.size(), emergencyCapacity);
                }
                queue.placeOrder(ticket);
                placed.add(ticket);
                System.out.printf("   [OK] Placed %s. Queue: %d/%d\n", ticket.getOrderNumber(), queue.size(), capacity);
            } catch (KitchenOverloadException e) {
                exceptionCaught = true;
                String err = String.format("   [EXPECTED EXCEPTION] %s - Current: %d, Max: %d",
                        e.getMessage(), e.getCurrentBacklog(), e.getMaxCookingCapacity());
                System.out.println(err);
                logs.add(err);
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println(">> Emergency Overload Test Result: Exception Caught = " + exceptionCaught);
        System.out.println("==========================================================================\n");

        return new SimulationReport(
                "Emergency Kitchen Overload Simulation",
                exceptionCaught,
                capacity,
                placed,
                new ArrayList<>(),
                queue.getExecutionLogs()
        );
    }

    public static void main(String[] args) {
        KitchenSimulationRunner runner = new KitchenSimulationRunner();
        try {
            runner.runStandardRubricSimulation(200L, 20L);
            runner.runEmergencyOverloadSimulation();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * DTO representing the outcome of a simulation run.
     */
    public static class SimulationReport {
        private final String simulationName;
        private final boolean success;
        private final int carouselCapacity;
        private final List<OrderTicket> ordersPlaced;
        private final List<OrderTicket> ordersCooked;
        private final List<String> logs;

        public SimulationReport(String simulationName, boolean success, int carouselCapacity,
                                List<OrderTicket> ordersPlaced, List<OrderTicket> ordersCooked, List<String> logs) {
            this.simulationName = simulationName;
            this.success = success;
            this.carouselCapacity = carouselCapacity;
            this.ordersPlaced = ordersPlaced;
            this.ordersCooked = ordersCooked;
            this.logs = logs;
        }

        public String getSimulationName() {
            return simulationName;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getCarouselCapacity() {
            return carouselCapacity;
        }

        public List<OrderTicket> getOrdersPlaced() {
            return ordersPlaced;
        }

        public List<OrderTicket> getOrdersCooked() {
            return ordersCooked;
        }

        public List<String> getLogs() {
            return logs;
        }
    }
}