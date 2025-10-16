package com.smurthy.ai.rag.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Composite provider that tries multiple stock quote providers with automatic fallback.
 * Tries providers in priority order (1 = highest priority) until one succeeds.
 */
@Service
public class CompositeStockQuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(CompositeStockQuoteProvider.class);

    private final List<StockQuoteProvider> providers;

    public CompositeStockQuoteProvider(List<StockQuoteProvider> providers) {
        // Sort providers by priority (lower number = higher priority)
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(StockQuoteProvider::getPriority))
                .toList();

        log.info("📊 Initialized CompositeStockQuoteProvider with {} providers:", providers.size());
        for (StockQuoteProvider provider : this.providers) {
            log.info("  - {} (priority: {})", provider.getProviderName(), provider.getPriority());
        }
    }

    /**
     * Get stock quote by trying providers in priority order with automatic fallback.
     *
     * @param symbol Stock symbol
     * @return StockQuote from the first successful provider, or error quote if all fail
     */
    public StockQuoteProvider.StockQuote getQuote(String symbol) {
        log.info(" Fetching quote for {} using composite provider with {} providers", symbol, providers.size());

        for (StockQuoteProvider provider : providers) {
            try {
                log.debug("  Trying provider: {} (priority: {})", provider.getProviderName(), provider.getPriority());

                StockQuoteProvider.StockQuote quote = provider.getQuote(symbol);

                if (!quote.isError()) {
                    log.info("Successfully fetched {} from {}", symbol, provider.getProviderName());
                    return quote;
                } else {
                    log.warn("  {} failed: {}", provider.getProviderName(), quote.errorMessage());
                }

            } catch (Exception e) {
                log.error("  {} threw exception: {}", provider.getProviderName(), e.getMessage());
            }
        }

        // All providers failed
        log.error("All {} providers failed for symbol: {}", providers.size(), symbol);
        return StockQuoteProvider.StockQuote.error(
                symbol,
                "All providers failed. Tried: " + getProviderNames(),
                "Composite (all failed)"
        );
    }

    /**
     * Get a comma-separated list of provider names for error messages.
     */
    private String getProviderNames() {
        return providers.stream()
                .map(StockQuoteProvider::getProviderName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    /**
     * Check if at least one provider is available.
     */
    public boolean isAnyProviderAvailable() {
        for (StockQuoteProvider provider : providers) {
            if (provider.isAvailable()) {
                log.debug("Provider available: {}", provider.getProviderName());
                return true;
            }
        }
        log.warn("No providers are currently available");
        return false;
    }

    /**
     * Get list of all registered providers (for debugging/monitoring).
     */
    public List<StockQuoteProvider> getProviders() {
        return providers;
    }
}