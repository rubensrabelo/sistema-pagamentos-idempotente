package com.payment.idempotency.application.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.payment.idempotency.application.dtos.PaymentRequest;
import com.payment.idempotency.application.dtos.PaymentResponse;
import com.payment.idempotency.application.mappers.PaymentMapper;
import com.payment.idempotency.domain.Payment;
import com.payment.idempotency.domain.enums.PaymentStatus;
import com.payment.idempotency.exceptions.domain.PaymentProcessingException;
import com.payment.idempotency.infra.repositories.PaymentAuditLogRepository;
import com.payment.idempotency.infra.repositories.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceUnitTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAuditLogRepository auditLogRepository;

    @Mock
    private PaymentMapper paymentMapper;

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
    void unitTest_ShouldProcessNewPaymentSuccessfully_WhenKeyDoesNotExist() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentMapper.toEntity(paymentRequest, idempotencyKey)).thenReturn(mockPayment);
        when(paymentRepository.saveAndFlush(mockPayment)).thenReturn(mockPayment);
        when(paymentRepository.findById(mockPaymentId)).thenReturn(Optional.of(mockPayment));
        when(paymentRepository.save(mockPayment)).thenReturn(mockPayment);
        when(paymentMapper.toResponse(mockPayment)).thenReturn(mockResponse);

        PaymentResponse response = paymentService.processPayment(idempotencyKey, paymentRequest);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        verify(paymentRepository).saveAndFlush(any(Payment.class));
        verify(auditLogRepository).save(any());
    }

    @Test
    void unitTest_ShouldReturnExistingPayment_WhenPaymentIsAlreadyFinalized() {
        mockPayment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(mockPayment));
        when(paymentMapper.toResponse(mockPayment)).thenReturn(mockResponse);

        PaymentResponse response = paymentService.processPayment(idempotencyKey, paymentRequest);

        assertNotNull(response);
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(auditLogRepository).save(any());
    }

    @Test
    void unitTest_ShouldThrowPaymentProcessingException_WhenPaymentIsAlreadyInPendingStatus() {
        mockPayment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(mockPayment));

        assertThrows(PaymentProcessingException.class, () -> 
            paymentService.processPayment(idempotencyKey, paymentRequest)
        );

        verify(paymentRepository, never()).saveAndFlush(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void unitTest_ShouldHandleAndBypassRaceCondition_WhenDatabaseThrowsDataIntegrityViolationOnInsert() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentMapper.toEntity(paymentRequest, idempotencyKey)).thenReturn(mockPayment);
        when(paymentRepository.saveAndFlush(mockPayment)).thenThrow(DataIntegrityViolationException.class);
        
        mockPayment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdempotencyKeyForUpdate(idempotencyKey)).thenReturn(Optional.of(mockPayment));
        when(paymentMapper.toResponse(mockPayment)).thenReturn(mockResponse);

        PaymentResponse response = paymentService.processPayment(idempotencyKey, paymentRequest);

        assertNotNull(response);
        verify(auditLogRepository).save(any());
    }

    @Test
    void unitTest_ShouldThrowProcessingExceptionOnRaceCondition_WhenDatabaseRowIsStillInPendingStatus() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentMapper.toEntity(paymentRequest, idempotencyKey)).thenReturn(mockPayment);
        when(paymentRepository.saveAndFlush(mockPayment)).thenThrow(DataIntegrityViolationException.class);
        
        mockPayment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByIdempotencyKeyForUpdate(idempotencyKey)).thenReturn(Optional.of(mockPayment));

        assertThrows(PaymentProcessingException.class, () -> 
            paymentService.processPayment(idempotencyKey, paymentRequest)
        );
    }
}
