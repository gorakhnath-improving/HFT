package com.finex.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finex.api.order.OrderRejectedException;
import com.finex.api.order.OrderResponse;

/**
 * Maps domain/validation exceptions to appropriate HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(OrderRejectedException.class)
    public ResponseEntity<OrderResponse> handleOrderRejected(OrderRejectedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(OrderResponse.rejected(ex.order(), ex.reason()));
    }
}
