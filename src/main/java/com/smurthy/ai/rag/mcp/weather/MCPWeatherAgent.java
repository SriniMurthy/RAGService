package com.smurthy.ai.rag.mcp.weather;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface MCPWeatherAgent {

    @SystemMessage("""
        You are a helpful weather assistant.
        You have access to tools that can get the current weather for a given city or ZIP code.
        Based on the user's question, decide which tool to use and what parameters to pass to it.
        Your final answer should be a concise, human-readable summary of the weather data returned by the tool.
    """)
    String getWeatherData(String userQuery);
}