# RAG + Agentic AI: Unified Intelligent Assistant

A production-ready Spring AI application combining **Retrieval Augmented Generation (RAG)** with **Agentic Function Calling**, featuring intelligent routing between document search and real-time APIs.

## What This Does

**Unified Intelligence** (`/unified/ask`) - Google-like search endpoint that automatically routes between:
- **Document Search**: RAG over PDFs, Word docs, Excel files
- **Real-Time APIs**: Live stock quotes, weather, news
- **Temporal Queries**: Date-filtered document search
- **Hybrid Queries**: Combines multiple data sources

 **Pure RAG**: Traditional document-based Q&A
 **Pure Agentic**: Real-time function calling
 **Research Endpoints**: Compare 3 different agentic strategies
 **Intelligent Provider Selection**: LLM-powered API routing with rate limit awareness

## Quick Start

### Prerequisites

- Java 21
- Maven
- Docker Desktop (running)
- OpenAI API key
- (Optional) Finnhub API key for real-time stock data

### Setup

1. **Set environment variables**:
```bash
export OPENAI_API_KEY="sk-your-key-here"
export FINNHUB_API_KEY="your-finnhub-key"  # Optional - get free at finnhub.io
```

2. **Configure document drop folder**:
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

## API Endpoints Overview

The application provides **4 endpoint categories**:

### 1. Unified Intelligent Endpoint  **RECOMMENDED**

**One endpoint for everything** - Let the AI decide what to do.

### 2. Pure RAG Endpoints

**Document-only queries** - No real-time data access.

### 3.  Pure Agentic Endpoints

**Tools-only queries** - No document retrieval.

### 4.  Research/Comparison Endpoints

**Benchmark different agentic strategies** - For optimization and analysis.

---

## 1. Unified Intelligent Endpoint

### GET /unified/ask  **START HERE**

The **smart endpoint** that intelligently routes your question to the right tools.

**Examples:**

```bash
# Document queries
curl "http://localhost:8080/RAG/unified/ask?question=Who%20is%20Srinivas%20Murthy"
# → Uses: queryDocuments tool → Vector store

# Real-time stock data
curl "http://localhost:8080/RAG/unified/ask?question=What%20is%20the%20stock%20price%20of%20AAPL"
# → Uses: getYahooQuote tool → Finnhub API (via AgenticProviderSelector)

# Temporal document queries
curl "http://localhost:8080/RAG/unified/ask?question=What%20projects%20did%20I%20work%20on%20in%202021"
# → Uses: queryDocumentsByYear tool → Vector store with date filtering

# Weather
curl "http://localhost:8080/RAG/unified/ask?question=What%27s%20the%20weather%20in%20San%20Francisco"
# → Uses: getWeatherByLocation tool → Open-Meteo API

# News
curl "http://localhost:8080/RAG/unified/ask?question=Latest%20AI%20news"
# → Uses: getMarketNews tool → Google News RSS

# Multi-tool hybrid queries
curl "http://localhost:8080/RAG/unified/ask?question=Compare%20AAPL%20and%20GOOGL%20stock%20prices%20and%20show%20my%202022%20portfolio"
# → Uses: getYahooQuote + compareStocks + queryDocumentsByYear
```

**Shorthand:** `/unified/q?query=...` (same functionality)

### How It Works

```
User Question
    ↓
Unified Controller (17 tools available)
    ↓
LLM Intelligence Layer
    ├─> Analyzes query intent
    ├─> Selects appropriate tool(s)
    └─> Follows tool hierarchy
    ↓
Tool Execution
    ├─> RAG Tools → PostgreSQL/PGVector → Documents
    ├─> Stock Tools → AgenticProviderSelector → Finnhub/Yahoo/Alpha Vantage
    ├─> News Tools → Google News RSS
    └─> Weather Tools → Open-Meteo API
    ↓
LLM Synthesis
    ↓
Unified Answer
```

### Available Tools (17 Total)

The LLM autonomously selects from:

