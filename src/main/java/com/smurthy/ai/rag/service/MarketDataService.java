package com.smurthy.ai.rag.service;

import org.springframework.stereotype.Service;

/**
 * Example service class if you prefer to keep business logic separate
 */
@Service
public class MarketDataService {

    public double getStockPrice(String symbol) {
        // Let's scrape Yahoo finance some time soon
        return 150.25;
    }

    public String getMarketAnalysis(String sector) {
        // We'll use some financial service API here
        return "Bullish trend detected";
    }
}