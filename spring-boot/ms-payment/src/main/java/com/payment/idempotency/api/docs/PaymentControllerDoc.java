package com.payment.idempotency.api.docs;

import com.payment.idempotency.application.dtos.PaymentRequest;
import com.payment.idempotency.application.dtos.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Payments", description = "Endpoints for processing idempotent transactions")
public interface PaymentControllerDoc {

    @Operation(
        summary = "Process a payment request",
        description = "Processes a financial transaction safely using an idempotency key to prevent double charging.",
        parameters = {
            @Parameter(
                name = "X-Idempotency-Key",
                in = ParameterIn.HEADER,
                required = true,
                description = "Unique identifier key for the transaction",
                schema = @Schema(type = "string")
            )
        },
        responses = {
            @ApiResponse(
                responseCode = "201", 
                description = "Payment processed successfully",
                content = @Content(schema = @Schema(implementation = PaymentResponse.class))
            ),
            @ApiResponse(
                responseCode = "400", 
                description = "Missing mandatory headers or invalid request payload",
                content = @Content(schema = @Schema(implementation = String.class))
            )
        }
    )
    ResponseEntity<?> createPayment(
            String idempotencyKey,
            @RequestBody(description = "Payment transaction details", required = true) PaymentRequest request
    );
}
