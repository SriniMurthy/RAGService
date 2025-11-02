package com.smurthy.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenWeatherMap API Client
 *
 * Free tier: 1,000 calls/day, 60 calls/minute
 * More accurate than Open-Meteo
 *
 * API Docs: https://openweathermap.org/api
 */
public class OpenWeatherMapClient {

    private static final Logger log = LoggerFactory.getLogger(OpenWeatherMapClient.class);
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5";

    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    // US State name to code mapping
    private static final Map<String, String> US_STATES = new HashMap<>();
    static {
        US_STATES.put("alabama", "AL");
        US_STATES.put("alaska", "AK");
        US_STATES.put("arizona", "AZ");
        US_STATES.put("arkansas", "AR");
        US_STATES.put("california", "CA");
        US_STATES.put("colorado", "CO");
        US_STATES.put("connecticut", "CT");
        US_STATES.put("delaware", "DE");
        US_STATES.put("florida", "FL");
        US_STATES.put("georgia", "GA");
        US_STATES.put("hawaii", "HI");
        US_STATES.put("idaho", "ID");
        US_STATES.put("illinois", "IL");
        US_STATES.put("indiana", "IN");
        US_STATES.put("iowa", "IA");
        US_STATES.put("kansas", "KS");
        US_STATES.put("kentucky", "KY");
        US_STATES.put("louisiana", "LA");
        US_STATES.put("maine", "ME");
        US_STATES.put("maryland", "MD");
        US_STATES.put("massachusetts", "MA");
        US_STATES.put("michigan", "MI");
        US_STATES.put("minnesota", "MN");
        US_STATES.put("mississippi", "MS");
        US_STATES.put("missouri", "MO");
        US_STATES.put("montana", "MT");
        US_STATES.put("nebraska", "NE");
        US_STATES.put("nevada", "NV");
        US_STATES.put("new hampshire", "NH");
        US_STATES.put("new jersey", "NJ");
        US_STATES.put("new mexico", "NM");
        US_STATES.put("new york", "NY");
        US_STATES.put("north carolina", "NC");
        US_STATES.put("north dakota", "ND");
        US_STATES.put("ohio", "OH");
        US_STATES.put("oklahoma", "OK");
        US_STATES.put("oregon", "OR");
        US_STATES.put("pennsylvania", "PA");
        US_STATES.put("rhode island", "RI");
        US_STATES.put("south carolina", "SC");
        US_STATES.put("south dakota", "SD");
        US_STATES.put("tennessee", "TN");
        US_STATES.put("texas", "TX");
        US_STATES.put("utah", "UT");
        US_STATES.put("vermont", "VT");
        US_STATES.put("virginia", "VA");
        US_STATES.put("washington", "WA");
        US_STATES.put("west virginia", "WV");
        US_STATES.put("wisconsin", "WI");
        US_STATES.put("wyoming", "WY");
    }

