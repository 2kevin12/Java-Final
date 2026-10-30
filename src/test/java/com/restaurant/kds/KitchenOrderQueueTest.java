package com.restaurant.kds;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class KitchenOrderQueueTest {

    @Test
    @DisplayName("Module 1 & 2: Verify queue capacity bounds and FIFO order")
    void testQueueCapacityAndFifoOrder() throws Exception {
        KitchenOrderQueue queue = new KitchenOrderQueue(5);

        assertEquals(5, queue.getCapacity());
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
        assertFalse(queue.isFull());

        for (int i = 1; i <= 5; i++) {
            queue.placeOrder(new OrderTicket(i, "Order #" + i, "Burger", 1, "T-" + i, "Waiter-1"));
        }

        assertEquals(5, queue.size());
        assertTrue(queue.isFull());

        // Dequeue should be strictly FIFO
        for (int i = 1; i <= 5; i++) {
            OrderTicket ticket = queue.cookNextOrder();
            assertNotNull(ticket);
            assertEquals("Order #" + i, ticket.getOrderNumber());
            assertEquals("T-" + i, ticket.getTableNumber());
        }

        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("Module 3 & Rubric: Producer pauses on wait() when queue is full and wakes up on notify()")
    void testProducerWaitsOnFullAndResumesOnNotify() throws Exception {
        KitchenOrderQueue queue = new KitchenOrderQueue(5);

        // Fill all 5 carousel slots
        for (int i = 1; i <= 5; i++) {
            queue.placeOrder(new OrderTicket(i, "Order #" + i, "Burger", 1, "T-" + i, "Waiter-1"));
        }
        assertTrue(queue.isFull());

        // Waiter thread attempts to place Order #6
        OrderTicket order6 = new OrderTicket(6, "Order #6", "Truffle Gourmet Burger", 1, "T-06", "Waiter-1");
        AtomicBoolean placed = new AtomicBoolean(false);

        Thread producerThread = new Thread(() -> {
            try {
                queue.placeOrder(order6);
                placed.set(true);
            } catch (Exception e) {
                fail("Unexpected exception: " + e.getMessage());
            }
        }, "Waiter-Test-Thread");

        producerThread.start();

        // Give thread time to enter placeOrder() and pause on wait()
        Thread.sleep(400);

        // Producer must be WAITING
        Thread.State state = producerThread.getState();
        assertTrue(state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING,
                "Producer thread should be waiting on full queue, but was: " + state);
        assertFalse(placed.get(), "Order #6 must NOT be placed while carousel is full");
        assertEquals(1, queue.getWaitingProducersCount());

        // Chef takes Order #1 -> frees up space on carousel and calls notifyAll()
        OrderTicket cookedOrder = queue.cookNextOrder();
        assertEquals("Order #1", cookedOrder.getOrderNumber());

        // Wait for producer to wake up and place Order #6
        producerThread.join(2000);

        assertTrue(placed.get(), "Order #6 must be placed after chef calls notify()");
        assertEquals(5, queue.size());
        assertEquals(0, queue.getWaitingProducersCount());
    }

    @Test
    @DisplayName("Module 3: Consumer pauses on wait() when queue is empty and wakes up on notify()")
    void testConsumerWaitsOnEmptyAndResumesOnNotify() throws Exception {
        KitchenOrderQueue queue = new KitchenOrderQueue(5);
        assertTrue(queue.isEmpty());

        AtomicBoolean orderReceived = new AtomicBoolean(false);

        Thread chefThread = new Thread(() -> {
            try {
                OrderTicket ticket = queue.cookNextOrder();
                if (ticket != null && ticket.getOrderNumber().equals("Order #1")) {
                    orderReceived.set(true);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Chef-Test-Thread");

        chefThread.start();
        Thread.sleep(300);

        // Chef must be in WAITING state on empty queue
        Thread.State state = chefThread.getState();
        assertTrue(state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING,
                "Chef thread should be waiting on empty queue, but was: " + state);
        assertFalse(orderReceived.get());
        assertEquals(1, queue.getWaitingConsumersCount());

        // Producer places an order and notifies
        queue.placeOrder(new OrderTicket(1, "Order #1", "Cheese Burger", 1, "T-1", "Waiter-1"));

        chefThread.join(2000);
        assertTrue(orderReceived.get(), "Chef must wake up and receive Order #1 after producer notify()");
        assertEquals(0, queue.size());
    }

    @Test
    @DisplayName("Custom Exception: KitchenOverloadException thrown when backlog exceeds maximum cooking capacity")
    void testKitchenOverloadExceptionThrown() {
        int capacity = 2;
        int emergencyCapacity = 3;
        KitchenOrderQueue queue = new KitchenOrderQueue(capacity, emergencyCapacity);

        // Place 2 orders (at capacity)
        assertDoesNotThrow(() -> {
            queue.placeOrder(new OrderTicket(1, "Order #1", "Burger", 1, "T-1", "Waiter-1"));
            queue.placeOrder(new OrderTicket(2, "Order #2", "Burger", 1, "T-2", "Waiter-1"));
        });

        // 3rd order reaches emergency threshold (size=2, plus blocked producer = 3)
        // Attempting to place beyond emergency capacity triggers KitchenOverloadException
        KitchenOrderQueue overloadQueue = new KitchenOrderQueue(2, 2);
        assertDoesNotThrow(() -> overloadQueue.placeOrder(new OrderTicket(1, "Order #1", "Burger", 1, "T-1", "Waiter-1")));
        assertDoesNotThrow(() -> overloadQueue.placeOrder(new OrderTicket(2, "Order #2", "Burger", 1, "T-2", "Waiter-1")));

        KitchenOverloadException ex = assertThrows(KitchenOverloadException.class, () -> {
            overloadQueue.placeOrder(new OrderTicket(3, "Order #3", "Burger", 1, "T-3", "Waiter-1"));
        });

        assertTrue(ex.getMessage().contains("EMERGENCY OVERLOAD"));
        assertEquals(2, ex.getMaxCookingCapacity());
    }

    @Test
    @DisplayName("End-to-End Simulation: OrderTakerThread and ChefStationThread complete 6 orders without lost tickets")
    void testEndToEndSimulationThreadWorkflow() throws Exception {
        KitchenOrderQueue queue = new KitchenOrderQueue(5);

        List<OrderTicket> orders = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            orders.add(new OrderTicket(i, "Order #" + i, "Burger " + i, 1, "Table " + i, "Waiter-1", 30L));
        }

        OrderTakerThread waiter = new OrderTakerThread("Waiter-Thread-1", queue, orders, 10L);
        ChefStationThread chef = new ChefStationThread("Chef-Station-1", queue, 6, 40L);

        waiter.start();
        chef.start();

        waiter.join(5000);
        chef.join(5000);

        assertEquals(6, waiter.getSuccessfullyPlacedOrders().size(), "All 6 orders must be placed");
        assertEquals(6, chef.getCookedOrders().size(), "All 6 orders must be cooked");
        assertTrue(queue.isEmpty(), "Carousel must be empty at the end");
    }
}