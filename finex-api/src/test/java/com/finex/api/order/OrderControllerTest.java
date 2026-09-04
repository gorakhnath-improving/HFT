package com.finex.api.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import com.finex.api.GlobalExceptionHandler;
import com.finex.api.security.ApiKeyAuthenticationFilter;
import com.finex.api.security.ApiKeyService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice tests for {@link OrderController}. No database is required; the matching
 * engine runs in-memory. Context is recreated after each test so the singleton OrderService
 * does not leak state between tests.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(OrderController.class)
@Import({OrderService.class, GlobalExceptionHandler.class, ApiKeyService.class, ApiKeyAuthenticationFilter.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyService apiKeyService;

    private static final String API_KEY_100 = "apikey-100";
    private static final String API_KEY_200 = "apikey-200";

    @BeforeEach
    void registerKeys() {
        apiKeyService.register(API_KEY_100, 100L);
        apiKeyService.register(API_KEY_200, 200L);
    }

    private static String limitOrder(String clientOrderId, String side, String price, String qty, long accountId) {
        return "{"
                + "\"clientOrderId\":\"" + clientOrderId + "\","
                + "\"symbol\":\"BTC-USD\","
                + "\"side\":\"" + side + "\","
                + "\"type\":\"LIMIT\","
                + "\"price\":\"" + price + "\","
                + "\"quantity\":\"" + qty + "\","
                + "\"accountId\":" + accountId
                + "}";
    }

    private static String limitOrder(String clientOrderId, String side, String price, String qty) {
        return limitOrder(clientOrderId, side, price, qty, 100L);
    }

    @Test
    void submitLimitOrderRestsAndReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-1", "BUY", "50000", "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.remainingQuantity").value(1))
                .andExpect(jsonPath("$.addedToBook").value(true))
                .andExpect(jsonPath("$.trades").isEmpty());
    }

    @Test
    void matchedOrdersProduceTrades() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-s", "SELL", "50000", "1")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-b", "BUY", "50000", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.trades").isNotEmpty())
                .andExpect(jsonPath("$.trades[0].price").value(50000))
                .andExpect(jsonPath("$.trades[0].quantity").value(1));
    }

    @Test
    void getOrderReturnsCurrentState() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-1", "SELL", "51000", "2")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/orders/{orderId}", 1L)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.symbol").value("BTC-USD"));
    }

    @Test
    void cancelOrderRemovesItFromBook() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-1", "SELL", "50000", "1")))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/orders/{orderId}", 1L)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/{orderId}", 1L)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/order-books/BTC-USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bids").isEmpty())
                .andExpect(jsonPath("$.asks").isEmpty());
    }

    @Test
    void getOrderBookSnapshot() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-1", "BUY", "50000", "1")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/order-books/{symbol}", "BTC-USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTC-USD"))
                .andExpect(jsonPath("$.bids").isNotEmpty())
                .andExpect(jsonPath("$.asks").isEmpty());
    }

    @Test
    void rejectsMissingApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitOrder("cid-1", "BUY", "50000", "1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAccountMismatch() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-1", "BUY", "50000", "1", 200L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMarketOrderWithPrice() throws Exception {
        String body = "{"
                + "\"clientOrderId\":\"cid-1\","
                + "\"symbol\":\"BTC-USD\","
                + "\"side\":\"BUY\","
                + "\"type\":\"MARKET\","
                + "\"price\":\"50000\","
                + "\"quantity\":\"1\","
                + "\"accountId\":100"
                + "}";

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPriceOutsideCollar() throws Exception {
        // Establish last trade price at 50000 using a cross from two accounts.
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_200)
                        .content(limitOrder("cid-s", "SELL", "50000", "1", 200L)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-b", "BUY", "50000", "1")))
                .andExpect(status().isOk());

        // 75000 is 50% away from the last trade price, exceeding the 10% collar.
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-far", "BUY", "75000", "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.containsString("collar")));
    }

    @Test
    void rejectsOrderExceedingPositionLimit() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-big", "SELL", "1", "200")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.containsString("position")));
    }

    @Test
    void rejectsOrderExceedingOpenNotional() throws Exception {
        // First buy order consumes the max open notional (10 * 50000 = 500000).
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-big", "BUY", "50000", "10")))
                .andExpect(status().isCreated());

        // A second buy would push total open notional above the max cash exposure.
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-more", "BUY", "50000", "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.containsString("notional")));
    }

    @Test
    void rejectsOrderWithInsufficientCash() throws Exception {
        // Deplete account 100 by buying 20 BTC from account 200 (20 * 50000 = 1000000).
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_200)
                        .content(limitOrder("cid-s1", "SELL", "50000", "10", 200L)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-b1", "BUY", "50000", "10")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_200)
                        .content(limitOrder("cid-s2", "SELL", "50000", "10", 200L)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-b2", "BUY", "50000", "10")))
                .andExpect(status().isOk());

        // Account 100 now has zero cash; the next buy is rejected.
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY_100)
                        .content(limitOrder("cid-broke", "BUY", "50000", "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.containsString("cash")));
    }
}
