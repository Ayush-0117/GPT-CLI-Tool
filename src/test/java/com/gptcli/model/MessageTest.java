package com.gptcli.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MessageTest {

    @Test
    public void testMessageCreation() {
        Message msg = new Message("user", "Hello");
        assertEquals("user", msg.getRole());
        assertEquals("Hello", msg.getContent());
        assertNull(msg.getImageUrl());
    }

    @Test
    public void testMessageWithImage() {
        Message msg = new Message("user", "Look at this", "base64data");
        assertEquals("user", msg.getRole());
        assertEquals("Look at this", msg.getContent());
        assertEquals("base64data", msg.getImageUrl());
    }
}
