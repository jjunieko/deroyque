package com.deroyque.activation;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestStore {
    private final RequestRepository repository;

    public RequestStore(RequestRepository repository) { this.repository = repository; }

    @Transactional
    public ActivationRequest create(CreateRequest input) {
        return repository.saveAndFlush(new ActivationRequest(input.externalId(), input.customerId(), input.productCode()));
    }

    @Transactional(readOnly = true)
    public ActivationRequest find(UUID id) {
        return repository.findById(id).orElseThrow(RequestNotFoundException::new);
    }

    @Transactional
    public ActivationRequest start(UUID id) {
        ActivationRequest request = find(id);
        request.startProcessing();
        return request;
    }

    @Transactional
    public ActivationRequest finish(UUID id, boolean successful) {
        ActivationRequest request = find(id);
        if (successful) request.complete();
        else request.fail();
        return request;
    }
}
