package com.finex.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.finex.clearing.ClearingResult;
import com.finex.clearing.ClearingService;
import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.Side;
import com.finex.ledger.DebitCredit;
import com.finex.ledger.Ledger;
import com.finex.ledger.LedgerEntry;
import com.finex.portfolio.PortfolioService;

/**
 * Orchestrates post-trade settlement: clearing computes net cash and fees, the ledger is
 * posted, and the portfolio is updated. This keeps the trade lifecycle explicit and makes
 * it easy to replay or audit.
 */
public class SettlementService {

    private final ClearingService clearingService;
    private final Ledger ledger;
    private final PortfolioService portfolioService;

    public SettlementService(ClearingService clearingService, Ledger ledger, PortfolioService portfolioService) {
        if (clearingService == null) {
            throw new IllegalArgumentException("clearingService must not be null");
        }
        if (ledger == null) {
            throw new IllegalArgumentException("ledger must not be null");
        }
        if (portfolioService == null) {
            throw new IllegalArgumentException("portfolioService must not be null");
        }
        this.clearingService = clearingService;
        this.ledger = ledger;
        this.portfolioService = portfolioService;
    }

    /**
     * Settles a trade: clear fees, post ledger, update portfolio, mark to market.
     *
     * @param trade     the matched trade
     * @param takerSide the side that removed liquidity (aggressor)
     * @param now       settlement timestamp
     * @param markPrice price used to revalue positions after the trade
     */
    public void settle(Trade trade, Side takerSide, Instant now, BigDecimal markPrice) {
        if (trade == null) {
            throw new IllegalArgumentException("trade must not be null");
        }
        if (takerSide == null) {
            throw new IllegalArgumentException("takerSide must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (markPrice == null || markPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("markPrice must be positive");
        }

        ClearingResult clearing = clearingService.clear(trade, takerSide);

        // Cash leg.
        List<LedgerEntry> cashEntries = new ArrayList<>(3);
        cashEntries.add(new LedgerEntry(0, now, cashAccount(trade.sellerAccountId()),
                clearing.sellerCashDelta().abs(), DebitCredit.DEBIT, "USD",
                "Trade " + trade.tradeId() + " cash received"));
        cashEntries.add(new LedgerEntry(0, now, cashAccount(trade.buyerAccountId()),
                clearing.buyerCashDelta().abs(), DebitCredit.CREDIT, "USD",
                "Trade " + trade.tradeId() + " cash paid"));
        if (clearing.feeAccrued().compareTo(BigDecimal.ZERO) > 0) {
            cashEntries.add(new LedgerEntry(0, now, "FEE.ACCRUAL",
                    clearing.feeAccrued(), DebitCredit.DEBIT, "USD",
                    "Trade " + trade.tradeId() + " fee accrual"));
        }
        ledger.post(cashEntries);

        // Asset leg.
        ledger.post(List.of(
                new LedgerEntry(0, now, assetAccount(trade.buyerAccountId(), trade.symbol()),
                        trade.quantity(), DebitCredit.DEBIT, trade.symbol(),
                        "Trade " + trade.tradeId() + " asset received"),
                new LedgerEntry(0, now, assetAccount(trade.sellerAccountId(), trade.symbol()),
                        trade.quantity(), DebitCredit.CREDIT, trade.symbol(),
                        "Trade " + trade.tradeId() + " asset delivered")));

        // Portfolio update.
        portfolioService.applyTrade(trade, markPrice);
        portfolioService.applyCashDelta(trade.buyerAccountId(), clearing.buyerCashDelta());
        portfolioService.applyCashDelta(trade.sellerAccountId(), clearing.sellerCashDelta());
        portfolioService.markToMarket(trade.symbol(), markPrice);
    }

    private static String cashAccount(long accountId) {
        return "CASH." + accountId;
    }

    private static String assetAccount(long accountId, String symbol) {
        return "ASSET." + symbol + "." + accountId;
    }
}
