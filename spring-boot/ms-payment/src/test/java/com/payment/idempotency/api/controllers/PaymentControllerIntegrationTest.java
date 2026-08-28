package com.payment.idempotency.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payment.idempotency.application.dtos.PaymentRequest;
import com.payment.idempotency.config.BaseIntegrationTest;
import com.payment.idempotency.infra.repositories.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void cleanDatabaseAndCache() {
        paymentRepository.deleteAll();
        Optional.ofNullable(redisTemplate.getConnectionFactory())
                .ifPresent(factory -> redisTemplate.execute((RedisCallback<Object>) connection -> {
                    connection.serverCommands().flushAll();
                    return null;
                }));
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
                .andExpect(jsonPath("$.status").value("PROCESSING"));
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

        redisTemplate.delete(idempotencyKey);

        mockMvc.perform(post("/payments")
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void integrationTest_ShouldReturnTooEarlyError_WhenConcurrentRequestIsReceivedWhileProcessing() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest(new BigDecimal("300.00"));

        redisTemplate.opsForValue().set(idempotencyKey, "PROCESSING");

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
        
        AtomicReference<Integer> firstRequestStatus = new AtomicReference<>(0);
        AtomicReference<Integer> secondRequestStatus = new AtomicReference<>(0);

        executor.execute(() -> {
            try {
                latch.await();
                int status = mockMvc.perform(post("/payments")
                                .header("X-Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
                firstRequestStatus.set(status);
            } catch (Exception e) {
                firstRequestStatus.set(500);
            }
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
            } catch (Exception e) {
                secondRequestStatus.set(500);
            }
        });

        latch.countDown();
        executor.shutdown();
        
        boolean finishedCleanly = executor.awaitTermination(6, TimeUnit.SECONDS);
        assertTrue(finishedCleanly, "Timeout ao encerrar as requisições paralelas.");

        await()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> "SUCCESS".equals(redisTemplate.opsForValue().get(idempotencyKey)));

        int s1 = firstRequestStatus.get();
        int s2 = secondRequestStatus.get();

        boolean oneCreatedAndOneBlocked = (s1 == 201 && s2 == 425) || (s1 == 425 && s2 == 201);
        
        assertTrue(oneCreatedAndOneBlocked, 
                "Falha na concorrência da idempotência. Resultados: S1=" + s1 + ", S2=" + s2);
    }
}
