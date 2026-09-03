# API Keys and Account Isolation

Phase 22 introduces API-key authentication for the trading REST endpoints.

## How it works

- `ApiKeyService` holds an in-memory registry mapping `X-API-Key` header values to accounts.
- `ApiKeyAuthenticationFilter` reads the header on every request under `/api/v1/orders/**`.
  - Missing or unknown key: `401 Unauthorized`.
  - Valid key: account id is stored as a request attribute.
- `OrderController` enforces account isolation:
  - `POST /api/v1/orders` rejects (`403 Forbidden`) if the request body's `accountId`
    does not match the authenticated account.
  - `GET /api/v1/orders/{orderId}` and `DELETE /api/v1/orders/{orderId}` return `404`
    unless the order belongs to the authenticated account.
- Public endpoints (`/api/v1/order-books/**`, `/actuator/**`) are not filtered.

## Rate limiting

Per-account order rate limiting is enforced by the pre-trade risk engine
(`RiskEngine` / `AccountRiskState`). The default limit is 10 orders per second per
account and is independent of the API key layer.

## Running locally

```bash
# Start dependencies
mvn spring-boot:run -pl finex-api

# Missing key
curl -X POST http://localhost:8080/api/v1/orders -H "Content-Type: application/json" \
  -d '{"clientOrderId":"x","symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":"50000","quantity":"1","accountId":100}'
# -> 401 Unauthorized

# With a valid key (the example key below must be registered at startup)
curl -X POST http://localhost:8080/api/v1/orders -H "X-API-Key: demo-key" \
  -H "Content-Type: application/json" \
  -d '{"clientOrderId":"x","symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":"50000","quantity":"1","accountId":100}'
```

The example key can be registered by a startup bean or initialization script. In a
real deployment `ApiKeyService` would be backed by an encrypted, auditable key store
with rotation and revocation.
