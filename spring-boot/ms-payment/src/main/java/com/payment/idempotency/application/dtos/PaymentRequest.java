package com.payment.idempotency.application.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PaymentRequest(
    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be greater than zero")
    BigDecimal amount
) {}
