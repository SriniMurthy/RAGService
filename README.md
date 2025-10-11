# RAG + Agentic AI Demo

A RAG (Retrieval Augmented Generation) application demonstrating document-based Q&A and agentic function calling with Spring AI and OpenAI GPT-4o.

## What This Does

- **Document Q&A**: Query PDFs, Word docs, and Excel files using natural language
- **Agentic Function Calling**: Access real-time stock quotes, news, weather, and market data via LLM-driven tool selection
- **Multi-format Support**: Reads PDF, DOCX, XLSX files with automatic content extraction
- **Dynamic Ingestion**: Drop files into a watched folder for automatic processing
- **Three Agentic Approaches**: Compare fully-agentic, meta-selection, and meta-reasoning strategies

## Quick Start

### Prerequisites

- Java 21
- Maven
- Docker Desktop (running)
- OpenAI API key

### Setup

1. **Set your OpenAI API key**:
```bash
export OPENAI_API_KEY="sk-your-key-here"
```

2. **Configure document drop folder** (optional):
```bash
mkdir /tmp/ragdocs
```
Files placed here are auto-ingested while the app runs.

3. **Run the application**:
```bash
./mvnw spring-boot:run
```

The app will:
- Start PostgreSQL + PGVector via Docker Compose
- Ingest documents from `src/main/resources/documents/`
- Start the API server on port 8080

## API Endpoints

Base URL: `http://localhost:8080/RAG`

### Document-Based Q&A (RAG)

**GET /chat**
```bash
curl "http://localhost:8080/RAG/chat?question=What%20are%20Municipal-to-Treasury%20Yield%20Ratios"
```
- Retrieves relevant document chunks from vector store
- Returns answers based solely on ingested documents
- Returns "I don't know" if answer not found in documents

**GET /chatWithReasoning**
```bash
curl "http://localhost:8080/RAG/chatWithReasoning?question=What%20is%20the%20weather%20in%20San%20Jose"
```
- No RAG - uses function calling only
- Has access to: stock quotes, news, weather, economic data
- Best for real-time data questions

### Agentic Approaches (Function Calling)

**GET /agentic/fullyAgentic**
```bash
curl "http://localhost:8080/RAG/agentic/fullyAgentic?question=Why%20is%20the%20market%20down%20today"
```
- LLM gets all 14 tools upfront
- Decides which tools to use autonomously
- No document retrieval - pure function calling

**GET /agentic/meta-selection**
```bash
curl "http://localhost:8080/RAG/agentic/meta-selection?question=Compare%20AAPL%20and%20TSLA"
```
- Meta-agent analyzes query and selects relevant tools first
- Second LLM call executes with filtered tool set
- Reduces token usage

**GET /agentic/meta-reasoning**
```bash
curl "http://localhost:8080/RAG/agentic/meta-reasoning?question=What%20are%20the%20biggest%20NASDAQ%20movers"
```
- Creates step-by-step execution plan with reasoning
- Executes plan with transparent decision-making
- Best for complex multi-step queries

**GET /agentic/compare-all**
```bash
curl "http://localhost:8080/RAG/agentic/compare-all?question=Get%20stock%20price%20of%20NVDA"
```
Runs all three approaches side-by-side for performance comparison.

## Available Functions (Tools)

The agentic endpoints have access to:

**Stock Data (FREE - No API key needed)**
- `getYahooQuote`: Real stock quotes (15-20 min delay)
- `getHistoricalPrices`: Historical stock data
- `getMarketMovers`: Top gainers/losers (mock data)
- `compareStocks`: Side-by-side stock comparison

**News & Information (FREE)**
- `getMarketNews`: Google News RSS (works for any topic, not just markets)
- `getWeatherByLocation`: Weather by city name
- `getWeatherByZipCode`: Weather by ZIP code

**Analytics (Mock Data)**
- `analyzeFinancialRatios`: P/E, ROE, debt ratios
- `getEconomicIndicators`: GDP, inflation data
- `predictMarketTrend`: ML-based predictions
- `calculatePortfolioMetrics`: Portfolio analysis
- `getCompanyProfile`: Company information

**Real-time Data (Requires API key)**
- `getRealTimeQuote`: Alpha Vantage real-time quotes (requires free API key)

## Document Ingestion

### Startup Ingestion
Place documents in `src/main/resources/documents/` before starting the app.

Supported formats:
- PDF (.pdf)
- Word (.docx)
- Excel (.xlsx)

