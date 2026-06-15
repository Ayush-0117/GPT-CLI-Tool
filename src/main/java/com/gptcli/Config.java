package com.gptcli;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    public static String get(String key) {
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return dotenv.get(key);
    }

    public static String get(String key, String defaultValue) {
        // Check system env first (consistent with the single-arg get())
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return dotenv.get(key, defaultValue);
    }

    public static String getOpenAiApiKey() {
        return get("OPENAI_API_KEY");
    }

    public static String getGeminiApiKey() {
        return get("GEMINI_API_KEY");
    }

    public static String getGrokApiKey() {
        return get("GROK_API_KEY");
    }

    public static String getDeepSeekApiKey() {
        return get("DEEPSEEK_API_KEY");
    }

    public static String getActiveProvider() {
        return get("ACTIVE_PROVIDER") != null ? get("ACTIVE_PROVIDER") : "gemini";
    }

    public static String getMongoUri() {
        return get("MONGO_URI", "mongodb://localhost:27017");
    }

    public static String getModel() {
        String configuredModel = get("MODEL");
        if (configuredModel != null && !configuredModel.isEmpty()) {
            return configuredModel;
        }
        
        // Smart defaults based on provider
        String provider = getActiveProvider();
        return com.gptcli.service.ModelService.getBestModel(provider);
    }

    public static String getGoogleSearchApiKey() {
        return get("GOOGLE_SEARCH_API_KEY");
    }

    public static String getGoogleSearchCx() {
        return get("GOOGLE_SEARCH_CX");
    }
}
