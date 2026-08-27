package com.payment.idempotency.application.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    String idempotencyKey,
    BigDecimal amount,
    String status,
    LocalDateTime createdAt
) {}
