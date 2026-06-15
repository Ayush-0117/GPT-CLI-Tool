package com.gptcli.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gptcli.Config;
import com.gptcli.model.Message;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

public class OpenAIChatClient implements ChatClient {
    private final HttpClient client;
    private final Gson gson;
    private final String apiKey;

    public OpenAIChatClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.apiKey = Config.getOpenAiApiKey();
    }

    @Override
    public String chat(List<Message> history) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", Config.getModel());
            
            JsonArray messages = new JsonArray();
            for (Message msg : history) {
                JsonObject messageObj = new JsonObject();
                messageObj.addProperty("role", msg.getRole());

                if (msg.getImageUrl() != null && !msg.getImageUrl().isEmpty()) {
                    JsonArray contentArray = new JsonArray();
                    
                    JsonObject textPart = new JsonObject();
                    textPart.addProperty("type", "text");
                    textPart.addProperty("text", msg.getContent());
                    contentArray.add(textPart);

                    JsonObject imagePart = new JsonObject();
                    imagePart.addProperty("type", "image_url");
                    JsonObject imageUrl = new JsonObject();
                    imageUrl.addProperty("url", msg.getImageUrl());
                    imagePart.add("image_url", imageUrl);
                    contentArray.add(imagePart);

                    messageObj.add("content", contentArray);
                } else {
                    messageObj.addProperty("content", msg.getContent());
                }
                messages.add(messageObj);
            }
            requestBody.add("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
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
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", Config.getModel());
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
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("API Error: " + response.statusCode());
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
                                } catch (Exception ignored) {}
                            }
                        });
                    }).join();

        } catch (Exception e) {
            throw new RuntimeException("Streaming failed: " + e.getMessage());
        }
    }
}