### Runtime Ingestion
Copy files to `/tmp/${ragdocs}/` while app is running. The file watcher polls every 5 seconds.

```bash
cp my-resume.pdf /tmp/ragdocs/
```

### Manual Ingestion
POST to `/documents/upload` endpoint (implementation in DocumentController).

## Architecture

### RAG Pipeline
```
Document → Reader (PDF/Word/Excel) → TextSplitter → Embeddings (OpenAI) → PGVector
                                                                              ↓
User Query → Embedding → Similarity Search ← Retrieved Chunks → LLM → Answer
```

### Key Components

**DocumentIngestionService**
- Strategy pattern for multi-format readers
- Parallel embedding with batching (50 chunks/batch, 5 parallel calls)
- Retry logic with exponential backoff for rate limiting

**RAGDataController**
- Similarity threshold: 0.50 (balanced for recall)
- TopK: 10 chunks per query
- Keyword-based function filtering for hybrid mode

**AgenticComparisonController**
- Tools-only mode (no RAG interference)
- Meta-agent for tool selection
- Execution planning with reasoning

**File Watcher**
- Spring Integration monitors `/tmp/ragdocs`
- Persistent metadata prevents reprocessing
- Automatic ingestion on file detection

### Configuration

**application.yaml**
- RAG prompt template with temporal reasoning instructions
- Document paths and watch directory
  - Batch processing settings
- Database connection (PostgreSQL + PGVector)
- OpenAI API key
- Context path: `/RAG`

## RAG vs Agentic AI: Understanding the Difference

This application demonstrates **two fundamentally different AI patterns** that are often confused:

### RAG (Retrieval Augmented Generation)
- **What it does:** Searches through ingested documents (PDFs, Word, Excel) stored in a vector database
- **Data source:** Static documents you've uploaded
- **Use case:** "What did the quarterly report say about revenue?"
- **Endpoint:** `/chat`
- **How it works:** Question → Embedding → Vector similarity search → Retrieve chunks → LLM generates answer from chunks
- **Configuration:** Uses `RetrievalAugmentationAdvisor`

### Agentic AI (Function Calling / Tool Use)
- **What it does:** Calls external APIs and functions to fetch real-time data
- **Data source:** Live APIs (stock prices, weather, news)
- **Use case:** "What's the current stock price of AAPL?"
- **Endpoints:** `/agentic/fullyAgentic`, `/agentic/metaReasoning`, `/chatWithReasoning`
- **How it works:** Question → LLM decides which tools to call → Executes functions → LLM synthesizes answer from tool results
- **Configuration:** Uses `toolsOnlyBuilder` (NO RAG advisor)

### Key Distinction: Agentic AI ≠ RAG

**The `/agentic/*` endpoints are NOT using RAG.** They exclude the `RetrievalAugmentationAdvisor` entirely:

```java
// RAG-enabled builder (for /chat endpoint)
this.chatClientBuilder = builder
    .defaultAdvisors(
        RetrievalAugmentationAdvisor.builder()...build(),  // ← RAG advisor
        MessageChatMemoryAdvisor.builder()...build()
    );

// Tools-only builder (for /agentic/* endpoints)
this.toolsOnlyBuilder = builder
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder()...build()
        // ← NO RetrievalAugmentationAdvisor - no document retrieval
    );
```

**Why the separation?** RAG and function calling interfere with each other:
- When RAG advisor is present, the LLM receives a "DOCUMENTS" section
- If documents are empty or irrelevant, the LLM says "I don't know"
- This prevents the LLM from using tools, even when they're available

**Exception: Hybrid Approach**
The `/agentic/metaSelection` endpoint uses `baseBuilder` which includes BOTH RAG and tools:
- First checks documents for the answer
- If documents don't contain the answer, falls back to calling tools
- This is the only true RAG + Agentic hybrid in the application

### Summary Table

| Pattern | RAG Advisor | Function Calling | Data Source | Endpoints |
|---------|-------------|------------------|-------------|-----------|
| **Pure RAG** | ✅ Yes | ❌ No | Documents | `/chat` |
| **Pure Agentic** | ❌ No | ✅ Yes | Live APIs | `/agentic/fullyAgentic`, `/agentic/metaReasoning`, `/chatWithReasoning` |
| **Hybrid** | ✅ Yes | ✅ Yes | Documents + APIs | `/agentic/metaSelection` |

---

