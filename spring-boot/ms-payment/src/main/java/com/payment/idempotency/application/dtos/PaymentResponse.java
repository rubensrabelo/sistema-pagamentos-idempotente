package com.payment.idempotency.application.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
    Long id,
    String idempotencyKey,
    BigDecimal amount,
    String status,
    LocalDateTime createdAt
) {}
