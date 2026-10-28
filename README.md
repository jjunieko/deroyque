# Product Activation

Java 21 + Spring Boot API for recording product activation requests, calling an external provider and checking the result. Uses Spring MVC, JPA, Flyway and PostgreSQL.

## Run

Install Docker with Compose, start Docker Desktop, then run:

```sh
git clone https://github.com/jjunieko/deroyque.git
cd deroyque
docker compose up --build
```

This starts the API, PostgreSQL and a WireMock provider. Local database data survives restarts. Stop with `docker compose down`.

- [Swagger UI](http://localhost:8080/swagger-ui.html): test the API in your browser.
- [OpenAPI JSON](http://localhost:8080/v3/api-docs): import into an API client.
- [Health](http://localhost:8080/actuator/health): check service health.

Without Docker, use JDK 21 and Maven 3.9+: `mvn spring-boot:run`. This uses temporary H2 storage and expects a provider at `http://localhost:8081`; without one, activations return `FAILED`.

## Try it in Swagger

1. Open POST, select **Try it out**, and execute the example. Expect `201` and `COMPLETED` with the mock provider running.
2. Copy the returned `id` into GET. Expect `200` with the saved request.
3. Repeat the POST with the same `externalId`. Expect `409`.
4. Clear a required field. Expect `400`. GET with an unknown UUID returns `404`.
5. To simulate provider failure, run `docker compose stop provider` and submit a valid POST with a new `externalId`. Expect `201` with `FAILED`. Restore with `docker compose start provider`.

**201 means the request was recorded. Check `status` for the activation outcome.** Use a fresh `externalId` for each new request. No token is required in this evaluation build.

## API and decisions

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/requests` | Create and process an activation; returns a `Location` header. |
| `GET /api/v1/requests/{id}` | Retrieve a request by its internal UUID. |

Input fields accept 1–100 ASCII letters, digits, underscores or hyphens. Values are case-sensitive; whitespace is rejected. Dates use UTC. Errors use Problem Details without internal exception messages.

- **Structure:** controller → service → transactional store/repository, plus an HTTP client. DTOs are separate from the entity.
- **Duplicates:** a database UNIQUE constraint on `external_id` handles concurrent inserts. Any repeated ID returns `409`, even with identical content, without another provider call. A lookup before inserting would not prevent races.
- **Transactions:** database operations commit separately; the provider call runs outside a database transaction.
- **States:** `PENDING → PROCESSING → COMPLETED/FAILED`. The entity controls transitions; `@Version` protects concurrent updates.
- **Processing:** synchronous, with one provider call. Connection timeout is 1s; total request timeout is 2s.

| Provider result | Request status | Retry |
| --- | --- | --- |
| 2xx | COMPLETED | No |
| 400 or other 4xx | FAILED | No; investigate the request. |
| 500, 429 or 503 | FAILED | No automatic retry. |
| Connection failure or timeout | FAILED | Reconcile before retrying. |

A timeout may happen after the provider activates the product. `FAILED` means confirmation was not received. The client sends `Idempotency-Key: <externalId>`; preventing duplicate activations still depends on provider support.

## Tests

```sh
mvn verify
```

All **10 tests passed**, including domain transitions, validation, lookup, concurrent duplicates, provider errors, timeout and Swagger. Integration tests use H2 and a real local HTTP stub; PostgreSQL/Testcontainers coverage is pending. GitHub Actions runs the build and tests.

On Windows, if the JDK reports a loopback error caused by a long temporary path, add `-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp` to Maven, using a short writable directory.

## Security and production

Authentication is documented rather than implemented, as allowed by the challenge. For production, use OAuth2 client credentials and Spring Security Resource Server: validate JWT signature via trusted JWKS, issuer, audience and expiration; require `activation:write` for POST and `activation:read` for GET. Restrict queries to the authenticated consumer. Store and rotate provider credentials through a secret manager.

Build with `docker build -t product-activation .`. The image runs as non-root with a JRE, container-aware heap and graceful shutdown. Configure `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `PROVIDER_BASE_URL`, `PROVIDER_CONNECT_TIMEOUT` and `PROVIDER_REQUEST_TIMEOUT`. Use external PostgreSQL, TLS and injected secrets; Compose credentials are local-only. Set `API_DOCS_ENABLED=false` to disable Swagger and OpenAPI. Liveness/readiness probes are under `/actuator/health`.

## Production investigation

For the challenge's slowdown (API 300ms → 2.5s, SQL 50ms → 1.2s, provider 400ms → 3s):

- Check traffic and recent changes; moderate CPU/memory suggest waiting or contention.
- Separate database pool wait, SQL execution and provider latency. Inspect slow-query plans, locks, thread dumps, latency percentiles and error rates.
- A traffic burst could overload both dependencies. Confirm by correlating throughput, active requests and connection-pool waits.
- Fix the measured bottleneck: query/index changes, bounded concurrency or background processing. Validate with load tests and a canary, comparing latency and error rates; retain a rollback path.

## Pending work and next steps

- Implement JWT and consumer isolation before exposing the API.
- Recover requests left in `PENDING` or `PROCESSING` after a crash. Add a durable worker with atomic claims, bounded backoff and provider reconciliation; delivery is not guaranteed today.
- Preserve failure categories for retry decisions; add PostgreSQL and container execution tests. Compose configuration was validated, but container startup remains unverified.
- Add correlation IDs, dependency metrics and tracing. Consider an outbox for reliable completion events if a broker is introduced.
