package com.finex.api.portfolio;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finex.api.order.OrderService;
import com.finex.portfolio.Portfolio;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final OrderService orderService;

    public PortfolioController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<Portfolio> getPortfolio(@PathVariable long accountId) {
        Portfolio portfolio = orderService.portfolio(accountId);
        return ResponseEntity.of(Optional.of(portfolio));
    }
}
