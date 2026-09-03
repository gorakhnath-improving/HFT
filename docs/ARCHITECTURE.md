# Architecture

FinEx is a Maven multi-module Java/Spring Boot simulated exchange. The design deliberately
separates two planes that have different priorities:

- A **Spring Boot control plane** (REST API, security, metrics, observability) that
  prioritizes developer ergonomics, operability, and correctness.
- A **performance-oriented trading plane** (matching engine, order book, risk, binary
  protocol, event log) that prioritizes low allocation, determinism, and measured
  throughput/latency.

The Spring Boot layer is a thin, synchronous front door onto the trading plane in this
baseline — it is not itself claimed to be an HFT engine. See
`docs/performance/FINAL_BENCHMARK_REPORT.md` for what has and has not been measured.

## System diagram

```text
                              Clients
                                 │
                                 ▼
                       Spring Boot Control Plane
                (REST API, API-key security, metrics/Prometheus)
                                 │
                                 ▼
                        OrderService (finex-api)
                 (numeric mode selection, shard routing)
                                 │
                 ┌───────────────┼───────────────────┐
                 ▼               ▼                    ▼
          Event Log         Risk Engine          Market Data
        (append + replay)  (BigDecimal ref. /    (book/trade/
                            fixed-point opt.)     execution events)
                 │               │
                 │               ▼
                 │        Shard Coordinator
                 │               │
                 │               ▼
                 │       Matching Engine + Order Book
                 │        (single-threaded per shard,
                 │         price-time priority)
                 │               │
                 │               ▼
                 │           Trade(s)
                 │               │
                 └───────►  Settlement
                                 │
                    ┌────────────┼────────────┐
                    ▼            ▼             ▼
                 Ledger      Clearing      Portfolio
              (double-entry) (fees/net    (positions,
                              cash)         P&L)
```

The **hot path** is `OrderService` → `RiskEngine`/`FixedPointRiskEngine` → `EngineShard` →
`MatchingEngine`/`OrderBook`. The **durable financial path** is the `EventStore` (append-only,
replayable) and everything under `Settlement` (ledger, clearing, portfolio) — this is where
correctness and auditability, not raw speed, are the priority.

## Module boundaries

| Module | Responsibility | Key types |
|--------|----------------|-----------|
| `finex-common` | Domain records and enums used by all modules | `Order`, `Trade`, `Instrument`, enums |
| `finex-order-book` | Price-time-priority limit order book | `OrderBook` |
| `finex-matching-engine` | Deterministic matching for a single symbol | `MatchingEngine`, `MatchResult` |
| `finex-risk` | Pre-trade risk and per-account reservations | `RiskEngine`, `AccountRiskState` |
| `finex-market-data` | Trade/book/execution events and pub/sub | `MarketDataPublisher`, `BookUpdate`, `TradeEvent` |
| `finex-protocol` | Compact binary codec for the hot path | `BinaryCodec`, `ProtocolMessage` |
| `finex-event-log` | Append-only command journal and replay | `EventStore`, `CommandSerializer`, `ReplayEngine` |
| `finex-shard` | Symbol-to-shard routing and engine shards | `ShardCoordinator`, `EngineShard` |
| `finex-ledger` | Double-entry ledger postings | `Ledger`, `LedgerEntry`, `DebitCredit` |
| `finex-portfolio` | Positions, P&L, and equity | `Portfolio`, `PortfolioService` |
| `finex-clearing` | Per-trade fee and net cash computation | `ClearingService`, `ClearingResult` |
| `finex-settlement` | Post-trade orchestration | `SettlementService` |
| `finex-load-generator` | Deterministic workload harness | `LoadConfig`, `LoadGenerator`, `LoadResult` |
| `finex-benchmarks` | JMH micro/component/end-to-end benchmarks and JFR profiling | `BenchmarkRunner`, `ProfileRunner` |
| `finex-api` | Spring Boot REST app, metrics, and security | `OrderService`, `OrderController`, `MetricsService`, `ApiKeyAuthenticationFilter` |

## Data flow: submit order

1. **REST /admin path:** `OrderController` validates the API key, enforces account isolation,
   and calls `OrderService.submitOrder`.
2. **Command journal:** `OrderService` converts the `OrderRequest` into a `SubmitOrderCommand`,
   serializes it, and appends it to the `EventStore`.
3. **Risk check:** `RiskEngine` validates the order against per-account limits (cash,
   position, open notional, price collar, rate limit).
4. **Shard routing:** `ShardCoordinator` maps the symbol to an `EngineShard`.
5. **Matching:** `MatchingEngine` walks the opposite side of the `OrderBook` from the top,
   producing `Trade`s and updated `Order` copies.
6. **Post-trade:** `SettlementService` clears each trade, posts balanced ledger entries,
   and updates the portfolio.
7. **Market data:** `OrderService` publishes `BookUpdate` and `ExecutionEvent` messages.
8. **Metrics:** `MetricsService` records submitted/rejected/cancelled orders, trade count,
   and latency.

The hot path (matching engine, order book, market-data publisher) is intentionally
single-threaded per symbol shard. The `OrderService` serializes access per account for
risk updates, but each symbol shard can run independently. Fixed-point numerics
(`FixedPoint`, `FixedPointRiskEngine`, `FixedPointClearingService`) are now implemented as a
selectable, measured optimization on top of this baseline (see OPT-010/012/013 in
`docs/performance/OPTIMIZATIONS.md`); a fully lock-free, multi-shard matching architecture
remains a documented future-research direction (`docs/FUTURE_RESEARCH.md`), not yet built.

## Concurrency model

- `OrderService` uses a `ShardCoordinator` to route symbols to isolated `EngineShard`s.
- `AccountRiskState` is per-account and updated only by the thread that owns the order
  flow for that account. For the current single-shard baseline, all calls are serialized
  through the same service instance; multi-shard and multi-node work is future.
- `EventStore` is append-only and read during replay.
- `OrderBook` and `MatchingEngine` are not thread-safe by design; the caller must
  serialize access per symbol.

## Security

- API-key authentication is implemented as a servlet filter (`ApiKeyAuthenticationFilter`).
- Only `/api/v1/orders/**` requires a key; `/api/v1/order-books/**` and actuator endpoints
  are public.
- `OrderController` rejects cross-account submit/get/cancel requests.

## Observability

- Micrometer metrics under the `finex.*` namespace are exposed on `/actuator/prometheus`.
- JFR recordings can be captured via `ProfileRunner`.
- A sample Grafana dashboard is provisioned under `docker/grafana/dashboards/`.

## Replay and audit

Every submit/cancel command is persisted as an `Event`. A new `OrderService` instance can
read the event log via `ReplayEngine` and reconstruct the full trading state, including
order books, ledger, and portfolios. This forms the basis for restart, backtesting, and
audit.
