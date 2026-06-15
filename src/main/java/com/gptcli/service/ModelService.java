package com.gptcli.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gptcli.Config;
import com.gptcli.util.Formatter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModelService {
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Gson gson = new Gson();
    private static String cachedModel = null;

    public static String getBestModel(String provider) {
        if (cachedModel != null) return cachedModel;

        if ("gemini".equalsIgnoreCase(provider)) {
            cachedModel = getBestGeminiModel();
        } else {
            // Fallback for other providers or if implementation is missing
            cachedModel = getDefaultModel(provider);
        }
        return cachedModel;
    }

    private static String getBestGeminiModel() {
        String apiKey = Config.getGeminiApiKey();
        if (apiKey == null || apiKey.isEmpty()) return "gemini-2.0-flash";

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5)) // Fix #1: Request-level timeout
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.out.println(Formatter.error("Failed to fetch models: " + response.statusCode()));
                return "gemini-2.0-flash";
            }

            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            if (!json.has("models")) return "gemini-2.0-flash";

            JsonArray models = json.getAsJsonArray("models");
            List<String> candidates = new ArrayList<>();

            for (JsonElement el : models) {
                JsonObject modelObj = el.getAsJsonObject();
                String name = modelObj.get("name").getAsString().replace("models/", "");
                JsonArray methods = modelObj.getAsJsonArray("supportedGenerationMethods");
                
                boolean supportsGenerateContent = false;
                if (methods != null) {
                    for (JsonElement method : methods) {
                        if ("generateContent".equals(method.getAsString())) {
                            supportsGenerateContent = true;
                            break;
                        }
                    }
                }

                if (supportsGenerateContent && name.startsWith("gemini")) {
                    candidates.add(name);
                }
            }

            return selectBestGeminiCandidate(candidates);

        } catch (java.net.http.HttpTimeoutException e) {
            // Fix #1: Graceful timeout handling — don't block startup
            System.out.println(Formatter.error("Model fetch timed out. Using default."));
            return "gemini-2.0-flash";
        } catch (Exception e) {
            System.out.println(Formatter.error("Error fetching models: " + e.getMessage()));
            return "gemini-2.0-flash";
        }
    }

    private static String selectBestGeminiCandidate(List<String> candidates) {
        // Regex to parse version: gemini-(\\d+\\.?\\d*)-(pro|flash|ultra)?.*
        Pattern pattern = Pattern.compile("gemini-(\\d+\\.?\\d*)-(pro|flash|ultra)?.*");

        candidates.sort((m1, m2) -> {
            Matcher matcher1 = pattern.matcher(m1);
            Matcher matcher2 = pattern.matcher(m2);

            double v1 = 0.0;
            double v2 = 0.0;
            String type1 = "";
            String type2 = "";

            if (matcher1.find()) {
                v1 = Double.parseDouble(matcher1.group(1));
                type1 = matcher1.group(2) != null ? matcher1.group(2) : "";
            }
            if (matcher2.find()) {
                v2 = Double.parseDouble(matcher2.group(1));
                type2 = matcher2.group(2) != null ? matcher2.group(2) : "";
            }

            // Prioritize Type (Flash > Pro > Ultra)
            int score1 = getTypeScore(type1);
            int score2 = getTypeScore(type2);
            int typeCompare = Integer.compare(score2, score1);
            if (typeCompare != 0) return typeCompare;

            // Then compare versions descending
            return Double.compare(v2, v1);
        });

        if (candidates.isEmpty()) return "gemini-2.0-flash";
        
        // Return the top candidate
        return candidates.get(0);
    }

    private static int getTypeScore(String type) {
        if (type == null) return 0;
        switch (type.toLowerCase()) {
            case "flash": return 3; // Prioritize Flash for free tier/speed
            case "pro": return 2;
            case "ultra": return 1;
            default: return 0;
        }
    }

    private static String getDefaultModel(String provider) {
        return switch (provider.toLowerCase()) {
            case "gemini" -> "gemini-2.0-flash";
            case "openai" -> "gpt-4o";
            case "grok" -> "grok-beta";
            case "deepseek" -> "deepseek-chat";
            default -> "gpt-4o";
        };
    }
}