** RAG & Document Tools (4)**
- `queryDocuments` - Search all ingested documents
- `queryDocumentsByYear` - Filter documents by specific year
- `queryDocumentsByDateRange` - Filter by date range
- `queryDocumentsAdvanced` - Complex queries with metadata

** Stock & Finance Tools (7)**
- `getYahooQuote` - **Composite with agentic selection** (DEFAULT)
- `getRealTimeQuote` - Alpha Vantage real-time (25 calls/day)
- `getFinnhubQuote` - Finnhub fundamentals (60 calls/min)
- `getQuoteFromYahooOnly` - Yahoo Finance only (delayed)
- `getQuoteFromGoogleFinance` - Google Finance (stale data)
- `getHistoricalPrices` - Historical stock data
- `analyzeFinancialRatios` - P/E, ROE, debt analysis (mock)

** News Tools (2)**
- `getMarketNews` - Google News RSS (any topic, unlimited)
- `getHeadlinesByCategory` - Category-specific headlines

** Weather Tools (2)**
- `getWeatherByLocation` - By city name (unlimited, free)
- `getWeatherByZipCode` - By US ZIP code (unlimited, free)

** Market Data Tools (2)**
- `getMarketMovers` - Top gainers/losers (mock data)
- `getEconomicIndicators` - GDP, inflation data (mock)

### Intelligent Tool Selection

The LLM follows a **tool hierarchy** for stock quotes:

1. **`getRealTimeQuote`** (Alpha Vantage) - Real-time queries
   - Keywords: "real-time", "live price", "current price now"
   - Limit: 25 calls/day
   - Use: Premium real-time data

2. **`getFinnhubQuote`** (Finnhub) - Deep fundamentals
   - Keywords: "analyst rating", "price target", "P/E ratio"
   - Limit: 60 calls/minute
   - Use: Financial analysis

3. **`getYahooQuote`** (Composite) - General purpose  **DEFAULT**
   - Default for: "What is the price of AAPL?"
   - Uses: **Agentic Provider Selection** (see below)
   - Limit: Effectively unlimited via fallbacks
   - Use: Routine price checks

4. **Provider-specific overrides** - Explicit provider requests
   - `getQuoteFromYahooOnly`, `getQuoteFromGoogleFinance`
   - Use: Only if user explicitly requests that provider

---

## 2. Pure RAG Endpoints (Document-Only)

### GET /chat

Traditional RAG with **no tool access**.

```bash
curl "http://localhost:8080/RAG/chat?question=What%20are%20Municipal-to-Treasury%20Yield%20Ratios"
```

- Searches vector store only
- Returns "I don't know" if not in documents
- No real-time API access
- Similarity threshold: 0.50
- TopK: 10 chunks

**Use when:** You want to force document-only responses.

---

## 3. Pure Agentic Endpoints (Tools-Only, No RAG)

### GET /chatWithReasoning

Simple tools-only endpoint with **no document retrieval**.

```bash
curl "http://localhost:8080/RAG/chatWithReasoning?question=What%27s%20the%20weather%20in%20San%20Jose"
```

- No RAG advisor (no document interference)
- Access to stock, news, weather tools
- Best for real-time queries

**Use when:** You want to force tool use without document search.

---

## 4. Research/Comparison Endpoints

Compare **three different agentic strategies** for function calling:

### GET /agentic/fullyAgentic

**Strategy:** Give LLM all 14 tools upfront, let it decide autonomously.

```bash
curl "http://localhost:8080/RAG/agentic/fullyAgentic?question=Why%20is%20the%20market%20down%20today"
```

- **Pros:** Simplest implementation, maximum flexibility
- **Cons:** Highest token usage (all tools in context)
- **Latency:** 2-5 seconds
- **Best for:** Prototyping, open-ended queries

### GET /agentic/metaSelection

**Strategy:** Meta-agent selects relevant tools first, then executes with filtered set.

```bash
curl "http://localhost:8080/RAG/agentic/metaSelection?question=Compare%20AAPL%20and%20TSLA"
```

