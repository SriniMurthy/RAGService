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
     * Check if the provider is configured enabled in the system
     * @return
     */
    boolean isEnabled();

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
            String provider,  // Provider name
            boolean isError,
            String errorMessage) {

        /**
         * Create an error quote
         */
        public static StockQuote error(String symbol, String errorMessage, String provider) {
            return new StockQuote(
                    symbol, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L,
                    "N/A", "ERROR", "USD", provider, true, errorMessage
            );
        }

        /**
         * Returns a new builder for creating a StockQuote instance.
         */
        public static StockQuoteBuilder builder() {
            return new StockQuoteBuilder();
        }

        /**
         * A fluent builder for creating StockQuote objects safely from various data sources.
         * It handles parsing strings to numbers and provides sensible defaults.
         */
        public static class StockQuoteBuilder {
            private String symbol;
            private double price;
            private double change;
            private double changePercent;
            private double dayHigh;
            private double dayLow;
            private double open;
            private double previousClose;
            private long volume;
            private String lastTradeTime;
            private String companyName;
            private String currency;
            private String provider;
            private boolean isError = false;
            private String errorMessage = "";

            public StockQuoteBuilder symbol(String symbol) { this.symbol = symbol; return this; }
            public StockQuoteBuilder companyName(String companyName) { this.companyName = companyName; return this; }
            public StockQuoteBuilder lastTradeTime(String lastTradeTime) { this.lastTradeTime = lastTradeTime; return this; }
            public StockQuoteBuilder currency(String currency) { this.currency = currency; return this; }
            public StockQuoteBuilder provider(String provider) { this.provider = provider; return this; }
            public StockQuoteBuilder isError(boolean isError) { this.isError = isError; return this; }
            public StockQuoteBuilder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

            // --- Safe parsing setters ---
            public StockQuoteBuilder price(String price) { this.price = safeParseDouble(price); return this; }
            public StockQuoteBuilder change(String change) { this.change = safeParseDouble(change); return this; }
            public StockQuoteBuilder changePercent(String changePercent) {
                if (changePercent != null) {
                    this.changePercent = safeParseDouble(changePercent.replace("%", ""));
                }
                return this;
            }
            public StockQuoteBuilder dayHigh(String dayHigh) { this.dayHigh = safeParseDouble(dayHigh); return this; }
            public StockQuoteBuilder dayLow(String dayLow) { this.dayLow = safeParseDouble(dayLow); return this; }
            public StockQuoteBuilder open(String open) { this.open = safeParseDouble(open); return this; }
            public StockQuoteBuilder previousClose(String previousClose) { this.previousClose = safeParseDouble(previousClose); return this; }
            public StockQuoteBuilder volume(String volume) { this.volume = safeParseLong(volume); return this; }

            public StockQuote build() {
                return new StockQuote(symbol, price, change, changePercent, dayHigh, dayLow, open, previousClose, volume, lastTradeTime, companyName, currency, provider, isError, errorMessage);
            }

            private double safeParseDouble(String value) {
                if (value == null || value.isBlank() || value.equalsIgnoreCase("N/A")) return 0.0;
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            }

            private long safeParseLong(String value) {
                if (value == null || value.isBlank() || value.equalsIgnoreCase("N/A")) return 0L;
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    return 0L;
                }
            }
        }
    }
}
