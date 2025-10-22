package com.smurthy.ai.rag.service.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Provider for Alpha Vantage real-time stock quotes.
 * This provider is conditional and will only be enabled if `finance.api.enabled=true`.
 */
@Service
@ConditionalOnProperty(name = "finance.alpha-vantage.enabled", havingValue = "true")
public class AlphaVantageProvider implements StockQuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageProvider.class);
    private final RestClient restClient;
    private final String apiKey;

    @Value("${finance.alpha-vantage.enabled:false}")
    private final boolean enabled;

    @Value("${finance.alpha-vantage.priority:99}")
    private final int priority;

    /**
     * Inner record to map the specific JSON response from Alpha Vantage.
     * The API returns a "Global Quote" object with specific field names.
     */
    private record AlphaVantageGlobalQuote(
            @JsonProperty("01. symbol") String symbol,
            @JsonProperty("02. open") String open,
            @JsonProperty("03. high") String high,
            @JsonProperty("04. low") String low,
            @JsonProperty("05. price") String price,
            @JsonProperty("06. volume") String volume,
            @JsonProperty("07. latest trading day") String latestTradingDay,
            @JsonProperty("08. previous close") String previousClose,
            @JsonProperty("09. change") String change,
            @JsonProperty("10. change percent") String changePercent
    ) {}

    private record AlphaVantageResponse(@JsonProperty("Global Quote") AlphaVantageGlobalQuote globalQuote) {}

    public AlphaVantageProvider(
            @Value("${finance.alpha-vantage.base-url}") String baseUrl,
            @Value("${finance.alpha-vantage.key}") String apiKey,
            @Value("${finance.alpha-vantage.enabled:false}") boolean enabled,
            @Value("${finance.alpha-vantage.priority:99}") int priority,
            RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.priority = priority;
    }

    @Override
    public StockQuote getQuote(String symbol) {
        log.info("Fetching quote from Alpha Vantage for symbol: {}", symbol);
        try {
            AlphaVantageResponse response = this.restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("function", "GLOBAL_QUOTE")
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", this.apiKey)
                            .build())
                    .retrieve()
                    .body(AlphaVantageResponse.class);

            if (response == null || response.globalQuote() == null || response.globalQuote().symbol() == null) {
                log.warn("Alpha Vantage returned empty or invalid response for symbol: {}", symbol);
                return StockQuote.error(symbol, "Empty or invalid response from provider", getProviderName());
            }

            AlphaVantageGlobalQuote quote = response.globalQuote();

            // Use the new, safe builder to construct the full StockQuote object
            return StockQuote.builder()
                    .symbol(quote.symbol())
                    .price(quote.price())
                    .open(quote.open())
                    .dayHigh(quote.high())
                    .dayLow(quote.low())
                    .previousClose(quote.previousClose())
                    .change(quote.change())
                    .changePercent(quote.changePercent())
                    .volume(quote.volume())
                    .lastTradeTime(quote.latestTradingDay())
                    .provider(getProviderName())
                    .companyName("N/A") // Not provided by this endpoint
                    .currency("USD")   // Assuming USD
                    .isError(false)
                    .errorMessage("")
                    .build();

        } catch (Exception e) {
            log.error("Failed to fetch quote from Alpha Vantage for symbol '{}': {}", symbol, e.getMessage());
            return StockQuote.error(symbol, e.getMessage(), getProviderName());
        }
    }

    @Override
    public String getProviderName() {
        return "Alpha Vantage";
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public boolean isAvailable() {
        if (!isEnabled()) {
            return false;
        }
        try {
            // Use a lightweight, free endpoint to check API status.
            log.debug("Checking Alpha Vantage API availability...");
            var response = this.restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("function", "GLOBAL_QUOTE")
                            .queryParam("symbol", "IBM") // A stable, common symbol
                            .queryParam("apikey", this.apiKey)
                            .build())
                    .retrieve()
                    .toBodilessEntity();

            boolean isSuccess = response.getStatusCode().is2xxSuccessful();
            log.debug("Alpha Vantage API availability check successful: {}", isSuccess);
            return isSuccess;
        } catch (Exception e) {
            log.warn("Alpha Vantage API availability check failed: {}", e.getMessage());
            return false;
        }
    }
}
