package com.workflowautomation.jiraplanner.web;

import com.workflowautomation.jiraplanner.automation.execution.ExecutionNotFoundException;
import com.workflowautomation.jiraplanner.automation.security.AutomationAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, exception);
    }

    @ExceptionHandler(AutomationAccessException.class)
    public ResponseEntity<Map<String, Object>> forbidden(AutomationAccessException exception) {
        return response(HttpStatus.FORBIDDEN, exception);
    }

    @ExceptionHandler(ExecutionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(ExecutionNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException exception) {
        return response(HttpStatus.CONFLICT, exception);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> serverError(RuntimeException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, exception);
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, RuntimeException exception) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", safeMessage(exception)
        ));
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
