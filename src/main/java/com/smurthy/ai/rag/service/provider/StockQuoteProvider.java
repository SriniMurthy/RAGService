package com.smurthy.ai.rag.service.provider;

/**
 * Interface for stock quote providers.
 * Allows multiple implementations (Yahoo Finance, Alpha Vantage, etc.) with fallback capability.
 */
public interface StockQuoteProvider {

    /**
     * Get a delayed/real-time stock quote for a symbol.
     *
     * @param symbol Stock symbol (e.g., "AAPL", "TSLA")
     * @return StockQuote object with price, change, volume, etc.
     */
    StockQuote getQuote(String symbol);

    /**
     * Check if this provider is currently available/working.
     * Used for fallback logic.
     *
     * @return true if provider is healthy, false otherwise
     */
    boolean isAvailable();

    /**
     * Get the name of this provider (e.g., "Yahoo Finance", "Alpha Vantage")
     *
     * @return Provider name
     */
    String getProviderName();

    /**
     * Get the priority of this provider (lower number = higher priority)
     * Used to determine order of fallback attempts.
     *
     * @return Priority (1 = highest priority)
     */
    int getPriority();

    /**
     * Stock quote data structure
     */
    record StockQuote(
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
            String source,  // Provider name
            boolean isError,
            String errorMessage
    ) {
        /**
         * Create an error quote
         */
        public static StockQuote error(String symbol, String errorMessage, String provider) {
            return new StockQuote(
                    symbol, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L,
                    "N/A", "ERROR", "USD", provider, true, errorMessage
            );
        }
    }
}