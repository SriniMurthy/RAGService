package com.smurthy.ai.rag.config;

import com.smurthy.ai.rag.agents.*;
import com.smurthy.ai.rag.mcp.finance.MCPFinanceAgent;
import com.smurthy.ai.rag.mcp.weather.MCPWeatherAgent;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for MasterAgent Tools
 *
 * Provides tool beans that the MasterAgent can call to delegate to specialized MCP agents.
 * This enables true agentic routing - the LLM decides which agent to call via function calling,
 * not hardcoded if/else logic.
 *
 * Architecture:
 * MasterAgent → MCPFinanceAgent/MCPWeatherAgent/ResearchAgent → ToolOrchestrator → Spring AI tools
 */
@Configuration
public class MasterAgentConfiguration {

    /**
     * Manually create MasterAgent bean since @AiService auto-discovery doesn't work
     */
    @Bean
    public MasterAgent masterAgent(
            dev.langchain4j.model.chat.ChatLanguageModel chatModel,
            AgentTools agentTools) {
        return dev.langchain4j.service.AiServices.builder(MasterAgent.class)
                .chatLanguageModel(chatModel)
                .tools(agentTools)
                .build();
    }

    /**
     * Tool for MasterAgent to call specialized MCP agents
     */
    @Bean
    public AgentTools agentTools(
            MCPFinanceAgent mcpFinanceAgent,
            MCPWeatherAgent mcpWeatherAgent,
            ResearchAgent researchAgent) {
        return new AgentTools(mcpFinanceAgent, mcpWeatherAgent, researchAgent);
    }

    /**
     * Tool class that MasterAgent can use to call specialized MCP agents
     */
    public static class AgentTools {
        private final MCPFinanceAgent mcpFinanceAgent;
        private final MCPWeatherAgent mcpWeatherAgent;
        private final ResearchAgent researchAgent;

        public AgentTools(MCPFinanceAgent mcpFinanceAgent, MCPWeatherAgent mcpWeatherAgent, ResearchAgent researchAgent) {
            this.mcpFinanceAgent = mcpFinanceAgent;
            this.mcpWeatherAgent = mcpWeatherAgent;
            this.researchAgent = researchAgent;
        }

        @Tool("Call FinancialAgent to answer questions about stocks, market data, financial analysis - has access to Finnhub, Alpha Vantage, Yahoo Finance")
        public String callFinancialAgent(String question) {
            return mcpFinanceAgent.getFinancialData(question);
        }

        @Tool("Call ResearchAgent to search uploaded documents (PDFs, Excel, Word, scanned images, news articles, reports)")
        public String callResearchAgent(String question) {
            AgentResult result = researchAgent.execute(question, "master-agent");
            return result.result();
        }

        @Tool("Call WeatherAgent to get weather forecasts and current conditions")
        public String callWeatherAgent(String question) {
            return mcpWeatherAgent.getWeatherData(question);
        }

        @Tool("Call ConversationAgent for greetings and casual conversation")
        public String callConversationAgent(String question) {
            // Simple conversational response
            return "Hello! How can I help you today?";
        }
    }
}