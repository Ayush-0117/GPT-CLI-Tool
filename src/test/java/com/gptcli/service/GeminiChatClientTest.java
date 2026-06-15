package com.gptcli.service;

import com.google.gson.Gson;
import com.gptcli.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GeminiChatClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GeminiChatClient chatClient;
    private Gson gson;

    @BeforeEach
    public void setUp() {
        gson = new Gson();
        chatClient = new GeminiChatClient(httpClient, gson);
    }

    @Test
    public void testChatSuccess() throws Exception {
        // Mock response body
        String jsonResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "Hello from Gemini" }
                    ]
                  }
                }
              ]
            }
            """;

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(httpResponse);

        List<Message> history = List.of(new Message("user", "Hello"));
        String response = chatClient.chat(history);

        assertEquals("Hello from Gemini", response);
        verify(httpClient, times(1)).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    public void testChatFailure() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("Internal Server Error");
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(httpResponse);

        List<Message> history = List.of(new Message("user", "Hello"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            chatClient.chat(history);
        });

        assertTrue(exception.getMessage().contains("Gemini API Error: 500"));
    }
}
