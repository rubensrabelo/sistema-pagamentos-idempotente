package com.payment.idempotency.application.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentAuditLogResponse(
    UUID id,
    UUID paymentId,
    String idempotencyKey,
    String statusTransition,
    String requestPayload,
    String responsePayload,
    LocalDateTime attemptedAt
) {}
