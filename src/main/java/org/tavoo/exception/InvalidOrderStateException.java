package org.tavoo.exception;

public class InvalidOrderStateException extends BusinessRuleException {

    public InvalidOrderStateException(Long orderId, String operation) {
        super("Order " + orderId + " must be OPEN to " + operation);
    }
}
