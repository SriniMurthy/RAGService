package com.smurthy.ai.rag.mcp.weather;


import com.smurthy.ai.rag.service.WeatherService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class WeatherTools {

    private final WeatherService weatherService;

    public WeatherTools(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Tool("Get the current weather for a specific location by name (e.g., 'San Francisco, CA')")
    public WeatherService.WeatherData getWeatherByLocation(String location) {
        return weatherService.getWeatherByLocation(location);
    }

    @Tool("Get the current weather for a specific US ZIP code")
    public WeatherService.WeatherData getWeatherByZipCode(String zipCode) {
        return weatherService.getWeatherByZipCode(zipCode);
    }
}