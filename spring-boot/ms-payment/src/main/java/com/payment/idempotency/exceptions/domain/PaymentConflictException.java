package com.payment.idempotency.exceptions.domain;

public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException(String message) {
        super(message);
    }
}
