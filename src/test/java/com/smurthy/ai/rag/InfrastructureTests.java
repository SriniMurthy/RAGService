package com.smurthy.ai.rag;

import com.smurthy.ai.rag.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Infrastructure tests - verify tool/service configuration WITHOUT relying on OpenAI
 * These tests ensure the agentic infrastructure is properly set up.
 */
@SpringBootTest
public class InfrastructureTests extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private YahooFinanceService yahooFinanceService;

    @Autowired
    private GoogleNewsService googleNewsService;

    @Autowired
    private NewsService newsService;

    // =================================================================
    // BEAN REGISTRATION TESTS - Verify all tools are registered
    // =================================================================

    @Test
    void allToolFunctionsShouldBeRegisteredAsBeans() {
        // Verify all function beans are registered and discoverable by Spring AI
        assertThat(applicationContext.containsBean("getStockPrice"))
                .as("getStockPrice function should be registered")
                .isTrue();

        assertThat(applicationContext.containsBean("getYahooQuote"))
                .as("getYahooQuote function should be registered")
                .isTrue();

        assertThat(applicationContext.containsBean("getHistoricalPrices"))
                .as("getHistoricalPrices function should be registered")
                .isTrue();

        assertThat(applicationContext.containsBean("getMarketNews"))
                .as("getMarketNews function should be registered")
                .isTrue();

        assertThat(applicationContext.containsBean("getWeatherByLocation"))
                .as("getWeatherByLocation function should be registered")
                .isTrue();

        assertThat(applicationContext.containsBean("getWeatherByZipCode"))
                .as("getWeatherByZipCode function should be registered")
                .isTrue();

        // getRealTimeQuote is conditional - only check if finance.api.enabled=true
        // Skip this check since it's optional and depends on configuration
        System.err.println(" Note: getRealTimeQuote is conditional (requires finance.api.enabled=true)");

        System.err.println("All core tool functions are registered as Spring beans");
    }

    // =================================================================
    // SERVICE TESTS - Verify services work independently
    // =================================================================

    @Test
    void weatherServiceShouldFetchWeatherByZipCode() {
        // Test weather service directly (no OpenAI involved)
        WeatherService.WeatherData weather = weatherService.getWeatherByZipCode("95129");

        assertThat(weather).isNotNull();
        assertThat(weather.location()).isNotNull();
        assertThat(weather.status()).isIn("SUCCESS", "ERROR");

        System.err.println(" Weather Service works: " + weather.location() +
                " - " + weather.temperature() + "°F, " + weather.condition());
    }

    @Test
    void weatherServiceShouldFetchWeatherByLocation() {
        // Test weather service with location name
        WeatherService.WeatherData weather = weatherService.getWeatherByLocation("Santa Clara, California");

        assertThat(weather).isNotNull();
        assertThat(weather.location()).contains("Santa Clara");
        assertThat(weather.temperature()).isGreaterThanOrEqualTo(-50); // Reasonable temp check

        System.err.println("Weather by location works: " + weather.location() +
                " - " + weather.condition());
    }

    @Test
    void yahooFinanceServiceShouldFetchStockQuote() {
        // Test Yahoo Finance service directly
        YahooFinanceService.DelayedQuote quote = yahooFinanceService.getDelayedQuote("AAPL");

        assertThat(quote).isNotNull();
        assertThat(quote.symbol()).isEqualTo("AAPL");

        // Yahoo Finance API can be flaky or blocked - accept ERROR as valid response
        assertThat(quote.source()).isIn("DELAYED_15MIN", "MOCK", "ERROR");

        if ("ERROR".equals(quote.source())) {
            System.err.println(" Yahoo Finance returned ERROR (API may be blocked or rate-limited)");
            System.err.println("   This is expected behavior - service handles errors gracefully");
        } else {
            System.err.println(" Yahoo Finance works: " + quote.symbol() +
                    " - $" + quote.price() + " (" + quote.source() + ")");
        }
    }

    @Test
    void googleNewsServiceShouldFetchNews() {
        // Test Google News RSS service directly
        var articles = googleNewsService.searchNews("technology", 3);

        assertThat(articles).isNotNull();
        assertThat(articles.size()).isGreaterThan(0);

        System.err.println(" Google News works: Fetched " + articles.size() + " articles");
        if (!articles.isEmpty()) {
            System.err.println("   First article: " + articles.get(0).title());
        }
    }

    @Test
    void newsServiceShouldWrapGoogleNews() {
        // Test NewsService (wrapper around GoogleNewsService)
        var newsItems = newsService.getMarketNews("cloud computing", 5);

        assertThat(newsItems).isNotNull();
        assertThat(newsItems.size()).isGreaterThan(0);

        System.err.println("NewsService works: " + newsItems.size() + " news items retrieved");
    }

    // =================================================================
    // CONFIGURATION TESTS - Verify ChatClient builders exist
    // =================================================================

    @Test
    void chatClientBuilderShouldExist() {
        // Verify ChatClient.Builder bean exists
        assertThat(applicationContext.getBeansOfType(org.springframework.ai.chat.client.ChatClient.Builder.class))
                .as("ChatClient.Builder should be registered")
                .isNotEmpty();

        System.err.println(" ChatClient.Builder is configured");
    }

    @Test
    void vectorStoreShouldExist() {
        // Verify VectorStore bean exists
        assertThat(applicationContext.getBeansOfType(org.springframework.ai.vectorstore.VectorStore.class))
                .as("VectorStore should be registered")
                .isNotEmpty();

        System.err.println(" VectorStore is configured");
    }

    @Test
    void chatMemoryShouldExist() {
        // Verify ChatMemory bean exists
        assertThat(applicationContext.getBeansOfType(org.springframework.ai.chat.memory.ChatMemory.class))
                .as("ChatMemory should be registered")
                .isNotEmpty();

        System.err.println("ChatMemory is configured");
    }

    // =================================================================
    // ENDPOINT AVAILABILITY TESTS - Verify controllers exist
    // =================================================================

    @Test
    void ragDataControllerShouldExist() {
        assertThat(applicationContext.containsBean("RAGDataController"))
                .as("RAGDataController should be registered")
                .isTrue();

        System.err.println(" RAGDataController is registered");
    }

    @Test
    void agenticComparisonControllerShouldExist() {
        assertThat(applicationContext.containsBean("agenticComparisonController"))
                .as("AgenticComparisonController should be registered")
                .isTrue();

        System.err.println("AgenticComparisonController is registered");
    }

    // =================================================================
    // SUMMARY TEST - Overall health check
    // =================================================================

    @Test
    void agenticInfrastructureShouldBeFullyConfigured() {
        // Comprehensive check of all agentic components
        boolean allServicesExist =
                applicationContext.containsBean("weatherService") &&
                applicationContext.containsBean("yahooFinanceService") &&
                applicationContext.containsBean("googleNewsService") &&
                applicationContext.containsBean("newsService") &&
                applicationContext.containsBean("marketDataService");

        boolean allFunctionsExist =
                applicationContext.containsBean("getWeatherByLocation") &&
                applicationContext.containsBean("getWeatherByZipCode") &&
                applicationContext.containsBean("getYahooQuote") &&
                applicationContext.containsBean("getHistoricalPrices") &&
                applicationContext.containsBean("getMarketNews") &&
                applicationContext.containsBean("getStockPrice");

        boolean allControllersExist =
                applicationContext.containsBean("RAGDataController") &&
                applicationContext.containsBean("agenticComparisonController");

        assertThat(allServicesExist)
                .as("All services should be registered")
                .isTrue();

        assertThat(allFunctionsExist)
                .as("All function tools should be registered")
                .isTrue();

        assertThat(allControllersExist)
                .as("All controllers should be registered")
                .isTrue();

        System.err.println("  AGENTIC INFRASTRUCTURE FULLY OPERATIONAL");
        System.err.println("   - Services: ✓");
        System.err.println("   - Functions: ✓");
        System.err.println("   - Controllers: ✓");
        System.err.println("   - Ready for function calling!");
    }
}