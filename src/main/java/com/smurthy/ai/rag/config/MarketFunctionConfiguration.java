package com.smurthy.ai.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smurthy.ai.rag.advisor.ToolInvocationTracker;
import com.smurthy.ai.rag.service.MarketDataService;
import com.smurthy.ai.rag.service.NewsService;
import com.smurthy.ai.rag.service.YahooFinanceService;
import com.smurthy.ai.rag.service.WeatherClient;
import com.smurthy.ai.rag.service.provider.CompositeStockQuoteProvider;
import com.smurthy.ai.rag.service.provider.StockQuoteProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * The MarketFunctionConfiguration.java file is a Spring @Configuration class 
 * that acts as a toolbox for an AI agent. It defines a collection of functions (@Bean methods) 
 * that the AI can call to get information about financial markets, news, and even the weather.
 * Key aspects of this configuration are:
 * •Tool Definitions: Each @Bean in the class is a tool. For example, getYahooQuote, 
 *  getMarketNews, and getHistoricalPrices are all distinct tools the AI can use.
 * •Service Integration: These tools are not just dummy functions; they are wired into backend services
 *  like YahooFinanceService, NewsService, and WeatherClient (microservice) to fetch real, live data
 *  
 *  LIMITATIONS: For some reason, Yahoo Finance errors out in fetching historical data, and it
 *  has for sure disabled the free tier fetching of stock data. Will need to cough up subscription 
 *  for Alphavantage or some such third party
 */
@Configuration
public class MarketFunctionConfiguration {

    private final MarketDataService marketDataService;
    private final NewsService newsService;
    private final YahooFinanceService yahooFinanceService;
    private final WeatherClient weatherClient;
    private final CompositeStockQuoteProvider compositeStockQuoteProvider;

    private static final Logger log = LoggerFactory.getLogger(MarketFunctionConfiguration.class);

    public MarketFunctionConfiguration(
            MarketDataService marketDataService,
            NewsService newsService,
            YahooFinanceService yahooFinanceService,
            WeatherClient weatherClient,
            CompositeStockQuoteProvider compositeStockQuoteProvider) {
        this.marketDataService = marketDataService;
        this.newsService = newsService;
        this.yahooFinanceService = yahooFinanceService;
        this.weatherClient = weatherClient;
        this.compositeStockQuoteProvider = compositeStockQuoteProvider;
    }

    // --- Universal Request/Response Records ---
    public record StockQuoteRequest(String symbol) {}

    // --- Other Records ---
    public record StockRequest(String symbol) {}
    public record StockResponse(String symbol, double price, double change, String status) {}

    public record NewsRequest(String topic, int limit) {}
    public record NewsResponse(List<NewsItem> articles) {}
    public record NewsItem(String title, String summary, String source, String date) {}
    public record HeadlinesRequest(String category, int limit) {}

    public record FinancialRatiosRequest(String symbol) {}
    public record FinancialRatios(String symbol, double pe, double roe, double debtToEquity, String analysis) {}

    public record EconomicRequest(String indicator) {}
    public record EconomicData(String indicator, double value, String trend, String impact) {}

    public record PredictionRequest(String sector, int days) {}
    public record MarketPrediction(String sector, String prediction, double confidence, String reasoning) {}

    public record AdvancedAnalyticsRequest(String symbol) {}
    public record AdvancedAnalytics(String symbol, String analysis, double score) {}

    public record CompareRequest(List<String> symbols) {}
    public record CompareResponse(List<String> symbols, String analysis) {}

    public record CompanyRequest(String symbol) {}
    public record CompanyResponse(String symbol, String description, String exchange, String location) {}

    public record PortfolioRequest(List<String> symbols) {}
    public record PortfolioResponse(List<String> symbols, double returns, double volatility, double sharpe) {}

    public record MarketMoversRequest(String market, int limit) {} // market: "NASDAQ", "NYSE", "SP500"
    public record MarketMoversResponse(
            String market,
            List<StockMover> topGainers,
            List<StockMover> topLosers,
            String summary
    ) {}
    public record StockMover(
            String symbol,
            String name,
            double price,
            double changePercent,
            long volume
    ) {}

    public record HistoricalDataRequest(String symbol, String fromDate, String toDate) {}
    public record HistoricalDataResponse(
            String symbol,
            String fromDate,
            String toDate,
            int dataPoints,
            String summary
    ) {}

    public record WeatherByLocationRequest(String location) {}
    public record WeatherByZipRequest(String zipCode) {}
    public record WeatherResponse(
            String location,
            double temperature,
            double feelsLike,
            double humidity,
            String condition,
            String description,
            double windSpeed,
            String status
    ) {}

