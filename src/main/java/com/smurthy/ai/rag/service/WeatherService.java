package com.smurthy.ai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Free Weather Service using Open-Meteo API
 *
 * Benefits:
 * - NO API KEY REQUIRED
 * - UNLIMITED REQUESTS
 * - Real-time weather data worldwide
 * - Supports zip codes and location names
 * - Completely free forever
 *
 * APIs used:
 * - Open-Meteo: Weather data (https://open-meteo.com)
 * - Nominatim: Geocoding for location names (https://nominatim.openstreetmap.org)
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String OPEN_METEO_API = "https://api.open-meteo.com/v1/forecast";
    private static final String GEOCODING_API = "https://nominatim.openstreetmap.org/search";

    private final RestClient restClient;

    public WeatherService() {
        this.restClient = RestClient.builder().build();
    }

    /**
     * Get current weather by location name
     * Examples: "Santa Clara, California", "New York", "London, UK"
     */
    public WeatherData getWeatherByLocation(String location) {
        try {
            log.info("🌤️  Fetching weather for location: {}", location);

            // Step 1: Geocode location name to coordinates
            GeoLocation geoLocation = geocodeLocation(location);

            if (geoLocation == null) {
                return createErrorWeather(location, "Location not found");
            }

            // Step 2: Get weather for coordinates
            return getWeatherByCoordinates(geoLocation.lat(), geoLocation.lon(), location);

        } catch (Exception e) {
            log.error("Error fetching weather for {}: {}", location, e.getMessage());
            return createErrorWeather(location, e.getMessage());
        }
    }

    /**
     * Get current weather by ZIP code (US only for ZIP codes)
     * For international postal codes, use location name instead
     */
    public WeatherData getWeatherByZipCode(String zipCode) {
        try {
            log.info("🌤️  Fetching weather for ZIP code: {}", zipCode);

            // Geocode ZIP code (assumes US)
            String query = zipCode + ", USA";
            GeoLocation geoLocation = geocodeLocation(query);

            if (geoLocation == null) {
                return createErrorWeather(zipCode, "ZIP code not found");
            }

            return getWeatherByCoordinates(geoLocation.lat(), geoLocation.lon(), zipCode);

        } catch (Exception e) {
            log.error("Error fetching weather for ZIP {}: {}", zipCode, e.getMessage());
            return createErrorWeather(zipCode, e.getMessage());
        }
    }

    /**
     * Get weather by latitude/longitude coordinates
     */
    private WeatherData getWeatherByCoordinates(double lat, double lon, String locationName) {
        try {
            String url = OPEN_METEO_API +
                "?latitude=" + lat +
                "&longitude=" + lon +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m" +
                "&temperature_unit=fahrenheit" +
                "&wind_speed_unit=mph" +
                "&precipitation_unit=inch" +
                "&timezone=auto";

            log.info("🌐 Calling Open-Meteo API: lat={}, lon={}", lat, lon);

            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("current")) {
                return createErrorWeather(locationName, "No weather data available");
            }

            Map<String, Object> current = (Map<String, Object>) response.get("current");
            Map<String, Object> currentUnits = (Map<String, Object>) response.get("current_units");

            double temperature = getDouble(current, "temperature_2m");
            double feelsLike = getDouble(current, "apparent_temperature");
            double humidity = getDouble(current, "relative_humidity_2m");
            double precipitation = getDouble(current, "precipitation");
            double windSpeed = getDouble(current, "wind_speed_10m");
            int weatherCode = getInt(current, "weather_code");
            String time = getString(current, "time");

            String condition = getWeatherCondition(weatherCode);
            String description = getWeatherDescription(weatherCode);

            log.info("  Weather data retrieved: {} - {}, {}°F", locationName, condition, temperature);

            return new WeatherData(
                    locationName,
                    lat,
                    lon,
                    temperature,
                    feelsLike,
                    humidity,
                    precipitation,
                    windSpeed,
                    condition,
                    description,
                    time,
                    "SUCCESS"
            );

        } catch (RestClientException e) {
            log.error("Error calling Open-Meteo API: {}", e.getMessage());
            return createErrorWeather(locationName, "Weather service error");
        }
    }

    /**
     * Geocode location name to coordinates using Nominatim (OpenStreetMap)
     */
    private GeoLocation geocodeLocation(String location) {
        try {
            String url = GEOCODING_API +
                "?q=" + java.net.URLEncoder.encode(location, "UTF-8") +
                "&format=json&limit=1";

            log.info("🗺️  Geocoding location: {}", location);

            List<Map<String, Object>> results = restClient.get()
                    .uri(url)
                    .header("User-Agent", "JavaRAGApp/1.0") // Nominatim requires User-Agent
                    .retrieve()
                    .body(List.class);

            if (results == null || results.isEmpty()) {
                log.warn("Location not found: {}", location);
                return null;
            }

            Map<String, Object> firstResult = results.get(0);
            double lat = Double.parseDouble(firstResult.get("lat").toString());
            double lon = Double.parseDouble(firstResult.get("lon").toString());
            String displayName = firstResult.get("display_name").toString();

            log.info("  Geocoded: {} -> ({}, {})", displayName, lat, lon);

            return new GeoLocation(lat, lon, displayName);

        } catch (Exception e) {
            log.error("Geocoding error for {}: {}", location, e.getMessage());
            return null;
        }
    }

    /**
     * Convert WMO weather code to human-readable condition
     * WMO codes: https://open-meteo.com/en/docs
     */
    private String getWeatherCondition(int code) {
        return switch (code) {
            case 0 -> "Clear";
            case 1, 2, 3 -> "Partly Cloudy";
            case 45, 48 -> "Foggy";
            case 51, 53, 55 -> "Drizzle";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snow";
            case 77 -> "Snow Grains";
            case 80, 81, 82 -> "Rain Showers";
            case 85, 86 -> "Snow Showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with Hail";
            default -> "Unknown";
        };
    }

    private String getWeatherDescription(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45 -> "Fog";
            case 48 -> "Depositing rime fog";
            case 51 -> "Light drizzle";
            case 53 -> "Moderate drizzle";
            case 55 -> "Dense drizzle";
            case 61 -> "Slight rain";
            case 63 -> "Moderate rain";
            case 65 -> "Heavy rain";
            case 71 -> "Slight snow fall";
            case 73 -> "Moderate snow fall";
            case 75 -> "Heavy snow fall";
            case 77 -> "Snow grains";
            case 80 -> "Slight rain showers";
            case 81 -> "Moderate rain showers";
            case 82 -> "Violent rain showers";
            case 85 -> "Slight snow showers";
            case 86 -> "Heavy snow showers";
            case 95 -> "Thunderstorm";
            case 96 -> "Thunderstorm with slight hail";
            case 99 -> "Thunderstorm with heavy hail";
            default -> "Weather condition unknown";
        };
    }

    // Helper methods
    private double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "N/A";
    }

    private WeatherData createErrorWeather(String location, String error) {
        return new WeatherData(
                location, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                "ERROR", error, "N/A", "ERROR"
        );
    }

    // Response records
    public record WeatherData(
            String location,
            double latitude,
            double longitude,
            double temperature,
            double feelsLike,
            double humidity,
            double precipitation,
            double windSpeed,
            String condition,
            String description,
            String time,
            String status
    ) {}

    record GeoLocation(double lat, double lon, String displayName) {}
}