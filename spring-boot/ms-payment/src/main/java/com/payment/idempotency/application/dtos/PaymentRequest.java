package com.payment.idempotency.application.dtos;

import java.math.BigDecimal;

public record PaymentRequest(
    BigDecimal amount
) {}
