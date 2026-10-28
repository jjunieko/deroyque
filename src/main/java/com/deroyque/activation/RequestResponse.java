package com.deroyque.activation;

import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record RequestResponse(
        @Schema(example = "0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90") UUID id,
        @Schema(example = "DEMO-001") String externalId,
        @Schema(example = "CUST-10001") String customerId,
        @Schema(example = "INTERNET-500") String productCode,
        @Schema(description = "PENDING → PROCESSING → COMPLETED or FAILED. FAILED means activation was not confirmed; it may have occurred externally.", example = "COMPLETED") RequestStatus status,
        @Schema(description = "Creation time in UTC.", example = "2026-10-28T15:00:00Z") Instant createdAt,
        @Schema(description = "Last status change in UTC.", example = "2026-10-28T15:00:01Z") Instant updatedAt) {
    static RequestResponse from(ActivationRequest request) {
        return new RequestResponse(request.getId(), request.getExternalId(), request.getCustomerId(),
                request.getProductCode(), request.getStatus(), request.getCreatedAt(), request.getUpdatedAt());
    }
}
