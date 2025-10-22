package com.smurthy.ai.rag.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A composite provider that intelligently selects the best stock quote provider
 * using either agentic LLM-based selection or traditional priority-based fallback.
 */
@Service
public class CompositeStockQuoteProvider implements StockQuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(CompositeStockQuoteProvider.class);
    private final List<StockQuoteProvider> providers;
    private final ProviderRateLimitTracker rateLimitTracker;
    private final AgenticProviderSelector agenticSelector;

    @Value("${finance.agentic-selection.enabled:false}")
    private boolean agenticSelectionEnabled;

    @Value("${finance.agentic-selection.fallback-on-failure:true}")
    private boolean fallbackOnFailure;

    /**
     * Injects all available beans that implement the StockQuoteProvider interface
     * and sorts them based on their defined priority.
     */
    public CompositeStockQuoteProvider(
            List<StockQuoteProvider> providers,
            ProviderRateLimitTracker rateLimitTracker,
            @Autowired(required = false) AgenticProviderSelector agenticSelector) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(StockQuoteProvider::getPriority))
                .toList();
        this.rateLimitTracker = rateLimitTracker;
        this.agenticSelector = agenticSelector;
        log.info("Initialized CompositeStockQuoteProvider with {} providers in order: {}",
                this.providers.size(), this.providers.stream().map(StockQuoteProvider::getProviderName).toList());
        log.info("Agentic selection: {}", agenticSelector != null && agenticSelectionEnabled ? "ENABLED" : "DISABLED");
    }

    @Override
    public StockQuote getQuote(String symbol) {
        return getQuote(symbol, null);
    }

    /**
     * Gets a stock quote with optional context for agentic selection.
     *
     * @param symbol The stock symbol
     * @param context Optional context for intelligent provider selection (e.g., "real-time needed", "bulk request")
     * @return StockQuote
     */
    public StockQuote getQuote(String symbol, String context) {
        log.debug("Fetching quote for '{}' using {} selection",
            symbol,
            (agenticSelector != null && agenticSelectionEnabled) ? "AGENTIC" : "priority-based");

        // AGENTIC SELECTION PATH
        if (agenticSelector != null && agenticSelectionEnabled) {
            List<StockQuoteProvider> availableProviders = getAvailableProviders();

            if (!availableProviders.isEmpty()) {
                StockQuoteProvider selectedProvider = agenticSelector.selectProvider(availableProviders, symbol, context);

                if (selectedProvider != null) {
                    log.info("Agentic selector chose: {} for symbol: {}", selectedProvider.getProviderName(), symbol);
                    StockQuote result = tryProvider(selectedProvider, symbol);

                    if (result != null && !result.isError()) {
                        return result;
                    }

                    log.warn("Agentic selection failed, falling back to sequential retry");
                }
            }
        }

        // TRADITIONAL FALLBACK PATH - Try all providers in priority order
        return getQuoteWithFallback(symbol);
    }

    /**
     * Traditional sequential fallback approach.
     */
    private StockQuote getQuoteWithFallback(String symbol) {
        log.debug("Using traditional fallback for symbol '{}'", symbol);

        for (StockQuoteProvider provider : providers) {
            if (!provider.isEnabled() || !provider.isAvailable()) {
                log.debug("Skipping disabled/unavailable provider: {}", provider.getProviderName());
                continue;
            }

            StockQuote result = tryProvider(provider, symbol);
            if (result != null && !result.isError()) {
                return result;
            }
        }

        // All providers failed
        throw new RuntimeException("All stock quote providers failed to fetch a quote for symbol: " + symbol);
    }

    /**
     * Tries a specific provider and tracks the result.
     */
    private StockQuote tryProvider(StockQuoteProvider provider, String symbol) {
        String providerName = provider.getProviderName();

        try {
            log.debug("Trying provider: {} for symbol: {}", providerName, symbol);
            StockQuote quote = provider.getQuote(symbol);

            if (quote != null && quote.price() > 0 && !quote.isError()) {
                log.info("SUCCESS: {} returned quote for '{}': ${}", providerName, symbol, quote.price());
                rateLimitTracker.recordSuccess(providerName);
                return quote;
            } else if (quote != null && quote.isError()) {
                String error = quote.errorMessage();
                log.warn("FAILURE: {} returned error for '{}': {}", providerName, symbol, error);

                // Detect rate limit errors
                if (isRateLimitError(error)) {
                    rateLimitTracker.recordRateLimit(providerName);
                } else {
                    rateLimitTracker.recordFailure(providerName, error);
                }
                return null; // Try next provider
            } else {
                log.warn("INVALID: {} returned invalid/empty quote for '{}'", providerName, symbol);
                rateLimitTracker.recordFailure(providerName, "Invalid response");
                return null;
            }

        } catch (Exception e) {
            log.error("EXCEPTION: {} threw error for '{}': {}", providerName, symbol, e.getMessage());

            // Detect rate limit exceptions
            if (isRateLimitError(e.getMessage())) {
                rateLimitTracker.recordRateLimit(providerName);
            } else {
                rateLimitTracker.recordFailure(providerName, e.getMessage());
            }
            return null;
        }
    }

    /**
     * Gets list of currently available providers.
     */
    private List<StockQuoteProvider> getAvailableProviders() {
        return providers.stream()
                .filter(StockQuoteProvider::isEnabled)
                .filter(StockQuoteProvider::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Detects if an error message indicates a rate limit issue.
     */
    private boolean isRateLimitError(String errorMessage) {
        if (errorMessage == null) return false;
        String lower = errorMessage.toLowerCase();
        return lower.contains("rate limit")
            || lower.contains("too many requests")
            || lower.contains("429")
            || lower.contains("quota exceeded")
            || lower.contains("limit reached");
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "Composite";
    }

    // The composite provider itself doesn't have a priority or enabled status.
    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // This method is for inspection and testing purposes.
    public List<StockQuoteProvider> getProviders() {
        return this.providers;
    }
}