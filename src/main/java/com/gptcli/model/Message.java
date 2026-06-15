package com.gptcli.model;

public class Message {
    private String role;
    private String content;
    private String imageUrl; // Base64 or URL

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public Message(String role, String content, String imageUrl) {
        this.role = role;
        this.content = content;
        this.imageUrl = imageUrl;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
