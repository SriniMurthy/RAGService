package com.smurthy.ai.rag;

import com.smurthy.ai.rag.config.EmbeddingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication(exclude = {
		org.springframework.ai.mcp.server.autoconfigure.McpServerAutoConfiguration.class,
		org.springframework.ai.mcp.client.autoconfigure.McpClientAutoConfiguration.class
})
@EnableRetry
@EnableConfigurationProperties(EmbeddingConfig.class)
public class DynamicRAGApplication {

	public static void main(String[] args) {
		SpringApplication.run(DynamicRAGApplication.class, args);
	}

}
