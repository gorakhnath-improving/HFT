package com.finex.api.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import com.finex.api.GlobalExceptionHandler;

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
@Import({OrderService.class, GlobalExceptionHandler.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static String limitOrder(String clientOrderId, String side, String price, String qty) {
        return "{"
                + "\"clientOrderId\":\"" + clientOrderId + "\","
                + "\"symbol\":\"BTC-USD\","
                + "\"side\":\"" + side + "\","
                + "\"type\":\"LIMIT\","
                + "\"price\":\"" + price + "\","
                + "\"quantity\":\"" + qty + "\","
                + "\"accountId\":100"
                + "}";
    }

    @Test
    void submitLimitOrderRestsAndReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
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
                        .content(limitOrder("cid-s", "SELL", "50000", "1")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
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
                        .content(limitOrder("cid-1", "SELL", "51000", "2")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/orders/{orderId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.symbol").value("BTC-USD"));
    }

    @Test
    void cancelOrderRemovesItFromBook() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitOrder("cid-1", "SELL", "50000", "1")))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/orders/{orderId}", 1L))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/{orderId}", 1L))
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
                        .content(limitOrder("cid-1", "BUY", "50000", "1")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/order-books/{symbol}", "BTC-USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTC-USD"))
                .andExpect(jsonPath("$.bids").isNotEmpty())
                .andExpect(jsonPath("$.asks").isEmpty());
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
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
