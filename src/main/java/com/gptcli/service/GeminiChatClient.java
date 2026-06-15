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

public class GeminiChatClient implements ChatClient {
    private final HttpClient client;
    private final Gson gson;
    private final String apiKey;

    public GeminiChatClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), new Gson());
    }

    public GeminiChatClient(HttpClient client, Gson gson) {
        this.client = client;
        this.gson = gson;
        this.apiKey = Config.get("GEMINI_API_KEY");
    }

    // Fix #3: Extract system prompt from history for Gemini's system_instruction field
    private String extractSystemPrompt(List<Message> history) {
        for (Message msg : history) {
            if ("system".equals(msg.getRole())) {
                return msg.getContent();
            }
        }
        return null;
    }

    @Override
    public String chat(List<Message> history) {
        try {
            String model = Config.getModel();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            JsonObject requestBody = buildRequestBody(history);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                return json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                        .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                        .get("text").getAsString();
            } else {
                throw new RuntimeException("Gemini API Error: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void streamChat(List<Message> history, Consumer<String> onUpdate) {
        // Gemini streaming uses streamGenerateContent endpoint
        try {
            String model = Config.getModel();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey;

            JsonObject requestBody = buildRequestBody(history);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            System.out.println(com.gptcli.util.Formatter.error("Error: " + response.statusCode()));
                            response.body().forEach(line -> System.out.println(com.gptcli.util.Formatter.error(line)));
                            return;
                        }
                        response.body().forEach(line -> {
                            if (line.startsWith("data: ")) {
                                String jsonStr = line.substring(6);
                                try {
                                    JsonObject json = gson.fromJson(jsonStr, JsonObject.class);
                                    JsonArray candidates = json.getAsJsonArray("candidates");
                                    if (candidates != null && candidates.size() > 0) {
                                        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                                        if (content != null) {
                                            JsonArray parts = content.getAsJsonArray("parts");
                                            if (parts != null && parts.size() > 0) {
                                                String text = parts.get(0).getAsJsonObject().get("text").getAsString();
                                                onUpdate.accept(text);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    // Ignore parsing errors for partial chunks
                                }
                            }
                        });
                    }).join();

        } catch (Exception e) {
            throw new RuntimeException("Gemini Streaming failed: " + e.getMessage());
        }
    }

    /**
     * Builds the Gemini request body with proper system_instruction support.
     * Fix #3: System prompts are no longer silently discarded.
     */
    private JsonObject buildRequestBody(List<Message> history) {
        JsonObject requestBody = new JsonObject();

        // Extract and set system instruction (Fix #3)
        String systemPrompt = extractSystemPrompt(history);
        if (systemPrompt != null) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray systemParts = new JsonArray();
            JsonObject systemText = new JsonObject();
            systemText.addProperty("text", systemPrompt);
            systemParts.add(systemText);
            systemInstruction.add("parts", systemParts);
            requestBody.add("system_instruction", systemInstruction);
        }

        JsonArray contents = new JsonArray();
        for (Message msg : history) {
            if ("system".equals(msg.getRole())) continue; // Handled above

            JsonObject content = new JsonObject();
            content.addProperty("role", "user".equals(msg.getRole()) ? "user" : "model");

            JsonArray parts = new JsonArray();

            if (msg.getImageUrl() != null && !msg.getImageUrl().isEmpty()) {
                JsonObject inlineData = new JsonObject();
                inlineData.addProperty("mime_type", "image/jpeg");
                String base64Data = msg.getImageUrl().contains(",") ? msg.getImageUrl().split(",")[1] : msg.getImageUrl();
                inlineData.addProperty("data", base64Data);

                JsonObject imagePart = new JsonObject();
                imagePart.add("inline_data", inlineData);
                parts.add(imagePart);
            }

            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", msg.getContent());
            parts.add(textPart);

            content.add("parts", parts);
            contents.add(content);
        }
        requestBody.add("contents", contents);

        return requestBody;
    }
}
