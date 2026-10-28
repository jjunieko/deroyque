package com.deroyque.activation;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RequestApiTest {
    static final HttpServer provider;
    static final AtomicInteger providerStatus = new AtomicInteger(200);
    static final AtomicInteger delay = new AtomicInteger();
    static final AtomicInteger calls = new AtomicInteger();
    static volatile String idempotencyKey;
    static final ExecutorService providerThreads = Executors.newVirtualThreadPerTaskExecutor();

    static {
        try {
            provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            provider.setExecutor(providerThreads);
            provider.createContext("/provider/v1/activate", exchange -> {
                calls.incrementAndGet();
                idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
                exchange.getRequestBody().readAllBytes();
                try {
                    Thread.sleep(delay.get());
                    exchange.sendResponseHeaders(providerStatus.get(), -1);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally { exchange.close(); }
            });
            provider.start();
        } catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("provider.base-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
        registry.add("provider.request-timeout", () -> "500ms");
    }

    @Autowired MockMvc mvc;
    @Autowired RequestRepository repository;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        providerStatus.set(200);
        delay.set(0);
        calls.set(0);
    }

    @AfterAll
    static void stopProvider() {
        provider.stop(0);
        providerThreads.shutdownNow();
    }

    @Test
    void servesInteractiveDocumentationAndApiContract() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Product Activation API"))
                .andExpect(jsonPath("$.paths['/api/v1/requests'].post.responses['201'].headers.Location").exists())
                .andExpect(jsonPath("$.paths['/api/v1/requests'].post.responses['409']['$ref']")
                        .value("#/components/responses/Conflict"))
                .andExpect(jsonPath("$.paths['/api/v1/requests/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.CreateRequest.properties.externalId.example").value("DEMO-001"))
                .andExpect(jsonPath("$.components.responses.Conflict.content['application/problem+json'].example.status").value(409));
    }

    @Test
    void createsAndRetrievesAnActivation() throws Exception {
        String location = mvc.perform(post("/api/v1/requests").contentType("application/json").content(payload("EXT-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.externalId").value("EXT-1"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getHeader("Location");
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(idempotencyKey).isEqualTo("EXT-1");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidFieldsAndMalformedInput() throws Exception {
        mvc.perform(post("/api/v1/requests").contentType("application/json")
                .content("{\"externalId\":\" \",\"customerId\":\"CUST-1\",\"productCode\":\"X\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        mvc.perform(post("/api/v1/requests").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/requests/not-a-uuid")).andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
        assertThat(calls.get()).isZero();
    }

    @Test
    void returnsNotFound() throws Exception {
        mvc.perform(get("/api/v1/requests/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Activation request was not found."));
    }

    @Test
    void rejectsDuplicateEvenWhenPayloadChanges() throws Exception {
        mvc.perform(post("/api/v1/requests").contentType("application/json").content(payload("EXT-1")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/requests").contentType("application/json").content(payload("EXT-1")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/requests").contentType("application/json")
                .content(payload("EXT-1").replace("INTERNET-500", "INTERNET-100")))
                .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void concurrentDuplicatesCreateOneRecordAndOneProviderCall() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mvc.perform(post("/api/v1/requests").contentType("application/json").content(payload("PARALLEL")))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) statuses.add(result.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsOnly(201, 409);
            assertThat(Collections.frequency(statuses, 201)).isEqualTo(1);
            assertThat(repository.count()).isEqualTo(1);
            assertThat(calls.get()).isEqualTo(1);
        }
    }

    @Test
    void providerErrorsPersistFailedRequests() throws Exception {
        for (int code : new int[]{400, 429, 500, 503}) {
            providerStatus.set(code);
            mvc.perform(post("/api/v1/requests").contentType("application/json").content(payload("ERROR-" + code)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("FAILED"));
        }
        assertThat(repository.findAll()).allMatch(request -> request.getStatus() == RequestStatus.FAILED);
    }

    @Test
    void providerTimeoutPersistsFailureWithoutRetrying() throws Exception {
        delay.set(1500);
        mvc.perform(post("/api/v1/requests").contentType("application/json").content(payload("TIMEOUT")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(calls.get()).isEqualTo(1);
        assertThat(repository.findAll().getFirst().getStatus()).isEqualTo(RequestStatus.FAILED);
    }

    private static String payload(String externalId) {
        return "{\"externalId\":\"" + externalId + "\",\"customerId\":\"CUST-1\",\"productCode\":\"INTERNET-500\"}";
    }
}
