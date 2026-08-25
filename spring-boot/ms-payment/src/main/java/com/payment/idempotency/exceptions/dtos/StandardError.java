package com.payment.idempotency.exceptions.dtos;

import java.time.LocalDateTime;

public record StandardError(
    LocalDateTime timestamp,
    Integer status,
    String error,
    String message,
    String path
) {}
