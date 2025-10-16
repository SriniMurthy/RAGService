# Unified Agentic Controller - Architecture Guide

## Overview

This document explains how the **UnifiedAgenticController** integrates with the configuration architecture, specifically focusing on how **RAGFunctionConfiguration** fits into the existing setup.

---

## Table of Contents

1. [Configuration Architecture Map](#configuration-architecture-map)
2. [Configuration Types](#configuration-types)
3. [How Configurations Work Together](#how-configurations-work-together)
4. [Complete Tool Inventory](#complete-tool-inventory)
5. [Spring Boot Tool Discovery](#spring-boot-tool-discovery)
6. [Why RAGFunctionConfiguration is Needed](#why-ragfunctionconfiguration-is-needed)
7. [Configuration Summary Table](#configuration-summary-table)

---

## Configuration Architecture Map

The application is organized into three main configuration layers:

```
┌─────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT APPLICATION                      │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐   ┌──────────────────┐   ┌─────────────────┐
│ INFRASTRUCTURE│   │  FUNCTION TOOLS  │   │  RAG PIPELINE   │
│  CONFIGS      │   │  (for LLM)       │   │  CONFIGS        │
└───────────────┘   └──────────────────┘   └─────────────────┘
```

---

## Configuration Types

### 1. Infrastructure Configurations

These configure **HOW** the RAG system processes documents:

| Configuration | Purpose |
|---------------|---------|
| **RAGConfiguration** | ChatMemory setup (20 message window) |
| **DocumentProcessingConfig** | Text splitter configuration |
| **EmbeddingConfig** | Batch size and parallel embedding settings |
| **FileWatcherConfig** | Monitors `/tmp/ragdocs` for new files |
| **StartupIngestionConfig** | Ingests documents on application startup |
| **RetryConfig** | Retry policies for API calls |

**Role**: These set up the RAG infrastructure - how documents are processed, embedded, and stored in the vector database.

---

### 2. Function Tool Configurations

These define **TOOLS/FUNCTIONS** that the LLM can call as part of function calling:

#### A. MarketFunctionConfiguration (Existing)

**Purpose**: External API tools for real-time data

**Tools Provided**:
- `getYahooQuote` - Stock quotes from Yahoo Finance
- `getHistoricalPrices` - Historical stock data
- `getMarketMovers` - Top stock gainers/losers
- `getMarketNews` - Latest news from Google News RSS
- `getHeadlinesByCategory` - Category-specific news
- `getWeatherByLocation` - Weather data by location
- `getWeatherByZipCode` - Weather data by ZIP code
- `analyzeFinancialRatios` - Financial analysis (P/E, ROE, Debt/Equity)
- `compareStocks` - Side-by-side stock comparison
- `calculatePortfolioMetrics` - Portfolio analytics
- `getEconomicIndicators` - Economic data (GDP, inflation)
- `predictMarketTrend` - ML-based market predictions

**Total**: 12 external API tools

#### B. RAGFunctionConfiguration (NEW)

**Purpose**: Document query tools - search internal documents with rich context

**Tools Provided**:
- `queryDocuments` - Search ALL documents (Excel, PDF, Word, text files)
- `queryDocumentsAdvanced` - Advanced search with custom similarity threshold and result count
- `queryDocumentsByYear` - Search documents from a specific year (temporal filtering)
- `queryDocumentsByDate` - Flexible date search (year OR specific date in MM-dd-yyyy format)
- `queryDocumentsByDateRange` - Search within a date range (ISO dates: YYYY-MM-DD)

**Total**: 5 RAG/temporal query tools

**Key Features**:
- Returns rich context including:
  - Source file names (e.g., `Q2_Sales_Report.xlsx`)
  - Excel sheet names (e.g., `"Revenue Data"`)
  - Sheet indices (e.g., Sheet #2)
  - Document types (Excel Spreadsheet, PDF Document, Word Document)
  - Date ranges from metadata
  - Full metadata for debugging

**Example Response Structure**:
```
=== DOCUMENT CHUNK ===
Source: Q2_Sales_Report.xlsx
Type: Excel Spreadsheet
Sheet: Revenue Data (Sheet #2)
Date Range: 2023-04-01 to 2023-06-30

CONTENT:
[Actual spreadsheet data with tab-separated columns...]
```

---

## How Configurations Work Together

### Before: The Old Approach

```java
// RAGDataController (OLD)
this.chatClientBuilder = builder
    .defaultAdvisors(
        RetrievalAugmentationAdvisor  // ← AUTOMATIC RAG
    );
```

**Problem**: The RAG advisor would automatically inject documents into every query, which interfered with pure function calling for stocks/weather/news queries.

**Example Issue**:
- User asks: "Stock price of AAPL"
- RAG advisor searches documents first
- Finds nothing relevant
- Confuses the LLM
- Result: Inconsistent function calling behavior

---

### After: The New Unified Approach

```java
// UnifiedAgenticController (NEW)
this.toolsOnlyBuilder = builder
    .defaultAdvisors(
        MessageChatMemoryAdvisor  // ← Only memory, NO automatic RAG
    );

// RAG is now a TOOL the LLM can choose to call
ChatClient client = toolsOnlyBuilder
    .defaultToolNames(ALL_TOOLS)  // ← Includes BOTH MarketFunctionConfiguration
                                   //   AND RAGFunctionConfiguration tools
    .build();
```

**Advantage**: RAG becomes a deliberate choice by the LLM, not an automatic behavior.

**Example Success**:
- User asks: "Stock price of AAPL"
- LLM analyzes: "This is a stock price question"
- LLM chooses: `getYahooQuote("AAPL")`
- Result: ✅ Direct API call, clean response

- User asks: "Who is John Doe?"
- LLM analyzes: "This is a biographical question"
- LLM chooses: `queryDocuments({question: "Who is John Doe"})`
- Result: ✅ Searches resumes/documents, returns with source citations

---

## Complete Tool Inventory

When calling `/unified/ask`, the LLM has access to **ALL** these tools:

### From MarketFunctionConfiguration (12 tools)

| Tool Name | Description |
|-----------|-------------|
| `getYahooQuote` | Real-time stock quotes from Yahoo Finance (FREE, unlimited) |
| `getHistoricalPrices` | Historical stock price data for trend analysis |
| `getMarketMovers` | Today's biggest stock price movers (top gainers/losers) |
| `getMarketNews` | Latest news on ANY topic from Google News RSS |
| `getHeadlinesByCategory` | News headlines by category (BUSINESS, TECH, SPORTS, etc.) |
| `getWeatherByLocation` | Current weather for any location (FREE, unlimited) |
| `getWeatherByZipCode` | Weather data by US ZIP code |
| `analyzeFinancialRatios` | Financial analysis (P/E ratio, ROE, Debt/Equity) |
| `compareStocks` | Side-by-side comparison of multiple stocks |
| `calculatePortfolioMetrics` | Portfolio analytics (returns, volatility, Sharpe ratio) |
| `getEconomicIndicators` | Economic data (GDP, inflation, unemployment rates) |
| `predictMarketTrend` | ML-based market trend predictions |

### From RAGFunctionConfiguration (5 tools)

| Tool Name | Description |
|-----------|-------------|
| `queryDocuments` | Search ALL documents (Excel, PDF, Word, text) with full context |
| `queryDocumentsAdvanced` | Advanced search with custom similarity threshold (0.0-1.0) and topK (1-20) |
| `queryDocumentsByYear` | Search documents from a specific year (e.g., 2021, 2023) |
| `queryDocumentsByDate` | Flexible date search: year ("2021") OR specific date ("01-15-2021") |
| `queryDocumentsByDateRange` | Search within date range (ISO format: "2021-01-01" to "2021-12-31") |

**Grand Total**: **17 tools** available for intelligent routing

---

## Spring Boot Tool Discovery

Spring AI automatically discovers `@Bean` methods with function signatures and makes them available as tools.

### How It Works

#### Step 1: Define Tools in Configuration Classes

```java
@Configuration
public class MarketFunctionConfiguration {

    @Bean("getYahooQuote")  // ← Spring registers this as a tool
    @Description("Get stock quotes from Yahoo Finance...")  // ← Description shown to LLM
    public Function<YahooQuoteRequest, YahooQuoteResponse> getYahooQuote() {
        return request -> {
            // Implementation that calls Yahoo Finance API
            return new YahooQuoteResponse(...);
        };
    }
}

@Configuration
public class RAGFunctionConfiguration {

    @Bean("queryDocuments")  // ← Spring registers this as a tool too
    @Description("Search ALL documents in the system (Excel, PDF, Word)...")
    public Function<DocumentQueryRequest, DocumentQueryResponse> queryDocuments() {
        return request -> {
            // Implementation that queries VectorStore
            return new DocumentQueryResponse(...);
        };
    }
}
```

#### Step 2: Reference Tools by Name in Controller

```java
private static final Set<String> ALL_TOOLS = Set.of(
    // RAG tools
    "queryDocuments",
    "queryDocumentsByYear",
    // ...

    // External API tools
    "getYahooQuote",
    "getMarketNews",
    // ...
);

ChatClient client = toolsOnlyBuilder
    .defaultToolNames(ALL_TOOLS)  // ← Reference by bean name
    .build();
```

#### Step 3: Spring AI Auto-Discovery Process

When you call `.defaultToolNames(ALL_TOOLS)`, Spring AI:

1. **Looks up** each tool name from the Spring application context
2. **Finds** the `@Bean` method with that name
3. **Reads** the `@Description` annotation for LLM context
4. **Extracts** the function signature (request/response types)
5. **Provides** it to the LLM as an available function with full type information

**Result**: The LLM knows about all 17 tools and can call them by name!

---

## Why RAGFunctionConfiguration is Needed

### The Problem Without It

**Old Approach**: RAG advisor always runs automatically

```java
ChatClient client = builder
    .defaultAdvisors(RetrievalAugmentationAdvisor)  // ← Always searches documents
    .build();
```

**Example Issue**:
```
Question: "Stock price of AAPL"

Process:
1. RAG advisor automatically searches document store
2. Finds nothing relevant about "AAPL stock price"
3. LLM gets confused with empty document context
4. May or may not call getYahooQuote function
5. Inconsistent behavior

Result: ❌ Unreliable function calling
```

---

### The Solution With RAGFunctionConfiguration

**New Approach**: RAG is a TOOL that the LLM chooses

```java
ChatClient client = toolsOnlyBuilder
    .defaultToolNames(["queryDocuments", "getYahooQuote", ...])
    .build();
```

**Example Success**:
```
Question: "Stock price of AAPL"

LLM Decision Process:
1. Analyzes question: "This is asking for real-time stock data"
2. Reviews available tools
3. Chooses: getYahooQuote (not queryDocuments)
4. Calls: getYahooQuote("AAPL")
5. Returns: Current stock price with change percentage

Result: ✅ Direct, clean API call
```

```
Question: "Who is John Doe?"

LLM Decision Process:
1. Analyzes question: "This is a biographical question"
2. Reviews available tools
3. Chooses: queryDocuments (not getYahooQuote)
4. Calls: queryDocuments({question: "Who is John Doe"})
5. Returns: Resume data with source citations

Result: ✅ Searches documents, cites sources
```

```
Question: "Compare AAPL stock with my 2022 portfolio"

LLM Decision Process:
1. Analyzes question: "This needs BOTH real-time data AND historical documents"
2. Reviews available tools
3. Chooses: MULTIPLE tools
4. Calls:
   - getYahooQuote("AAPL") for current price
   - queryDocumentsByYear({question: "portfolio", year: 2022}) for historical data
5. Synthesizes: Combines both results into comprehensive answer

Result: ✅ Hybrid query using multiple sources
```

---

## Configuration Dependency Graph

This diagram shows how the UnifiedAgenticController depends on various configurations:

```
UnifiedAgenticController
    │
    ├──→ ChatClient.Builder (from Spring AI auto-configuration)
    │
    ├──→ ChatMemory (from RAGConfiguration)
    │
    └──→ Tools (auto-discovered @Bean methods):
         │
         ├──→ MarketFunctionConfiguration
         │    │
         │    ├── getYahooQuote (uses YahooFinanceService)
         │    ├── getMarketNews (uses NewsService)
         │    ├── getWeatherByLocation (uses WeatherService)
         │    ├── analyzeFinancialRatios (uses MarketDataService)
         │    └── ... (8 more external API tools)
         │
         └──→ RAGFunctionConfiguration
              │
              ├── queryDocuments (uses VectorStore)
              ├── queryDocumentsByYear (uses TemporalQueryService)
              ├── queryDocumentsByDate (uses TemporalQueryService)
              ├── queryDocumentsByDateRange (uses TemporalQueryService)
              └── queryDocumentsAdvanced (uses VectorStore)
```

### Service Layer Dependencies

The function configurations delegate to service layers:

**MarketFunctionConfiguration uses**:
- `YahooFinanceService` - Yahoo Finance API integration
- `NewsService` - Google News RSS integration
- `WeatherService` - Weather API integration
- `MarketDataService` - Market data aggregation
- `CompositeStockQuoteProvider` - Multi-provider stock quotes with fallback

**RAGFunctionConfiguration uses**:
- `VectorStore` - PGVector database for semantic search
- `TemporalQueryService` - Date-filtered document queries
- `EmbeddingModel` - OpenAI embeddings for queries

---

## Configuration Summary Table

| Configuration | Type | Purpose | Used By | Provides |
|---------------|------|---------|---------|----------|
| **RAGConfiguration** | Infrastructure | ChatMemory setup (20 messages) | All controllers | `ChatMemory` bean |
| **DocumentProcessingConfig** | Infrastructure | Text splitting configuration | Document ingestion | `TextSplitter` bean |
| **EmbeddingConfig** | Infrastructure | Batch size, parallel embeddings | Document ingestion | Embedding settings |
| **FileWatcherConfig** | Infrastructure | File monitoring (polls `/tmp/ragdocs`) | Background service | File watcher service |
| **StartupIngestionConfig** | Infrastructure | Startup document loading | Application startup | Document ingestion runner |
| **RetryConfig** | Infrastructure | API retry logic (exponential backoff) | All API calls | Retry templates |
| **MarketFunctionConfiguration** | **Function Tools** | Stock/weather/news tools | UnifiedAgenticController | 12 external API tools |
| **RAGFunctionConfiguration** | **Function Tools** | Document query tools | UnifiedAgenticController | 5 RAG/temporal tools |

---

## Key Architectural Principles

### 1. Separation of Concerns

- **Infrastructure configs** handle system setup (embeddings, file watching, retry logic)
- **Function tool configs** define LLM capabilities (what can be called)
- **Controllers** orchestrate tool usage (how to combine tools)

### 2. Tool-Based Architecture

Instead of hardcoding RAG into the request flow, we expose it as a tool:
- **Flexibility**: LLM decides when to search documents
- **Composability**: Can combine RAG with external APIs
- **Transparency**: Clear which tools were called

### 3. Spring Boot Auto-Discovery

No manual wiring required:
- Define `@Bean` methods in `@Configuration` classes
- Spring AI automatically discovers them
- Reference by name in `.defaultToolNames(...)`

### 4. Rich Context Response

RAGFunctionConfiguration returns comprehensive metadata:
- Source file names and paths
- Excel sheet names and indices
- Document types (Excel, PDF, Word)
- Date ranges from metadata
- Full content with formatting preserved

---

## Usage Examples

### Example 1: Pure External API Query

```bash
curl "http://localhost:8080/RAGJava/unified/ask?question=Stock+price+of+AAPL"
```

**LLM Decision**:
- Chooses: `getYahooQuote`
- Calls: `getYahooQuote("AAPL")`

**Response**:
```json
{
  "answer": "The current stock price of AAPL (Apple Inc.) is $182.45, up $2.30 (+1.28%) today.",
  "totalToolsAvailable": 17,
  "executionTimeMs": 1243
}
```

---

### Example 2: Pure Document Query

```bash
curl "http://localhost:8080/RAGJava/unified/ask?question=Who+is+Srinivas+Murthy"
```

**LLM Decision**:
- Chooses: `queryDocuments`
- Calls: `queryDocuments({question: "Who is Srinivas Murthy"})`

**Response**:
```json
{
  "answer": "According to the srinivas_resume.pdf file, Srinivas Murthy is a Senior Software Engineer with 10 years of experience in distributed systems and AI. He worked at Google (2015-2020) and currently works at Zscaler as a Principal Engineer.",
  "totalToolsAvailable": 17,
  "executionTimeMs": 2156
}
```

**Note**: The response includes source citations from the document metadata.

---

### Example 3: Temporal Query

```bash
curl "http://localhost:8080/RAGJava/unified/ask?question=What+projects+did+I+work+on+in+2021"
```

**LLM Decision**:
- Chooses: `queryDocumentsByYear`
- Calls: `queryDocumentsByYear({question: "projects", year: 2021})`

**Response**:
```json
{
  "answer": "According to documents from 2021:\n\n1. From project_timeline.xlsx (Sheet: '2021 Projects'):\n   - Cloud Migration Initiative (Jan-Jun 2021)\n   - Security Platform Rebuild (Jul-Dec 2021)\n\n2. From annual_report_2021.pdf:\n   - Led team of 5 engineers on zero-trust architecture implementation",
  "totalToolsAvailable": 17,
  "executionTimeMs": 2834
}
```

**Note**: Excel sheet names and file sources are included in the response.

---

### Example 4: Hybrid Query (Multiple Tools)

```bash
curl "http://localhost:8080/RAGJava/unified/ask?question=Compare+current+AAPL+price+with+my+portfolio+from+2022"
```

**LLM Decision**:
- Chooses: MULTIPLE tools
- Calls:
  1. `getYahooQuote("AAPL")` for current price
  2. `queryDocumentsByYear({question: "portfolio AAPL", year: 2022})` for historical data

**Response**:
```json
{
  "answer": "Current AAPL price is $182.45.\n\nAccording to your portfolio_2022.xlsx file (Sheet: 'Holdings'), you purchased AAPL at $150.20 in January 2022. Your investment has gained $32.25 per share (+21.5%).\n\nBased on the 100 shares listed in your portfolio, your unrealized gain is approximately $3,225.",
  "totalToolsAvailable": 17,
  "executionTimeMs": 3421
}
```

**Note**: The LLM autonomously decided to call both tools and synthesized the results into a comprehensive answer.

---

## Conclusion

The **RAGFunctionConfiguration** is the missing piece that turns RAG from an automatic behavior into an intelligent tool choice:

✅ **No Interference**: Tools work independently without RAG advisor interference
✅ **Smart Routing**: LLM decides when to search documents vs. call external APIs
✅ **Rich Context**: Returns Excel sheet names, file sources, dates, and metadata
✅ **Composability**: Can combine multiple tools (RAG + external APIs) in one query
✅ **Flexibility**: 17 total tools covering documents, temporal queries, stocks, weather, and news

The architecture is clean, extensible, and follows Spring Boot best practices with automatic tool discovery through `@Bean` methods.

---

## Document Information

- **Created**: 2025
- **Purpose**: Architecture documentation for Unified Agentic Controller
- **Audience**: Developers, architects, technical documentation
- **Project**: RAGService - Spring AI RAG Application