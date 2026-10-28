package com.deroyque.activation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "All fields are case-sensitive and accept 1–100 ASCII letters, digits, underscores or hyphens. Whitespace is rejected.")
public record CreateRequest(
        @Schema(description = "Unique identifier supplied by the calling system. Reusing it returns 409.", example = "DEMO-001")
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String externalId,
        @Schema(description = "Customer whose product should be activated.", example = "CUST-10001")
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String customerId,
        @Schema(description = "Product to activate.", example = "INTERNET-500")
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String productCode) {}
