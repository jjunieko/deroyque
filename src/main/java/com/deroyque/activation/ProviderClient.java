package com.deroyque.activation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Duration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ProviderClient {
    private static final Logger log = LoggerFactory.getLogger(ProviderClient.class);
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final Duration requestTimeout;

    public ProviderClient(ObjectMapper mapper,
                          @Value("${provider.base-url}") String baseUrl,
                          @Value("${provider.connect-timeout:1s}") Duration connectTimeout,
                          @Value("${provider.request-timeout:2s}") Duration requestTimeout) {
        this.mapper = mapper;
        this.endpoint = URI.create(baseUrl + "/provider/v1/activate");
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    public boolean activate(ActivationRequest request) {
        String body;
        try {
            body = mapper.writeValueAsString(new CreateRequest(request.getExternalId(),
                    request.getCustomerId(), request.getProductCode()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize provider request", exception);
        }
        HttpRequest outgoing = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", request.getExternalId())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            int status = client.send(outgoing, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status < 200 || status >= 300) {
                log.warn("Provider rejected activation: requestId={}, httpStatus={}", request.getId(), status);
            }
            return status >= 200 && status < 300;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Provider call interrupted: requestId={}", request.getId());
            return false;
        } catch (IOException exception) {
            log.warn("Provider call failed: requestId={}, failure={}", request.getId(), exception.getClass().getSimpleName());
            return false;
        }
    }
}
