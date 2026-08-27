package com.payment.idempotency.application.mappers;

import com.payment.idempotency.application.dtos.PaymentRequest;
import com.payment.idempotency.application.dtos.PaymentResponse;
import com.payment.idempotency.domain.Payment;
import com.payment.idempotency.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMapperUnitTest {

    private final PaymentMapper paymentMapper = new PaymentMapper();

    @Test
    void unitTest_ShouldMapPaymentRequestToPaymentEntityWithPendingStatus() {
        String key = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest(new BigDecimal("250.00"));

        Payment entity = paymentMapper.toEntity(request, key);

        assertNotNull(entity);
        assertEquals(key, entity.getIdempotencyKey());
        assertEquals(new BigDecimal("250.00"), entity.getAmount());
        assertEquals(PaymentStatus.PENDING, entity.getStatus());
    }

    @Test
    void unitTest_ShouldMapPaymentEntityToPaymentResponseRecord() {
        String key = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        UUID paymentId = UUID.randomUUID();
        
        Payment entity = Payment.builder()
                .id(paymentId)
                .idempotencyKey(key)
                .amount(new BigDecimal("500.00"))
                .status(PaymentStatus.SUCCESS)
                .createdAt(now)
                .build();

        PaymentResponse response = paymentMapper.toResponse(entity);

        assertNotNull(response);
        assertEquals(paymentId, response.id());
        assertEquals(key, response.idempotencyKey());
        assertEquals(new BigDecimal("500.00"), response.amount());
        assertEquals("SUCCESS", response.status());
        assertEquals(now, response.createdAt());
    }

    @Test
    void unitTest_ShouldReturnNull_WhenPaymentMapperReceivesNullInputs() {
        assertNull(paymentMapper.toEntity(null, "key"));
        assertNull(paymentMapper.toResponse(null));
    }
}
