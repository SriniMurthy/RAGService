package com.smurthy.ai.rag.controllers;

import com.smurthy.ai.rag.service.YahooFinanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple test controller to directly test Yahoo Finance service
 * without going through the LLM
 */
@RestController
@RequestMapping("/test")
public class TestYahooFinanceController {

    private final YahooFinanceService yahooFinanceService;
    private static final Logger log = LoggerFactory.getLogger(TestYahooFinanceController.class);


    public TestYahooFinanceController(YahooFinanceService yahooFinanceService) {
        this.yahooFinanceService = yahooFinanceService;
    }

    @GetMapping("/yahoo-quote")
    public YahooFinanceService.DelayedQuote testYahooQuote(
            @RequestParam(defaultValue = "GOOG") String symbol) {

        log.debug("\n=== DIRECT TEST: Yahoo Finance Service ===");
        log.debug("Symbol: " + symbol);

        YahooFinanceService.DelayedQuote quote = yahooFinanceService.getDelayedQuote(symbol);

        log.debug("Result:");
        log.debug("  Symbol: " + quote.symbol());
        log.debug("  Price: $" + quote.price());
        log.debug("  Change: " + quote.change());
        log.debug("  Change %: " + quote.changePercent() + "%");
        log.debug("  Company: " + quote.companyName());
        log.debug("  Source: " + quote.source());
        log.debug("==========================================\n");

        return quote;
    }
}