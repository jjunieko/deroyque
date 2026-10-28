package com.deroyque.activation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activation_requests")
public class ActivationRequest {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 100)
    private String externalId;
    @Column(nullable = false, length = 100)
    private String customerId;
    @Column(nullable = false, length = 100)
    private String productCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected ActivationRequest() {}

    public ActivationRequest(String externalId, String customerId, String productCode) {
        this.id = UUID.randomUUID();
        this.externalId = externalId;
        this.customerId = customerId;
        this.productCode = productCode;
        this.status = RequestStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void startProcessing() { transition(RequestStatus.PENDING, RequestStatus.PROCESSING); }
    public void complete() { transition(RequestStatus.PROCESSING, RequestStatus.COMPLETED); }
    public void fail() { transition(RequestStatus.PROCESSING, RequestStatus.FAILED); }

    private void transition(RequestStatus expected, RequestStatus next) {
        if (status != expected) {
            throw new IllegalStateException("Cannot transition from " + status + " to " + next);
        }
        status = next;
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getCustomerId() { return customerId; }
    public String getProductCode() { return productCode; }
    public RequestStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
