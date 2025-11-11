package com.smurthy.ai.rag.tools.providers;

import com.smurthy.ai.rag.tools.ToolFramework;
import com.smurthy.ai.rag.tools.ToolMetadata;
import com.smurthy.ai.rag.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Discovers all Spring AI @Bean function tools
 *
 * Scans ApplicationContext for Function beans
 */
@Component
public class SpringAIToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(SpringAIToolProvider.class);

    private final ApplicationContext context;

    public SpringAIToolProvider(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public List<ToolMetadata> getTools() {
        List<ToolMetadata> tools = new ArrayList<>();

        // Discover Function beans (from MarketFunctionConfiguration)
        Map<String, Function> functionBeans = context.getBeansOfType(Function.class);

        for (Map.Entry<String, Function> entry : functionBeans.entrySet()) {
            String beanName = entry.getKey();

            try {
                // Extract @Description annotation
                Description desc = context.findAnnotationOnBean(beanName, Description.class);
                String description = desc != null ? desc.value() : "No description available";

                tools.add(new ToolMetadata(
                    beanName,
                    description,
                    categorizeFromName(beanName),
                    "Spring AI Functions",
                    ToolFramework.SPRING_AI,
                    estimateLatency(beanName),
                    "FREE",
                    "99%"
                ));

                log.debug("Registered Spring AI tool: {}", beanName);

            } catch (Exception e) {
                log.warn("Failed to register Spring AI tool {}: {}", beanName, e.getMessage());
            }
        }

        log.info("Spring AI Tool Provider discovered {} tools", tools.size());
        return tools;
    }

    @Override
    public ToolFramework getFramework() {
        return ToolFramework.SPRING_AI;
    }

    /**
     * Categorize tool based on name heuristics
     */
    private String categorizeFromName(String name) {
        String lowerName = name.toLowerCase();

        if (lowerName.contains("weather")) return "weather";
        if (lowerName.contains("stock") || lowerName.contains("finance") ||
            lowerName.contains("quote") || lowerName.contains("market")) return "finance";
        if (lowerName.contains("news")) return "news";
        if (lowerName.contains("document") || lowerName.contains("rag")) return "documents";
        if (lowerName.contains("search")) return "search";

        return "general";
    }

    /**
     * Estimate latency based on tool type
     */
    private String estimateLatency(String name) {
        String lowerName = name.toLowerCase();

        // External API calls have higher latency
        if (lowerName.contains("realtime") || lowerName.contains("api")) return "200ms";
        if (lowerName.contains("yahoo") || lowerName.contains("finnhub")) return "150ms";

        // Most Spring AI functions are fast
        return "50ms";
    }
}