package com.gptcli;

import com.gptcli.util.Formatter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileManager {
    
    public static String readFile(String pathStr) {
        try {
            Path path = Paths.get(pathStr);
            if (!Files.exists(path)) {
                return Formatter.error("File not found: " + pathStr);
            }
            return Files.readString(path);
        } catch (IOException e) {
            return Formatter.error("Error reading file: " + e.getMessage());
        }
    }

    public static void saveFile(String pathStr, String content) {
        try {
            Path path = Paths.get(pathStr);
            // Fix #13: Check for null parent (root-level filenames like "output.txt")
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content);
            System.out.println(Formatter.success("Saved to " + pathStr));
        } catch (IOException e) {
            System.out.println(Formatter.error("Error saving file: " + e.getMessage()));
        }
    }
}
