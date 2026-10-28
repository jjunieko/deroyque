package com.deroyque.activation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String externalId,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String customerId,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String productCode) {}
