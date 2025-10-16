package com.smurthy.ai.rag.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Google Finance implementation of StockQuoteProvider.
 * Uses Google Finance unofficial API - FREE, NO API KEY, UNLIMITED requests.
 *
 * This is a fallback provider when both Alpha Vantage and Yahoo Finance fail.
 * Priority: 3 (lowest - unofficial API, may break)
 */
@Component
public class GoogleFinanceProvider implements StockQuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleFinanceProvider.class);
    private static final int PRIORITY = 3; // Lowest priority - unofficial API

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GoogleFinanceProvider() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public StockQuote getQuote(String symbol) {
        try {
            log.info("📊 Fetching quote for {} from Google Finance (unofficial API, free)", symbol);

            // Google Finance URL - uses their internal API endpoint
            // Format: https://www.google.com/finance/quote/{SYMBOL}:{EXCHANGE}
            // We'll try NASDAQ first, then NYSE as fallback
            String url = String.format("https://www.google.com/finance/quote/%s:NASDAQ", symbol);

            String html = restTemplate.getForObject(url, String.class);

            if (html == null || html.isEmpty()) {
                log.warn("No data returned from Google Finance for symbol: {}", symbol);
                return StockQuote.error(symbol, "No data returned", getProviderName());
            }

            // Parse the HTML to extract stock data
            // Google embeds JSON data in the page
            StockQuote quote = parseGoogleFinanceHTML(symbol, html);

            if (quote.isError()) {
                // Try NYSE if NASDAQ failed
                log.debug("NASDAQ failed, trying NYSE for symbol: {}", symbol);
                url = String.format("https://www.google.com/finance/quote/%s:NYSE", symbol);
                html = restTemplate.getForObject(url, String.class);
                quote = parseGoogleFinanceHTML(symbol, html);
            }

            return quote;

        } catch (Exception e) {
            log.error("Error fetching quote for {} from Google Finance: {}", symbol, e.getMessage());
            return StockQuote.error(symbol, "Google Finance error: " + e.getMessage(), getProviderName());
        }
    }

    /**
     * Parse Google Finance HTML to extract stock quote data.
     * Google embeds stock data in various formats within the HTML.
     */
    private StockQuote parseGoogleFinanceHTML(String symbol, String html) {
        try {
            // Method 1: Try to extract from meta tags and data attributes
            double price = extractPrice(html);
            double change = extractChange(html);
            double changePercent = extractChangePercent(html);
            double dayHigh = extractDayHigh(html);
            double dayLow = extractDayLow(html);
            String companyName = extractCompanyName(html, symbol);

            if (price == 0.0) {
                return StockQuote.error(symbol, "Could not parse price from Google Finance", getProviderName());
            }

            return new StockQuote(
                    symbol,
                    price,
                    change,
                    changePercent,
                    dayHigh,
                    dayLow,
                    0.0,  // open - not easily available
                    0.0,  // previousClose - not easily available
                    0L,   // volume - not easily available
                    "Real-time",
                    companyName,
                    "USD",
                    getProviderName(),
                    false,
                    null
            );

        } catch (Exception e) {
            log.error("Error parsing Google Finance HTML for {}: {}", symbol, e.getMessage());
            return StockQuote.error(symbol, "Parse error: " + e.getMessage(), getProviderName());
        }
    }

    /**
     * Extract stock price from Google Finance HTML.
     * Google uses various class names and data attributes.
     */
    private double extractPrice(String html) {
        try {
            // Look for price in data-last-price attribute
            String pricePattern = "data-last-price=\"([0-9.]+)\"";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(pricePattern);
            java.util.regex.Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }

            // Fallback: Look for price in YMlKec fxKbKc class (price div)
            pricePattern = "<div[^>]*class=\"[^\"]*YMlKec[^\"]*\"[^>]*>\\s*\\$?([0-9,]+\\.?[0-9]*)";
            pattern = java.util.regex.Pattern.compile(pricePattern);
            matcher = pattern.matcher(html);

            if (matcher.find()) {
                String priceStr = matcher.group(1).replace(",", "");
                return Double.parseDouble(priceStr);
            }

            return 0.0;
        } catch (Exception e) {
            log.debug("Error extracting price: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Extract price change from Google Finance HTML.
     */
    private double extractChange(String html) {
        try {
            // Look for change in data-last-change attribute or in HTML
            String changePattern = "data-last-change=\"([\\-0-9.]+)\"";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(changePattern);
            java.util.regex.Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }

            return 0.0;
        } catch (Exception e) {
            log.debug("Error extracting change: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Extract percent change from Google Finance HTML.
     */
    private double extractChangePercent(String html) {
        try {
            // Look for percent change
            String percentPattern = "([\\-0-9.]+)%";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(percentPattern);
            java.util.regex.Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }

            return 0.0;
        } catch (Exception e) {
            log.debug("Error extracting change percent: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Extract day high from Google Finance HTML.
     */
    private double extractDayHigh(String html) {
        try {
            // Look for "Day's high" label and extract value
            String highPattern = "High[^<]*</div>\\s*<div[^>]*>\\$?([0-9,]+\\.?[0-9]*)";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(highPattern);
            java.util.regex.Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                String highStr = matcher.group(1).replace(",", "");
                return Double.parseDouble(highStr);
            }

            return 0.0;
        } catch (Exception e) {
            log.debug("Error extracting day high: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Extract day low from Google Finance HTML.
     */
    private double extractDayLow(String html) {
        try {
            // Look for "Day's low" label and extract value
            String lowPattern = "Low[^<]*</div>\\s*<div[^>]*>\\$?([0-9,]+\\.?[0-9]*)";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(lowPattern);
            java.util.regex.Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                String lowStr = matcher.group(1).replace(",", "");
                return Double.parseDouble(lowStr);
            }

            return 0.0;
        } catch (Exception e) {
            log.debug("Error extracting day low: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Extract company name from Google Finance HTML.
     */
    private String extractCompanyName(String html, String symbol) {
        try {
            // Look for company name in title or h1 tag
            String namePattern = "<title>([^-|]+)";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(namePattern);
            java.util.regex.Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                String name = matcher.group(1).trim();
                // Remove " Stock Price" suffix if present
                name = name.replaceAll("\\s+Stock.*$", "");
                return name;
            }

            return symbol;
        } catch (Exception e) {
            log.debug("Error extracting company name: {}", e.getMessage());
            return symbol;
        }
    }

    @Override
    public boolean isAvailable() {
        // Simple health check - try to fetch a well-known symbol
        try {
            String url = "https://www.google.com/finance/quote/AAPL:NASDAQ";
            String html = restTemplate.getForObject(url, String.class);
            return html != null && html.contains("AAPL");
        } catch (Exception e) {
            log.debug("Google Finance health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "Google Finance";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}