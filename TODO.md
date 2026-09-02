# TODO — Active Task Queue

Only the current phase's atomic tasks live here in detail. See PROJECT_PLAN.md for the
full roadmap.

## Phase 1 — Repository Bootstrap (done)

- [x] Commit (`ac84774`)

## Phase 2 — Financial Domain Model (done)

- [x] Commit (`f975cb9`)

## Phase 3 — Correct Order Book (done)

- [x] Commit (`32a22ca`)

## Phase 4 — Matching Engine (done)

- [x] Create `finex-matching-engine` module
- [x] `MatchingEngine` + `MatchResult`
- [x] 15 unit tests (`MatchingEngineTest`)
- [x] ADR-004
- [ ] Commit Phase 4 work

## Phase 5 — REST/API Layer (next)

Goal: Order submit/cancel/query endpoints on top of the matching engine. Dependencies:
Phase 4 (done).

- [ ] Add `finex-matching-engine` dependency to `finex-api`
- [ ] `OrderService` / `OrderRequest` / `OrderResponse` DTOs in `finex-api`
- [ ] `OrderController`:
  - [ ] `POST /api/v1/orders` — submit
  - [ ] `DELETE /api/v1/orders/{orderId}` — cancel
  - [ ] `GET /api/v1/orders/{orderId}` — query (in-memory lookup for now)
- [ ] Keep a per-symbol `MatchingEngine` map in a simple `@Component`
- [ ] Validate incoming requests, map to `Order` domain objects
- [ ] Basic integration tests with `MockMvc` or `HttpClient`
- [ ] Update `README.md` with API examples
- [ ] Build + `mvn test` green
- [ ] Commit
