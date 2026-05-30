package ru.hse.hw5;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hse.hw5.chat.ChatService;
import ru.hse.hw5.netty.ChatServer;
import ru.hse.hw5.netty.ChatServerInitializer;

public final class ServerMain {
    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);
    private static final int PORT = 8080;

    private ServerMain() {
    }

    public static void main(String[] args) {
        ChatService chatService = new ChatService();
        ChatServer chatServer = new ChatServer(PORT, new ChatServerInitializer(chatService));

        try {
            chatServer.start();
            log.info("Chat server started at ws://localhost:8080/chat");
            chatServer.awaitShutdown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Server interrupted: {}", e.getMessage());
        } finally {
            chatServer.stop();
        }
    }
}
