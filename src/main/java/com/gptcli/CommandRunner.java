package com.gptcli;

import com.gptcli.util.Formatter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class CommandRunner {

    private static final int TIMEOUT_SECONDS = 30;

    public static String run(String command) {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder processBuilder = new ProcessBuilder();

        if (os.contains("win")) {
            processBuilder.command("cmd.exe", "/c", command);
        } else {
            processBuilder.command("sh", "-c", command);
        }

        // Fix #6: Merge stdout and stderr to avoid pipe-buffer deadlock
        processBuilder.redirectErrorStream(true);

        StringBuilder outputBuilder = new StringBuilder();

        try {
            Process process = processBuilder.start();
            
            // Single reader for merged stdout+stderr
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                outputBuilder.append(line).append("\n");
            }

            // Fix #6: Timeout — don't wait forever for a stuck process
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                String msg = "Command timed out after " + TIMEOUT_SECONDS + "s — killed.";
                System.out.println(Formatter.error(msg));
                outputBuilder.append(msg).append("\n");
            } else {
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    String msg = "Command exited with code: " + exitCode;
                    System.out.println(Formatter.error(msg));
                    outputBuilder.append(msg).append("\n");
                }
            }

        } catch (Exception e) {
            String msg = "Failed to run command: " + e.getMessage();
            System.out.println(Formatter.error(msg));
            outputBuilder.append(msg).append("\n");
        }
        
        return outputBuilder.toString();
    }
}
