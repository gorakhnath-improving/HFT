package com.finex.api.security;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates protected trading endpoints with an {@code X-API-Key} header.
 * The resolved account id is stored as a request attribute for {@link com.finex.api.order.OrderController}.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String ACCOUNT_ID_ATTRIBUTE = "accountId";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only trading endpoints require an API key. Order books, health, metrics etc. are public.
        return path == null || !path.startsWith("/api/v1/orders");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader(API_KEY_HEADER);
        if (key == null || key.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing API key");
            return;
        }
        Optional<ApiKey> apiKey = apiKeyService.findByKey(key);
        if (apiKey.isEmpty() || !apiKey.get().active()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }
        request.setAttribute(ACCOUNT_ID_ATTRIBUTE, apiKey.get().accountId());
        filterChain.doFilter(request, response);
    }
}
