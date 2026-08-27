package com.payment.idempotency.application.dtos;

import java.io.Serializable;
import java.math.BigDecimal;

public record PaymentQueueMessage(
    String idempotencyKey,
    BigDecimal amount
) implements Serializable {}
