package com.smurthy.weather;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Langchain4j Weather Tools
 *
 * Proper tool definitions using @Tool annotation.
 * Can be exposed as:
 * - REST API (current)
 * - MCP server (when Spring AI fixes dependencies)
 * - Direct Langchain4j tool execution
 */
public class WeatherTools {

    private static final Logger log = LoggerFactory.getLogger(WeatherTools.class);
    private final OpenWeatherMapClient weatherClient;

    public WeatherTools(String apiKey) {
        this.weatherClient = new OpenWeatherMapClient(apiKey);
        log.info("WeatherTools initialized with OpenWeatherMap client");
    }

    /**
     * Get current weather by location name
     *
     * Langchain4j @Tool annotation makes this discoverable by LLMs
     */
    @Tool("Get current weather for any location by name (e.g. 'Santa Clara, California', 'New York', 'London, UK'). " +
          "Returns temperature, humidity, wind speed, and weather conditions. Uses OpenWeatherMap for accurate real-time data.")
    public WeatherResult getWeatherByLocation(String location) {
        log.info("[TOOL] getWeatherByLocation: {}", location);

        try {
            OpenWeatherMapClient.WeatherData data = weatherClient.getWeatherByLocation(location);

            return new WeatherResult(
                    data.location(),
                    data.temperature(),
                    data.feelsLike(),
                    data.humidity(),
                    data.pressure(),
                    data.windSpeed(),
                    data.windDirection(),
                    data.cloudiness(),
                    data.condition(),
                    data.description(),
                    data.timestamp(),
                    "SUCCESS",
                    null
            );
        } catch (Exception e) {
            log.error("[TOOL] getWeatherByLocation failed: {}", e.getMessage());
            return WeatherResult.error(location, e.getMessage());
        }
    }

    /**
     * Get current weather by US ZIP code
     *
     * Langchain4j @Tool annotation makes this discoverable by LLMs
     */
    @Tool("Get current weather by US ZIP code (e.g. '95051', '10001'). " +
          "Returns temperature, humidity, wind speed, and weather conditions. Uses OpenWeatherMap for accurate real-time data.")
    public WeatherResult getWeatherByZipCode(String zipCode) {
        log.info("[TOOL] getWeatherByZipCode: {}", zipCode);

        try {
            OpenWeatherMapClient.WeatherData data = weatherClient.getWeatherByZipCode(zipCode);

            return new WeatherResult(
                    data.location(),
                    data.temperature(),
                    data.feelsLike(),
                    data.humidity(),
                    data.pressure(),
                    data.windSpeed(),
                    data.windDirection(),
                    data.cloudiness(),
                    data.condition(),
                    data.description(),
                    data.timestamp(),
                    "SUCCESS",
                    null
            );
        } catch (Exception e) {
            log.error("[TOOL] getWeatherByZipCode failed: {}", e.getMessage());
            return WeatherResult.error(zipCode, e.getMessage());
        }
    }

    /**
     * Get 5-day weather forecast for a location
     *
     * Langchain4j @Tool annotation makes this discoverable by LLMs
     */
    @Tool("Get 5-day weather forecast for any location. " +
          "Returns hourly forecast with temperature and conditions. Useful for planning ahead.")
    public ForecastResult getForecast(String location) {
        log.info("[TOOL] getForecast: {}", location);

        try {
            OpenWeatherMapClient.ForecastData data = weatherClient.getForecast(location);

            return new ForecastResult(
                    data.location(),
                    data.status(),
                    data.error(),
                    data.forecast()
            );
        } catch (Exception e) {
            log.error("[TOOL] getForecast failed: {}", e.getMessage());
            return new ForecastResult(location, "ERROR", e.getMessage(), null);
        }
    }

    /**
     * Langchain4j-friendly result objects
     * These are serialized for tool responses
     */
    public record WeatherResult(
            String location,
            double temperature,
            double feelsLike,
            double humidity,
            double pressure,
            double windSpeed,
            int windDirection,
            int cloudiness,
            String condition,
            String description,
            long timestamp,
            String status,
            String error
    ) {
        public static WeatherResult error(String location, String errorMessage) {
            return new WeatherResult(
                    location, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0,
                    "ERROR", errorMessage, 0L, "ERROR", errorMessage
            );
        }

        /**
         * Human-readable summary for LLM consumption
         */
        public String toSummary() {
            if ("ERROR".equals(status)) {
                return String.format("Error getting weather for %s: %s", location, error);
            }

            return String.format(
                    "%s: %s, %.1f°F (feels like %.1f°F), humidity %.0f%%, wind %.1f mph, %s",
                    location, condition, temperature, feelsLike, humidity, windSpeed, description
            );
        }
    }

    public record ForecastResult(
            String location,
            String status,
            String error,
            String forecast
    ) {
        public String toSummary() {
            if ("ERROR".equals(status)) {
                return String.format("Error getting forecast for %s: %s", location, error);
            }
            return String.format("Forecast for %s:\n%s", location, forecast);
        }
    }
}