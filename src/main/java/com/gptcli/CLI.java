package com.gptcli;

import com.gptcli.model.Message;
import com.gptcli.model.Session;
import com.gptcli.service.*;
import com.gptcli.util.Formatter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CLI {
    private ChatClient chatClient;
    private final List<Message> history;
    private volatile MongoStorage storage; // volatile for async init visibility

    private final DeepResearchService deepResearchService;
    private String pendingImage = null;
    private String pendingImageMime = null;

    // Fix #7: Precompile the regex pattern once (was recompiled on every response)
    private static final Pattern CODE_BLOCK_PATTERN =
            Pattern.compile("```(?:bash|sh|zsh)?\\n(.*?)```", Pattern.DOTALL);

    // Fix #4: Context window budget (~100K chars ≈ 25K tokens)
    private static final int MAX_CONTEXT_CHARS = 100_000;

    // Fix #8: Single Scanner instance, reused everywhere
    private Scanner scanner;

    public CLI() {
        this.history = new ArrayList<>();
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String userName = System.getProperty("user.name");
        String time = java.time.LocalDateTime.now().toString();
        
        String systemPrompt = "You are a helpful AI assistant in a terminal.\n" +
                            "Current System Context:\n" +
                            "- OS: " + osName + " " + osVersion + "\n" +
                            "- User: " + userName + "\n" +
                            "- Time: " + time + "\n" +
                            "You can suggest shell commands by wrapping them in markdown code blocks like:\n" +
                            "```bash\n" +
                            "command here\n" +
                            "```\n" +
                            "The user will be prompted to execute them.";
        this.history.add(new Message("system", systemPrompt));
        
        // Fix #2: Initialize MongoDB asynchronously so CLI prompt appears instantly
        CompletableFuture.runAsync(() -> {
            try {
                this.storage = new MongoStorage();
            } catch (Exception e) {
                System.out.println(Formatter.error("Warning: MongoDB not connected. History will not be saved."));
                // this.storage remains null
            }
        });

        // Initialize Services
        this.deepResearchService = new DeepResearchService();

        // Initialize Provider
        setProvider(Config.getActiveProvider());
    }

    private void setProvider(String provider) {
        try {
            switch (provider.toLowerCase()) {
                case "openai":
                    this.chatClient = new OpenAIChatClient();
                    break;
                case "gemini":
                    this.chatClient = new GeminiChatClient();
                    break;
                case "grok":
                    this.chatClient = new GenericChatClient(Config.getGrokApiKey(), "https://api.grok.x.ai/v1", "grok-beta");
                    break;
                case "deepseek":
                    this.chatClient = new GenericChatClient(Config.getDeepSeekApiKey(), "https://api.deepseek.com/v1", "deepseek-chat");
                    break;
                default:
                    System.out.println(Formatter.error("Unknown provider: " + provider + ". Falling back to OpenAI."));
                    this.chatClient = new OpenAIChatClient();
            }
            System.out.println(Formatter.success("Switched to provider: " + provider));
        } catch (Exception e) {
            System.out.println(Formatter.error("Failed to initialize provider " + provider + ": " + e.getMessage()));
        }
    }

    public void start() {
        Formatter.printBanner();
        
        String welcomeMsg = "Provider: " + Config.getActiveProvider() + "\n" +
                           "Model:    " + Config.getModel() + "\n" +
                           "Type :help for commands.";
        Formatter.printBox("Welcome", welcomeMsg);

        // Add Shutdown Hook for Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n" + Formatter.info("Saving session before exit..."));
            saveSession();
        }));

        // Fix #8: Use a single Scanner instance across the entire CLI lifecycle
        scanner = new Scanner(System.in);
        while (true) {
            Formatter.printPrompt();
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            if (input.startsWith(":")) {
                handleCommand(input);
            } else if (input.startsWith("!")) {
                handleShellCommand(input);
            } else if (input.equalsIgnoreCase("help")) {
                showHelp();
            } else if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                handleCommand(":exit");
            } else {
                handleChat(input);
            }
        }
    }

    private void handleCommand(String input) {
        String[] parts = input.split(" ", 2);
        String command = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case ":exit":
                saveSession();
                System.out.println(Formatter.info("Goodbye!"));
                System.exit(0);
                break;
            case ":help":
                showHelp();
                break;
            case ":clear":
                // Preserve just the system prompt
                Message systemMsg = null;
                for (Message m : history) {
                    if ("system".equals(m.getRole())) {
                        systemMsg = m;
                        break;
                    }
                }
                history.clear();
                if (systemMsg != null) {
                    history.add(systemMsg);
                } else {
                    history.add(new Message("system", "You are a helpful AI assistant in a terminal."));
                }
                System.out.println(Formatter.success("Context cleared."));
                break;
            case ":provider":
                if (args.isEmpty()) {
                    System.out.println(Formatter.error("Usage: :provider <openai|gemini|grok|deepseek>"));
                } else {
                    setProvider(args);
                }
                break;
            case ":history":
                showHistory();
                break;
            case ":add":
                if (args.isEmpty()) {
                    System.out.println(Formatter.error("Usage: :add <file_or_dir>"));
                } else {
                    ContextManager.addFile(args, history);
                }
                break;
            case ":image":
                if (args.isEmpty()) {
                    System.out.println(Formatter.error("Usage: :image <path_to_image>"));
                } else {
                    handleImage(args);
                }
                break;
            case ":deep":
                if (args.isEmpty()) {
                    System.out.println(Formatter.error("Usage: :deep <query>"));
                } else {
                    handleDeepResearch(args);
                }
                break;
            case ":tokens":
                // New command: show context usage
                showContextUsage();
                break;
            case "open":
                handleOpenFile(args);
                break;
            case "save":
                handleSaveFile(args);
                break;
            default:
                System.out.println(Formatter.error("Unknown command: " + command));
        }
    }

    // Fix #14: Detect MIME type from file extension
    private void handleImage(String path) {
        try {
            Path filePath = Paths.get(path);
            byte[] fileContent = Files.readAllBytes(filePath);
            String base64 = java.util.Base64.getEncoder().encodeToString(fileContent);

            // Detect MIME type from file extension
            String mimeType = Files.probeContentType(filePath);
            if (mimeType == null || !mimeType.startsWith("image/")) {
                // Fallback: detect from extension
                String name = filePath.getFileName().toString().toLowerCase();
                if (name.endsWith(".png")) mimeType = "image/png";
                else if (name.endsWith(".gif")) mimeType = "image/gif";
                else if (name.endsWith(".webp")) mimeType = "image/webp";
                else if (name.endsWith(".bmp")) mimeType = "image/bmp";
                else mimeType = "image/jpeg"; // Default fallback
            }

            this.pendingImage = "data:" + mimeType + ";base64," + base64;
            this.pendingImageMime = mimeType;
            System.out.println(Formatter.success("Image attached (" + mimeType + "). It will be sent with your next message."));
        } catch (Exception e) {
            System.out.println(Formatter.error("Failed to load image: " + e.getMessage()));
        }
    }

    private void handleDeepResearch(String query) {
        System.out.println(Formatter.info("Performing deep research on: " + query + " ..."));
        String researchResult = deepResearchService.searchAndScrape(query);
        
        // Add research result to context as a system/user message
        history.add(new Message("user", "Research Data:\n" + researchResult + "\n\nBased on the above research, please answer the query: " + query));
        
        // Trigger chat
        handleChat("Please summarize the research findings.");
    }

    // ... handleOpenFile, handleSaveFile, saveSession, showHistory, handleShellCommand ...

    private void handleChat(String input) {
        if (pendingImage != null) {
            history.add(new Message("user", input, pendingImage));
            pendingImage = null;
            pendingImageMime = null;
        } else {
            history.add(new Message("user", input));
        }

        // Fix #4: Trim history if it exceeds the context budget
        trimHistory();

        int loopCount = 0;
        final int MAX_LOOPS = 5;

        while (loopCount < MAX_LOOPS) {
            loopCount++;
            
            try {
                // Modern spinner animation
                Thread spinnerThread = new Thread(() -> {
                    String[] spinner = {" ", "▂", "▃", "▄", "▅", "▆", "▇", "█", "▇", "▆", "▅", "▄", "▃", "▂"};
                    int i = 0;
                    while (!Thread.currentThread().isInterrupted()) {
                        System.out.print("\r" + com.diogonunes.jcolor.Ansi.colorize(" " + spinner[i++ % spinner.length] + " Thinking...", com.diogonunes.jcolor.Attribute.CYAN_TEXT()));
                        try { Thread.sleep(80); } catch (InterruptedException e) { break; }
                    }
                });
                spinnerThread.start();
    
                StringBuilder responseBuilder = new StringBuilder();
                final StringBuilder lineBuffer = new StringBuilder();
                
                final boolean[] spinnerRunning = {true};
                
                chatClient.streamChat(history, token -> {
                    if (spinnerRunning[0]) {
                        spinnerRunning[0] = false;
                        spinnerThread.interrupt();
                        try { spinnerThread.join(); } catch (InterruptedException e) {}
                        
                        // Clear spinner line
                        System.out.print("\r" + " ".repeat(30) + "\r");
                        
                        // Print header
                        System.out.print(com.diogonunes.jcolor.Ansi.colorize("\nAI", com.diogonunes.jcolor.Attribute.BOLD(), com.diogonunes.jcolor.Attribute.GREEN_TEXT()) + ": ");
                    }
                    
                    responseBuilder.append(token);
                    lineBuffer.append(token);
                    
                    // Fix #9: Check the incoming token first (fast path) before scanning the entire buffer
                    if (token.contains("\n")) {
                        String buffered = lineBuffer.toString();
                        int lastNewline = buffered.lastIndexOf("\n");
                        if (lastNewline >= 0) {
                            String toPrint = buffered.substring(0, lastNewline + 1);
                            String remnant = buffered.substring(lastNewline + 1);
                            
                            System.out.print(Formatter.renderMarkdown(toPrint));
                            
                            lineBuffer.setLength(0);
                            lineBuffer.append(remnant);
                        }
                    }
                });
                
                if (spinnerRunning[0]) {
                     spinnerRunning[0] = false;
                     spinnerThread.interrupt();
                     try { spinnerThread.join(); } catch (InterruptedException e) {}
                     System.out.print("\r" + " ".repeat(30) + "\r");
                }
    
                // Print remaining buffer (if any)
                if (lineBuffer.length() > 0) {
                    System.out.print(Formatter.renderMarkdown(lineBuffer.toString()));
                }
                System.out.println(); // Final newline

                String fullResponse = responseBuilder.toString();
                
                history.add(new Message("assistant", fullResponse));
                
                // Check for commands to execute (Fix #7: uses precompiled pattern, Fix #8: reuses scanner)
                boolean executed = checkForCommands(fullResponse);
                
                if (!executed) {
                    break; // Exit loop if no command was executed
                }
                
                // If executed, we loop again (AI thinks about the result)
                System.out.println(Formatter.info("  (AI is thinking about the result...)"));
                
            } catch (Exception e) {
                System.out.println(Formatter.error("\nError: " + e.getMessage()));
                if (!history.isEmpty() && history.get(history.size() - 1).getRole().equals("user")) {
                     history.remove(history.size() - 1);
                }
                break;
            }
        }
    }

    /**
     * Fix #4: Trim history to stay within the context budget.
     * Keeps the system prompt and the most recent messages.
     */
    private void trimHistory() {
        int totalChars = 0;
        for (Message msg : history) {
            totalChars += msg.getContent().length();
        }

        if (totalChars <= MAX_CONTEXT_CHARS) return;

        // Keep the system prompt (index 0) and trim from the front
        System.out.println(Formatter.info("Context is large (" + totalChars / 1000 + "K chars). Trimming older messages..."));
        
        while (history.size() > 2 && totalChars > MAX_CONTEXT_CHARS) {
            // Remove the oldest non-system message
            Message removed = history.remove(1);
            totalChars -= removed.getContent().length();
        }
    }

    /**
     * New command: Show context usage for debugging token limits
     */
    private void showContextUsage() {
        int totalChars = 0;
        int userCount = 0, assistantCount = 0, systemCount = 0;
        for (Message msg : history) {
            totalChars += msg.getContent().length();
            switch (msg.getRole()) {
                case "user": userCount++; break;
                case "assistant": assistantCount++; break;
                case "system": systemCount++; break;
            }
        }
        String usage = "Messages: " + history.size() + " (" + systemCount + " system, " + userCount + " user, " + assistantCount + " assistant)\n" +
                       "Context:  " + totalChars / 1000 + "K / " + MAX_CONTEXT_CHARS / 1000 + "K chars (~" + totalChars / 4 + " tokens est.)";
        Formatter.printBox("Context Usage", usage);
    }

    private boolean checkForCommands(String response) {
        // Fix #7: Uses precompiled static pattern instead of recompiling
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(response);
        
        // Fix #8: Reuses the main scanner instance instead of creating a new one
        boolean anyExecuted = false;
        
        while (matcher.find()) {
            String command = matcher.group(1).trim();
            if (command.isEmpty()) continue;
            
            System.out.println(Formatter.info("\nAI suggests executing command:"));
            System.out.println(Formatter.code(command));
            System.out.print(Formatter.info("Do you want to execute this? (y/N): "));
            
            String choice = scanner.nextLine().trim();
            if (choice.equalsIgnoreCase("y")) {
                System.out.println(Formatter.info("Executing..."));
                String output = CommandRunner.run(command);
                
                // Add execution result to history
                history.add(new Message("user", "Executed command: " + command + "\nOutput:\n" + output));
                anyExecuted = true;
                
            } else {
                System.out.println(Formatter.info("Skipped execution."));
                history.add(new Message("user", "User skipped execution of command: " + command));
            }
        }
        return anyExecuted;
    }

    private void showHelp() {
        String help = ":help            Show this help message\n" +
                     ":exit            Exit and save session\n" +
                     ":clear           Clear chat context\n" +
                     ":provider <p>    Switch provider (openai, gemini, grok, deepseek)\n" +
                     ":history         List past sessions\n" +
                     ":add <path>      Add file/dir to context\n" +
                     ":image <path>    Attach image to next message\n" +
                     ":deep <query>    Perform deep web research\n" +
                     ":tokens          Show context window usage\n" +
                     "!cmd             Run shell command";
        Formatter.printBox("Commands", help);
    }

    private void handleOpenFile(String path) {
        if (path.isEmpty()) {
            System.out.println(Formatter.error("Usage: open <file_path>"));
            return;
        }
        String content = FileManager.readFile(path);
        System.out.println(Formatter.code(content));
        // Optionally add to context
        history.add(new Message("user", "Content of file " + path + ":\n" + content));
        System.out.println(Formatter.info("File content added to context."));
    }

    private void handleSaveFile(String path) {
        if (path.isEmpty()) {
            System.out.println(Formatter.error("Usage: save <file_path>"));
            return;
        }
        // Get last assistant message
        String lastResponse = "";
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("assistant".equals(history.get(i).getRole())) {
                lastResponse = history.get(i).getContent();
                break;
            }
        }
        
        if (lastResponse.isEmpty()) {
            System.out.println(Formatter.error("No assistant response to save."));
            return;
        }
        
        FileManager.saveFile(path, lastResponse);
    }

    private void saveSession() {
        if (storage == null) return;
        if (history.size() <= 1) return; // Don't save empty sessions (just system prompt)
        try {
            Session session = new Session(history);
            storage.saveSession(session);
            System.out.println(Formatter.success("Session saved: " + session.getId()));
        } catch (Exception e) {
            System.out.println(Formatter.error("Failed to save session: " + e.getMessage()));
        }
    }

    private void showHistory() {
        if (storage == null) {
            System.out.println(Formatter.error("History unavailable (MongoDB not connected)."));
            return;
        }
        try {
            var sessions = storage.listSessions();
            System.out.println(Formatter.info("--- Recent Sessions ---"));
            for (var doc : sessions) {
                System.out.println(doc.getString("_id") + " | " + doc.getString("createdAt"));
            }
        } catch (Exception e) {
            System.out.println(Formatter.error("Failed to list history: " + e.getMessage()));
        }
    }

    private void handleShellCommand(String input) {
        String command = input.substring(1).trim(); // Remove '!'
        String output = CommandRunner.run(command);
        
        // Add command and output to history so AI knows what happened
        history.add(new Message("user", "Executed shell command: " + command + "\nOutput:\n" + output));
    }
}
