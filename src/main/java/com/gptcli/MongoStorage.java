package com.gptcli;

import com.gptcli.model.Message;
import com.gptcli.model.Session;
import com.gptcli.util.Formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MongoStorage {
    private final com.mongodb.client.MongoClient client;
    private final com.mongodb.client.MongoCollection<org.bson.Document> sessions;

    public MongoStorage() {
        // Fix #2: Apply a short server-selection timeout so startup doesn't block for 30s
        com.mongodb.MongoClientSettings settings = com.mongodb.MongoClientSettings.builder()
                .applyConnectionString(new com.mongodb.ConnectionString(Config.getMongoUri()))
                .applyToClusterSettings(builder ->
                        builder.serverSelectionTimeout(3, java.util.concurrent.TimeUnit.SECONDS))
                .build();
        this.client = com.mongodb.client.MongoClients.create(settings);
        com.mongodb.client.MongoDatabase db = client.getDatabase("gpt_cli_tool");
        this.sessions = db.getCollection("sessions");
    }

    public void saveSession(Session session) {
        List<org.bson.Document> messageDocs = session.getMessages().stream()
                .map(m -> new org.bson.Document("role", m.getRole()).append("content", m.getContent()))
                .collect(Collectors.toList());

        org.bson.Document doc = new org.bson.Document("_id", session.getId())
                .append("createdAt", session.getCreatedAt())
                .append("messages", messageDocs);

        sessions.insertOne(doc);
    }

    public List<org.bson.Document> listSessions() {
        return sessions.find().sort(new org.bson.Document("createdAt", -1)).into(new ArrayList<>());
    }
    
    public Session loadSession(String id) {
        org.bson.Document doc = sessions.find(new org.bson.Document("_id", id)).first();
        if (doc == null) return null;
        
        List<org.bson.Document> msgDocs = doc.getList("messages", org.bson.Document.class);
        List<Message> messages = msgDocs.stream()
                .map(d -> new Message(d.getString("role"), d.getString("content")))
                .collect(Collectors.toList());
                
        // Reconstruct session (ID is lost in this simple constructor but that's fine for loading history)
        return new Session(messages);
    }
}
