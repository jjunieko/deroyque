CREATE TABLE activation_requests (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_activation_external_id UNIQUE (external_id)
);
