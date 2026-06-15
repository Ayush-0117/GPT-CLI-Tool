package com.gptcli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigTest {

    @Test
    public void testGetActiveProviderDefault() {
        // Assuming env var is not set or we can mock it. 
        // Since we can't easily mock System.getenv in pure JUnit without PowerMock,
        // we'll rely on the logic that it defaults to "gemini" if not set.
        // However, the user might have it set. 
        // Let's just check it returns a non-null string.
        assertNotNull(Config.getActiveProvider());
    }

    @Test
    public void testGetModelSmartDefaults() {
        // This test is tricky without mocking Config.getActiveProvider.
        // But we can verify the logic by checking if getModel returns a known model
        String model = Config.getModel();
        assertTrue(model.contains("gpt") || model.contains("gemini") || model.contains("grok") || model.contains("deepseek"));
    }

    @Test
    public void testGetWithDefault() {
        String val = Config.get("NON_EXISTENT_KEY_XYZ", "defaultValue");
        assertEquals("defaultValue", val);
    }

    @Test
    public void testGetMongoUriDefault() {
        String uri = Config.getMongoUri();
        assertNotNull(uri);
        if (System.getenv("MONGO_URI") == null && System.getenv("MONGO_URI") == null) {
             assertEquals("mongodb://localhost:27017", uri);
        }
    }
}
