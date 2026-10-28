# Product Activation

A small Spring Boot service that records product activation requests and submits them to an external provider. Java 21, Maven, Spring MVC, Spring Data JPA and Flyway. PostgreSQL is used in Compose; the default local database is an in-memory H2 instance.

## Run

Prerequisites: JDK 21 and Maven 3.9+, or Docker with Compose. No IDE plugins are required.

```sh
mvn verify
docker compose up --build
```

Compose starts the API on `localhost:8080`, PostgreSQL and a WireMock provider on `localhost:8081`. The first build downloads dependencies and container images. The database volume survives restarts. The credentials in Compose are for local development only.

Without Docker:

```sh
mvn spring-boot:run
```

This starts the API with H2. Set `PROVIDER_BASE_URL` to a running provider (default `http://localhost:8081`). Without a provider, requests are recorded as `FAILED`. H2 data is lost when the process stops.

```sh
curl -i http://localhost:8080/api/v1/requests \
  -H 'Content-Type: application/json' \
  -d '{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}'

curl http://localhost:8080/api/v1/requests/REPLACE_WITH_RETURNED_UUID
```

`POST` returns `201 Created`, a `Location` header and the persisted request, including its final status. `GET` returns `200`, or `404` for an unknown UUID. Dates use UTC ISO-8601. Each input field must contain 1–100 ASCII letters, digits, underscores or hyphens. Values are case-sensitive; whitespace is rejected rather than silently normalized. Invalid fields, JSON or UUIDs return `400`. Duplicate external IDs return `409`. Unexpected failures return a generic `500`. Errors use Problem Details without internal exception messages.

## Interactive API documentation

