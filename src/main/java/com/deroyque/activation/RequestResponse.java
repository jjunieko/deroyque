package com.deroyque.activation;

import java.time.Instant;
import java.util.UUID;

public record RequestResponse(UUID id, String externalId, String customerId, String productCode,
                              RequestStatus status, Instant createdAt, Instant updatedAt) {
    static RequestResponse from(ActivationRequest request) {
        return new RequestResponse(request.getId(), request.getExternalId(), request.getCustomerId(),
                request.getProductCode(), request.getStatus(), request.getCreatedAt(), request.getUpdatedAt());
    }
}
