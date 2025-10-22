package com.smurthy.ai.rag.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.jfr.DataAmount;
import lombok.Data;
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
 *   finance.finnhub.enabled=true
 *   finance.finnhub.key=your_api_key
 */
@Component
@Data
@ConditionalOnProperty(name = "finance.finnhub.enabled", havingValue = "true")
public class FinnhubProvider implements StockQuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubProvider.class);
    private static final String BASE_URL = "https://finnhub.io/api/v1";

    @Value("${finance.finnhub.key:}")
    private String apiKey;

    @Value("${finance.finnhub.enabled:false}")
    private boolean enabled;

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

    @Override
    public boolean isAvailable() {
        return true;
    }

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
    public String getProviderName() {
        return "Finnhub";
    }



    @Value("${finance.finnhub.priority:10}")
    private int priority;

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}
