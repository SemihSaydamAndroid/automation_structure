package io.github.semihsaydamandroid.automation.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/** Scripted model: returns queued answers and records every request. */
class FakeChatModel implements ChatModel {

    final Deque<String> answers = new ArrayDeque<>();
    final List<ChatRequest> requests = new ArrayList<>();
    RuntimeException failure;

    FakeChatModel answer(String text) {
        answers.add(text);
        return this;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        requests.add(request);
        if (failure != null) {
            throw failure;
        }
        return ChatResponse.builder().aiMessage(AiMessage.from(answers.isEmpty() ? "" : answers.poll())).build();
    }

    String lastPrompt() {
        return requests.getLast().messages().getLast().toString();
    }
}
