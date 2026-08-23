CREATE TABLE payment_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT,
    idempotency_key VARCHAR(255) NOT NULL,
    status_transition VARCHAR(50) NOT NULL,
    request_payload TEXT,
    response_payload TEXT,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_payment_id FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE SET NULL
);
