package com.smurthy.ai.rag.service;

import com.smurthy.ai.rag.config.MarketFunctionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private final GoogleNewsService googleNewsService;

    public NewsService(GoogleNewsService googleNewsService) {
        this.googleNewsService = googleNewsService;
    }

    /**
     * Get market news using Google News RSS (FREE, unlimited, no API key!)
     */
    public List<MarketFunctionConfiguration.NewsItem> getMarketNews(String topic, int limit) {
        log.info("Fetching market news for: {} (limit: {})", topic, limit);

        try {
            // Use Google News service to fetch real news
            List<GoogleNewsService.NewsArticle> articles = googleNewsService.getMarketNews(topic, limit);

            // Convert to MarketFunctionConfiguration.NewsItem format
            return articles.stream()
                    .map(article -> new MarketFunctionConfiguration.NewsItem(
                            article.title(),
                            article.summary(),
                            article.source(),
                            article.publishedDate()
                    ))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error fetching news for {}: {}", topic, e.getMessage());
            // Fallback to single error message
            return List.of(
                    new MarketFunctionConfiguration.NewsItem(
                            "Error fetching news",
                            "Unable to fetch news at this time: " + e.getMessage(),
                            "System",
                            "N/A"
                    )
            );
        }
    }

    /**
     * Get top headlines by category (BUSINESS, TECHNOLOGY, etc.)
     */
    public List<MarketFunctionConfiguration.NewsItem> getHeadlinesByCategory(String category, int limit) {
        log.info(" Fetching {} headlines (limit: {})", category, limit);

        try {
            List<GoogleNewsService.NewsArticle> articles = googleNewsService.getTopHeadlines(category, limit);

            return articles.stream()
                    .map(article -> new MarketFunctionConfiguration.NewsItem(
                            article.title(),
                            article.summary(),
                            article.source(),
                            article.publishedDate()
                    ))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error fetching {} headlines: {}", category, e.getMessage());
            return List.of(
                    new MarketFunctionConfiguration.NewsItem(
                            "Error fetching headlines",
                            "Unable to fetch headlines: " + e.getMessage(),
                            "System",
                            "N/A"
                    )
            );
        }
    }
}