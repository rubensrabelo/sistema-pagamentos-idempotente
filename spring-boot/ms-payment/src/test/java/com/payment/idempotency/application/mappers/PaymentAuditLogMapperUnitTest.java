package com.payment.idempotency.application.mappers;

import com.payment.idempotency.application.dtos.PaymentAuditLogResponse;
import com.payment.idempotency.domain.Payment;
import com.payment.idempotency.domain.PaymentAuditLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentAuditLogMapperUnitTest {

    private final PaymentAuditLogMapper auditLogMapper = new PaymentAuditLogMapper();

    @Test
    void unitTest_ShouldMapPaymentAuditLogToResponseRecord() {
        String key = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        UUID auditLogId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        
        Payment payment = Payment.builder().id(paymentId).build();
        
        PaymentAuditLog auditLog = PaymentAuditLog.builder()
                .id(auditLogId)
                .payment(payment)
                .idempotencyKey(key)
                .statusTransition("PAYMENT_SUCCESS")
                .requestPayload("req")
                .responsePayload("res")
                .attemptedAt(now)
                .build();

        PaymentAuditLogResponse response = auditLogMapper.toResponse(auditLog);

        assertNotNull(response);
        assertEquals(auditLogId, response.id());
        assertEquals(paymentId, response.paymentId());
        assertEquals(key, response.idempotencyKey());
        assertEquals("PAYMENT_SUCCESS", response.statusTransition());
        assertEquals("req", response.requestPayload());
        assertEquals("res", response.responsePayload());
        assertEquals(now, response.attemptedAt());
    }

    @Test
    void unitTest_ShouldReturnNull_WhenAuditLogMapperReceivesNullInput() {
        assertNull(auditLogMapper.toResponse(null));
    }
}
