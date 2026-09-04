package com.finex.api.order;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finex.matching.MatchResult;

/**
 * Administrative/developer-facing REST entry point for order management (Master Plan §27).
 * This is not the HFT hot path; it is the slow/administrative API. The hot path
 * (binary protocol) is Phase 8.
 *
 * <p>Order endpoints are scoped to the authenticated account resolved by the API key filter.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> submitOrder(
            @RequestAttribute("accountId") long accountId,
            @RequestBody OrderRequest request) {
        if (request.accountId() != accountId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        MatchResult result = orderService.submitOrder(request, Instant.now());
        OrderResponse body = OrderResponse.from(
                result.order().orderId(), result.order(), result.trades(), result.addedToBook());
        return ResponseEntity.status(result.addedToBook() ? HttpStatus.CREATED : HttpStatus.OK).body(body);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @RequestAttribute("accountId") long accountId,
            @PathVariable("orderId") long orderId) {
        return orderService.getOrder(orderId)
                .filter(r -> r.accountId() == accountId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @RequestAttribute("accountId") long accountId,
            @PathVariable("orderId") long orderId) {
        boolean owns = orderService.getOrder(orderId)
                .filter(r -> r.accountId() == accountId)
                .isPresent();
        if (!owns) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        boolean cancelled = orderService.cancelOrder(orderId, Instant.now());
        return cancelled ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @GetMapping("/order-books/{symbol}")
    public ResponseEntity<OrderBookView> getOrderBook(@PathVariable("symbol") String symbol) {
        return orderService.getOrderBook(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