- **Pros:** 40-70% token reduction, predictable patterns
- **Cons:** Extra meta-agent call
- **Latency:** 2-4.5 seconds
- **Best for:** Production workloads, cost optimization

### GET /agentic/metaReasoning

**Strategy:** Create step-by-step execution plan with reasoning, then execute.

```bash
curl "http://localhost:8080/RAG/agentic/metaReasoning?question=What%20are%20the%20biggest%20NASDAQ%20movers"
```

- **Pros:** Transparency, auditability, strategic ordering
- **Cons:** Slightly higher latency
- **Latency:** 3-6 seconds
- **Best for:** Compliance, debugging, explainability

### GET /agentic/compare-all

Run all three approaches **side-by-side** for comparison.

```bash
curl "http://localhost:8080/RAG/agentic/compare-all?question=Get%20stock%20price%20of%20NVDA"
```

Returns performance metrics and answers from all three approaches.

**Comparison Summary:**

| Approach | LLM Calls | Tokens | Latency | Transparency | Best For |
|----------|-----------|--------|---------|--------------|----------|
| **fullyAgentic** | 1 | High (all tools) | 2-5s | Low (black box) | Prototyping, flexibility |
| **metaSelection** | 2 | Medium (filtered) | 2-4.5s | Medium (tool list) | Production, cost savings |
| **metaReasoning** | 2 | Medium (filtered) | 3-6s | High (full plan) | Compliance, debugging |

---

## Intelligent Stock Quote Provider Selection

When the LLM calls `getYahooQuote` (the composite provider tool), the system uses **Agentic Provider Selection** - an LLM-powered routing system that intelligently chooses the best API provider based on real-time conditions.

### Architecture

```
getYahooQuote tool called
    ↓
CompositeStockQuoteProvider
    ↓
AgenticProviderSelector (LLM Decision Engine)
    ├─> ProviderRateLimitTracker
    │   ├─> Finnhub: 12/60 calls last min, 95% success rate
    │   ├─> Alpha Vantage: 2/25 calls today, 100% success rate
    │   └─> Yahoo Finance: Unlimited, 60% success rate
    │
    ├─> LLM analyzes provider status
    └─> Decision: "Use Finnhub - under limit, highest success rate"
    ↓
FinnhubProvider.getQuote("AAPL")
    ↓
Track result → Update statistics → Return to LLM
    ↓
If fails: Try next provider in priority order
```

### Available Providers

| Provider | Free Tier | Latency | Data Quality | Priority | Default? |
|----------|-----------|---------|--------------|----------|----------|
| **Finnhub** | 60/min (3600/hour) | Low | Real-time | 20 (DEFAULT) | ✅ Yes |
| **Alpha Vantage** | 25/day | Low | Real-time | 10 (Premium) | Only via `getRealTimeQuote` |
| **Yahoo Finance** | Unlimited | Medium | 15-min delayed | 100 (Fallback) | If Finnhub limited |
| **Google Finance** | Unlimited | High | Stale data | 110 (Last resort) | If all fail |

### How It Works

**Step 1: LLM Analyzes Providers**
```
AVAILABLE PROVIDERS:
- Finnhub: 45 calls in last minute (45/60), 85% success rate, NOT rate-limited
- Alpha Vantage: 2 calls today (2/25), 100% success rate, NOT rate-limited
- Yahoo Finance: Unlimited, 60% success rate, NOT rate-limited

DECISION: Select Finnhub
REASONING: Under rate limit (45/60), high success rate, most liberal tier
```

**Step 2: Execute & Track**
- Calls selected provider
- Detects success/failure/rate limit
- Updates statistics for future decisions

**Step 3: Fallback (if needed)**
- If Finnhub fails or rate-limited → Try Yahoo
- If Yahoo fails → Try Google
- All failures logged with reasons

### Configuration

