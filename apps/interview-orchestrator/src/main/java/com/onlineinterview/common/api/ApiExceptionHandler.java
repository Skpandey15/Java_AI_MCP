package com.onlineinterview.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var errors = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        var problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed",
                "Correct the invalid fields and try again.", request);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail optimisticConflict(
            ObjectOptimisticLockingFailureException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Concurrent update detected",
                "This review changed in another request. Reload it before saving again.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataConflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Data conflict",
                "The requested change conflicts with the current persisted state.", request);
    }

    private ProblemDetail problem(
            HttpStatus status, String title, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://online-interview.local/problems/"
                + title.toLowerCase().replace(' ', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        var requestId = request.getAttribute(CorrelationIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId != null) problem.setProperty("requestId", requestId);
        return problem;
    }
}
