package edu.eci.tdse.orders.service;

import edu.eci.tdse.orders.exception.InvalidOrderException;
import edu.eci.tdse.orders.exception.OrderNotFoundException;
import edu.eci.tdse.orders.model.CreateOrderRequest;
import edu.eci.tdse.orders.model.Order;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderService {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(42);

    public OrderService() {
        // Predefined data (in-memory only, NOT persistent storage)
        save(new Order("ORD-001", "CUST-42", "MacBook Air", 1));
        save(new Order("ORD-42", "CUST-7", "Keyboard", 2));
    }

    public Order createOrder(CreateOrderRequest request) {
        validate(request);
        String id = "ORD-" + sequence.incrementAndGet();
        Order order = new Order(
                id,
                request.getCustomerId().trim(),
                request.getProduct().trim(),
                request.getQuantity());
        save(order);
        return order;
    }

    public Order getOrder(String id) {
        if (isBlank(id)) {
            throw new InvalidOrderException("Order id must not be blank");
        }
        Order order = orders.get(id);
        if (order == null) {
            throw new OrderNotFoundException(id);
        }
        return order;
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        if (isBlank(customerId)) {
            throw new InvalidOrderException("customerId must not be blank");
        }
        return orders.values().stream()
                .filter(o -> o.getCustomerId().equals(customerId))
                .sorted(Comparator.comparing(Order::getId))
                .toList();
    }

    private void validate(CreateOrderRequest request) {
        if (request == null) {
            throw new InvalidOrderException("Request must not be null");
        }
        if (isBlank(request.getCustomerId())) {
            throw new InvalidOrderException("customerId must not be blank");
        }
        if (isBlank(request.getProduct())) {
            throw new InvalidOrderException("product must not be blank");
        }
        if (request.getQuantity() <= 0) {
            throw new InvalidOrderException("quantity must be greater than 0");
        }
    }

    private void save(Order order) {
        orders.put(order.getId(), order);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}