**Enable Agentic Selection** (`application.yaml`):
```yaml
finance:
  # Agentic Provider Selection (LLM-powered routing)
  agentic-selection:
    enabled: true  # Use LLM-powered intelligent routing
    fallback-on-failure: true  # Fall back to priority-based if LLM fails

  # Provider Configuration
  finnhub:
    enabled: true
    priority: 20  # Lower = higher priority
    key: ${FINNHUB_API_KEY}  # Get free at finnhub.io

  alpha-vantage:
    enabled: true
    priority: 10
    key: demo  # Get free key at alphavantage.co
    base-url: https://www.alphavantage.co

  yahoo:
    enabled: true
    priority: 100

  google:
    enabled: true
    priority: 110
```

**Disable Agentic Selection** (use simple priority-based):
```yaml
finance:
  agentic-selection:
    enabled: false  # Falls back to priority order
```

### Benefits

**Traditional Priority-Based Routing:**
-  Always tries Alpha Vantage first (wastes 25/day quota)
-  No learning from failures
-  No adaptation to rate limits

**Agentic LLM-Based Routing:**
-  Defaults to Finnhub (60 calls/min - most liberal)
-  Reserves Alpha Vantage for critical queries
-  Learns from rate limits and failures
-  Adapts to changing conditions
-  Provides reasoning transparency
-  Automatic fallback on errors

### Monitoring

The `ProviderRateLimitTracker` maintains detailed statistics:

```java
// Finnhub: Success: 145, Failures: 3, RateLimits: 0, LastMin: 12, LastHour: 145, SuccessRate: 97.97%
// Yahoo Finance: Success: 23, Failures: 8, RateLimits: 0, LastMin: 0, LastHour: 23, SuccessRate: 74.19%
```

**Key Components:**
- **`ProviderRateLimitTracker`**: Tracks success/failure rates, sliding window counters
- **`AgenticProviderSelector`**: LLM-powered decision engine with reasoning
- **`CompositeStockQuoteProvider`**: Orchestrates selection and fallback

---

## Endpoint Selection Guide

**Which endpoint should I use?**

| Use Case | Best Endpoint | Reason |
|----------|---------------|--------|
| **Production app with mixed queries** | `/unified/ask`  | One endpoint handles everything |
| "What did the report say?" | `/unified/ask` or `/chat` | Both work, unified is future-proof |
| "Stock price of TSLA" | `/unified/ask` or `/chatWithReasoning` | Both work, unified has better routing |
| "Compare my portfolio with current prices" | `/unified/ask`  | Only this can combine RAG + APIs |
| Benchmarking agentic strategies | `/agentic/compare-all` | Research/optimization |
| Forcing document-only responses | `/chat` | Prevents tool use |
| Forcing tool-only responses | `/chatWithReasoning` | Prevents document search |

**Recommendation:** Use `/unified/ask` for everything unless you have a specific reason not to.

---

## Document Ingestion

### Startup Ingestion
Place documents in `src/main/resources/documents/` before starting the app.

**Supported formats:**
- PDF (.pdf)
- Word (.docx)
- Excel (.xlsx)

### Runtime Ingestion
Copy files to `/tmp/ragdocs/` while app is running. The file watcher polls every 5 seconds.

```bash
cp my-resume.pdf /tmp/ragdocs/
```

### Manual Ingestion
POST to `/documents/upload` endpoint (implementation in DocumentController).

---

## RAG Pipeline Architecture

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

**File Watcher**
- Spring Integration monitors `/tmp/ragdocs`
- Persistent metadata prevents reprocessing
- Automatic ingestion on file detection

---

## RAG vs Agentic AI: Understanding the Difference

This application demonstrates **two fundamentally different AI patterns**:

### RAG (Retrieval Augmented Generation)
- **What it does:** Searches through ingested documents stored in vector database
- **Data source:** Static documents you've uploaded (PDFs, Word, Excel)
- **Use case:** "What did the quarterly report say about revenue?"
- **Endpoints:** `/chat`, `/unified/ask` (when appropriate)
- **How it works:** Question → Embedding → Vector search → Retrieve chunks → LLM answer
- **Configuration:** Uses `RetrievalAugmentationAdvisor`

