package com.payment.idempotency.infra.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payment.idempotency.domain.PaymentAuditLog;

public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, Long> {
}
