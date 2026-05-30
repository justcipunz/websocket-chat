package ru.hse.hw5.chat;

import java.util.ArrayList;
import java.util.List;
import ru.hse.hw5.proto.ChatProtocol;

public final class ChatMessageHistory {
    private static final int MAX_MESSAGES = 50;

    private final List<ChatProtocol.ChatMessage> messages = new ArrayList<>();

    public synchronized void add(ChatProtocol.ChatMessage message) {
        messages.add(message);
        if (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
    }

    public synchronized List<ChatProtocol.ChatMessage> visibleFor(String userName) {
        List<ChatProtocol.ChatMessage> result = new ArrayList<>();
        for (ChatProtocol.ChatMessage message : messages) {
            if (!message.getPrivateMessage()) {
                result.add(message);
                continue;
            }

            boolean isSender = message.getSenderName().equals(userName);
            boolean isRecipient = message.getRecipientName().equals(userName);
            if (isSender || isRecipient) {
                result.add(message);
            }
        }
        return result;
    }

    public synchronized int size() {
        return messages.size();
    }
}
