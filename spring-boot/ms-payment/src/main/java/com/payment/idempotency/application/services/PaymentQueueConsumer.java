package com.payment.idempotency.application.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.idempotency.application.dtos.PaymentQueueMessage;
import com.payment.idempotency.application.dtos.PaymentRequest;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentQueueConsumer implements MessageListener {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentQueueConsumer(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            PaymentQueueMessage queueMessage = objectMapper.readValue(message.getBody(), PaymentQueueMessage.class);
            PaymentRequest request = new PaymentRequest(queueMessage.amount());
            paymentService.executeAsynchronousPersistence(queueMessage.idempotencyKey(), request);
        } catch (Exception ignored) {}
    }
}
