package com.smurthy.ai.rag.mcp.finance;

import com.smurthy.ai.rag.service.provider.StockQuoteProvider;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FinanceTools {

    private final Map<String, StockQuoteProvider> providerMap;

    // Inject all beans of type StockQuoteProvider into a Map
    public FinanceTools(List<StockQuoteProvider> providers) {
        this.providerMap = providers.stream()
                .filter(p -> p.getProviderName() != null && !p.getProviderName().equals("Composite"))
                .collect(Collectors.toMap(StockQuoteProvider::getProviderName, Function.identity()));
    }

    @Tool("Get a real-time stock quote using the premium Alpha Vantage service.")
    public StockQuoteProvider.StockQuote getRealTimeQuote(String symbol) {
        return getProvider("Alpha Vantage").getQuote(symbol);
    }

    @Tool("Get a standard, possibly delayed stock quote from Yahoo Finance.")
    public StockQuoteProvider.StockQuote getYahooQuote(String symbol) {
        return getProvider("Yahoo Finance").getQuote(symbol);
    }

    @Tool("Get a stock quote from Finnhub.")
    public StockQuoteProvider.StockQuote getFinnhubQuote(String symbol) {
        return getProvider("Finnhub").getQuote(symbol);
    }

    private StockQuoteProvider getProvider(String name) {
        StockQuoteProvider provider = providerMap.get(name);
        if (provider == null) {
            throw new IllegalStateException("Stock quote provider not found: " + name);
        }
        return provider;
    }
}