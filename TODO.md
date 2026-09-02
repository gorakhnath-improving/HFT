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

- [x] Commit (`27a7ab1`)

## Phase 6 — Risk Engine (done)

- [x] New `finex-risk` module
- [x] `RiskEngine`, `RiskConfig`, `AccountRiskState`, `RiskResult`
- [x] `RiskEngineTest`
- [x] Integrate into `OrderService` / `OrderController`
- [x] Update `OrderControllerTest` with risk rejection cases
- [x] Update `README.md`
- [x] Build + `mvn test` green
- [ ] Commit Phase 6 work

## Phase 7 — Market Data (next)

Goal: BOOK_UPDATE/TRADE/EXECUTION events, snapshot + incremental, async publication.
Dependencies: Phase 4, 5, 6 (done).
