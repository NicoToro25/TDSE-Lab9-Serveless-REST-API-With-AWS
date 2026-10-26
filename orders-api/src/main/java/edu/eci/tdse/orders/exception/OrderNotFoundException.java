package main.java.edu.eci.tdse.orders.exception;

public class OrderNotFoundException extends RuntimeException {

    private final String orderId;

    public OrderNotFoundException(String orderId) {
        super("Order " + orderId + " does not exist");
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}