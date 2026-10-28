package com.deroyque.activation;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import java.util.Map;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI activationApi() {
        return new OpenAPI()
                .info(new Info().title("Product Activation API").version("v1")
                        .description("""
                                Record product activation requests and inspect their outcomes.

                                **Quick demo**
                                1. Expand POST, select **Try it out**, and submit the example body.
                                2. Copy the returned `id` into GET to retrieve the request.
                                3. Submit the same `externalId` again to see **409 Conflict**.
                                4. Set a required field to an empty string to see **400 Bad Request**.
                                5. Use a new `externalId` for each new activation.

                                Processing is synchronous: POST waits for one bounded provider call.
                                **201 means the request was recorded**, not necessarily activated.
                                Inspect `status`: `COMPLETED` confirms activation; `FAILED` means
                                confirmation was not received. Timeouts can have an uncertain external outcome.
                                There are no automatic retries. The Compose provider normally returns success.

                                Authentication is not implemented in this evaluation build.
                                Do not expose this service publicly without access control.
                                """))
                .components(new Components()
                        .addResponses("BadRequest", problem("Invalid input, malformed JSON or an invalid UUID.",
                                HttpStatus.BAD_REQUEST, "One or more fields are invalid."))
                        .addResponses("NotFound", problem("No activation request exists with this UUID.",
                                HttpStatus.NOT_FOUND, "Activation request was not found."))
                        .addResponses("Conflict", problem("The externalId already exists, even if the payload is identical. No new provider call is made.",
                                HttpStatus.CONFLICT, "An activation request with this externalId already exists."))
                        .addResponses("InternalError", problem("An unexpected server error occurred. Internal details are not exposed.",
                                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.")));
    }

    private ApiResponse problem(String description, HttpStatus status, String detail) {
        return new ApiResponse().description(description).content(new Content()
                .addMediaType("application/problem+json", new io.swagger.v3.oas.models.media.MediaType()
                        .schema(new Schema<>().type("object")
                                .addProperty("type", new Schema<>().type("string").format("uri"))
                                .addProperty("title", new Schema<>().type("string"))
                                .addProperty("status", new Schema<>().type("integer"))
                                .addProperty("detail", new Schema<>().type("string"))
                                .addProperty("instance", new Schema<>().type("string").format("uri"))
                                .additionalProperties(true))
                        .example(Map.of("type", "about:blank", "title", status.getReasonPhrase(),
                                "status", status.value(), "detail", detail))));
    }
}
