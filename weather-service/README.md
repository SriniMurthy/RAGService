# Weather Microservice

Lightweight weather microservice using **Langchain4j** and **OpenWeatherMap API**.

Decoupled from the main RAG application for better architecture and accurate weather data.

##  Features

-  **Lightweight**: Javalin (not Spring Boot) - 30MB memory, 1-2s startup
-  **Accurate**: OpenWeatherMap API (better than Open-Meteo)
-  **Free tier**: 1,000 calls/day, 60 calls/minute
-  **Containerized**: Docker support with health checks
-  **RESTful API**: Simple HTTP endpoints

##  Quick Start

### 1. Get OpenWeatherMap API Key

```bash
# Free API key from OpenWeatherMap
https://openweathermap.org/api

# Add to your environment
export OPENWEATHER_API_KEY=your_key_here
```

### 2. Run Locally

```bash
cd weather-service

# Build
mvn clean package

# Run
java -jar target/weather-service-*.jar
```

Service starts on **http://localhost:8081**

### 3. Run with Docker

```bash
# From root directory
docker-compose up weather-service
```

##  API Endpoints

### Get Weather by Location

```bash
GET /weather/location?q=Santa Clara, CA

Response:
{
  "location": "Santa Clara, US",
  "temperature": 72.5,
  "feelsLike": 70.2,
  "humidity": 65.0,
  "pressure": 1013.0,
  "windSpeed": 5.5,
  "windDirection": 180,
  "cloudiness": 20,
  "condition": "Sunny",
  "description": "Clear Sky",
  "timestamp": 1699564800,
  "status": "SUCCESS"
}
```

### Get Weather by ZIP Code

```bash
GET /weather/zipcode?zip=95051

Response: (same format as above)
```

### Get Forecast

```bash
GET /weather/forecast?q=Santa Clara, CA

Response:
{
  "location": "Santa Clara",
  "status": "SUCCESS",
  "error": null,
  "forecast": "2025-11-01 12:00:00: 72.5°F, Clear Sky\n..."
}
```

### Health Check

```bash
GET /health

Response:
{
  "status": "UP",
  "message": "Weather service is running"
}
```

## 🏗️ Architecture

```
Main RAG App (Spring AI)
     ↓ HTTP
Weather Microservice (Langchain4j + Javalin)
     ↓ HTTPS
OpenWeatherMap API
```

**Benefits:**
- Decoupled from main app
- Can restart independently
- Easy to swap weather providers
- No Spring Boot overhead

##  Docker Integration

The weather service is part of the main `docker-compose.yaml`:

```yaml
weather-service:
  build: ./weather-service
  ports:
    - "8081:8081"
  environment:
    - OPENWEATHER_API_KEY=${OPENWEATHER_API_KEY}
  healthcheck:
    test: ["CMD", "wget", "--spider", "http://localhost:8081/health"]
```

Start all services:

```bash
docker-compose up
```

##  Configuration

### Environment Variables

- `OPENWEATHER_API_KEY` (required) - Your OpenWeatherMap API key
- `PORT` (optional, default: 8081) - Server port

### Main App Configuration

In main app's `application.yaml`:

```yaml
weather:
  service:
    url: http://localhost:8081  # or http://weather-service:8081 in Docker
```

## 📊 Performance

- **Startup**: 1-2 seconds
- **Memory**: ~30MB
- **Container size**: ~50MB (Alpine-based)
- **Latency**: ~200-500ms per API call

##  Development

### Build

```bash
mvn clean package
```

### Run Tests

```bash
mvn test
```

### Local Development

```bash
# Set API key
export OPENWEATHER_API_KEY=your_key

# Run
mvn exec:java
```

## 📝 Notes

- **Free tier limits**: 1,000 calls/day, 60 calls/minute
- **Rate limiting**: Built into OpenWeatherMap client
- **Error handling**: Returns ERROR status with message on failures
- **Geocoding**: Uses OSM Nominatim (free, no key needed)

##  Integration

The main RAG app calls this service via `WeatherClient`:

```java
@Service
public class WeatherClient {
    private final RestClient restClient;

    public WeatherData getWeatherByLocation(String location) {
        return restClient.get()
            .uri("/weather/location?q={location}", location)
            .retrieve()
            .body(WeatherData.class);
    }
}
```

## 🚨 Troubleshooting

### "API key not set"
```bash
export OPENWEATHER_API_KEY=your_key_here
```

### "Connection refused"
Check if service is running:
```bash
curl http://localhost:8081/health
```

### "Location not found"
Use specific location names:
-  "Santa Clara, CA"
-  "Santa Clara, California"
-  "Santa Clara" (ambiguous)

##  References

- OpenWeatherMap API: https://openweathermap.org/api
- Langchain4j: https://github.com/langchain4j/langchain4j
- Javalin: https://javalin.io/