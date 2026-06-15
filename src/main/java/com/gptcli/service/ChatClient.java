package com.gptcli.service;

import com.gptcli.model.Message;
import java.util.List;
import java.util.function.Consumer;

public interface ChatClient {
    String chat(List<Message> history);
    void streamChat(List<Message> history, Consumer<String> onUpdate);
}
