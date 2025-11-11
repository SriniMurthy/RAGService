package com.smurthy.ai.rag.mcp.weather;

import com.smurthy.ai.rag.mcp.MCPAgent;
import org.springframework.stereotype.Service;

/**
 * The MCP "Server" implementation for the Weather Agent.
 * This service is registered with the bean name "WeatherAgent" to be discoverable
 * by the MCPClient router. It delegates to MCPWeatherAgent which now has all
 * Spring AI tools wired in via Langchain4jAgentConfiguration.
 */
@Service("WeatherAgent") // The bean name MUST match the targetAgent name in the MCP request
public class MCPWeatherService implements MCPAgent {

    private final MCPWeatherAgent weatherAgent;

    public MCPWeatherService(MCPWeatherAgent weatherAgent) {
        this.weatherAgent = weatherAgent;
    }

    @Override
    public MCPResponse handleRequest(MCPRequest request) {
        // Validate that the action is one this agent can handle
        if (!"Search".equals(request.action())) {
            return new MCPResponse("Error", "Invalid action '" + request.action() + "' for WeatherAgent.");
        }

        // Extract the user's natural language query from the parameters
        String userQuery = request.parameters().get("Query");
        if (userQuery == null || userQuery.isBlank()) {
            return new MCPResponse("Error", "Missing 'Query' parameter for WeatherAgent search.");
        }

        // The LangChain4j agent now has all Spring AI tools wired in
        String agentResponse = weatherAgent.getWeatherData(userQuery);

        // Wrap the final, human-readable string in the standard MCP response
        return new MCPResponse("WeatherAgent", agentResponse);
    }
}