### Agentic AI (Function Calling / Tool Use)
- **What it does:** Calls external APIs and functions to fetch real-time data
- **Data source:** Live APIs (stock prices, weather, news)
- **Use case:** "What's the current stock price of AAPL?"
- **Endpoints:** `/chatWithReasoning`, `/agentic/*`, `/unified/ask` (when appropriate)
- **How it works:** Question → LLM decides tools → Execute functions → LLM synthesis
- **Configuration:** Uses function beans with `@Description`

### Unified Approach
The `/unified/ask` endpoint **combines both patterns**:
- Has access to both RAG tools (document search) AND agentic tools (APIs)
- LLM intelligently decides which approach to use
- Can combine multiple data sources in a single query
- This is the only **true RAG + Agentic hybrid** endpoint

### Why Separate Endpoints Exist

The pure RAG (`/chat`) and pure agentic (`/chatWithReasoning`) endpoints exist because:

**RAG advisor interference:** When RAG advisor is present:
1. It retrieves documents (which may be empty or irrelevant)
2. Prompts model to say "I don't know" when documents don't contain answer
3. Prevents model from using tools even when they're available

**Solution:**
- `/chat`: RAG advisor enabled, no tools → Pure document search
- `/chatWithReasoning`: No RAG advisor, tools enabled → Pure function calling
- `/unified/ask`: Both RAG tools and API tools → Intelligent routing

---

## Configuration

### Required Environment Variables
```bash
export OPENAI_API_KEY="sk-your-key-here"
```

### Optional Environment Variables
```bash
export FINNHUB_API_KEY="your-key"  # Get free at https://finnhub.io
export SM_USER_NAME="postgres-user"  # Database credentials
export SM_PASSWORD="postgres-password"
```

### Application Configuration (`application.yaml`)

**RAG Settings:**
```yaml
app:
  retrieval:
    topK: 5
    similarityThreshold: 0.60
    candidateMultiplier: 4
```

**Document Processing:**
```yaml
app:
  ingestion:
    batch-size: 50
    parallel-embeddings: 5
```

**Stock Quote Providers:**
```yaml
finance:
  agentic-selection:
    enabled: true  # LLM-powered provider selection
    fallback-on-failure: true

  finnhub:
    enabled: true
    priority: 20
    key: ${FINNHUB_API_KEY}
```

**Chat Memory:**
```yaml
chat:
  memory:
    dynamodb:
      enabled: true
      endpoint: http://localhost:8000  # DynamoDB Local
      table-name: chat_history
      ttl-days: 7
```

---

## Database Management

**Check vector store contents:**
```bash
psql -h localhost -U smurthy -d RAGJava -c "SELECT COUNT(*) FROM vector_store;"
```

**Clear vector store** (if you need to re-ingest):
```bash
psql -h localhost -U smurthy -d RAGJava -c "TRUNCATE TABLE vector_store;"
```

**Create HNSW index for performance** (recommended for production):
```bash
psql -h localhost -U smurthy -d RAGJava -f src/main/resources/schema.sql
```

What this does:
- Creates `vector_store` table with PGVector support
- Creates HNSW index: ~10-100x faster similarity search
- Essential for large document sets (>10,000 chunks)

**Note:** Spring AI's `initialize-schema=true` does NOT create HNSW index.

---

## Testing

**Run all tests:**
```bash
./mvnw test
```

**Run specific test class:**
```bash
./mvnw test -Dtest=UnifiedAgenticControllerIntegrationTest
```

**Test unified endpoint manually:**
```bash
# Document query
curl "http://localhost:8080/RAG/unified/ask?question=Who%20is%20Srinivas%20Murthy"

# Stock query
curl "http://localhost:8080/RAG/unified/ask?question=Price%20of%20AAPL"

# Hybrid query
curl "http://localhost:8080/RAG/unified/ask?question=Compare%20my%202022%20investments%20with%20current%20TSLA%20price"
```

