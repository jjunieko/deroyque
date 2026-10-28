package com.deroyque.activation;

import org.springframework.stereotype.Service;

@Service
public class RequestService {
    private final RequestStore store;
    private final ProviderClient provider;

    public RequestService(RequestStore store, ProviderClient provider) {
        this.store = store;
        this.provider = provider;
    }

    public RequestResponse create(CreateRequest input) {
        ActivationRequest created = store.create(input);
        ActivationRequest processing = store.start(created.getId());
        boolean successful = provider.activate(processing);
        return RequestResponse.from(store.finish(created.getId(), successful));
    }
}
