# Financial Model

FinEx models a spot-style exchange: buyers pay cash and receive the asset; sellers deliver
the asset and receive cash. All amounts use `BigDecimal` with exact decimal arithmetic.

## Order lifecycle

1. **NEW** — `OrderService` creates an `Order` after `RiskEngine` validation.
2. **OPEN** — a limit order that has not matched rests in the `OrderBook`.
3. **PARTIALLY_FILLED** — some quantity matched; remainder rests in the book.
4. **FILLED** — all quantity traded.
5. **CANCELLED** — removed from the book before being fully filled.
6. **REJECTED** — failed risk or validation checks; never reaches the book.

## Pre-trade risk

`RiskEngine` checks the following before an order is accepted:

| Limit | Default | Checked for |
|-------|---------|-------------|
| Max order quantity | 1000 | both sides |
| Max order notional | 500000 | both sides |
| Max position | 100 | both sides (long/short absolute) |
| Max cash exposure | 500000 | BUY total open notional |
| Initial cash | 1000000 | BUY available cash |
| Price collar | 10% | LIMIT vs last trade price |
| Max orders/second | 10 | per account |

Risk state is per-account and includes reserved cash for open BUY orders and reserved
positions for open SELL orders.

## Clearing

For each trade `ClearingService` computes:

- `notional = price * quantity`
- `takerFee = notional * takerRate` and `makerFee = notional * makerRate`
- `buyerCashDelta = -(notional + buyerFee)`
- `sellerCashDelta = notional - sellerFee`
- `feeAccrued = buyerFee + sellerFee`

The aggressor side is the taker and pays the taker rate; the resting side is the maker.
Default rates are 0.1% taker / 0.0% maker.

## Settlement / ledger

`SettlementService` posts a balanced double-entry ledger for every trade:

- **Cash leg**
  - Debit `CASH.<sellerAccount>` by `notional - sellerFee`
  - Credit `CASH.<buyerAccount>` by `notional + buyerFee`
  - Credit `FEE.ACCRUAL` by total fees
- **Asset leg**
  - Debit `ASSET.<symbol>.<buyerAccount>` by `quantity`
  - Credit `ASSET.<symbol>.<sellerAccount>` by `quantity`

The `InMemoryLedger` asserts `sum(debits) == sum(credits)` for every posting batch.

## Portfolio

`PortfolioService` tracks per-account cash and signed positions.

- `Position` records `quantity`, `averagePrice`, `realizedPnl`, and `unrealizedPnl`.
- `realizedPnl` is updated when a trade reduces the position and crosses the average price.
- `unrealizedPnl` is recomputed on `markToMarket` using the last trade price:
  `(markPrice - averagePrice) * quantity` for longs, negated for shorts.
- `totalEquity = cash + sum(unrealizedPnl across positions)`.

## Replay

All submit/cancel commands are appended to the `EventStore`. A fresh `OrderService` can
replay the log and reconstruct identical order books, ledger balances, and portfolios.
This makes the system auditable and restartable.