## Three Agentic Approaches Explained

The `/agentic/*` endpoints demonstrate three distinct strategies for LLM function calling (NOT RAG), each with different trade-offs between simplicity, efficiency, and transparency.

### Approach 1: Fully Agentic (`/agentic/fullyAgentic`)

**Strategy:** Give the model ALL 14 tools upfront and let it autonomously decide which to use.

**How it works:**
```java
ChatClient client = toolsOnlyBuilder
    .defaultToolNames(allTools.keySet().toArray(new String[0]))  // All 14 tools
    .build();

ChatResponse response = client.prompt()
    .user(question)
    .call()
    .chatResponse();
```

**Characteristics:**
- **Simplest implementation** - One LLM call with all tools available
- **Maximum flexibility** - Model can use any combination of tools
- **Highest token usage** - All 14 tool definitions sent in every request
- **No RAG advisor** - Pure function calling, no document retrieval interference

**Best for:**
- Open-ended queries requiring multiple tools
- Exploratory questions where tool needs are unpredictable
- Prototyping and development (easiest to debug)

**Example:**
```bash
curl "http://localhost:8080/RAG/agentic/fullyAgentic?question=Why%20is%20the%20market%20down%20today"
# Model autonomously chooses: getMarketNews → getMarketMovers → getEconomicIndicators
```

**Performance:**
- Execution time: 2-5 seconds (single LLM call)
- Token usage: ~2000-3000 tokens (all tool definitions in context)

---

### Approach 2: Meta-Agent Selection (`/agentic/meta-selection`)

**Strategy:** Use a meta-agent to analyze the query and select only relevant tools, then execute with a filtered tool set.

**How it works:**
```java
// STEP 1: Meta-agent selects relevant tools
String selectionPrompt = "Analyze this question and determine which tools are needed: " + question;
String toolsJson = metaAgent.prompt().user(selectionPrompt).call().content();
List<String> selectedTools = parseToolList(toolsJson);  // e.g., ["getStockPrice", "analyzeFinancialRatios"]

// STEP 2: Execute with only selected tools
ChatClient focusedClient = baseBuilder
    .defaultToolNames(selectedTools.toArray(new String[0]))  // Only 2-3 tools
    .build();

ChatResponse response = focusedClient.prompt().user(question).call().chatResponse();
```

**Characteristics:**
- **Two LLM calls** - Meta-agent selection + Execution
- **Reduced context size** - Only relevant tools sent to execution model
- **Token efficiency** - ~40-70% reduction compared to fully agentic
- **Predictable patterns** - Works well for keyword-matchable queries

**Best for:**
- Production workloads with predictable query patterns
- Cost-sensitive applications (reduced token usage)
- Queries where tool needs are deterministic (e.g., "stock price" → getYahooQuote)

**Example:**
```bash
curl "http://localhost:8080/RAG/agentic/meta-selection?question=Compare%20AAPL%20and%20TSLA"
# Meta-agent selects: ["getYahooQuote", "compareStocks", "analyzeFinancialRatios"]
# Execution only sees these 3 tools instead of all 14
```

**Performance:**
- Meta-agent time: 0.5-1.5 seconds
- Execution time: 1.5-3 seconds
- Total: 2-4.5 seconds (similar to fully agentic)
- Token savings: 40-70% (fewer tool definitions)

**Trade-off:**
- Extra meta-agent call adds latency
- But reduced context size speeds up execution
- Net result: Similar latency, lower cost

---

### Approach 3: Meta-Reasoning with Plan (`/agentic/meta-reasoning`)

**Strategy:** Create a step-by-step execution plan with strategic reasoning, then execute the plan.

**How it works:**
```java
// STEP 1: Meta-agent creates execution plan
String planningPrompt = """
    Create a step-by-step execution plan for this question:
    Question: %s
    Available tools: %s

    Format:
    REASONING: [Strategic analysis]
    STEPS:
    1. [tool_name] - [purpose]
    2. [tool_name] - [purpose]
    """.formatted(question, allTools.keySet());

String planText = metaAgent.prompt().user(planningPrompt).call().content();
ExecutionPlan plan = parsePlan(planText);

// STEP 2: Execute with plan-aware system prompt
ChatClient reasoningClient = baseBuilder
    .defaultToolNames(extractToolsFromPlan(plan))
    .defaultSystem("""
        Execute this plan:
        REASONING: %s
        STEPS: %s
        """.formatted(plan.reasoning(), formatSteps(plan.steps())))
    .build();

ChatResponse response = reasoningClient.prompt().user(question).call().chatResponse();
```

