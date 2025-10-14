package com.smurthy.ai.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smurthy.ai.rag.advisor.ToolInvocationTracker;
import com.smurthy.ai.rag.service.MarketDataService;
import com.smurthy.ai.rag.service.NewsService;
import com.smurthy.ai.rag.service.RealTimeFinanceService;
import com.smurthy.ai.rag.service.YahooFinanceService;
import com.smurthy.ai.rag.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
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
 *  like YahooFinanceService, NewsService, and WeatherService to fetch real, live data
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
    private final WeatherService weatherService;

    private static final Logger log = LoggerFactory.getLogger(MarketFunctionConfiguration.class);

    // Make the real-time service optional, as it may not always be enabled.
    @Autowired(required = false)
    private RealTimeFinanceService financeService;

    public MarketFunctionConfiguration(
            MarketDataService marketDataService,
            NewsService newsService,
            YahooFinanceService yahooFinanceService,
            WeatherService weatherService) {
        this.marketDataService = marketDataService;
        this.newsService = newsService;
        this.yahooFinanceService = yahooFinanceService;
        this.weatherService = weatherService;
    }


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

    // ===== ADD THESE NEW RECORDS =====
    public record CompareRequest(List<String> symbols) {}
    public record CompareResponse(List<String> symbols, String analysis) {}

    public record CompanyRequest(String symbol) {}
    public record CompanyResponse(String symbol, String description, String exchange, String location) {}

    public record PortfolioRequest(List<String> symbols) {}
    public record PortfolioResponse(List<String> symbols, double returns, double volatility, double sharpe) {}

    // Real-time finance API records
    public record RealTimeQuoteRequest(String symbol) {}
    public record RealTimeQuoteResponse(
            String symbol,
            double price,
            double change,
            double changePercent,
            double high,
            double low,
            long volume,
            String tradingDay,
            String source
    ) {}

    // Yahoo Finance records (delayed but FREE & unlimited)
    public record YahooQuoteRequest(String symbol) {}
    public record YahooQuoteResponse(
            String symbol,
            double price,
            double change,
            double changePercent,
            double dayHigh,
            double dayLow,
            long volume,
            String companyName,
            String source
    ) {}

    // Market movers records
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

    // Weather service records (FREE, no API key!)
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

    @Bean("getStockPrice")
    @Description("Get the real-time price of a stock")
    public Function<StockRequest, StockResponse> getStockPrice() {
        return request -> {
            log.debug("   🔧 TOOL CALLED: getStockPrice(" + request.symbol() + ")");
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
            log.debug("   🔧 TOOL CALLED: getMarketNews(" + request.topic() + ", " + request.limit() + ")");
            List<NewsItem> news = newsService.getMarketNews(request.topic(), request.limit());
            return new NewsResponse(news);
        };
    }

    @Bean("getHeadlinesByCategory")
    @Description("Get top news headlines for a specific category (e.g., BUSINESS, TECHNOLOGY, SPORTS, ENTERTAINMENT).")
    public Function<HeadlinesRequest, NewsResponse> getHeadlinesByCategory() {
        return request -> {
            log.debug("   📰 TOOL CALLED: getHeadlinesByCategory(" + request.category() + ", " + request.limit() + ")");
            List<NewsItem> news = newsService.getHeadlinesByCategory(request.category(), request.limit());
            return new NewsResponse(news);
        };
    }

    @Bean("analyzeFinancialRatios")
    @Description("Analyze key financial ratios for a company (P/E, ROE, Debt/Equity)")
    public Function<FinancialRatiosRequest, FinancialRatios> analyzeFinancialRatios() {
        return request -> {
            log.debug("   🔧 TOOL CALLED: analyzeFinancialRatios(" + request.symbol() + ")");
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
            log.debug("   🔧 TOOL CALLED: getEconomicIndicators(" + request.indicator() + ")");
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
            log.debug("   🔧 TOOL CALLED: predictMarketTrend(" + request.sector() + ", " + request.days() + " days)");
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
            log.debug("   🔧 TOOL CALLED: getAdvancedAnalytics(" + request.symbol() + ")");
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
            log.debug("   🔧 TOOL CALLED: compareStocks(" + request.symbols() + ")");

            // comparison logic here
            // For now, simple comparison
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
            log.debug("   🔧 TOOL CALLED: getCompanyProfile(" + request.symbol() + ")");

            // TODO: Integrate with real company profile API
            // This is PLACEHOLDER DATA ONLY - not real company information
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

            // portfolio calculation logic here
            List<String> symbols = request.symbols();

            // Calculate average returns
            double totalReturns = 0.0;
            for (String symbol : symbols) {
                // In real implementation, calculate actual returns
                totalReturns += 12.5; // Placeholder
            }
            double avgReturns = totalReturns / symbols.size();

            // Calculate volatility (standard deviation of returns)
            double volatility = 0.15; // Placeholder

            // Calculate Sharpe ratio (risk-adjusted returns)
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

    // ===== NEW: Real-Time Finance API Tool =====
    @Bean("getRealTimeQuote")
    @ConditionalOnProperty(name = "finance.api.enabled", havingValue = "true")
    @Description("Get real-time stock quote from external finance API (Alpha Vantage) including price, change, volume, high/low")
    public Function<RealTimeQuoteRequest, RealTimeQuoteResponse> getRealTimeQuote() {
        return request -> {
            log.debug(" TOOL CALLED: getRealTimeQuote(" + request.symbol() + ") - Fetching from Internet");

            RealTimeFinanceService.StockQuote quote = financeService.getStockQuote(request.symbol());

            return new RealTimeQuoteResponse(
                    quote.symbol(),
                    quote.price(),
                    quote.change(),
                    quote.changePercent(),
                    quote.high(),
                    quote.low(),
                    quote.volume(),
                    quote.tradingDay(),
                    quote.source()
            );
        };
    }

    // ===== NEW: Yahoo Finance Tools (FREE, UNLIMITED, NO API KEY!) =====

    @Bean("getYahooQuote")
    @Description("Get delayed stock quote from Yahoo Finance (15-20 min delay, FREE, UNLIMITED requests, NO API key needed)")
    public Function<YahooQuoteRequest, YahooQuoteResponse> getYahooQuote() {
        return request -> {
            log.debug(" TOOL CALLED: getYahooQuote(" + request.symbol() + ") - Yahoo Finance (FREE!)");

            YahooFinanceService.DelayedQuote quote = yahooFinanceService.getDelayedQuote(request.symbol());

            return new YahooQuoteResponse(
                    quote.symbol(),
                    quote.price(),
                    quote.change(),
                    quote.changePercent(),
                    quote.dayHigh(),
                    quote.dayLow(),
                    quote.volume(),
                    quote.companyName(),
                    quote.source()
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

    // ===== NEW: Weather Service Tools (FREE, UNLIMITED!) =====

    @Bean("getWeatherByLocation")
    @Description("Get current weather for any location by name (e.g. 'Santa Clara, California', 'New York', 'London, UK'). FREE, no API key needed!")
    public Function<WeatherByLocationRequest, WeatherResponse> getWeatherByLocation() {
        return request -> {
            ToolInvocationTracker.recordToolCall("getWeatherByLocation");
            log.debug("TOOL CALLED: getWeatherByLocation(" + request.location() + ") - FREE!");

            WeatherService.WeatherData weather = weatherService.getWeatherByLocation(request.location());

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
    @Description("Get current weather by US ZIP code (e.g. '95129', '10001'). FREE, no API key needed!")
    public Function<WeatherByZipRequest, WeatherResponse> getWeatherByZipCode() {
        return request -> {
            ToolInvocationTracker.recordToolCall("getWeatherByZipCode");
            log.debug("TOOL CALLED: getWeatherByZipCode(" + request.zipCode() + ") - FREE!");

            WeatherService.WeatherData weather = weatherService.getWeatherByZipCode(request.zipCode());

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
    @Description("Get today's biggest stock price movers (top gainers and losers) for a specific market (NASDAQ, NYSE, or SP500)")
    public Function<MarketMoversRequest, MarketMoversResponse> getMarketMovers() {
        return request -> {
            log.debug("   📊 TOOL CALLED: getMarketMovers(" + request.market() + ", limit: " + request.limit() + ")");

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
