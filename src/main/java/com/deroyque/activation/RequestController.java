package com.deroyque.activation;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/requests")
public class RequestController {
    private final RequestService service;
    private final RequestStore store;

    public RequestController(RequestService service, RequestStore store) {
        this.service = service;
        this.store = store;
    }

    @PostMapping
    public ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequest input) {
        RequestResponse response = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/requests/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public RequestResponse find(@PathVariable UUID id) { return RequestResponse.from(store.find(id)); }
}
