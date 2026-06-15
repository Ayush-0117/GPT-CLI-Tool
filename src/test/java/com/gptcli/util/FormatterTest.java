package com.gptcli.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

public class FormatterTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
    }

    @Test
    public void testErrorFormatting() {
        String msg = "Test Error";
        String formatted = Formatter.error(msg);
        assertNotNull(formatted);
        assertTrue(formatted.contains(msg));
        // Check for ANSI Red code start
        assertTrue(formatted.contains("\u001B[31m"));
    }

    @Test
    public void testSuccessFormatting() {
        String msg = "Test Success";
        String formatted = Formatter.success(msg);
        assertNotNull(formatted);
        assertTrue(formatted.contains(msg));
        // Check for ANSI Green code start
        assertTrue(formatted.contains("\u001B[32m"));
    }

    @Test
    public void testInfoFormatting() {
        String msg = "Test Info";
        String formatted = Formatter.info(msg);
        assertNotNull(formatted);
        assertTrue(formatted.contains(msg));
        // Check for ANSI Cyan code start
        assertTrue(formatted.contains("\u001B[36m"));
    }

    @Test
    public void testCodeFormatting() {
        String msg = "System.out.println();";
        String formatted = Formatter.code(msg);
        assertNotNull(formatted);
        assertTrue(formatted.contains(msg));
        // Check for ANSI White text and Black background
        assertTrue(formatted.contains("\u001B[37;40m") || (formatted.contains("\u001B[37m") && formatted.contains("\u001B[40m")));
    }

    @Test
    public void testPrintBox() {
        String title = "Test Title";
        String content = "Line 1\nLine 2";
        Formatter.printBox(title, content);
        String output = outContent.toString();
        
        assertTrue(output.contains(title));
        assertTrue(output.contains("Line 1"));
        assertTrue(output.contains("Line 2"));
        assertTrue(output.contains("╭")); // Check for border characters
        assertTrue(output.contains("╰"));
    }
    
    @Test
    public void testPrintBanner() {
        Formatter.printBanner();
        String output = outContent.toString();
        assertTrue(output.contains("G P T"));
    }
}
