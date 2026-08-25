package com.payment.idempotency.application.mappers;

import com.payment.idempotency.application.dtos.PaymentAuditLogResponse;
import com.payment.idempotency.domain.PaymentAuditLog;
import org.springframework.stereotype.Component;

@Component
public class PaymentAuditLogMapper {

    public PaymentAuditLogResponse toResponse(PaymentAuditLog auditLog) {
        if (auditLog == null) {
            return null;
        }
        return new PaymentAuditLogResponse(
                auditLog.getId(),
                auditLog.getPayment() != null ? auditLog.getPayment().getId() : null,
                auditLog.getIdempotencyKey(),
                auditLog.getStatusTransition(),
                auditLog.getRequestPayload(),
                auditLog.getResponsePayload(),
                auditLog.getAttemptedAt()
        );
    }
}
