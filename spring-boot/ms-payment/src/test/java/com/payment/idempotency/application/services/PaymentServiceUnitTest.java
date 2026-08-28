package com.payment.idempotency.application.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.ChannelTopic;

import com.payment.idempotency.application.dtos.PaymentRequest;
import com.payment.idempotency.application.dtos.PaymentResponse;
import com.payment.idempotency.application.mappers.PaymentMapper;
import com.payment.idempotency.domain.Payment;
import com.payment.idempotency.domain.enums.PaymentStatus;
import com.payment.idempotency.exceptions.domain.PaymentProcessingException;
import com.payment.idempotency.infra.repositories.PaymentAuditLogRepository;
import com.payment.idempotency.infra.repositories.PaymentRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceUnitTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAuditLogRepository auditLogRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ChannelTopic paymentTopic;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentService paymentService;

    private String idempotencyKey;
    private PaymentRequest paymentRequest;
    private Payment mockPayment;
    private PaymentResponse mockResponse;
    private UUID mockPaymentId;

    @BeforeEach
    void setUp() {
        idempotencyKey = UUID.randomUUID().toString();
        mockPaymentId = UUID.randomUUID();
        paymentRequest = new PaymentRequest(new BigDecimal("100.00"));
        
        mockPayment = Payment.builder()
                .id(mockPaymentId)
                .idempotencyKey(idempotencyKey)
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        mockResponse = new PaymentResponse(
                mockPaymentId,
                idempotencyKey,
                new BigDecimal("100.00"),
                "SUCCESS",
                LocalDateTime.now()
        );
    }

    @Test
    void unitTest_ShouldProcessNewPaymentSuccessfully_WhenKeyDoesNotExist() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(idempotencyKey), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentTopic.getTopic()).thenReturn("payment-processing-queue");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentResponse response = paymentService.processPayment(idempotencyKey, paymentRequest);

        assertNotNull(response);
        assertEquals("PROCESSING", response.status());
        verify(redisTemplate).convertAndSend(eq("payment-processing-queue"), anyString());
    }

    @Test
    void unitTest_ShouldReturnExistingPayment_WhenPaymentIsAlreadyFinalized() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(idempotencyKey), eq("PROCESSING"), any(Duration.class))).thenReturn(false);
        when(valueOperations.get(idempotencyKey)).thenReturn("SUCCESS");
        
        mockPayment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(mockPayment));
        when(paymentMapper.toResponse(mockPayment)).thenReturn(mockResponse);

        PaymentResponse response = paymentService.processPayment(idempotencyKey, paymentRequest);

        assertNotNull(response);
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
        verify(auditLogRepository).save(any());
    }

    @Test
    void unitTest_ShouldThrowPaymentProcessingException_WhenPaymentIsAlreadyInPendingStatus() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(idempotencyKey), eq("PROCESSING"), any(Duration.class))).thenReturn(false);
        when(valueOperations.get(idempotencyKey)).thenReturn("PROCESSING");

        assertThrows(PaymentProcessingException.class, () -> 
            paymentService.processPayment(idempotencyKey, paymentRequest)
        );

        verify(paymentRepository, never()).findByIdempotencyKey(anyString());
    }

    @Test
    void unitTest_ShouldHandleAndBypassRaceCondition_WhenDatabaseThrowsDataIntegrityViolationOnInsert() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(idempotencyKey), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentTopic.getTopic()).thenReturn("payment-processing-queue");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenThrow(new RedisSystemException("Connection lost", new RuntimeException()));
        when(valueOperations.get(idempotencyKey)).thenReturn("PROCESSING");
        
        mockPayment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdempotencyKeyForUpdate(idempotencyKey)).thenReturn(Optional.of(mockPayment));
        when(paymentMapper.toResponse(mockPayment)).thenReturn(mockResponse);

        assertThrows(PaymentProcessingException.class, () -> 
            paymentService.processPayment(idempotencyKey, paymentRequest)
        );
    }

    @Test
    void unitTest_ShouldThrowProcessingExceptionOnRaceCondition_WhenDatabaseRowIsStillInPendingStatus() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(idempotencyKey), eq("PROCESSING"), any(Duration.class))).thenReturn(false);
        when(valueOperations.get(idempotencyKey)).thenReturn("PROCESSING");

        assertThrows(PaymentProcessingException.class, () -> 
            paymentService.processPayment(idempotencyKey, paymentRequest)
        );
    }
}
