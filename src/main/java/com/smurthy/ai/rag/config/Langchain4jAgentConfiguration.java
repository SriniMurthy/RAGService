package com.smurthy.ai.rag.config;

import com.smurthy.ai.rag.config.MarketFunctionConfiguration.*;
import com.smurthy.ai.rag.mcp.finance.MCPFinanceAgent;
import com.smurthy.ai.rag.mcp.weather.MCPWeatherAgent;
import com.smurthy.ai.rag.service.provider.StockQuoteProvider;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Configuration to wire Spring AI function beans into LangChain4j @AiService agents.
 *
 * Bridges the two framework's tool systems:
 * - Spring AI: @Bean functions
 * - LangChain4j: @Tool annotated methods
 */
@Configuration
public class Langchain4jAgentConfiguration {

    /**
     * Create MCPFinanceAgent with all Spring AI financial tools wired in
     */
    @Bean
    public MCPFinanceAgent mcpFinanceAgent(
            dev.langchain4j.model.chat.ChatLanguageModel chatModel,
            ApplicationContext context) {

        // Get all Spring AI function beans
        FinancialTools tools = new FinancialTools(context);

        return dev.langchain4j.service.AiServices.builder(MCPFinanceAgent.class)
                .chatLanguageModel(chatModel)
                .tools(tools)
                .build();
    }

    /**
     * Create MCPWeatherAgent with all Spring AI weather tools wired in
     */
    @Bean
    public MCPWeatherAgent mcpWeatherAgent(
            dev.langchain4j.model.chat.ChatLanguageModel chatModel,
            ApplicationContext context) {

        WeatherTools tools = new WeatherTools(context);

        return dev.langchain4j.service.AiServices.builder(MCPWeatherAgent.class)
                .chatLanguageModel(chatModel)
                .tools(tools)
                .build();
    }

    /**
     * Wrapper class that exposes Spring AI financial function beans as LangChain4j @Tool methods
     */
    public static class FinancialTools {
        private final Function<StockQuoteRequest, StockQuoteProvider.StockQuote> getYahooQuote;
        private final Function<StockQuoteRequest, StockQuoteProvider.StockQuote> getFinnhubQuote;
        private final Function<StockQuoteRequest, StockQuoteProvider.StockQuote> getRealTimeQuote;
        private final Function<NewsRequest, NewsResponse> getMarketNews;
        private final Function<HeadlinesRequest, NewsResponse> getHeadlinesByCategory;
        private final Function<MarketMoversRequest, MarketMoversResponse> getMarketMovers;
        private final Function<HistoricalDataRequest, HistoricalDataResponse> getHistoricalPrices;

        @SuppressWarnings("unchecked")
        public FinancialTools(ApplicationContext context) {
            this.getYahooQuote = (Function<StockQuoteRequest, StockQuoteProvider.StockQuote>)
                context.getBean("getYahooQuote");
            this.getFinnhubQuote = (Function<StockQuoteRequest, StockQuoteProvider.StockQuote>)
                context.getBean("getFinnhubQuote");
            this.getRealTimeQuote = (Function<StockQuoteRequest, StockQuoteProvider.StockQuote>)
                context.getBean("getRealTimeQuote");
            this.getMarketNews = (Function<NewsRequest, NewsResponse>)
                context.getBean("getMarketNews");
            this.getHeadlinesByCategory = (Function<HeadlinesRequest, NewsResponse>)
                context.getBean("getHeadlinesByCategory");
            this.getMarketMovers = (Function<MarketMoversRequest, MarketMoversResponse>)
                context.getBean("getMarketMovers");
            this.getHistoricalPrices = (Function<HistoricalDataRequest, HistoricalDataResponse>)
                context.getBean("getHistoricalPrices");
        }

        @Tool("Get stock quote with automatic fallback between Yahoo, Finnhub, and Google Finance. Use this as the default for stock prices.")
        public String getYahooQuote(String symbol) {
            StockQuoteProvider.StockQuote quote = getYahooQuote.apply(new StockQuoteRequest(symbol));
            return formatQuote(quote);
        }

        @Tool("Get stock quote from Finnhub API. FREE tier: 60 calls/minute. Good for real-time data.")
        public String getFinnhubQuote(String symbol) {
            StockQuoteProvider.StockQuote quote = getFinnhubQuote.apply(new StockQuoteRequest(symbol));
            return formatQuote(quote);
        }

