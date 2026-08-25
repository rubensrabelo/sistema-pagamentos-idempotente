package com.payment.idempotency.application.services;

import com.payment.idempotency.application.dtos.PaymentRequest;
import com.payment.idempotency.application.dtos.PaymentResponse;
import com.payment.idempotency.application.mappers.PaymentMapper;
import com.payment.idempotency.domain.Payment;
import com.payment.idempotency.domain.PaymentAuditLog;
import com.payment.idempotency.domain.enums.PaymentStatus;
import com.payment.idempotency.infra.repositories.PaymentAuditLogRepository;
import com.payment.idempotency.infra.repositories.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAuditLogRepository auditLogRepository;
    private final PaymentMapper paymentMapper;

    public PaymentService(PaymentRepository paymentRepository, 
                          PaymentAuditLogRepository auditLogRepository, 
                          PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.auditLogRepository = auditLogRepository;
        this.paymentMapper = paymentMapper;
    }

    @Transactional
    public PaymentResponse processPayment(String idempotencyKey, PaymentRequest request) {
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            saveAuditLog(payment, idempotencyKey, "DUPLICATE_REQUEST_DETECTED", request.toString(), "Payment already processed");
            return paymentMapper.toResponse(payment);
        }

        Payment newPayment = paymentMapper.toEntity(request, idempotencyKey);

        try {
            newPayment = paymentRepository.save(newPayment);
            newPayment.setStatus(PaymentStatus.SUCCESS);
            newPayment = paymentRepository.save(newPayment);

            saveAuditLog(newPayment, idempotencyKey, "PAYMENT_SUCCESS", request.toString(), "Payment processed successfully");

            return paymentMapper.toResponse(newPayment);

        } catch (DataIntegrityViolationException ex) {
            Payment raceConditionPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Conflict detected but transaction data not found."));
            
            saveAuditLog(raceConditionPayment, idempotencyKey, "RACE_CONDITION_BLOCKED", request.toString(), "Concurrent request blocked");
            return paymentMapper.toResponse(raceConditionPayment);
        }
    }

    private void saveAuditLog(Payment payment, String key, String transition, String requestBody, String responseBody) {
        PaymentAuditLog log = PaymentAuditLog.builder()
                .payment(payment)
                .idempotencyKey(key)
                .statusTransition(transition)
                .requestPayload(requestBody)
                .responsePayload(responseBody)
                .build();
        auditLogRepository.save(log);
    }
}
