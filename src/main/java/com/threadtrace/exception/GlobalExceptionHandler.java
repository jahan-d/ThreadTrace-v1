package com.threadtrace.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatusException(
            ResponseStatusException exception) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                exception.getStatusCode(),
                exception.getReason()
        );

        problem.setTitle(exception.getStatusCode().toString());
        problem.setType(URI.create("about:blank"));

        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(
            MethodArgumentNotValidException exception) {

        String errorMessage = exception.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                exception.getStatusCode(),
                errorMessage
        );

        problem.setTitle("Bad Request");
        problem.setType(URI.create("about:blank"));

        return problem;
    }
}
