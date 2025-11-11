#!/usr/bin/env node

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import fs from 'fs';
import fetch from 'node-fetch';

// Create MCP server instance
const server = new Server(
  {
    name: "ragService",
    version: "1.0.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

// Define the RAG query tool
server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: "query_rag",
        description: "Search and query ALL ingested documents including resumes, PDFs, images, Excel files, Word docs, financial data, and any other uploaded content. Use this for questions about people, projects, work history, financial information, or ANY content from the document store. This tool has access to the complete RAG vector database.",
        inputSchema: {
          type: "object",
          properties: {
            question: {
              type: "string",
              description: "The question to ask the RAG service"
            }
          },
          required: ["question"]
        }
      }
    ]
  };
});

// Handle tool execution
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  console.error(`[MCP] Tool called: ${request.params.name}`);
  console.error(`[MCP] Arguments:`, JSON.stringify(request.params.arguments));

  if (request.params.name === "query_rag") {
    // Extract question from whatever parameter Claude sends
    const args = request.params.arguments;
    const question = args.question || args.query || args.q || Object.values(args)[0];

    console.error(`[MCP] Question: ${question}`);
    console.error(`[MCP] Full args structure:`, args);

    if (!question) {
      return {
        content: [
          {
            type: "text",
            text: `Error: No question parameter found. Received arguments: ${JSON.stringify(args)}`
          }
        ],
        isError: true
      };
    }

    try {
      const url = `http://localhost:8080/RAG/unified/ask?question=${encodeURIComponent(question)}`;
      console.error(`[MCP] Calling URL: ${url}`);

      // Call the RAG service unified endpoint with 30 second timeout
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 30000);

      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Accept': 'application/json'
        },
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      console.error(`[MCP] Response status: ${response.status}`);

      if (!response.ok) {
        const errorText = await response.text();
        console.error(`[MCP] Error response: ${errorText}`);
        throw new Error(`RAG service returned status ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();

      // Log to separate file for debugging
      const logEntry = `\n=== ${new Date().toISOString()} ===\nQuestion: ${question}\nResponse: ${JSON.stringify(data, null, 2)}\n`;
      fs.appendFileSync('/tmp/mcp-rag-debug.log', logEntry);

      const answer = data.answer || JSON.stringify(data);

      return {
        content: [
          {
            type: "text",
            text: answer
          }
        ]
      };
    } catch (error) {
      console.error(`[MCP] Error occurred: ${error.message}`);
      return {
        content: [
          {
            type: "text",
            text: `Error querying RAG service: ${error.message}`
          }
        ],
        isError: true
      };
    }
  }

  throw new Error(`Unknown tool: ${request.params.name}`);
});

// Start the server with stdio transport
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("RAG MCP Server running on stdio");
}

main().catch((error) => {
  console.error("Server error:", error);
  process.exit(1);
});