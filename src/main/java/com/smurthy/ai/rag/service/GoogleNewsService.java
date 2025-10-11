package com.smurthy.ai.rag.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Google News RSS Feed integration
 *
 * Benefits:
 * - NO API KEY REQUIRED
 * - UNLIMITED REQUESTS
 * - Real-time news from Google News
 * - Multiple languages and regions supported
 * - No rate limiting
 *
 * Perfect for getting actual news without any paid subscriptions!
 */
@Service
public class GoogleNewsService {

    private static final Logger log = LoggerFactory.getLogger(GoogleNewsService.class);
    private static final String GOOGLE_NEWS_RSS_BASE = "https://news.google.com/rss";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Fetch news articles for a specific search query
     * Examples: "AAPL stock", "technology news", "Tesla earnings"
     */
    public List<NewsArticle> searchNews(String query, int limit) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String rssUrl = GOOGLE_NEWS_RSS_BASE + "/search?q=" + encodedQuery + "&hl=en-US&gl=US&ceid=US:en";

            log.info("📰 Fetching news from Google News RSS: {}", query);

            return fetchRssFeed(rssUrl, limit);

        } catch (Exception e) {
            log.error("Error fetching Google News for query '{}': {}", query, e.getMessage());
            return List.of(createErrorArticle(query, e.getMessage()));
        }
    }

    /**
     * Fetch top headlines by category
     * Categories: WORLD, NATION, BUSINESS, TECHNOLOGY, ENTERTAINMENT, SPORTS, SCIENCE, HEALTH
     */
    public List<NewsArticle> getTopHeadlines(String category, int limit) {
        try {
            String rssUrl = switch (category.toUpperCase()) {
                case "BUSINESS" -> GOOGLE_NEWS_RSS_BASE + "/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx6TVdZU0FtVnVHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en";
                case "TECHNOLOGY" -> GOOGLE_NEWS_RSS_BASE + "/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRGRqTVhZU0FtVnVHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en";
                case "WORLD" -> GOOGLE_NEWS_RSS_BASE + "/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx1YlY4U0FtVnVHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en";
                case "SPORTS" -> GOOGLE_NEWS_RSS_BASE + "/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRFp1ZEdvU0FtVnVHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en";
                case "ENTERTAINMENT" -> GOOGLE_NEWS_RSS_BASE + "/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNREpxYW5RU0FtVnVHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en";
                case "SCIENCE" -> GOOGLE_NEWS_RSS_BASE + "/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRFp0Y1RjU0FtVnVHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en";
                case "HEALTH" -> GOOGLE_NEWS_RSS_BASE + "/topics/CAAqIQgKIhtDQkFTRGdvSUwyMHZNR3QwTlRFU0FtVnVLQUFQAQ?hl=en-US&gl=US&ceid=US:en";
                default -> GOOGLE_NEWS_RSS_BASE + "?hl=en-US&gl=US&ceid=US:en"; // Top stories
            };

            log.info("📰 Fetching {} headlines from Google News", category);

            return fetchRssFeed(rssUrl, limit);

        } catch (Exception e) {
            log.error("Error fetching {} headlines: {}", category, e.getMessage());
            return List.of(createErrorArticle(category, e.getMessage()));
        }
    }

    /**
     * Get news for any topic (intelligently handles market/financial vs general news)
     */
    public List<NewsArticle> getMarketNews(String topic, int limit) {
        // Only append financial keywords if topic is clearly financial/market-related
        String lowerTopic = topic.toLowerCase();
        boolean isFinancialTopic = lowerTopic.contains("stock") ||
                                   lowerTopic.contains("market") ||
                                   lowerTopic.contains("trading") ||
                                   lowerTopic.contains("finance") ||
                                   lowerTopic.contains("aapl") ||
                                   lowerTopic.contains("tsla") ||
                                   lowerTopic.contains("googl") ||
                                   lowerTopic.matches(".*\\b[A-Z]{2,5}\\b.*"); // Stock ticker pattern

        String query = isFinancialTopic ? (topic + " stock market") : topic;
        return searchNews(query, limit);
    }

    /**
     * Fetch and parse RSS feed
     */
    private List<NewsArticle> fetchRssFeed(String rssUrl, int limit) {
        try {
            URL feedUrl = new URL(rssUrl);
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(feedUrl));

            List<NewsArticle> articles = feed.getEntries().stream()
                    .limit(limit > 0 ? limit : 10)
                    .map(this::convertToNewsArticle)
                    .collect(Collectors.toList());

            log.info("  Successfully fetched {} articles from Google News", articles.size());
            return articles;

        } catch (Exception e) {
            log.error("Error parsing RSS feed from {}: {}", rssUrl, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Convert RSS entry to NewsArticle
     */
    private NewsArticle convertToNewsArticle(SyndEntry entry) {
        String publishedDate = "N/A";
        if (entry.getPublishedDate() != null) {
            publishedDate = entry.getPublishedDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(DATE_FORMATTER);
        }

        // Extract source from title (Google News includes source in title)
        String title = entry.getTitle();
        String source = "Google News";
        if (title.contains(" - ")) {
            String[] parts = title.split(" - ");
            if (parts.length > 1) {
                source = parts[parts.length - 1]; // Last part is usually the source
                title = String.join(" - ", java.util.Arrays.copyOf(parts, parts.length - 1));
            }
        }

        String description = entry.getDescription() != null
                ? entry.getDescription().getValue()
                : "No description available";

        // Clean HTML tags from description
        description = description.replaceAll("<[^>]*>", "");

        return new NewsArticle(
                title,
                description,
                source,
                publishedDate,
                entry.getLink()
        );
    }

    private NewsArticle createErrorArticle(String query, String error) {
        return new NewsArticle(
                "Error fetching news for: " + query,
                "Error: " + error,
                "System",
                "N/A",
                ""
        );
    }

    /**
     * News article record
     */
    public record NewsArticle(
            String title,
            String summary,
            String source,
            String publishedDate,
            String url
    ) {}
}