**Characteristics:**
- **Most sophisticated** - Explicit planning and reasoning
- **Transparency** - Plan reveals AI decision-making process
- **Strategic ordering** - Defines sequence of tool calls
- **Auditability** - Plan can be logged, reviewed, or modified before execution

**Best for:**
- Complex multi-step analysis requiring orchestration
- Scenarios requiring explainability and audit trails
- Applications where plan review/approval is needed
- Debugging tool calling behavior (plan reveals model's intent)

**Example:**
```bash
curl "http://localhost:8080/RAG/agentic/meta-reasoning?question=What%20are%20the%20biggest%20NASDAQ%20movers"
# Meta-agent creates plan:
# REASONING: "Need real-time market data for NASDAQ top movers, then contextual news"
# STEPS:
# 1. getMarketMovers - Get top NASDAQ gainers/losers
# 2. getMarketNews - Get news context for market movement
# 3. analyzeFinancialRatios - Analyze fundamentals of top movers
```

**Response includes:**
```json
{
  "answer": "Based on today's market data...",
  "reasoning": "Need real-time market data for NASDAQ top movers, then contextual news",
  "executionPlan": [
    {"action": "getMarketMovers", "purpose": "Get top NASDAQ gainers/losers"},
    {"action": "getMarketNews", "purpose": "Get news context for market movement"},
    {"action": "analyzeFinancialRatios", "purpose": "Analyze fundamentals of top movers"}
  ],
  "toolsUsed": ["getMarketMovers", "getMarketNews", "analyzeFinancialRatios"],
  "planningTimeMs": 1200,
  "executionTimeMs": 2800,
  "totalTimeMs": 4000
}
```

**Performance:**
- Planning time: 1-2 seconds
- Execution time: 2-4 seconds
- Total: 3-6 seconds (slightly slower than other approaches)
- Token usage: Similar to meta-selection (filtered tool set)

**Trade-off:**
- Longer latency due to planning step
- But provides transparency and strategic reasoning
- Enables human-in-the-loop approval workflows

---

### Comparison Summary

| Approach | LLM Calls | Tokens | Latency | Transparency | Best For |
|----------|-----------|--------|---------|--------------|----------|
| **Fully Agentic** | 1 | High (all tools) | 2-5s | Low (black box) | Prototyping, flexibility |
| **Meta-Selection** | 2 | Medium (filtered) | 2-4.5s | Medium (tool list) | Production, cost savings |
| **Meta-Reasoning** | 2 | Medium (filtered) | 3-6s | High (full plan) | Compliance, debugging |

**Compare all three approaches side-by-side:**
```bash
curl "http://localhost:8080/RAG/agentic/compare-all?question=Get%20stock%20price%20of%20NVDA"
```

Returns performance metrics and answers from all three approaches for direct comparison.

### Key Design Principle: No RAG Interference

All three agentic approaches use `toolsOnlyBuilder` which **excludes the RAG advisor**:

```java
// Tools-only builder (NO RAG advisor)
this.toolsOnlyBuilder = builder
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build()
    );
    // Notice: NO RetrievalAugmentationAdvisor
```

**Why?** The RAG advisor interferes with function calling by:
1. Retrieving documents (which may be empty or irrelevant)
2. Prompting the model to say "I don't know" when documents don't contain the answer
3. Preventing the model from using tools even when they're available

For pure function calling scenarios (stock prices, news, weather), we **bypass RAG entirely** to ensure tools are actually used.

## Current Limitations & Design Decisions

### Vector Search Limitations

**Temporal Queries**: Questions like "What did I do in 2014?" work if:
- The LLM receives enough chunks (topK=10) containing the full timeline
- The prompt guides the LLM to reason about date ranges containing the query year
- Limitation: Vector similarity doesn't understand "2014 is within 2008-2017" - relies on LLM reasoning

**Why not metadata filtering?**
- Would require domain-specific parsing logic (resume date extraction, financial report parsing, etc.)
- Breaks the generic, multi-vertical design principle
- Current prompt-based approach works across resumes, contracts, financial reports, and technical docs

### Function Calling Limitations

**Market Movers**: Returns mock data because:
- Yahoo Finance removed free screener API access
- Real-time movers require paid APIs (Finnhub, Polygon.io)
- Mock data demonstrates the tool calling pattern

**Stock Quotes**: 15-20 minute delayed via Yahoo Finance
- Real-time quotes require Alpha Vantage API key (5 calls/day on free tier)
- Yahoo Finance provides unlimited free delayed quotes

**News Search**: Google News RSS
- Free and unlimited
- Works for any topic (not just finance)
- No API key required

### RAG vs Agentic Separation

**Why separate endpoints?**
- RAG advisor interferes with tool calling (LLM sees empty documents, says "I don't know")
- `/RAG/chat`: Pure document retrieval
- `/chatWithReasoning`: Pure function calling
- `/agentic/*`: Demonstrates three different tool selection strategies

**Why not GraphRAG?**
- Current temporal/comparison queries work with standard RAG + better prompting
- GraphRAG needed for entity relationships ("Who worked with X on project Y?")
- Adds complexity without clear benefit for current use cases

## Database Management

**Check vector store contents**:
```bash
psql -h localhost -U smurthy -d RAGJava -c "SELECT COUNT(*) FROM vector_store;"
```

**Clear vector store** (if you need to re-ingest):
```bash
psql -h localhost -U smurthy -d RAGJava -c "TRUNCATE TABLE vector_store;"
```

After truncating, restart the app to re-ingest documents.

**Schema initialization**: Set `spring.ai.vectorstore.pgvector.initialize-schema=true` only on first run.

**HNSW Indexing for Performance** (Recommended for Production):

The application includes `schema.sql` which creates an HNSW (Hierarchical Navigable Small World) index on the vector_store table for optimal similarity search performance:

```bash
psql -h localhost -U smurthy -d RAGJava -f src/main/resources/schema.sql
```

What this does:
- Creates the `vector_store` table with PGVector support
- Creates an HNSW index: `CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops);`
- HNSW provides ~10-100x faster similarity search compared to sequential scans
- Essential for production workloads with large document sets (>10,000 chunks)

**When to run schema.sql:**
- First-time setup (alternative to setting `initialize-schema=true`)
- After dropping/recreating the database
- When upgrading to add HNSW indexing to existing deployments

**Note:** Spring AI's auto-initialization (`initialize-schema=true`) creates the table but **does not** create the HNSW index. For optimal performance, run schema.sql manually.

## Token Usage & Costs

- Embeddings: `text-embedding-ada-002` (~$0.0001 per 1K tokens)
- Chat: `gpt-4o` (~$0.03 per 1K tokens)
- Average query: ~$0.01-0.05 depending on context size

## Testing

**Run all tests**:
```bash
./mvnw test
```

**Run specific test class**:
```bash
./mvnw test -Dtest=AgenticBehaviorIntegrationTests
```

## Configuration Reference

### Required Environment Variables
- `OPENAI_API_KEY`: Your OpenAI API key

### Optional Configuration
- `finance.api.key`: Alpha Vantage API key (default: "demo")
- `finance.api.enabled`: Enable real-time finance service (default: true)

### Document Processing
- `app.ingestion.batch-size`: Chunks per embedding API call (default: 50)
- `app.ingestion.parallel-embeddings`: Parallel API calls (default: 5)

### RAG Settings
- Similarity threshold: 0.50 (in RAGDataController)
- TopK: 10 chunks (in RAGDataController)
- Memory window: 20 messages (in RAGConfiguration)

## Tech Stack

- **Java 21**: Language runtime
- **Spring Boot 3.2.6**: Application framework
- **Spring AI 1.0.3**: LLM integration
- **OpenAI GPT-4o**: Language model
- **PostgreSQL + PGVector**: Vector database
- **Apache POI**: Office document processing
- **Yahoo Finance API**: Free stock data
- **Google News RSS**: Free news aggregation
- **Docker Compose**: Container orchestration

## Troubleshooting

**"I don't know" responses on valid document questions**:
- Check vector store has documents: `SELECT COUNT(*) FROM vector_store;`
- Verify chunks contain expected content
- Lower similarity threshold if needed

**No tools called on agentic endpoints**:
- Ensure using `/agentic/fullyAgentic` (not `/RAG/chat`)
- Check console logs for tool execution
- Try `/chatWithReasoning` for simpler function-only mode

**File watcher not picking up documents**:
- Verify `/tmp/ragdocs` exists
- Check file permissions
- Watch for log messages: "📰 Fetching market news..."

## License

Demo project - use freely for learning and prototyping.