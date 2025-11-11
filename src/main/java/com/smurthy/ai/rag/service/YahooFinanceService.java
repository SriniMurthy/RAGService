package com.smurthy.ai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Yahoo Finance integration service
 *
 * Benefits:
 * - NO API KEY REQUIRED
 * - UNLIMITED REQUESTS (no daily limits)
 * - 15-20 minute delayed quotes (real data)
 * - Historical data access
 * - Multiple stock symbols in one call
 *
 * Perfect for testing and development!
 */
@Service
public class YahooFinanceService {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceService.class);

    /**
     * Get delayed quote for a stock symbol (15-20 minute delay)
     * No API key required, unlimited requests
     */
    public DelayedQuote getDelayedQuote(String symbol) {
        try {
            log.info(" Fetching delayed quote for {} from Yahoo Finance (free, unlimited)", symbol);

            Stock stock = YahooFinance.get(symbol);

            if (stock == null || stock.getQuote() == null) {
                log.warn("No data found for symbol: {}", symbol);
                return createErrorQuote(symbol, "Symbol not found");
            }

            var quote = stock.getQuote();

            return new DelayedQuote(
                    symbol,
                    safeDecimal(quote.getPrice()),
                    safeDecimal(quote.getChange()),
                    safeDecimal(quote.getChangeInPercent()),
                    safeDecimal(quote.getDayHigh()),
                    safeDecimal(quote.getDayLow()),
                    safeDecimal(quote.getOpen()),
                    safeDecimal(quote.getPreviousClose()),
                    safeLong(quote.getVolume()),
                    quote.getLastTradeTime() != null ? quote.getLastTradeTime().toString() : "N/A",
                    stock.getName() != null ? stock.getName() : symbol,
                    stock.getCurrency() != null ? stock.getCurrency() : "USD",
                    "DELAYED_15MIN"
            );

        } catch (IOException e) {
            log.error("Error fetching quote for {}: {}", symbol, e.getMessage());
            return createErrorQuote(symbol, "Network error: " + e.getMessage());
        }
    }

    /**
     * Get historical stock prices for a date range
     * Perfect for trend analysis and backtesting
     */
    public HistoricalData getHistoricalData(String symbol, LocalDate from, LocalDate to) {
        try {
            log.info("Fetching historical data for {} from {} to {}", symbol, from, to);

            Stock stock = YahooFinance.get(symbol);

            Calendar calFrom = Calendar.getInstance();
            calFrom.setTime(java.util.Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant()));

            Calendar calTo = Calendar.getInstance();
            calTo.setTime(java.util.Date.from(to.atStartOfDay(ZoneId.systemDefault()).toInstant()));

            List<HistoricalQuote> history = stock.getHistory(calFrom, calTo, Interval.DAILY);

            if (history == null || history.isEmpty()) {
                return new HistoricalData(symbol, from.toString(), to.toString(), List.of(), "No data available");
            }

            List<HistoricalPrice> prices = history.stream()
                    .map(h -> new HistoricalPrice(
                            h.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                            safeDecimal(h.getOpen()),
                            safeDecimal(h.getHigh()),
                            safeDecimal(h.getLow()),
                            safeDecimal(h.getClose()),
                            safeDecimal(h.getAdjClose()),
                            safeLong(h.getVolume())
                    ))
                    .collect(Collectors.toList());

            String summary = String.format("Retrieved %d trading days. Range: $%.2f - $%.2f",
                    prices.size(),
                    prices.stream().mapToDouble(HistoricalPrice::low).min().orElse(0.0),
                    prices.stream().mapToDouble(HistoricalPrice::high).max().orElse(0.0)
            );

            return new HistoricalData(symbol, from.toString(), to.toString(), prices, summary);

        } catch (IOException e) {
            log.error("Error fetching historical data for {}: {}", symbol, e.getMessage());
            return new HistoricalData(symbol, from.toString(), to.toString(), List.of(),
                    "Error: " + e.getMessage());
        }
    }

    /**
     * Get stock statistics (P/E ratio, market cap, etc.)
     */
    public StockStats getStockStats(String symbol) {
        try {
            log.info(" Fetching statistics for {}", symbol);

            Stock stock = YahooFinance.get(symbol, true); // true = include stats

            if (stock == null || stock.getStats() == null) {
                return new StockStats(symbol, 0, 0, 0, 0, 0, 0, "Stats not available");
            }

            var stats = stock.getStats();
            var quote = stock.getQuote();

            return new StockStats(
                    symbol,
                    safeDecimal(stats.getMarketCap()),
                    safeDecimal(stats.getPe()),
                    safeDecimal(stats.getEps()),
                    safeDecimal(quote.getYearHigh()),
                    safeDecimal(quote.getYearLow()),
                    safeDecimal(BigDecimal.valueOf(quote.getAvgVolume())),
                    String.format("Market Cap: $%.2fB, P/E: %.2f",
                            safeDecimal(stats.getMarketCap()) / 1_000_000_000.0,
                            safeDecimal(stats.getPe()))
            );

        } catch (IOException e) {
            log.error("Error fetching stats for {}: {}", symbol, e.getMessage());
            return new StockStats(symbol, 0, 0, 0, 0, 0, 0, "Error: " + e.getMessage());
        }
    }

    /**
     * Compare multiple stocks side-by-side
     */
    public MultiStockComparison compareStocks(List<String> symbols) {
        log.info(" Comparing stocks: {}", symbols);

        List<DelayedQuote> quotes = symbols.stream()
                .map(this::getDelayedQuote)
                .collect(Collectors.toList());

        // Find best performer
        String bestPerformer = quotes.stream()
                .max((a, b) -> Double.compare(a.changePercent(), b.changePercent()))
                .map(DelayedQuote::symbol)
                .orElse("N/A");

        String analysis = String.format(
                "Compared %d stocks. Best performer today: %s (%.2f%%)",
                quotes.size(),
                bestPerformer,
                quotes.stream()
                        .filter(q -> q.symbol().equals(bestPerformer))
                        .findFirst()
                        .map(DelayedQuote::changePercent)
                        .orElse(0.0)
        );

        return new MultiStockComparison(symbols, quotes, bestPerformer, analysis);
    }

    // ===== Helper Methods =====

    private double safeDecimal(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private long safeLong(Long value) {
        return value != null ? value : 0L;
    }

    private DelayedQuote createErrorQuote(String symbol, String error) {
        return new DelayedQuote(
                symbol, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L,
                "N/A", error, "USD", "ERROR"
        );
    }

    // ===== Response Records =====

    public record DelayedQuote(
            String symbol,
            double price,
            double change,
            double changePercent,
            double dayHigh,
            double dayLow,
            double open,
            double previousClose,
            long volume,
            String lastTradeTime,
            String companyName,
            String currency,
            String source  // DELAYED_15MIN or ERROR
    ) {}

    public record HistoricalData(
            String symbol,
            String fromDate,
            String toDate,
            List<HistoricalPrice> prices,
            String summary
    ) {}

    public record HistoricalPrice(
            String date,
            double open,
            double high,
            double low,
            double close,
            double adjClose,
            long volume
    ) {}

    public record StockStats(
            String symbol,
            double marketCap,
            double peRatio,
            double eps,
            double yearHigh,
            double yearLow,
            double avgVolume,
            String summary
    ) {}

    public record MultiStockComparison(
            List<String> symbols,
            List<DelayedQuote> quotes,
            String bestPerformer,
            String analysis
    ) {}

    /**
     * Get market movers (top gainers and losers)
     * NOTE: Yahoo Finance removed free API access to screener data.
     * This function is currently disabled to prevent returning misleading mock data.
     * For production, implement web scraping or use Finnhub/Polygon.io/Alpha Vantage API.
     */
    public MarketMovers getMarketMovers(String market, int limit) {
        log.warn("getMarketMovers called but returning ERROR - Yahoo screener requires paid subscription");

        String errorMessage = String.format(
                "ERROR: Market movers data is not available. Yahoo Finance discontinued free screener API access. " +
                "To get market movers, either:\n" +
                "1. Use getMarketNews tool with query '%s top gainers and losers today'\n" +
                "2. Implement paid API (Finnhub, Polygon.io, Alpha Vantage)\n" +
                "3. Implement web scraping solution\n" +
                "DO NOT make up or guess market movement data.",
                market
        );

        return new MarketMovers(
                market,
                List.of(), // Empty gainers list
                List.of(), // Empty losers list
                errorMessage
        );
    }

    public record MarketMovers(
            String market,
            List<Mover> topGainers,
            List<Mover> topLosers,
            String summary
    ) {}

    public record Mover(
            String symbol,
            String name,
            double price,
            double changePercent,
            long volume
    ) {}
}