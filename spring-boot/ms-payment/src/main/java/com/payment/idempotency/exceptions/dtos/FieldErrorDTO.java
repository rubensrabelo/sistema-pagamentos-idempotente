package com.payment.idempotency.exceptions.dtos;

public record FieldErrorDTO(
    String field,
    String message
) {}
