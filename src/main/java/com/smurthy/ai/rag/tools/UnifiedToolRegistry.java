package com.smurthy.ai.rag.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * UNUSED - OPTION B ORCHESTRATION PATH
 *
 * Central registry of ALL available tools across all frameworks
 *
 * Provides unified catalog for agentic routing decisions
 * Uses lazy initialization to avoid circular dependencies with @AiService beans
 *
 * Currently using Option A (MasterAgent) instead. This is preserved for potential revert.
 */
@Service
@Deprecated // Using MasterAgent (Option A) - keeping this for potential revert
public class UnifiedToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(UnifiedToolRegistry.class);

    private final Map<String, ToolMetadata> toolCatalog = new HashMap<>();
    private final Map<ToolFramework, List<ToolMetadata>> toolsByFramework = new EnumMap<>(ToolFramework.class);
    private final List<ToolProvider> toolProviders;
    private volatile boolean initialized = false;

    public UnifiedToolRegistry(List<ToolProvider> toolProviders) {
        this.toolProviders = toolProviders;
        log.info("Unified Tool Registry created (lazy initialization mode)");
    }

    /**
     * Lazy initialization - populate catalog on first access
     */
    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }

        log.info("Initializing tool catalog from {} providers...", toolProviders.size());

        // Register all tools from all providers
        for (ToolProvider provider : toolProviders) {
            List<ToolMetadata> tools = provider.getTools();

            for (ToolMetadata tool : tools) {
                toolCatalog.put(tool.name(), tool);

                // Group by framework
                toolsByFramework
                    .computeIfAbsent(tool.framework(), k -> new ArrayList<>())
                    .add(tool);
            }

            log.info("Registered {} tools from {}", tools.size(), provider.getProviderName());
        }

        initialized = true;
        log.info("Tool Registry initialized with {} total tools from {} providers",
            toolCatalog.size(), toolProviders.size());
        log.info("Tools by framework: {}", getFrameworkSummary());
    }

    /**
     * Get all available tools as a formatted catalog for LLM consumption
     */
    public String getToolCatalogForLLM() {
        ensureInitialized();
        StringBuilder catalog = new StringBuilder();
        catalog.append("=== AVAILABLE TOOLS ===\n\n");

        // Group by category for better readability
        Map<String, List<ToolMetadata>> byCategory = toolCatalog.values().stream()
            .collect(Collectors.groupingBy(ToolMetadata::category));

        for (Map.Entry<String, List<ToolMetadata>> entry : byCategory.entrySet()) {
            catalog.append(String.format("## %s Tools\n\n", entry.getKey().toUpperCase()));

            for (ToolMetadata tool : entry.getValue()) {
                catalog.append(tool.toSummary()).append("\n");
            }
        }

        return catalog.toString();
    }

    /**
     * Get tool metadata by name
     */
    public Optional<ToolMetadata> getTool(String name) {
        ensureInitialized();
        return Optional.ofNullable(toolCatalog.get(name));
    }

    /**
     * Get all tools in a specific category
     */
    public List<ToolMetadata> getToolsByCategory(String category) {
        ensureInitialized();
        return toolCatalog.values().stream()
            .filter(t -> t.category().equalsIgnoreCase(category))
            .toList();
    }

    /**
     * Get all tools from a specific framework
     */
    public List<ToolMetadata> getToolsByFramework(ToolFramework framework) {
        ensureInitialized();
        return toolsByFramework.getOrDefault(framework, Collections.emptyList());
    }

    /**
     * Get total count of registered tools
     */
    public int getTotalToolCount() {
        ensureInitialized();
        return toolCatalog.size();
    }

    /**
     * Get summary of tools per framework
     */
    private Map<ToolFramework, Integer> getFrameworkSummary() {
        return toolsByFramework.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().size()
            ));
    }

    /**
     * Check if a tool exists
     */
    public boolean hasTool(String name) {
        ensureInitialized();
        return toolCatalog.containsKey(name);
    }
}