---

## Tech Stack

- **Java 21**: Language runtime
- **Spring Boot 3.2.6**: Application framework
- **Spring AI 1.0.3**: LLM integration
- **OpenAI GPT-4o**: Primary language model
- **Claude Haiku**: Alternative model for testing
- **PostgreSQL + PGVector**: Vector database
- **Apache POI**: Office document processing
- **Finnhub API**: Real-time stock quotes (60 calls/min free)
- **Alpha Vantage API**: Premium real-time quotes (25 calls/day free)
- **Yahoo Finance**: Delayed stock quotes (unlimited)
- **Google News RSS**: Free news aggregation
- **Open-Meteo API**: Free weather data
- **DynamoDB Local**: Chat memory persistence
- **Docker Compose**: Container orchestration

---

## Current Limitations & Design Decisions

### Stock Quote APIs

**Multiple Provider Strategy:**
- **Agentic Selection** (when enabled): LLM chooses best provider
  - Defaults to Finnhub (60 calls/min - most liberal free tier)
  - Falls back to Yahoo Finance (unlimited, 15-20 min delay)
  - Reserves Alpha Vantage for critical queries (25 calls/day)
- **Priority-based** (when disabled): Alpha Vantage → Finnhub → Yahoo → Google

**Market Movers:** Returns mock data
- Yahoo Finance removed free screener API
- Real-time movers require paid APIs (Finnhub Pro, Polygon.io)
- Mock data demonstrates tool calling pattern

### RAG Limitations

**Temporal Queries:** Questions like "What did I do in 2014?" work if:
- LLM receives enough chunks (topK=10) containing full timeline
- Prompt guides LLM to reason about date ranges containing query year
- Limitation: Vector similarity doesn't understand "2014 is within 2008-2017"

**Why not metadata filtering?**
- Would require domain-specific parsing (resume dates, financial reports, etc.)
- Breaks generic, multi-vertical design principle
- Current prompt-based approach works across all document types

**Why not GraphRAG?**
- Current temporal/comparison queries work with standard RAG + better prompting
- GraphRAG needed for entity relationships ("Who worked with X on project Y?")
- Adds complexity without clear benefit for current use cases

---

## Token Usage & Costs

- **Embeddings:** `text-embedding-ada-002` (~$0.0001 per 1K tokens)
- **Chat:** `gpt-4o` (~$0.03 per 1K tokens)
- **Average query:** ~$0.01-0.05 depending on context size
- **Agentic selection:** Adds ~$0.005 per composite provider call

---

## Troubleshooting

**"I don't know" responses on valid document questions:**
- Check vector store has documents: `SELECT COUNT(*) FROM vector_store;`
- Verify chunks contain expected content
- Lower similarity threshold if needed
- Try `/unified/ask` instead of `/chat`

**No tools called on queries:**
- Use `/unified/ask` or `/chatWithReasoning` (not `/chat`)
- Check console logs for tool execution
- Verify question clearly indicates need for real-time data

**File watcher not picking up documents:**
- Verify `/tmp/ragdocs` exists
- Check file permissions
- Watch logs for "Fetching market news..." or similar

**Provider selection not working:**
- Check `finance.agentic-selection.enabled=true` in config
- Verify Finnhub API key is set
- Check logs for "Agentic selector chose: ..." messages

**Rate limit errors:**
- Finnhub: 60/min limit - wait 1 minute or disable
- Alpha Vantage: 25/day limit - wait 24 hours or use different provider
- Check `ProviderRateLimitTracker` statistics in logs

---

## License

Demo project - use freely for learning and prototyping.

https://docs.google.com/document/d/18L1GfYR5_JGAX3vNgMTBlst6kb7ceTrSCPuLb_FGRjw/edit?usp=sharing

---

## Generating RAFT Datasets

To manually generate a raw RAFT training dataset for a specific document category:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dataset-generation -Dgenerate.category=<category_name>
```

Replace `<category_name>` with the target category (e.g., finance, general).