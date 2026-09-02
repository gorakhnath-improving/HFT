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

- [x] Commit (`081d4ab`)

## Phase 5 — REST/API Layer (done)

- [x] Add `finex-matching-engine` dependency to `finex-api`
- [x] Expose `OrderBook.findOrder(long)`
- [x] `OrderService`, DTOs, `OrderController`
- [x] `OrderControllerTest` (7 tests) with `MockMvc`
- [x] Update `README.md`
- [x] Build + `mvn test` green
- [ ] Commit Phase 5 work

## Phase 6 — Risk Engine (next)

Goal: Pre-trade risk checks (size, notional, collar, position, exposure, rate limit).
Dependencies: Phase 2, 4, 5 (done).