        @Tool("Get real-time stock quote from Alpha Vantage. Use for queries demanding live, up-to-the-second data. Requires API key.")
        public String getRealTimeQuote(String symbol) {
            StockQuoteProvider.StockQuote quote = getRealTimeQuote.apply(new StockQuoteRequest(symbol));
            return formatQuote(quote);
        }

        @Tool("Get latest news articles for any topic. Use for: market news, company news, current events, political news, tech news. Parameters: topic (search query), limit (number of articles, default 5)")
        public String getMarketNews(String topic, int limit) {
            NewsResponse response = getMarketNews.apply(new NewsRequest(topic, limit));
            return formatNews(response);
        }

        @Tool("Get top news headlines by category. Categories: BUSINESS, TECHNOLOGY, WORLD, SPORTS, ENTERTAINMENT, SCIENCE, HEALTH. Parameters: category, limit")
        public String getHeadlinesByCategory(String category, int limit) {
            NewsResponse response = getHeadlinesByCategory.apply(new HeadlinesRequest(category, limit));
            return formatNews(response);
        }

        @Tool("Get today's biggest stock price movers (top gainers and losers). Parameters: market (NASDAQ, NYSE, or SP500), limit (number of results)")
        public String getMarketMovers(String market, int limit) {
            MarketMoversResponse response = getMarketMovers.apply(new MarketMoversRequest(market, limit));
            return formatMarketMovers(response);
        }

        @Tool("Get historical stock prices for a date range. Parameters: symbol, fromDate (YYYY-MM-DD), toDate (YYYY-MM-DD)")
        public String getHistoricalPrices(String symbol, String fromDate, String toDate) {
            HistoricalDataResponse response = getHistoricalPrices.apply(
                new HistoricalDataRequest(symbol, fromDate, toDate));
            return response.summary();
        }

        private String formatQuote(StockQuoteProvider.StockQuote quote) {
            if (quote.isError()) {
                return String.format("Error fetching %s: %s (Provider: %s)",
                    quote.symbol(), quote.errorMessage(), quote.provider());
            }
            return String.format("%s (%s): $%.2f (%.2f%%) [%s]",
                quote.symbol(), quote.companyName(), quote.price(),
                quote.changePercent(), quote.provider());
        }

        private String formatNews(NewsResponse response) {
            if (response.articles().isEmpty()) {
                return "No news articles found.";
            }
            StringBuilder sb = new StringBuilder();
            for (NewsItem article : response.articles()) {
                sb.append(String.format("- %s (%s, %s)\n  %s\n",
                    article.title(), article.source(), article.date(), article.summary()));
            }
            return sb.toString();
        }

        private String formatMarketMovers(MarketMoversResponse response) {
            return response.summary();
        }
    }

    /**
     * Wrapper class that exposes Spring AI weather function beans as LangChain4j @Tool methods
     */
    public static class WeatherTools {
        private final Function<WeatherByLocationRequest, WeatherResponse> getWeatherByLocationFn;
        private final Function<WeatherByZipRequest, WeatherResponse> getWeatherByZipCodeFn;

        @SuppressWarnings("unchecked")
        public WeatherTools(ApplicationContext context) {
            this.getWeatherByLocationFn = (Function<WeatherByLocationRequest, WeatherResponse>)
                context.getBean("getWeatherByLocation");
            this.getWeatherByZipCodeFn = (Function<WeatherByZipRequest, WeatherResponse>)
                context.getBean("getWeatherByZipCode");
        }

        @Tool("Get current weather for any location by name (e.g. 'Santa Clara, California', 'London, UK'). Uses OpenWeatherMap.")
        public String getWeatherByLocation(String location) {
            WeatherResponse response = getWeatherByLocationFn.apply(new WeatherByLocationRequest(location));
            return formatWeather(response);
        }

        @Tool("Get current weather by US ZIP code (e.g. '95129', '10001'). Uses OpenWeatherMap.")
        public String getWeatherByZipCode(String zipCode) {
            WeatherResponse response = getWeatherByZipCodeFn.apply(new WeatherByZipRequest(zipCode));
            return formatWeather(response);
        }

        private String formatWeather(WeatherResponse response) {
            if (response.status().contains("Error") || response.status().contains("error")) {
                return String.format("Weather error for %s: %s", response.location(), response.status());
            }
            return String.format("Weather in %s: %.1f°F (feels like %.1f°F), %s - %s. Humidity: %.0f%%, Wind: %.1f mph",
                response.location(), response.temperature(), response.feelsLike(),
                response.condition(), response.description(), response.humidity(), response.windSpeed());
        }
    }
}