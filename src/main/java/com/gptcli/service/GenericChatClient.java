package com.gptcli.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.gptcli.model.Message;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

public class GenericChatClient implements ChatClient {
    private final HttpClient client;
    private final Gson gson;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public GenericChatClient(String apiKey, String baseUrl, String model) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String chat(List<Message> history) {
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("API Key is not set for generic provider.");
            }

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            
            JsonArray messages = new JsonArray();
            for (Message msg : history) {
                JsonObject messageObj = new JsonObject();
                messageObj.addProperty("role", msg.getRole());
                messageObj.addProperty("content", msg.getContent());
                messages.add(messageObj);
            }
            requestBody.add("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                return json.getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString();
            } else {
                throw new RuntimeException("API Error: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void streamChat(List<Message> history, Consumer<String> onUpdate) {
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("API Key is not set for generic provider.");
            }

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("stream", true);

            JsonArray messages = new JsonArray();
            for (Message msg : history) {
                JsonObject messageObj = new JsonObject();
                messageObj.addProperty("role", msg.getRole());
                messageObj.addProperty("content", msg.getContent());
                messages.add(messageObj);
            }
            requestBody.add("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("API Error: " + response.statusCode() + " - " + response.body());
                        }
                        response.body().forEach(line -> {
                            if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
                                String jsonStr = line.substring(6);
                                try {
                                    JsonObject json = gson.fromJson(jsonStr, JsonObject.class);
                                    JsonArray choices = json.getAsJsonArray("choices");
                                    if (choices.size() > 0) {
                                        JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
                                        if (delta.has("content")) {
                                            String content = delta.get("content").getAsString();
                                            onUpdate.accept(content);
                                        }
                                    }
                                } catch (Exception ignored) {} // Ignore parsing errors for malformed lines
                            }
                        });
                    }).join(); // Block until streaming is complete

        } catch (Exception e) {
            throw new RuntimeException("Streaming failed: " + e.getMessage(), e);
        }
    }
}
