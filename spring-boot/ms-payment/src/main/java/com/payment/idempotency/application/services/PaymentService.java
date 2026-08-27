package com.payment.idempotency.application.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.idempotency.application.dtos.PaymentQueueMessage;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAuditLogRepository auditLogRepository;
    private final PaymentMapper paymentMapper;
    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic paymentTopic;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentAuditLogRepository auditLogRepository,
                          PaymentMapper paymentMapper,
                          StringRedisTemplate redisTemplate,
                          ChannelTopic paymentTopic,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.auditLogRepository = auditLogRepository;
        this.paymentMapper = paymentMapper;
        this.redisTemplate = redisTemplate;
        this.paymentTopic = paymentTopic;
        this.objectMapper = objectMapper;
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

        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            redisTemplate.opsForValue().set(idempotencyKey, payment.getStatus().name(), Duration.ofHours(24));
            return paymentMapper.toResponse(payment);
        }

        try {
            PaymentQueueMessage queueMessage = new PaymentQueueMessage(idempotencyKey, request.amount());
            String jsonMessage = objectMapper.writeValueAsString(queueMessage);
            redisTemplate.convertAndSend(paymentTopic.getTopic(), jsonMessage);
        } catch (Exception ex) {
            redisTemplate.delete(idempotencyKey);
            throw new PaymentProcessingException("Failed to queue transaction.");
        }

        return new PaymentResponse(UUID.randomUUID(), idempotencyKey, request.amount(), "PROCESSING", LocalDateTime.now());
    }

    @Transactional
    public void executeAsynchronousPersistence(String idempotencyKey, PaymentRequest request) {
        Optional<Payment> dbCheck = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (dbCheck.isPresent()) return;

        Payment payment = registerInitialPayment(idempotencyKey, request);
        PaymentStatus finalStatus = executeExternalGatewayCall(request);
        finalizePayment(payment.getId(), finalStatus, request.toString(), idempotencyKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment registerInitialPayment(String idempotencyKey, PaymentRequest request) {
        Payment newPayment = paymentMapper.toEntity(request, idempotencyKey);
        return paymentRepository.saveAndFlush(newPayment);
    }

    private PaymentStatus executeExternalGatewayCall(PaymentRequest request) {
        try {
            Thread.sleep(100); 
            return PaymentStatus.SUCCESS;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PaymentStatus.FAILED;
        }
    }

    @Transactional
    public void finalizePayment(UUID id, PaymentStatus status, String requestPayload, String idempotencyKey) {
        Payment payment = paymentRepository.findById(id).orElse(null);
        if (payment == null) return;

        payment.setStatus(status);
        paymentRepository.save(payment);

        redisTemplate.opsForValue().set(idempotencyKey, status.name(), Duration.ofHours(24));
        saveAuditLog(payment, payment.getIdempotencyKey(), "PAYMENT_FINALIZED_" + status.name(), requestPayload, "Gateway call finished");
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