After starting the application, open [Swagger UI](http://localhost:8080/swagger-ui.html).
The [OpenAPI JSON](http://localhost:8080/v3/api-docs) is also available for importing into API clients.
Documentation and examples are in English and are generated from the API controllers and DTOs.

For a presentation:

1. Run `docker compose up --build` and open Swagger UI.
2. Expand **POST /api/v1/requests**, select **Try it out**, and execute the example body. With the Compose provider running, expect `201` and `COMPLETED`.
3. Copy the returned `id`, expand **GET /api/v1/requests/{id}**, paste it and execute. Expect `200` with the saved request.
4. Repeat the POST without changing `externalId`. Expect `409`, including when the body is identical.
5. Change `externalId` to a fresh value and clear `customerId`. Expect `400` with validation errors.
6. Call GET with a valid UUID that was not created. Expect `404`. An invalid UUID returns `400`.
7. To demonstrate provider failure, run `docker compose stop provider`, submit a valid POST with a new `externalId`, and inspect the `FAILED` status. Run `docker compose start provider` afterward. Failed requests are not automatically retried.

`201` confirms resource creation; the response `status` describes the activation outcome.
Use a new `externalId` each time you want a new activation. No token is required in this evaluation build.
Set `API_DOCS_ENABLED=false` to disable both the UI and OpenAPI endpoints in environments where documentation should not be exposed.
The documentation addition was verified with `mvn verify`: all 10 tests passed, including Swagger UI delivery and the generated OpenAPI contract.

## Design decisions

The application uses one feature package with a controller, service, transactional store, repository and HTTP client. DTOs are separate from the JPA entity. There are no generic base services or repository wrappers. The store exists to make transaction boundaries explicit: creating a row, claiming it and storing the result each commit separately. The HTTP call never holds a database transaction open.

The database unique constraint on `external_id` is the authority for duplicate detection. A check followed by an insert would race across concurrent requests or application instances. Both identical retries and conflicting payloads return `409`, and neither triggers another provider call. This deliberately uses one simple duplicate policy; callers should retain the original request UUID. A richer contract could return the original resource for matching retries.

The entity allows only `PENDING → PROCESSING → COMPLETED/FAILED`. Terminal states cannot be reopened. Optimistic locking (`@Version`) rejects competing updates. The API is synchronous: the POST waits for one bounded provider call and returns the recorded outcome. This avoids adding a queue and worker for the exercise, at the cost of tying API latency to the provider. `201` means the request resource was created; consumers must inspect `status` to determine whether activation succeeded.

| Provider outcome | Stored status | Automatic retry | Reason |
| --- | --- | --- | --- |
| Any 2xx | COMPLETED | No | Provider acknowledged activation. |
| 400 or other 4xx | FAILED | No | Request or authorization needs investigation. |
| 500 | FAILED | No | A retry may be useful, but requires a retry/reconciliation policy. |
| Connection failure or timeout | FAILED | No | The provider may have applied the operation before the response was lost. |
| 429 / 503 | FAILED | No | Retry-After scheduling is not implemented. |

`FAILED` means this service did not receive confirmation, not proof that the provider did nothing. Every outgoing call carries `Idempotency-Key: <externalId>`. Safe retries still require a provider contract that stores and honors that key; sending a header alone cannot guarantee idempotency. Connection and total request timeouts default to 1s and 2s, configurable with `PROVIDER_CONNECT_TIMEOUT` and `PROVIDER_REQUEST_TIMEOUT`. The client follows no redirects and ignores response bodies.

There is a known crash window: a committed row may remain `PENDING` or `PROCESSING` if the application stops before sending or recording the result. This version does not guarantee eventual delivery. Before production, add a database-backed worker that claims due rows atomically, uses bounded exponential backoff with jitter, and reconciles expired processing leases against the provider. Persist attempt counts, next attempt time and sanitized failure categories. Do not blindly replay uncertain activations. A transactional outbox is an alternative when a broker is already part of the environment.

## Security

Authentication is described rather than implemented, as permitted by the challenge. **The current API is unauthenticated and intended for local evaluation.** Compose binds its published ports to localhost.

For production, use OAuth 2.0 client credentials for system-to-system access and configure Spring Security as an OAuth2 Resource Server. Validate the JWT signature with the identity provider's JWKS, trusted issuer, expected audience, expiration and not-before claim. Require `activation:write` for POST and `activation:read` for GET. Do not accept arbitrary issuer URLs from requests. Return `401` for missing/invalid tokens and `403` for insufficient scope.

Persist the authenticated consumer ID and constrain reads to that consumer to prevent object-level authorization failures. Decide whether external ID uniqueness is global or per consumer before changing the constraint. Store provider credentials in a managed secret store, inject them at runtime, rotate them with overlapping validity, and never log them. Terminate TLS at a trusted ingress, enforce request-body and rate limits there, restrict direct access to the service, and keep health endpoints free of sensitive details. Add JWT and cross-consumer access tests alongside the security implementation.

## Tests

`mvn verify` runs domain unit tests and full Spring integration tests. The latter exercise MVC validation, Flyway migrations, JPA and unique constraints against H2, plus actual HTTP requests to an embedded JDK HTTP server. No provider account or Docker daemon is needed.

Scenarios include successful creation and lookup, invalid data/JSON/UUID, missing records, duplicate and conflicting payloads, six concurrent duplicate submissions, provider 400/429/500/503 responses, timeout, idempotency headers and invalid domain transitions. The concurrency test checks both one database row and one provider invocation. H2 is useful feedback but does not replace a PostgreSQL concurrency test; Testcontainers coverage remains pending.

Verified locally on 2026-10-28: `mvn verify` completed successfully with 9 tests and no failures. Compose configuration also passed `docker compose config --quiet`. Container startup was not verified because the local Docker daemon was stopped. On Windows environments with an unusually long temporary directory, run Maven with `-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp` (or another short writable directory); the JDK HTTP test server uses local sockets whose path length is limited.

## Production investigation

Given API latency rising from 300ms to 2.5s, SQL from 50ms to 1.2s and provider latency from 400ms to 3s, first compare timing windows, traffic volume, recent releases and infrastructure changes. Moderate CPU (45%) and memory (60%) suggest investigating waiting and contention before increasing compute.

Separate request time into connection-pool wait, SQL execution, provider connection/response and application time. Inspect database active sessions, locks, slow statements and execution plans with representative parameters. Compare pool occupancy and timeouts with database connection limits. Check provider p50/p95/p99, status rates, DNS/TLS/connect timings and timeout counts. Review thread dumps for blocked request threads. Add correlation IDs and distributed traces before relying on logs alone; do not include customer data, request bodies or credentials.

One common cause could be a traffic burst: provider calls occupy request threads while database concurrency and pool waits rise. Confirm by correlating throughput, in-flight HTTP calls, busy threads, pool wait and database locks over the same period. A network or infrastructure incident is another hypothesis, to test against dependency-specific timings. Do not assume one cause from averages alone.

Apply the fix supported by evidence: a targeted index or query change, shorter transactions, bounded concurrency, provider timeouts or asynchronous processing. Do not increase pool sizes without checking database capacity. Validate with a representative load test, then a canary deployment. Compare latency percentiles, error rates, pool waits, throughput and completion rates against baseline; keep a rollback path and verify that improvements persist during peak load.

## Production execution

Build with `docker build -t product-activation .` and deploy the image with an external PostgreSQL database. The multi-stage Dockerfile runs tests, uses a JRE-only runtime, runs as a non-root user and limits JVM heap to 75% of container memory. Set a container memory limit with headroom for native memory and threads. Shutdown is graceful; health probes are available at `/actuator/health/liveness` and `/actuator/health/readiness`. The image health check queries overall health. Provider availability is deliberately not a readiness dependency.

Configure `DATABASE_URL` (JDBC URL), `DATABASE_USER`, `DATABASE_PASSWORD`, `PROVIDER_BASE_URL` and the timeout variables. Use TLS for remote dependencies, scoped database credentials, backups and secret injection. Flyway runs migrations on startup; larger production changes should use a separate migration step with backward-compatible schema changes. Deploy only after implementing access control and crash recovery.

The unique constraint remains correct across multiple instances. The current service only processes the request it inserted; a future worker needs leases or database locking to prevent duplicate claims. To notify other services reliably, write completion events to an outbox in the same transaction as the status change and publish them to a queue/topic. Consumers must deduplicate events.

The included GitHub Actions workflow builds and tests on Java 21. An expanded deployment pipeline would run PostgreSQL integration tests, scan dependencies and the image, publish an immutable image digest, deploy to staging, run smoke tests and promote through a canary with rollback.

## Pending work and future improvements

- JWT enforcement and consumer isolation, as detailed above.
- Durable background processing, retry scheduling and provider reconciliation.
- PostgreSQL/Testcontainers integration tests and container smoke tests.
- Correlation IDs propagated to the provider, structured logs, dependency latency metrics and tracing.
- Optional paginated search. Add indexes on `(customer_id, created_at, id)` and `(status, created_at, id)` only after confirming query shapes and selectivity; avoid indexing every column.
- Maven Wrapper for builds without a preinstalled Maven, and dependency version updates after compatibility testing.

## AI use

An OpenAI coding assistant generated the initial implementation, tests and documentation from the challenge requirements. AI was also used to review transaction boundaries, duplicate handling and operational tradeoffs. This disclosure is intentional; the repository makes no claim that the code was written without assistance.

