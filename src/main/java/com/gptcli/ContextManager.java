package com.gptcli;

import com.gptcli.model.Message;
import com.gptcli.util.Formatter;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public class ContextManager {
    private static final long MAX_FILE_SIZE = 100 * 1024; // 100KB limit per file to avoid token overflow

    public static void addFile(String pathStr, List<Message> history) {
        try {
            Path path = Paths.get(pathStr);
            if (!Files.exists(path)) {
                System.out.println(Formatter.error("File not found: " + pathStr));
                return;
            }
            
            if (Files.isDirectory(path)) {
                addDirectory(path, history);
            } else {
                addSingleFile(path, history);
            }
        } catch (Exception e) {
            System.out.println(Formatter.error("Error adding context: " + e.getMessage()));
        }
    }

    private static void addSingleFile(Path path, List<Message> history) throws IOException {
        if (Files.size(path) > MAX_FILE_SIZE) {
            System.out.println(Formatter.error("Skipping large file: " + path));
            return;
        }
        String content = Files.readString(path);
        history.add(new Message("user", "Context from file " + path.getFileName() + ":\n```\n" + content + "\n```"));
        System.out.println(Formatter.success("Added " + path.getFileName() + " to context."));
    }

    private static void addDirectory(Path dir, List<Message> history) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.getFileName().toString().startsWith(".")) { // Skip hidden files
                    addSingleFile(file, history);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
