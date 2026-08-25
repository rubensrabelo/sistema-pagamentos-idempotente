package com.payment.idempotency.exceptions.dtos;

import java.time.LocalDateTime;
import java.util.List;

public record ValidationError(
    LocalDateTime timestamp,
    Integer status,
    String error,
    String message,
    String path,
    List<FieldErrorDTO> errors
) {}