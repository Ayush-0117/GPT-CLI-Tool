package com.gptcli.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Session {
    private String id;
    private String createdAt;
    private List<Message> messages;

    public Session(List<Message> messages) {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now().toString();
        this.messages = messages;
    }

    public String getId() {
        return id;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public List<Message> getMessages() {
        return messages;
    }
}
