package com.payment.idempotency.application.dtos;

import java.time.LocalDateTime;

public record PaymentAuditLogResponse(
    Long id,
    Long paymentId,
    String idempotencyKey,
    String statusTransition,
    String requestPayload,
    String responsePayload,
    LocalDateTime attemptedAt
) {}
