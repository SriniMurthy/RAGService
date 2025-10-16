package com.smurthy.ai.rag.service.provider;

import com.smurthy.ai.rag.service.RealTimeFinanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Alpha Vantage implementation of StockQuoteProvider.
 * Requires API key but provides real-time, reliable data.
 * Only enabled when finance.api.enabled=true
 */
@Component
@ConditionalOnProperty(name = "finance.api.enabled", havingValue = "true")
public class AlphaVantageProvider implements StockQuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageProvider.class);
    // Second-highest priority,  most reliable but most restrictive
    private static final int PRIORITY = 2;

    @Autowired
    private RealTimeFinanceService financeService;

    @Override
    public StockQuote getQuote(String symbol) {
        try {
            log.info("Fetching quote for {} from Alpha Vantage (real-time)", symbol);

            RealTimeFinanceService.StockQuote quote = financeService.getStockQuote(symbol);

            // Check if Alpha Vantage returned an error
            if ("ERROR".equals(quote.source())) {
                return StockQuote.error(symbol, "Alpha Vantage error", getProviderName());
            }

            return new StockQuote(
                    quote.symbol(),
                    quote.price(),
                    quote.change(),
                    quote.changePercent(),
                    quote.high(),
                    quote.low(),
                    0.0,  // Alpha Vantage doesn't provide 'open' in quote
                    0.0,  // Alpha Vantage doesn't provide 'previousClose' in quote
                    quote.volume(),
                    quote.tradingDay(),
                    quote.symbol(), // Alpha Vantage doesn't provide company name in quote
                    "USD",
                    getProviderName(),
                    false,
                    null
            );

        } catch (Exception e) {
            log.error("Error fetching quote for {} from Alpha Vantage: {}", symbol, e.getMessage());
            return StockQuote.error(symbol, "Alpha Vantage error: " + e.getMessage(), getProviderName());
        }
    }

    @Override
    public boolean isAvailable() {
        // Check if the service is configured and working
        if (financeService == null) {
            return false;
        }

        try {
            // Try a simple health check
            RealTimeFinanceService.StockQuote quote = financeService.getStockQuote("AAPL");
            return quote != null && !"ERROR".equals(quote.source());
        } catch (Exception e) {
            log.debug("Alpha Vantage health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "Alpha Vantage";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}