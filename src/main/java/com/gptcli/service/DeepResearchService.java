package com.gptcli.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gptcli.Config;
import com.gptcli.util.Formatter;
import org.jsoup.Jsoup;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class DeepResearchService {
    private final HttpClient client;
    private final Gson gson;
    private final String apiKey;
    private final String cx;

    public DeepResearchService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.apiKey = Config.getGoogleSearchApiKey();
        this.cx = Config.getGoogleSearchCx();
    }

    public String searchAndScrape(String query) {
        if (apiKey == null || cx == null) {
            return "Error: Google Search API Key or CX not configured.";
        }

        try {
            System.out.println(Formatter.info("Searching Google for: " + query));
            List<String> urls = searchGoogle(query);
            
            StringBuilder researchData = new StringBuilder();
            researchData.append("=== Deep Research Report ===\n");
            researchData.append("Query: ").append(query).append("\n");
            researchData.append("Time: ").append(java.time.LocalDateTime.now()).append("\n\n");

            // Fix #12: Scrape all URLs concurrently instead of sequentially
            ExecutorService scrapePool = Executors.newFixedThreadPool(Math.min(urls.size(), 5));
            try {
                List<CompletableFuture<String>> futures = urls.stream()
                        .map(url -> CompletableFuture.supplyAsync(() -> {
                            System.out.println(Formatter.info("Reading: " + url));
                            String content = scrapeContent(url);
                            if (!content.isEmpty()) {
                                // Limit content length per source to avoid token limits
                                if (content.length() > 2000) {
                                    content = content.substring(0, 2000) + "... [truncated]";
                                }
                                return "--- Source: " + url + " ---\n" + content + "\n\n";
                            }
                            return "";
                        }, scrapePool))
                        .collect(Collectors.toList());

                // Wait for all and collect results
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                for (CompletableFuture<String> future : futures) {
                    String result = future.get();
                    if (!result.isEmpty()) {
                        researchData.append(result);
                    }
                }
            } finally {
                scrapePool.shutdown();
            }

            return researchData.toString();

        } catch (Exception e) {
            return "Error during deep research: " + e.getMessage();
        }
    }

    private List<String> searchGoogle(String query) throws Exception {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://www.googleapis.com/customsearch/v1?key=" + apiKey + "&cx=" + cx + "&q=" + encodedQuery + "&num=5";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        List<String> urls = new ArrayList<>();
        if (response.statusCode() == 200) {
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            if (json.has("items")) {
                JsonArray items = json.getAsJsonArray("items");
                for (int i = 0; i < items.size(); i++) {
                    urls.add(items.get(i).getAsJsonObject().get("link").getAsString());
                }
            }
        }
        return urls;
    }

    private String scrapeContent(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(5000)
                    .get()
                    .text(); // Get visible text only
        } catch (Exception e) {
            System.out.println(Formatter.error("Failed to scrape " + url + ": " + e.getMessage()));
            return "";
        }
    }
}
