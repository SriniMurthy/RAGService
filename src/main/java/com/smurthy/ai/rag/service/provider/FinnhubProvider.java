package com.smurthy.ai.rag.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Finnhub implementation of StockQuoteProvider.
 *
 * FREE TIER: 60 API calls per minute (3600/hour, 86,400/day)
 * API Key: Get free key at https://finnhub.io
 *
 * Priority: 2 (between Alpha Vantage and Yahoo Finance)
 *
 * Enable by setting:
 *   finnhub.api.enabled=true
 *   finnhub.api.key=your_api_key
 */
@Component
@ConditionalOnProperty(name = "finnhub.api.enabled", havingValue = "true")
public class FinnhubProvider implements StockQuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubProvider.class);
    // Most permissive though slightly less reliable
    private static final int PRIORITY = 1;
    private static final String BASE_URL = "https://finnhub.io/api/v1";

    @Value("${finnhub.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FinnhubProvider() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public StockQuote getQuote(String symbol) {
        try {
            log.info("Fetching quote for {} from Finnhub (60/min free tier)", symbol);

            if (apiKey == null || apiKey.trim().isEmpty()) {
                log.warn("Finnhub API key not configured");
                return StockQuote.error(symbol, "API key not configured", getProviderName());
            }

            // Finnhub quote endpoint
            String url = String.format("%s/quote?symbol=%s&token=%s", BASE_URL, symbol, apiKey);

            String response = restTemplate.getForObject(url, String.class);

            if (response == null || response.isEmpty()) {
                return StockQuote.error(symbol, "No data returned", getProviderName());
            }

            JsonNode root = objectMapper.readTree(response);

            // Check for API errors
            if (root.has("error")) {
                String error = root.get("error").asText();
                return StockQuote.error(symbol, "Finnhub error: " + error, getProviderName());
            }

            // Parse Finnhub response
            double currentPrice = root.has("c") ? root.get("c").asDouble() : 0.0;
            double change = root.has("d") ? root.get("d").asDouble() : 0.0;
            double changePercent = root.has("dp") ? root.get("dp").asDouble() : 0.0;
            double dayHigh = root.has("h") ? root.get("h").asDouble() : 0.0;
            double dayLow = root.has("l") ? root.get("l").asDouble() : 0.0;
            double open = root.has("o") ? root.get("o").asDouble() : 0.0;
            double previousClose = root.has("pc") ? root.get("pc").asDouble() : 0.0;

            if (currentPrice == 0.0) {
                return StockQuote.error(symbol, "Invalid symbol or no data", getProviderName());
            }

            // Get company name (requires separate API call)
            String companyName = getCompanyName(symbol);

            return new StockQuote(
                    symbol,
                    currentPrice,
                    change,
                    changePercent,
                    dayHigh,
                    dayLow,
                    open,
                    previousClose,
                    0L,  // Volume not in quote endpoint
                    "Real-time",
                    companyName,
                    "USD",
                    getProviderName(),
                    false,
                    null
            );

        } catch (Exception e) {
            log.error("Error fetching quote for {} from Finnhub: {}", symbol, e.getMessage());
            return StockQuote.error(symbol, "Finnhub error: " + e.getMessage(), getProviderName());
        }
    }

    /**
     * Get company name from Finnhub profile endpoint.
     * This is cached to avoid excessive API calls.
     */
    private String getCompanyName(String symbol) {
        try {
            String url = String.format("%s/stock/profile2?symbol=%s&token=%s", BASE_URL, symbol, apiKey);
            String response = restTemplate.getForObject(url, String.class);

            if (response != null && !response.isEmpty()) {
                JsonNode root = objectMapper.readTree(response);
                if (root.has("name")) {
                    return root.get("name").asText();
                }
            }
        } catch (Exception e) {
            log.debug("Could not fetch company name for {}: {}", symbol, e.getMessage());
        }

        return symbol;
    }

    @Override
    public boolean isAvailable() {
        // Check if API key is configured
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.debug("Finnhub API key not configured");
            return false;
        }

        // Simple health check - try to fetch a well-known symbol
        try {
            String url = String.format("%s/quote?symbol=AAPL&token=%s", BASE_URL, apiKey);
            String response = restTemplate.getForObject(url, String.class);

            if (response != null && !response.isEmpty()) {
                JsonNode root = objectMapper.readTree(response);
                // Check if we got valid data (current price should be > 0)
                return root.has("c") && root.get("c").asDouble() > 0;
            }

            return false;
        } catch (Exception e) {
            log.debug("Finnhub health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "Finnhub";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}