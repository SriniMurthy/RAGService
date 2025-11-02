package com.smurthy.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight Weather Microservice
 *
 * Architecture:
 * - Uses Javalin (not Spring Boot) for minimal overhead
 * - Delegates to Langchain4j @Tool definitions (WeatherTools)
 * - Positioned for future MCP server migration
 * - Maintains REST API for current integration
 *
 * Performance:
 * - Startup: 1-2 seconds
 * - Memory: ~30MB
 * - Container size: ~50MB
 *
 * Endpoints:
 * - GET /weather/location?q=Santa Clara, CA
 * - GET /weather/zipcode?zip=95051
 * - GET /weather/forecast?q=Santa Clara, CA
 * - GET /health
 */
public class WeatherServiceApp {

    private static final Logger log = LoggerFactory.getLogger(WeatherServiceApp.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        // Get configuration from environment
        String apiKey = System.getenv("OPENWEATHER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.error("OPENWEATHER_API_KEY environment variable not set!");
            System.exit(1);
        }

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));

        // Initialize Langchain4j Weather Tools
        WeatherTools weatherTools = new WeatherTools(apiKey);

        // Create Javalin app
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.http.defaultContentType = "application/json";
        }).start(port);

        log.info("╔══════════════════════════════════════════╗");
        log.info("║   Weather Service Started                ║");
        log.info("╚══════════════════════════════════════════╝");
        log.info("Port: {}", port);
        log.info("Framework: Langchain4j @Tool definitions");
        log.info("Provider: OpenWeatherMap");
        log.info("Future: MCP-ready when Spring AI dependencies stabilize");
        log.info("Endpoints:");
        log.info("  - GET /weather/location?q=<location>");
        log.info("  - GET /weather/zipcode?zip=<zipcode>");
        log.info("  - GET /weather/forecast?q=<location>");
        log.info("  - GET /health");

        // Health check endpoint
        app.get("/health", ctx -> {
            ctx.json(new HealthResponse("UP", "Weather service is running"));
        });

        // Get weather by location - Delegates to Langchain4j @Tool
        app.get("/weather/location", ctx -> {
            String location = ctx.queryParam("q");
            if (location == null || location.isBlank()) {
                ctx.status(400).json(new ErrorResponse("Missing 'q' parameter"));
                return;
            }

            try {
                WeatherTools.WeatherResult result = weatherTools.getWeatherByLocation(location);

                if ("ERROR".equals(result.status())) {
                    ctx.status(500).json(new ErrorResponse(result.error()));
                } else {
                    ctx.json(result);
                }
            } catch (Exception e) {
                log.error("Error processing location request: {}", e.getMessage());
                ctx.status(500).json(new ErrorResponse(e.getMessage()));
            }
        });

        // Get weather by ZIP code - Delegates to Langchain4j @Tool
        app.get("/weather/zipcode", ctx -> {
            String zipCode = ctx.queryParam("zip");
            if (zipCode == null || zipCode.isBlank()) {
                ctx.status(400).json(new ErrorResponse("Missing 'zip' parameter"));
                return;
            }

            try {
                WeatherTools.WeatherResult result = weatherTools.getWeatherByZipCode(zipCode);

                if ("ERROR".equals(result.status())) {
                    ctx.status(500).json(new ErrorResponse(result.error()));
                } else {
                    ctx.json(result);
                }
            } catch (Exception e) {
                log.error("Error processing ZIP code request: {}", e.getMessage());
                ctx.status(500).json(new ErrorResponse(e.getMessage()));
            }
        });

        // Get forecast - Delegates to Langchain4j @Tool
        app.get("/weather/forecast", ctx -> {
            String location = ctx.queryParam("q");
            if (location == null || location.isBlank()) {
                ctx.status(400).json(new ErrorResponse("Missing 'q' parameter"));
                return;
            }

            try {
                WeatherTools.ForecastResult result = weatherTools.getForecast(location);

                if ("ERROR".equals(result.status())) {
                    ctx.status(500).json(new ErrorResponse(result.error()));
                } else {
                    ctx.json(result);
                }
            } catch (Exception e) {
                log.error("Error processing forecast request: {}", e.getMessage());
                ctx.status(500).json(new ErrorResponse(e.getMessage()));
            }
        });

        // Exception handler
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled exception", e);
            ctx.status(500).json(new ErrorResponse("Internal server error: " + e.getMessage()));
        });

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down weather service...");
            app.stop();
        }));
    }

    // Response records
    record HealthResponse(String status, String message) {}
    record ErrorResponse(String error) {}
}