package ru.hse.hw5.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ru.hse.hw5.proto.ChatProtocol;

public final class ImageHistory {
    private static final int MAX_IMAGES = 50;

    private final List<ChatProtocol.ChatMessage> imageMessages = new ArrayList<>();

    public synchronized void addFromMessage(ChatProtocol.ChatMessage message) {
        if (!message.hasAttachment()) {
            return;
        }

        imageMessages.add(message);
        if (imageMessages.size() > MAX_IMAGES) {
            imageMessages.remove(0);
        }
    }

    public synchronized Optional<ChatProtocol.ImageData> lastVisibleFor(String userName) {
        for (int i = imageMessages.size() - 1; i >= 0; i--) {
            ChatProtocol.ChatMessage message = imageMessages.get(i);

            if (!message.getPrivateMessage()) {
                return Optional.of(message.getAttachment().getImage());
            }

            boolean isSender = message.getSenderName().equals(userName);
            boolean isRecipient = message.getRecipientName().equals(userName);
            if (isSender || isRecipient) {
                return Optional.of(message.getAttachment().getImage());
            }
        }
        return Optional.empty();
    }

    public synchronized int size() {
        return imageMessages.size();
    }
}