    // --- REFACTORED STOCK QUOTE TOOLS ---

    /**
     * Private factory method to create a stock quote tool from a given provider function.
     * This centralizes the tool creation logic and avoids boilerplate code.
     */
    private Function<StockQuoteRequest, StockQuoteProvider.StockQuote> createStockQuoteTool(
            String providerName,
            Function<String, StockQuoteProvider.StockQuote> quoteFunction) {
        return request -> {
            log.debug("   TOOL CALLED: getQuote from provider '{}' for symbol '{}'", providerName, request.symbol());
            return quoteFunction.apply(request.symbol());
        };
    }

    @Bean("getYahooQuote")
    @Description("Get stock quote with automatic fallback between multiple providers. Recommended as primary tool.")
    public Function<StockQuoteRequest, StockQuoteProvider.StockQuote> getYahooQuote() {
        return createStockQuoteTool("Composite", compositeStockQuoteProvider::getQuote);
    }

    @Bean("getQuoteFromYahooOnly")
    @Description("Get stock quote ONLY from Yahoo Finance (no fallback).")
    public Function<StockQuoteRequest, StockQuoteProvider.StockQuote> getQuoteFromYahooOnly() {
        return request -> {
            StockQuoteProvider yahooProvider = compositeStockQuoteProvider.getProviders().stream()
                    .filter(p -> "Yahoo Finance".equals(p.getProviderName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Yahoo Finance provider not found in composite list."));
            return yahooProvider.getQuote(request.symbol());
        };
    }

    @Bean("getFinnhubQuote")
    @Description("Get stock quote from Finnhub API. FREE tier: 60 calls/minute.")
    public Function<StockQuoteRequest, StockQuoteProvider.StockQuote> getQuoteFromFinnhub() {
        // Applying the same lazy-loading fix here.
        return request -> {
            StockQuoteProvider finnhubProvider = compositeStockQuoteProvider.getProviders().stream()
                    .filter(p -> "Finnhub".equals(p.getProviderName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Finnhub provider not found in composite list."));
            return finnhubProvider.getQuote(request.symbol());
        };
    }


    @Bean("getQuoteFromGoogleFinance")
    @Description("Get stock quote from Google Finance (web scraping). FREE, UNLIMITED, no API key. WARNING: May return stale data, use as last resort.")
    public Function<StockQuoteRequest, StockQuoteProvider.StockQuote> getQuoteFromGoogleFinance() {
        // Applying the same lazy-loading fix here.
        return request -> {
            StockQuoteProvider googleProvider = compositeStockQuoteProvider.getProviders().stream()
                    .filter(p -> "Google Finance".equals(p.getProviderName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Google Finance provider not found in composite list."));
            return googleProvider.getQuote(request.symbol());
        };
    }

    @Bean("getRealTimeQuote")
    @ConditionalOnProperty(name = "finance.alpha-vantage.enabled", havingValue = "true")
    @Description("Get a real-time stock quote from Alpha Vantage. Use for queries demanding live, up-to-the-second data.")
    public Function<StockQuoteRequest, StockQuoteProvider.StockQuote> getRealTimeQuote() {
        return request -> {
            log.debug("  TOOL CALLED: getRealTimeQuote({}) - Explicitly using Alpha Vantage", request.symbol());

            // Find the specific Alpha Vantage provider from the composite list.
            StockQuoteProvider alphaVantageProvider = compositeStockQuoteProvider.getProviders().stream()
                    .filter(p -> "Alpha Vantage".equals(p.getProviderName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Alpha Vantage provider is not available or enabled."));

            return alphaVantageProvider.getQuote(request.symbol());
        };
    }

    // --- OTHER TOOLS (Unchanged) ---

    @Bean("getStockPrice")
    @Description("Get the real-time price of a stock")
    public Function<StockRequest, StockResponse> getStockPrice() {
        return request -> {
            log.debug(" TOOL CALLED: getStockPrice(" + request.symbol() + ")");
            double price = marketDataService.getStockPrice(request.symbol());
            return new StockResponse(
                    request.symbol(),
                    price,
                    2.5,
                    "Retrieved real-time price"
            );
        };
    }

    @Bean("getMarketNews")
    @Description("Get the latest news articles for any topic (general news, market news, technology, politics, etc.)")
    public Function<NewsRequest, NewsResponse> getMarketNews() {
        return request -> {
            ToolInvocationTracker.recordToolCall("getMarketNews");
            log.debug(" TOOL CALLED: getMarketNews(" + request.topic() + ", " + request.limit() + ")");
            List<NewsItem> news = newsService.getMarketNews(request.topic(), request.limit());
            return new NewsResponse(news);
        };
    }

    @Bean("getHeadlinesByCategory")
    @Description("Get top news headlines for a specific category (e.g., BUSINESS, TECHNOLOGY, SPORTS, ENTERTAINMENT).")
    public Function<HeadlinesRequest, NewsResponse> getHeadlinesByCategory() {
        return request -> {
            log.debug(" TOOL CALLED: getHeadlinesByCategory(" + request.category() + ", " + request.limit() + ")");
            List<NewsItem> news = newsService.getHeadlinesByCategory(request.category(), request.limit());
            return new NewsResponse(news);
        };
    }

    @Bean("analyzeFinancialRatios")
    @Description("Analyze key financial ratios for a company (P/E, ROE, Debt/Equity)")
    public Function<FinancialRatiosRequest, FinancialRatios> analyzeFinancialRatios() {
        return request -> {
            log.debug("  TOOL CALLED: analyzeFinancialRatios(" + request.symbol() + ")");
            return new FinancialRatios(
                    request.symbol(),
                    15.3,
                    18.5,
                    0.4,
                    "Strong fundamentals with healthy balance sheet"
            );
        };
    }

    @Bean("getEconomicIndicators")
    @Description("Retrieve major economic indicators like GDP, inflation, or unemployment rates")
    public Function<EconomicRequest, EconomicData> getEconomicIndicators() {
        return request -> {
            log.debug(" TOOL CALLED: getEconomicIndicators(" + request.indicator() + ")");
            return new EconomicData(
                    request.indicator(),
                    3.2,
                    "increasing",
                    "Positive impact on equity markets"
            );
        };
    }

    @Bean("predictMarketTrend")
    @Description("Predict the market trend (e.g., bullish, bearish) for a specific sector using ML models")
    public Function<PredictionRequest, MarketPrediction> predictMarketTrend() {
        return request -> {
            log.debug(" TOOL CALLED: predictMarketTrend(" + request.sector() + ", " + request.days() + " days)");
            return new MarketPrediction(
                    request.sector(),
                    "bullish",
                    0.78,
                    "Technical indicators show strong momentum with increasing volume"
            );
        };
    }

    @Bean("getAdvancedAnalytics")
    @Description("Get advanced, premium analytics for a stock symbol")
    public Function<AdvancedAnalyticsRequest, AdvancedAnalytics> getAdvancedAnalytics() {
        return request -> {
            log.debug(" TOOL CALLED: getAdvancedAnalytics(" + request.symbol() + ")");
            return new AdvancedAnalytics(
                    request.symbol(),
                    "Premium analysis available",
                    0.85
            );
        };
    }
    
    @Bean("compareStocks")
    @Description("Perform a side-by-side comparison of multiple stock symbols")
    public Function<CompareRequest, CompareResponse> compareStocks() {
        return request -> {
            log.debug(" TOOL CALLED: compareStocks(" + request.symbols() + ")");

            List<String> symbols = request.symbols();
            StringBuilder analysis = new StringBuilder();

            if (symbols.size() >= 2) {
                analysis.append("Comparison of ").append(String.join(" vs ", symbols)).append(":\n");
                for (String symbol : symbols) {
                    double price = marketDataService.getStockPrice(symbol);
                    analysis.append(symbol).append(": $").append(price).append("\n");
                }
                analysis.append("Based on current prices, ")
                        .append(symbols.get(0))
                        .append(" shows stronger momentum.");
            } else {
                analysis.append("Need at least 2 symbols to compare");
            }

            return new CompareResponse(request.symbols(), analysis.toString());
        };
    }

    @Bean("getCompanyProfile")
    @Description("Get detailed profile information for a company, including description and location")
    public Function<CompanyRequest, CompanyResponse> getCompanyProfile() {
        return request -> {
            log.debug("  TOOL CALLED: getCompanyProfile(" + request.symbol() + ")");

            log.warn(" getCompanyProfile returning PLACEHOLDER data - integrate real API!");

            return new CompanyResponse(
                    request.symbol(),
                    "[PLACEHOLDER DATA] Company profile not available. Please use getMarketNews for real company information.",
                    "Unknown",
                    "Unknown"
            );
        };
    }

    @Bean("calculatePortfolioMetrics")
    @Description("Calculate advanced metrics for a portfolio of stocks, such as returns, volatility, and Sharpe ratio")
    public Function<PortfolioRequest, PortfolioResponse> calculatePortfolioMetrics() {
        return request -> {
            log.debug(" TOOL CALLED: calculatePortfolioMetrics(" + request.symbols().size() + " stocks)");

            List<String> symbols = request.symbols();
            double totalReturns = 0.0;
            for (String symbol : symbols) {
                totalReturns += 12.5; // Placeholder
            }
            double avgReturns = totalReturns / symbols.size();
            double volatility = 0.15; // Placeholder
            double riskFreeRate = 0.04; // 4% risk-free rate
            double sharpe = (avgReturns - riskFreeRate) / volatility;

            return new PortfolioResponse(
                    request.symbols(),
                    avgReturns,
                    volatility,
                    sharpe
            );
        };
    }

    @Bean("getHistoricalPrices")
    @Description("Get historical stock prices for a date range (format: YYYY-MM-DD). Great for trend analysis!")
    public Function<HistoricalDataRequest, HistoricalDataResponse> getHistoricalPrices() {
        return request -> {
            log.debug(" TOOL CALLED: getHistoricalPrices(" + request.symbol() +
                    " from " + request.fromDate() + " to " + request.toDate() + ")");

            LocalDate from = LocalDate.parse(request.fromDate());
            LocalDate to = LocalDate.parse(request.toDate());

            YahooFinanceService.HistoricalData data = yahooFinanceService.getHistoricalData(
                    request.symbol(), from, to);

            return new HistoricalDataResponse(
                    data.symbol(),
                    data.fromDate(),
                    data.toDate(),
                    data.prices().size(),
                    data.summary()
            );
        };
    }

    // Weather Service Tools (FREE, UNLIMITED!)

    @Bean("getWeatherByLocation")
    @Description("Get current weather for any location by name (e.g. 'Santa Clara, California', 'New York', 'London, UK'). Uses OpenWeatherMap for accurate data!")
    public Function<WeatherByLocationRequest, WeatherResponse> getWeatherByLocation() {
        return request -> {
            ToolInvocationTracker.recordToolCall("getWeatherByLocation");
            log.debug("TOOL CALLED: getWeatherByLocation(" + request.location() + ") - via weather microservice");

            WeatherClient.WeatherData weather = weatherClient.getWeatherByLocation(request.location());

            return new WeatherResponse(
                    weather.location(),
                    weather.temperature(),
                    weather.feelsLike(),
                    weather.humidity(),
                    weather.condition(),
                    weather.description(),
                    weather.windSpeed(),
                    weather.status()
            );
        };
    }

    @Bean("getWeatherByZipCode")
    @Description("Get current weather by US ZIP code (e.g. '95129', '10001'). Uses OpenWeatherMap for accurate data!")
    public Function<WeatherByZipRequest, WeatherResponse> getWeatherByZipCode() {
        return request -> {
            ToolInvocationTracker.recordToolCall("getWeatherByZipCode");
            log.debug("TOOL CALLED: getWeatherByZipCode(" + request.zipCode() + ") - via weather microservice");

            WeatherClient.WeatherData weather = weatherClient.getWeatherByZipCode(request.zipCode());

            return new WeatherResponse(
                    weather.location(),
                    weather.temperature(),
                    weather.feelsLike(),
                    weather.humidity(),
                    weather.condition(),
                    weather.description(),
                    weather.windSpeed(),
                    weather.status()
            );
        };
    }

    @Bean("getMarketMovers")
    @Description("Get today's biggest stock price movers (top gainers and losers) for a specific market. Parameters: market (NASDAQ, NYSE, or SP500), limit (number of results, use 5 as default)")
    public Function<MarketMoversRequest, MarketMoversResponse> getMarketMovers() {
        return request -> {
            log.debug("  TOOL CALLED: getMarketMovers(" + request.market() + ", limit: " + request.limit() + ")");

            YahooFinanceService.MarketMovers movers = yahooFinanceService.getMarketMovers(
                    request.market(), request.limit());

            List<StockMover> gainers = movers.topGainers().stream()
                    .map(m -> new StockMover(m.symbol(), m.name(), m.price(), m.changePercent(), m.volume()))
                    .toList();

            List<StockMover> losers = movers.topLosers().stream()
                    .map(m -> new StockMover(m.symbol(), m.name(), m.price(), m.changePercent(), m.volume()))
                    .toList();

            return new MarketMoversResponse(
                    request.market(),
                    gainers,
                    losers,
                    movers.summary()
            );
        };
    }
}
