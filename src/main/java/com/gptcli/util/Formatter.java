package com.gptcli.util;

import com.diogonunes.jcolor.Ansi;
import com.diogonunes.jcolor.Attribute;

public class Formatter {
    // Modern Color Palette (using standard ANSI for compatibility, but styled)
    private static final Attribute PRIMARY = Attribute.CYAN_TEXT();
    private static final Attribute SECONDARY = Attribute.BLUE_TEXT();
    private static final Attribute ACCENT = Attribute.MAGENTA_TEXT();
    private static final Attribute ERROR = Attribute.RED_TEXT();
    private static final Attribute SUCCESS = Attribute.GREEN_TEXT();

    // Fix #10: Cache terminal width — detected once, reused everywhere
    private static int cachedTerminalWidth = -1;
    
    public static String error(String msg) {
        return Ansi.colorize("✖ " + msg, ERROR);
    }

    public static String success(String msg) {
        return Ansi.colorize("✔ " + msg, SUCCESS);
    }

    public static String info(String msg) {
        return Ansi.colorize(msg, PRIMARY);
    }
    
    public static String code(String msg) {
        return Ansi.colorize(msg, Attribute.WHITE_TEXT(), Attribute.BLACK_BACK()); // standardized
    }

    public static void printBanner() {
        System.out.println();
        System.out.println(Ansi.colorize("   G P T   C L I   ", Attribute.BLACK_TEXT(), Attribute.CYAN_BACK()));
        System.out.println(Ansi.colorize("    by Ayush...       ", Attribute.BRIGHT_BLACK_TEXT()));
        System.out.println();
    }

    private static int getTerminalWidth() {
        // Fix #10: Return cached value if available
        if (cachedTerminalWidth > 0) {
            return cachedTerminalWidth;
        }
        
        int width = 100; // Default fallback

        // Try environment variable first (works on most systems, no subprocess)
        try {
            String cols = System.getenv("COLUMNS");
            if (cols != null && !cols.isEmpty()) {
                width = Integer.parseInt(cols.trim());
                cachedTerminalWidth = width;
                return width;
            }
        } catch (NumberFormatException ignored) {}

        // Try tput (Unix/macOS)
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            try {
                Process process = new ProcessBuilder("sh", "-c", "tput cols").start();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                String output = reader.readLine();
                boolean finished = process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
                if (finished && output != null) {
                    width = Integer.parseInt(output.trim());
                }
            } catch (Exception ignored) {}
        }

        cachedTerminalWidth = width;
        return width;
    }

    public static void printBox(String title, String content) {
        int width = getTerminalWidth();
        if (width < 20) width = 80; // Safety minimum
        
        String horizontal = "─".repeat(width - title.length() - 5); // 5 for "╭─ " and " ─..." chars roughly
        
        // Header
        System.out.println(Ansi.colorize("╭─ " + title + " " + horizontal, SECONDARY));
        
        // Content
        for (String line : content.split("\n")) {
            System.out.println(Ansi.colorize("│ ", SECONDARY) + line);
        }
        
        // Footer
        System.out.println(Ansi.colorize("╰" + "─".repeat(width - 2), SECONDARY));
    }

    public static void printPrompt() {
        System.out.print(Ansi.colorize("\n➜ ", ACCENT) + Ansi.colorize("You", Attribute.BOLD()) + ": ");
    }
    
    public static String renderMarkdown(String text) {
        // Simple Markdown Parser
        
        // Bold: **text**
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", Ansi.colorize("$1", Attribute.BOLD()));
        
        // Inline Code: `text`
        text = text.replaceAll("`([^`]+)`", Ansi.colorize(" $1 ", Attribute.WHITE_TEXT(), Attribute.BRIGHT_BLACK_BACK()));
        
        // Headers: # Text -> Bold Underlined (remove #)
        text = text.replaceAll("(?m)^#+\\s+(.*)$", Ansi.colorize("\n$1", Attribute.BOLD(), Attribute.UNDERLINE()));
        
        // Lists: * Text or - Text -> • Text
        text = text.replaceAll("(?m)^\\s*[\\*\\-]\\s+(.*)$", " • $1");

        return text;
    }
}
