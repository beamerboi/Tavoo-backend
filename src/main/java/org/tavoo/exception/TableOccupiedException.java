package org.tavoo.exception;

public class TableOccupiedException extends BusinessRuleException {

    public TableOccupiedException(Long tableId) {
        super("Restaurant table " + tableId + " is already occupied");
    }
}
