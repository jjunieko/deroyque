package com.deroyque.activation;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/requests")
@Tag(name = "Activation requests", description = "Create a request, then use its returned UUID to query the persisted result.")
public class RequestController {
    private final RequestService service;
    private final RequestStore store;

    public RequestController(RequestService service, RequestStore store) {
        this.service = service;
        this.store = store;
    }

    @PostMapping
    @Operation(summary = "Create and process an activation request",
            description = "Waits for the provider before returning. A recorded request returns 201 with COMPLETED or FAILED. Duplicate external IDs return 409, including identical retries.")
    @ApiResponse(responseCode = "201", description = "Request recorded. Inspect status to determine the activation outcome.",
            headers = @Header(name = "Location", description = "Relative URL of the created request.", schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RequestResponse.class)))
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequest input) {
        RequestResponse response = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/requests/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find an activation request by UUID", description = "Use the id returned by POST, not the externalId.")
    @ApiResponse(responseCode = "200", description = "The persisted activation request.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RequestResponse.class)))
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public RequestResponse find(
            @Parameter(description = "UUID returned in the POST response.", example = "0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90")
            @PathVariable UUID id) { return RequestResponse.from(store.find(id)); }
}
