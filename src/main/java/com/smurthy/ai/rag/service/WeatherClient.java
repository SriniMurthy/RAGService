package com.smurthy.ai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP Client for Weather Microservice
 *
 * Calls external weather-service (Langchain4j-based) running on port 8081
 * Replaces the embedded WeatherService with a microservice architecture
 *
 * Benefits:
 * - Decoupled from main application
 * - Can restart weather service independently
 * - Easy to swap weather providers
 * - Uses OpenWeatherMap (more accurate than Open-Meteo)
 */
@Service
public class WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);
    private final RestClient restClient;
    private final String weatherServiceUrl;

    public WeatherClient(@Value("${weather.service.url:http://localhost:8081}") String weatherServiceUrl) {
        this.weatherServiceUrl = weatherServiceUrl;
        this.restClient = RestClient.builder()
                .baseUrl(weatherServiceUrl)
                .build();

        log.info("WeatherClient initialized: {}", weatherServiceUrl);
    }

    /**
     * Get current weather by location name
     * Examples: "Santa Clara, California", "New York", "London, UK"
     */
    public WeatherData getWeatherByLocation(String location) {
        try {
            log.info("Fetching weather for location: {}", location);

            WeatherData weather = restClient.get()
                    .uri("/weather/location?q={location}", location)
                    .retrieve()
                    .body(WeatherData.class);

            if (weather != null && "SUCCESS".equals(weather.status())) {
                log.info("Weather retrieved: {} - {}, {}°F",
                    weather.location(), weather.condition(), weather.temperature());
            }

            return weather;

        } catch (RestClientException e) {
            log.error("Error fetching weather for {}: {}", location, e.getMessage());
            return createErrorWeather(location, "Weather service unavailable: " + e.getMessage());
        }
    }

    /**
     * Get current weather by ZIP code (US only)
     */
    public WeatherData getWeatherByZipCode(String zipCode) {
        try {
            log.info("Fetching weather for ZIP code: {}", zipCode);

            WeatherData weather = restClient.get()
                    .uri("/weather/zipcode?zip={zipCode}", zipCode)
                    .retrieve()
                    .body(WeatherData.class);

            if (weather != null && "SUCCESS".equals(weather.status())) {
                log.info("Weather retrieved: {} - {}, {}°F",
                    weather.location(), weather.condition(), weather.temperature());
            }

            return weather;

        } catch (RestClientException e) {
            log.error("Error fetching weather for ZIP {}: {}", zipCode, e.getMessage());
            return createErrorWeather(zipCode, "Weather service unavailable: " + e.getMessage());
        }
    }

    /**
     * Get 5-day forecast by location
     */
    public ForecastData getForecast(String location) {
        try {
            log.info("Fetching forecast for location: {}", location);

            return restClient.get()
                    .uri("/weather/forecast?q={location}", location)
                    .retrieve()
                    .body(ForecastData.class);

        } catch (RestClientException e) {
            log.error("Error fetching forecast for {}: {}", location, e.getMessage());
            return new ForecastData(location, "ERROR", "Weather service unavailable: " + e.getMessage(), null);
        }
    }

    private WeatherData createErrorWeather(String location, String error) {
        return new WeatherData(
                location, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0,
                "ERROR", error, 0L, "ERROR"
        );
    }

    // Response records matching weather-service API
    public record WeatherData(
            String location,
            double latitude,
            double longitude,
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
            String status
    ) {}

    public record ForecastData(
            String location,
            String status,
            String error,
            String forecast
    ) {}
}