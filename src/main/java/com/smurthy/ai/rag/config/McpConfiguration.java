package com.smurthy.ai.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP Configuration (Model Context Protocol)
 *
 * TODO: Complete Wikipedia MCP integration with 0.15.0 SDK
 * Temporarily disabled to avoid compilation errors.
 */
@Configuration
public class McpConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpConfiguration.class);

    @Bean
    public List<ToolCallback> wikipediaMcpTools() {
        log.info("MCP tools temporarily disabled (integration in progress)");
        return List.of();
    }
}
