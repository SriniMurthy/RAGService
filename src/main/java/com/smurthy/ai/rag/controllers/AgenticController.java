package com.smurthy.ai.rag.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * A controller that provides a single, intelligent endpoint for answering questions
 * by automatically selecting and calling the appropriate tools (e.g., for weather, stocks, or news).
 * Work In Progress....
 */
@RestController
@RequestMapping("/agent")
public class AgenticController {

    private static final Logger log = LoggerFactory.getLogger(AgenticController.class);
    private final ChatClient.Builder chatClientBuilder;

    // A set of all available real-time tools the agent can choose from.
    private static final Set<String> AVAILABLE_TOOLS = Set.of(
            "getYahooQuote",
            "getMarketNews",
            "getWeatherByLocation",
            "getWeatherByZipCode",
            "getMarketMovers"
    );

    public AgenticController(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    /**
     * A universal endpoint that uses an AI agent to answer natural language questions.
     * The agent can automatically choose and call tools for real-time data.
     *
     * @param question The user's question in natural language.
     * @return A coherent answer synthesized from the results of any tools that were called.
     */
    @GetMapping("/ask")
    public String askAgent(@RequestParam String question) {
        log.info("🤖 Received agentic query: '{}'", question);

        //  Define the System Prompt: This is the instruction manual for the agent.
        // It tells the agent its role, its capabilities, and how to choose the right tool.
        String systemPrompt = """
            You are a helpful AI assistant that answers questions by calling functions to get real-time information.
            You must use the provided tools to answer questions about weather, stock prices, or news.

            TOOL SELECTION GUIDE:
            - For questions about the weather in a specific location, use the 'getWeatherByLocation' or 'getWeatherByZipCode' tool.
            - For questions about the stock price of a specific company symbol (e.g., AAPL, GOOGL), use the 'getYahooQuote' tool.
            - For questions about news on any topic (e.g., "latest news on AI", "market news"), use the 'getMarketNews' tool.
            - For questions about top market movers (gainers/losers), use the 'getMarketMovers' tool.

            If a question involves multiple topics (e.g., weather and stocks), you must call all the necessary tools.
            Synthesize the results from all tool calls into a single, coherent, human-readable answer.
            """;

        // Build the ChatClient for this specific request.
        // We provide it with the system prompt and the list of tools it's allowed to use.
        ChatClient client = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultToolNames(AVAILABLE_TOOLS.toArray(new String[0]))
                .build();

        // Call the agent and return the response.
        // Spring AI handles the entire multi-step process (tool selection, execution, and final response generation) automatically.
        log.info("...Agent is thinking and selecting tools...");
        String response = client.prompt()
                .user(question)
                .call()
                .content();

        log.info("Agent generated final response.");
        return response;
    }
}