package com.payment.idempotency.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payment.idempotency.application.dtos.PaymentRequest;
import com.payment.idempotency.config.BaseIntegrationTest;
import com.payment.idempotency.domain.Payment;
import com.payment.idempotency.domain.enums.PaymentStatus;
import com.payment.idempotency.infra.repositories.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
    }

    @Test
    void integrationTest_ShouldProcessPaymentSuccessfully_OnFirstAttempt() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest(new BigDecimal("150.00"));

        mockMvc.perform(post("/payments")
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void integrationTest_ShouldReturnBadRequest_WhenIdempotencyKeyHeaderIsMissing() throws Exception {
        PaymentRequest request = new PaymentRequest(new BigDecimal("150.00"));

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("The 'X-Idempotency-Key' header is mandatory for payment transactions."));
    }

    @Test
    void integrationTest_ShouldReturnBadRequest_WhenPayloadIsInvalid() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest(new BigDecimal("-50.00"));

        mockMvc.perform(post("/payments")
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    void integrationTest_ShouldReturnSameResponse_WhenRequestIsDuplicatedAndFinalized() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest(new BigDecimal("200.00"));

        mockMvc.perform(post("/payments")
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/payments")
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void integrationTest_ShouldReturnTooEarlyError_WhenConcurrentRequestIsReceivedWhileProcessing() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest(new BigDecimal("300.00"));

        Payment pendingPayment = Payment.builder()
                .idempotencyKey(idempotencyKey)
                .amount(request.amount())
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.saveAndFlush(pendingPayment);

        mockMvc.perform(post("/payments")
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooEarly())
                .andExpect(jsonPath("$.error").value("Transaction In Progress"));
    }

    @Test
    void integrationTest_ShouldHandleTrueMultiThreadedRaceCondition() throws InterruptedException {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest(new BigDecimal("500.00"));
        
        int threadsCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch latch = new CountDownLatch(1);
        
        AtomicReference<Integer> firstRequestStatus = new AtomicReference<>();
        AtomicReference<Integer> secondRequestStatus = new AtomicReference<>();

        executor.execute(() -> {
            try {
                latch.await();
                int status = mockMvc.perform(post("/payments")
                                .header("X-Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
                firstRequestStatus.set(status);
            } catch (Exception ignored) {}
        });

        executor.execute(() -> {
            try {
                latch.await();
                int status = mockMvc.perform(post("/payments")
                                .header("X-Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
                secondRequestStatus.set(status);
            } catch (Exception ignored) {}
        });

        latch.countDown();
        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(50);
        }

        int s1 = firstRequestStatus.get();
        int s2 = secondRequestStatus.get();

        boolean oneCreatedAndOneBlocked = (s1 == 201 && s2 == 425) || (s1 == 425 && s2 == 201);
        assertEquals(true, oneCreatedAndOneBlocked);
    }
}
