package com.payment.idempotency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IdempotentPaymentSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdempotentPaymentSystemApplication.class, args);
	}

}