    public OpenWeatherMapClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenWeatherMap API key is required");
        }
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get current weather by location name
     * Examples: "Santa Clara, CA", "New York", "London, UK"
     * Also handles: "Santa Clara, California" → "Santa Clara, CA, US"
     */
    public WeatherData getWeatherByLocation(String location) {
        try {
            // Normalize location string (convert state names to codes)
            String normalizedLocation = normalizeLocation(location);

            String url = String.format("%s/weather?q=%s&appid=%s&units=imperial",
                    BASE_URL, normalizedLocation, apiKey);

            log.info("Fetching weather for location: {} (normalized: {})", location, normalizedLocation);

            String json = makeRequest(url);
            return parseWeatherResponse(json, location);

        } catch (Exception e) {
            log.error("Error fetching weather for {}: {}", location, e.getMessage());
            return createErrorWeather(location, e.getMessage());
        }
    }

    /**
     * Get current weather by ZIP code (US only)
     */
    public WeatherData getWeatherByZipCode(String zipCode) {
        try {
            String url = String.format("%s/weather?zip=%s,US&appid=%s&units=imperial",
                    BASE_URL, zipCode, apiKey);

            log.info("Fetching weather for ZIP code: {}", zipCode);

            String json = makeRequest(url);
            return parseWeatherResponse(json, zipCode);

        } catch (Exception e) {
            log.error("Error fetching weather for ZIP {}: {}", zipCode, e.getMessage());
            return createErrorWeather(zipCode, e.getMessage());
        }
    }

    /**
     * Get 5-day forecast by location
     */
    public ForecastData getForecast(String location) {
        try {
            // Normalize location string (convert state names to codes)
            String normalizedLocation = normalizeLocation(location);

            String url = String.format("%s/forecast?q=%s&appid=%s&units=imperial&cnt=8",
                    BASE_URL, normalizedLocation, apiKey);

            log.info("Fetching forecast for location: {} (normalized: {})", location, normalizedLocation);

            String json = makeRequest(url);
            return parseForecastResponse(json, location);

        } catch (Exception e) {
            log.error("Error fetching forecast for {}: {}", location, e.getMessage());
            return new ForecastData(location, "ERROR", e.getMessage(), null);
        }
    }

    /**
     * Normalize location string for OpenWeatherMap API
     *
     * Handles:
     * - "Santa Clara, California" → "Santa Clara, CA, US"
     * - "New York, NY" → "New York, NY, US"
     * - "Seattle, Washington" → "Seattle, WA, US"
     * - "London, UK" → "London, UK" (unchanged)
     */
    private String normalizeLocation(String location) {
        if (location == null || location.isBlank()) {
            return location;
        }

        // Split by comma
        String[] parts = location.split(",");
        if (parts.length == 0) {
            return location;
        }

        // Trim all parts
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        // If only city name (e.g., "Seattle"), return as-is
        if (parts.length == 1) {
            return location;
        }

        String city = parts[0];
        String stateOrCountry = parts[1].toLowerCase();

        // Check if it's a US state name
        String stateCode = US_STATES.get(stateOrCountry);

        if (stateCode != null) {
            // Convert state name to code and append US
            // "Santa Clara, California" → "Santa Clara, CA, US"
            return String.format("%s, %s, US", city, stateCode);
        }

        // Check if it's already a 2-letter state code
        if (stateOrCountry.length() == 2 && US_STATES.containsValue(stateOrCountry.toUpperCase())) {
            // Already a state code, just append US
            // "Santa Clara, CA" → "Santa Clara, CA, US"
            return String.format("%s, %s, US", city, stateOrCountry.toUpperCase());
        }

        // Check if there's already a country code (3rd part)
        if (parts.length >= 3) {
            // Already has country code, return as-is
            // "Santa Clara, CA, US" → "Santa Clara, CA, US"
            return location;
        }

        // Not a US state, return as-is
        // "London, UK" → "London, UK"
        return location;
    }

    private String makeRequest(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("HTTP " + response.code() + ": " + errorBody);
            }

            return response.body().string();
        }
    }

    private WeatherData parseWeatherResponse(String json, String location) throws IOException {
        JsonNode root = objectMapper.readTree(json);

        // Extract weather data
        JsonNode main = root.get("main");
        JsonNode weather = root.get("weather").get(0);
        JsonNode wind = root.get("wind");
        JsonNode clouds = root.get("clouds");
        JsonNode sys = root.get("sys");

        double temperature = main.get("temp").asDouble();
        double feelsLike = main.get("feels_like").asDouble();
        double humidity = main.get("humidity").asDouble();
        double pressure = main.get("pressure").asDouble();

        double windSpeed = wind.get("speed").asDouble();
        int windDeg = wind.has("deg") ? wind.get("deg").asInt() : 0;

        String condition = weather.get("main").asText();
        String description = weather.get("description").asText();
        int cloudiness = clouds.get("all").asInt();

        String cityName = root.get("name").asText();
        String country = sys.get("country").asText();

        double lat = root.get("coord").get("lat").asDouble();
        double lon = root.get("coord").get("lon").asDouble();

        long timestamp = root.get("dt").asLong();

        log.info("Weather retrieved: {} - {}, {}°F", cityName, condition, temperature);

        return new WeatherData(
                cityName + ", " + country,
                lat,
                lon,
                temperature,
                feelsLike,
                humidity,
                pressure,
                windSpeed,
                windDeg,
                cloudiness,
                condition,
                capitalizeWords(description),
                timestamp,
                "SUCCESS"
        );
    }

    private ForecastData parseForecastResponse(String json, String location) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode list = root.get("list");

        StringBuilder forecast = new StringBuilder();

        for (int i = 0; i < Math.min(list.size(), 8); i++) {
            JsonNode item = list.get(i);
            String dt = item.get("dt_txt").asText();
            double temp = item.get("main").get("temp").asDouble();
            String desc = item.get("weather").get(0).get("description").asText();

            forecast.append(String.format("%s: %.1f°F, %s\n", dt, temp, desc));
        }

        String cityName = root.get("city").get("name").asText();

        return new ForecastData(cityName, "SUCCESS", null, forecast.toString());
    }

    private String capitalizeWords(String text) {
        String[] words = text.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    private WeatherData createErrorWeather(String location, String error) {
        return new WeatherData(
                location, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0,
                "ERROR", error, 0L, "ERROR"
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