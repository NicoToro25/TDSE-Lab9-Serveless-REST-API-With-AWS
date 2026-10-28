package edu.eci.tdse.orders;

import edu.eci.tdse.orders.exception.InvalidOrderException;
import edu.eci.tdse.orders.exception.OrderNotFoundException;
import edu.eci.tdse.orders.model.CreateOrderRequest;
import edu.eci.tdse.orders.model.Order;
import edu.eci.tdse.orders.service.OrderService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService();
    }

    // ---------- create ----------

    @Test
    void createOrder_withValidRequest_returnsOrderWithGeneratedId() {
        Order order = service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));

        assertEquals("ORD-43", order.getId());
        assertEquals("CUST-42", order.getCustomerId());
        assertEquals("MacBook Air", order.getProduct());
        assertEquals(1, order.getQuantity());
    }

    @Test
    void createOrder_generatesDifferentIdsForEachOrder() {
        Order first = service.createOrder(new CreateOrderRequest("CUST-1", "Mouse", 1));
        Order second = service.createOrder(new CreateOrderRequest("CUST-1", "Mouse", 1));

        assertNotEquals(first.getId(), second.getId());
    }

    // ---------- retrieve ----------

    @Test
    void getOrder_existingOrder_returnsIt() {
        Order order = service.getOrder("ORD-42");

        assertEquals("ORD-42", order.getId());
        assertEquals("CUST-7", order.getCustomerId());
        assertEquals("Keyboard", order.getProduct());
        assertEquals(2, order.getQuantity());
    }

    @Test
    void getOrder_afterCreate_returnsTheCreatedOrder() {
        Order created = service.createOrder(new CreateOrderRequest("CUST-9", "Monitor", 3));

        Order found = service.getOrder(created.getId());

        assertEquals("CUST-9", found.getCustomerId());
        assertEquals(3, found.getQuantity());
    }

    @Test
    void getOrder_nonExistingOrder_throwsOrderNotFound() {
        OrderNotFoundException ex = assertThrows(OrderNotFoundException.class,
                () -> service.getOrder("ORD-999"));

        assertEquals("ORD-999", ex.getOrderId());
        assertEquals("Order ORD-999 does not exist", ex.getMessage());
    }

    @Test
    void getOrder_blankId_throwsInvalidOrder() {
        assertThrows(InvalidOrderException.class, () -> service.getOrder("  "));
        assertThrows(InvalidOrderException.class, () -> service.getOrder(null));
    }

    // ---------- search by customer ----------

    @Test
    void getOrdersByCustomer_returnsOnlyOrdersOfThatCustomer() {
        service.createOrder(new CreateOrderRequest("CUST-42", "Mouse", 2));

        List<Order> result = service.getOrdersByCustomer("CUST-42");

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(o -> o.getCustomerId().equals("CUST-42")));
    }

    @Test
    void getOrdersByCustomer_customerWithoutOrders_returnsEmptyList() {
        List<Order> result = service.getOrdersByCustomer("CUST-100");

        assertTrue(result.isEmpty());
    }

    @Test
    void getOrdersByCustomer_blankCustomerId_throwsInvalidOrder() {
        assertThrows(InvalidOrderException.class, () -> service.getOrdersByCustomer(""));
        assertThrows(InvalidOrderException.class, () -> service.getOrdersByCustomer(null));
    }

    // ---------- invalid input ----------

    @Test
    void createOrder_nullRequest_throwsInvalidOrder() {
        assertThrows(InvalidOrderException.class, () -> service.createOrder(null));
    }

    @Test
    void createOrder_blankCustomerId_throwsInvalidOrder() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest(" ", "Mouse", 1)));
    }

    @Test
    void createOrder_blankProduct_throwsInvalidOrder() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-1", "", 1)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -3})
    void createOrder_nonPositiveQuantity_throwsInvalidOrder(int quantity) {
        InvalidOrderException ex = assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-1", "Mouse", quantity)));

        assertEquals("quantity must be greater than 0", ex.getMessage());
    }

    @Test
    void createOrder_invalidRequest_doesNotStoreAnything() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-77", "Mouse", 0)));

        assertTrue(service.getOrdersByCustomer("CUST-77").isEmpty());
    }
}