package com.deroyque.activation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RequestNotFoundException.class)
    ProblemDetail notFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Activation request was not found.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail duplicate(DataIntegrityViolationException exception) {
        if (exception.getMostSpecificCause() instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "An activation request with this externalId already exists.");
        }
        return unexpected(exception);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), "Must contain 1 to 100 letters, digits, underscores or hyphens."))
                .distinct().toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unexpected request failure", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private record FieldError(String field, String message) {}
}
