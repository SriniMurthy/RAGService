package com.smurthy.ai.rag.service.provider;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Yahoo Finance implementation of StockQuoteProvider.
 * FREE, UNLIMITED, NO API KEY - but may have network issues due to Yahoo blocking.
 */
@Component
@Data
public class YahooFinanceProvider implements StockQuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceProvider.class);

    @Value("${finance.yahoo.priority:100}")
    private  int priority; // Lower priority due to reliability issues

    @Value("${finance.yahoo.enabled:true}")
    private boolean enabled;

    @Override
    public StockQuote getQuote(String symbol) {
        try {
            log.info("Fetching quote for {} from Yahoo Finance (free, unlimited)", symbol);

            Stock stock = YahooFinance.get(symbol);

            if (stock == null || stock.getQuote() == null) {
                log.warn("No data found for symbol: {}", symbol);
                return StockQuote.error(symbol, "Symbol not found", getProviderName());
            }

            var quote = stock.getQuote();

            return new StockQuote(
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
                    getProviderName(),
                    false,
                    null
            );

        } catch (IOException e) {
            log.error("Error fetching quote for {} from Yahoo Finance: {}", symbol, e.getMessage());
            return StockQuote.error(symbol, "Network error: " + e.getMessage(), getProviderName());
        }
    }

    @Override
    public boolean isAvailable() {
        // Simple health check - try to fetch a well-known symbol
        try {
            Stock stock = YahooFinance.get("AAPL");
            return stock != null && stock.getQuote() != null;
        } catch (Exception e) {
            log.debug("Yahoo Finance health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "Yahoo Finance";
    }

    private double safeDecimal(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private long safeLong(Long value) {
        return value != null ? value : 0L;
    }
}