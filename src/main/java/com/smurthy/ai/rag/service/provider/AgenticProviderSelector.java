package com.smurthy.ai.rag.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agentic provider selector that uses LLM reasoning to intelligently choose
 * the best stock quote provider based on current conditions, rate limits, and context.
 */
@Service
public class AgenticProviderSelector {

    private static final Logger log = LoggerFactory.getLogger(AgenticProviderSelector.class);

    private final ChatClient chatClient;
    private final ProviderRateLimitTracker rateLimitTracker;

    @Value("${finance.agentic-selection.enabled:false}")
    private boolean agenticSelectionEnabled;

    public AgenticProviderSelector(
            ChatClient.Builder chatClientBuilder,
            ProviderRateLimitTracker rateLimitTracker) {
        this.chatClient = chatClientBuilder.build();
        this.rateLimitTracker = rateLimitTracker;
    }

    /**
     * Selects the best provider using LLM-based reasoning.
     * Falls back to simple priority-based selection if agentic selection is disabled.
     */
    public StockQuoteProvider selectProvider(List<StockQuoteProvider> availableProviders, String symbol, String context) {
        if (!agenticSelectionEnabled || availableProviders.isEmpty()) {
            log.debug("Agentic selection disabled or no providers available, using priority-based fallback");
            return selectByPriority(availableProviders);
        }

        try {
            String providerInfo = buildProviderInfo(availableProviders);
            String prompt = buildSelectionPrompt(providerInfo, symbol, context);

            log.debug("Invoking LLM for provider selection with {} available providers", availableProviders.size());

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            String selectedProviderName = parseProviderSelection(response);
            log.info("LLM selected provider: {} for symbol: {}", selectedProviderName, symbol);

            return availableProviders.stream()
                    .filter(p -> p.getProviderName().equalsIgnoreCase(selectedProviderName))
                    .findFirst()
                    .orElseGet(() -> {
                        log.warn("LLM selected unknown provider '{}', falling back to priority", selectedProviderName);
                        return selectByPriority(availableProviders);
                    });

        } catch (Exception e) {
            log.error("Error during agentic provider selection: {}", e.getMessage(), e);
            return selectByPriority(availableProviders);
        }
    }

    /**
     * Simple priority-based fallback selection.
     */
    private StockQuoteProvider selectByPriority(List<StockQuoteProvider> providers) {
        return providers.stream()
                .filter(StockQuoteProvider::isEnabled)
                .filter(StockQuoteProvider::isAvailable)
                .min((p1, p2) -> Integer.compare(p1.getPriority(), p2.getPriority()))
                .orElse(null);
    }

    /**
     * Builds detailed information about available providers including rate limit status.
     */
    private String buildProviderInfo(List<StockQuoteProvider> providers) {
        StringBuilder info = new StringBuilder();

        for (StockQuoteProvider provider : providers) {
            String providerName = provider.getProviderName();
            ProviderRateLimitTracker.ProviderUsageStats stats = rateLimitTracker.getStats(providerName);

            info.append(String.format("""

                Provider: %s
                - Priority: %d (lower is better)
                - Enabled: %b
                - Available: %b
                - Calls in last minute: %d
                - Calls in last hour: %d
                - Success rate: %.1f%%
                - Recently rate-limited: %b
                - Rate limit details:
                  * Alpha Vantage: 25 calls/day (very limited, expensive)
                  * Finnhub: 60 calls/minute = 3600/hour (MOST LIBERAL, FREE)
                  * Yahoo Finance: Unlimited but unreliable, web scraping
                  * Google Finance: Unlimited but stale data, last resort
                """,
                providerName,
                provider.getPriority(),
                provider.isEnabled(),
                provider.isAvailable(),
                stats.getCallsInLastMinute(),
                stats.getCallsInLastHour(),
                stats.getSuccessRate() * 100,
                stats.isRateLimitedRecently()
            ));
        }

        return info.toString();
    }

    /**
     * Builds the LLM prompt for provider selection.
     */
    private String buildSelectionPrompt(String providerInfo, String symbol, String context) {
        return String.format("""
            You are an intelligent API provider selector for stock quote services.
            Your goal is to choose the BEST provider based on current conditions.

            AVAILABLE PROVIDERS AND THEIR CURRENT STATUS:
            %s

            REQUEST DETAILS:
            - Symbol: %s
            - Context: %s

            SELECTION CRITERIA (in priority order):
            1. AVOID providers that are rate-limited or near their limits
            2. PREFER Finnhub for most requests (60 calls/min = most liberal free tier)
            3. RESERVE Alpha Vantage for critical requests only (only 25 calls/day)
            4. Use Yahoo/Google as fallbacks only if others are unavailable
            5. Consider success rates and recent failures

            IMPORTANT DECISION RULES:
            - Finnhub should be DEFAULT choice unless rate-limited
            - If Finnhub has made >50 calls in last minute, consider alternatives
            - Alpha Vantage should only be used if explicitly requested or all others fail
            - Yahoo/Google are last resorts due to reliability/data quality issues

            Respond with ONLY the provider name (e.g., "Finnhub", "Alpha Vantage", "Yahoo Finance", "Google Finance").
            No explanation, just the provider name.
            """,
            providerInfo,
            symbol,
            context != null ? context : "General quote request"
        );
    }

    /**
     * Parses the LLM response to extract the selected provider name.
     */
    private String parseProviderSelection(String response) {
        // Clean up response and extract provider name
        String cleaned = response.trim()
                .replace("\"", "")
                .replace("'", "")
                .replaceAll("[.!,]$", "");

        // Handle various response formats
        if (cleaned.toLowerCase().contains("finnhub")) {
            return "Finnhub";
        } else if (cleaned.toLowerCase().contains("alpha vantage")) {
            return "Alpha Vantage";
        } else if (cleaned.toLowerCase().contains("yahoo")) {
            return "Yahoo Finance";
        } else if (cleaned.toLowerCase().contains("google")) {
            return "Google Finance";
        }

        log.warn("Could not parse provider name from LLM response: {}", response);
        return cleaned; // Return as-is and hope for the best
    }

    /**
     * Utility method to get a simplified provider selection without LLM (for testing).
     */
    public StockQuoteProvider selectProviderSimple(List<StockQuoteProvider> providers) {
        // Prefer Finnhub by default if available and not rate-limited
        for (StockQuoteProvider provider : providers) {
            if (!provider.isEnabled() || !provider.isAvailable()) {
                continue;
            }

            String name = provider.getProviderName();
            ProviderRateLimitTracker.ProviderUsageStats stats = rateLimitTracker.getStats(name);

            // Finnhub is most liberal - use it unless rate-limited
            if ("Finnhub".equals(name) && !stats.isRateLimitedRecently() && stats.getCallsInLastMinute() < 55) {
                log.info("Selecting Finnhub (most liberal provider)");
                return provider;
            }
        }

        // Fallback to priority-based selection
        return selectByPriority(providers);
    }
}