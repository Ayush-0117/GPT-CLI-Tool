package com.gptcli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

public class CommandRunnerTest {

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
    public void testRunEchoCommand() {
        CommandRunner.run("echo 'Hello World'");
        String output = outContent.toString().trim();
        assertEquals("Hello World", output);
    }

    @Test
    public void testRunInvalidCommand() {
        // This test depends on the OS, but 'invalid_command_xyz' is likely invalid on both
        CommandRunner.run("invalid_command_xyz");
        String output = outContent.toString();
        // It should print some error message, likely containing "not found" or similar
        // But Formatter.error adds ANSI codes, so we just check if it's not empty
        assertFalse(output.isEmpty());
    }
}
