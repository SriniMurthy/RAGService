package com.smurthy.ai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Real-time finance data service using Alpha Vantage API
 *
 * Alternative APIs you can use:
 * - Alpha Vantage: https://www.alphavantage.co (Free tier: 25 requests/day)
 * - Finnhub: https://finnhub.io (Free tier: 60 calls/minute)
 * - Yahoo Finance (unofficial): https://github.com/sstrickx/yahoofinance-api
 * - Polygon.io: https://polygon.io (Free tier: 5 API calls/minute)
 */
@Service
public class RealTimeFinanceService {

    private static final Logger log = LoggerFactory.getLogger(RealTimeFinanceService.class);

    private final RestClient restClient;
    private final String apiKey;
    private final boolean isEnabled;

    public RealTimeFinanceService(
            @Value("${finance.api.key:demo}") String apiKey,
            @Value("${finance.api.enabled:false}") boolean isEnabled,
            @Value("${finance.api.base-url:https://www.alphavantage.co}") String baseUrl) {

        this.apiKey = apiKey;
        this.isEnabled = isEnabled;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        if (isEnabled && "demo".equals(apiKey)) {
            log.warn("⚠️  Finance API is enabled but using DEMO key. Get your free key at: https://www.alphavantage.co/support/#api-key");
        }
    }

    /**
     * Get real-time stock quote for a symbol
     */
    public StockQuote getStockQuote(String symbol) {
        if (!isEnabled) {
            log.info("Finance API disabled - returning mock data for {}", symbol);
            return createMockQuote(symbol);
        }

        try {
            log.info("🌐 Fetching real-time quote for {} from Alpha Vantage", symbol);

            // Alpha Vantage Global Quote endpoint
            String endpoint = "/query?function=GLOBAL_QUOTE&symbol={symbol}&apikey={apiKey}";

            Map<String, Object> response = restClient.get()
                    .uri(endpoint, symbol, apiKey)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("Global Quote")) {
                log.warn("Invalid response from Alpha Vantage for {}: {}", symbol, response);
                return createMockQuote(symbol);
            }

            Map<String, String> quote = (Map<String, String>) response.get("Global Quote");

            return new StockQuote(
                    symbol,
                    parseDouble(quote.get("05. price")),
                    parseDouble(quote.get("09. change")),
                    parseDouble(quote.get("10. change percent").replace("%", "")),
                    parseDouble(quote.get("03. high")),
                    parseDouble(quote.get("04. low")),
                    parseLong(quote.get("06. volume")),
                    quote.get("07. latest trading day"),
                    "REAL-TIME"
                );

        } catch (RestClientException e) {
            log.error("Error fetching quote for {}: {}", symbol, e.getMessage());
            return createMockQuote(symbol);
        }
    }

    /**
     * Get company overview (fundamentals)
     */
    public CompanyOverview getCompanyOverview(String symbol) {
        if (!isEnabled) {
            return createMockOverview(symbol);
        }

        try {
            log.info("🌐 Fetching company overview for {} from Alpha Vantage", symbol);

            String endpoint = "/query?function=OVERVIEW&symbol={symbol}&apikey={apiKey}";

            Map<String, String> response = restClient.get()
                    .uri(endpoint, symbol, apiKey)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.isEmpty()) {
                return createMockOverview(symbol);
            }

            return new CompanyOverview(
                    symbol,
                    response.get("Name"),
                    response.get("Description"),
                    response.get("Exchange"),
                    response.get("Sector"),
                    response.get("Industry"),
                    response.get("Country"),
                    parseDouble(response.get("MarketCapitalization")),
                    parseDouble(response.get("PERatio")),
                    parseDouble(response.get("EPS")),
                    parseDouble(response.get("DividendYield"))
            );

        } catch (RestClientException e) {
            log.error("Error fetching overview for {}: {}", symbol, e.getMessage());
            return createMockOverview(symbol);
        }
    }

    // ===== Helper Methods =====

    private double parseDouble(String value) {
        try {
            return value != null ? Double.parseDouble(value) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private long parseLong(String value) {
        try {
            return value != null ? Long.parseLong(value) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private StockQuote createMockQuote(String symbol) {
        // Mock data for testing without API key
        double basePrice = 150.0 + (symbol.hashCode() % 100);
        return new StockQuote(
                symbol,
                basePrice,
                2.5,
                1.7,
                basePrice + 3.0,
                basePrice - 2.0,
                50_000_000L,
                "2025-10-09",
                "MOCK"
        );
    }

    private CompanyOverview createMockOverview(String symbol) {
        return new CompanyOverview(
                symbol,
                symbol + " Corporation",
                "A leading technology company focused on innovation and growth.",
                "NASDAQ",
                "Technology",
                "Software",
                "United States",
                500_000_000_000.0,
                25.5,
                6.50,
                1.2
        );
    }

    // ===== Response Records =====

    public record StockQuote(
            String symbol,
            double price,
            double change,
            double changePercent,
            double high,
            double low,
            long volume,
            String tradingDay,
            String source  // REAL-TIME or MOCK
    ) {}

    public record CompanyOverview(
            String symbol,
            String name,
            String description,
            String exchange,
            String sector,
            String industry,
            String country,
            double marketCap,
            double peRatio,
            double eps,
            double dividendYield
    ) {}
}