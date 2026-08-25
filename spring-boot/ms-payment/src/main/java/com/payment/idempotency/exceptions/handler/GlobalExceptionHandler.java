package com.payment.idempotency.exceptions.handler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.payment.idempotency.exceptions.domain.PaymentConflictException;
import com.payment.idempotency.exceptions.domain.PaymentProcessingException;
import com.payment.idempotency.exceptions.dtos.FieldErrorDTO;
import com.payment.idempotency.exceptions.dtos.StandardError;
import com.payment.idempotency.exceptions.dtos.ValidationError;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(PaymentConflictException.class)
        public ResponseEntity<StandardError> handlePaymentConflict(PaymentConflictException ex,
                        HttpServletRequest request) {
                HttpStatus status = HttpStatus.CONFLICT;
                StandardError error = new StandardError(
                                LocalDateTime.now(),
                                status.value(),
                                "Payment Conflict Error",
                                ex.getMessage(),
                                request.getRequestURI());
                return ResponseEntity.status(status).body(error);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ValidationError> handleValidation(MethodArgumentNotValidException ex,
                        HttpServletRequest request) {
                HttpStatus status = HttpStatus.BAD_REQUEST;

                List<FieldErrorDTO> fieldErrors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(fe -> new FieldErrorDTO(fe.getField(), fe.getDefaultMessage()))
                                .toList();

                ValidationError error = new ValidationError(
                                LocalDateTime.now(),
                                status.value(),
                                "Validation Error",
                                "One or more fields are invalid",
                                request.getRequestURI(),
                                fieldErrors);
                return ResponseEntity.status(status).body(error);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<StandardError> handleGenericException(Exception ex, HttpServletRequest request) {
                HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
                StandardError error = new StandardError(
                                LocalDateTime.now(),
                                status.value(),
                                "Internal Server Error",
                                ex.getMessage(),
                                request.getRequestURI());
                return ResponseEntity.status(status).body(error);
        }

        @ExceptionHandler(PaymentProcessingException.class)
        public ResponseEntity<StandardError> handlePaymentProcessing(PaymentProcessingException ex,
                        HttpServletRequest request) {
                HttpStatus status = HttpStatus.TOO_EARLY;
                StandardError error = new StandardError(
                                LocalDateTime.now(),
                                status.value(),
                                "Transaction In Progress",
                                ex.getMessage(),
                                request.getRequestURI());
                return ResponseEntity.status(status).body(error);
        }
}
