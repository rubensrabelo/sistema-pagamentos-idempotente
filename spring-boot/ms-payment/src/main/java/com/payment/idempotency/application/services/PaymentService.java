package com.payment.idempotency.application.services;

import com.payment.idempotency.application.dtos.PaymentRequest;
import com.payment.idempotency.application.dtos.PaymentResponse;
import com.payment.idempotency.application.mappers.PaymentMapper;
import com.payment.idempotency.domain.Payment;
import com.payment.idempotency.domain.PaymentAuditLog;
import com.payment.idempotency.domain.enums.PaymentStatus;
import com.payment.idempotency.exceptions.domain.PaymentConflictException;
import com.payment.idempotency.exceptions.domain.PaymentProcessingException;
import com.payment.idempotency.infra.repositories.PaymentAuditLogRepository;
import com.payment.idempotency.infra.repositories.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAuditLogRepository auditLogRepository;
    private final PaymentMapper paymentMapper;
    private final StringRedisTemplate redisTemplate;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentAuditLogRepository auditLogRepository,
                          PaymentMapper paymentMapper,
                          StringRedisTemplate redisTemplate) {
        this.paymentRepository = paymentRepository;
        this.auditLogRepository = auditLogRepository;
        this.paymentMapper = paymentMapper;
        this.redisTemplate = redisTemplate;
    }

    public PaymentResponse processPayment(String idempotencyKey, PaymentRequest request) {
        Boolean isLockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "PROCESSING", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isLockAcquired)) {
            String currentStatus = redisTemplate.opsForValue().get(idempotencyKey);
            if ("PROCESSING".equals(currentStatus)) {
                throw new PaymentProcessingException("Concurrent execution detected. Transaction in progress.");
            }

            Payment existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new PaymentConflictException("Transaction record not found in persistence layer."));

            saveAuditLog(existingPayment, idempotencyKey, "DUPLICATE_REQUEST_DETECTED", request.toString(), "Payment already processed");
            return paymentMapper.toResponse(existingPayment);
        }

        Payment payment;
        try {
            payment = registerInitialPayment(idempotencyKey, request);
        } catch (DataIntegrityViolationException ex) {
            String finalDbStatus = redisTemplate.opsForValue().get(idempotencyKey);
            if ("PROCESSING".equals(finalDbStatus)) {
                Payment raceConditionPayment = paymentRepository.findByIdempotencyKeyForUpdate(idempotencyKey)
                        .orElseThrow(() -> new PaymentConflictException("Conflict detected but transaction data not found."));

                if (raceConditionPayment.getStatus() == PaymentStatus.PENDING) {
                    throw new PaymentProcessingException("Concurrent execution detected. Transaction in progress.");
                }

                saveAuditLog(raceConditionPayment, idempotencyKey, "RACE_CONDITION_BLOCKED", request.toString(), "Concurrent request blocked");
                return paymentMapper.toResponse(raceConditionPayment);
            }
            
            Payment finishedPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new PaymentConflictException("Payment record lost."));
            return paymentMapper.toResponse(finishedPayment);
        }

        PaymentStatus finalStatus = executeExternalGatewayCall(request);

        return finalizePayment(payment.getId(), finalStatus, request.toString(), idempotencyKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment registerInitialPayment(String idempotencyKey, PaymentRequest request) {
        Payment newPayment = paymentMapper.toEntity(request, idempotencyKey);
        return paymentRepository.saveAndFlush(newPayment);
    }

    private PaymentStatus executeExternalGatewayCall(PaymentRequest request) {
        try {
            Thread.sleep(2000);
            return PaymentStatus.SUCCESS;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PaymentStatus.FAILED;
        }
    }

    @Transactional
    public PaymentResponse finalizePayment(UUID id, PaymentStatus status, String requestPayload, String idempotencyKey) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentConflictException("Payment record lost during lifecycle"));

        payment.setStatus(status);
        payment = paymentRepository.save(payment);

        redisTemplate.opsForValue().set(idempotencyKey, status.name(), Duration.ofHours(24));

        saveAuditLog(payment, payment.getIdempotencyKey(), "PAYMENT_FINALIZED_" + status.name(), requestPayload, "Gateway call finished");
        return paymentMapper.toResponse(payment);
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
