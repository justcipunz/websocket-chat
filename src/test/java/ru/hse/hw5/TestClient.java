package ru.hse.hw5;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import ru.hse.hw5.proto.ChatProtocol;

public final class TestClient {
    private TestClient() {
    }

    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "Alex";
        String privateRecipient = args.length > 1 ? args[1] : "Bob";

        CountDownLatch connected = new CountDownLatch(1);
        TestListener listener = new TestListener(connected);

        WebSocket webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:8080/chat"), listener)
                .join();

        connected.await(5, TimeUnit.SECONDS);

        sendJoin(webSocket, name);
        Thread.sleep(300);

        sendPublicMessage(webSocket, "hello from " + name);
        Thread.sleep(300);

        sendPrivateMessage(webSocket, privateRecipient, "private hi from " + name);
        Thread.sleep(300);

        sendMessageWithImage(webSocket, "image message from " + name);
        Thread.sleep(1000);

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
    }

    private static void sendJoin(WebSocket webSocket, String name) {
        ChatProtocol.JoinRequest join = ChatProtocol.JoinRequest.newBuilder()
                .setName(name)
                .build();
        ChatProtocol.ClientRequest request = ChatProtocol.ClientRequest.newBuilder()
                .setJoin(join)
                .build();
        send(webSocket, request);
    }

    private static void sendPublicMessage(WebSocket webSocket, String text) {
        ChatProtocol.SendMessageRequest message = ChatProtocol.SendMessageRequest.newBuilder()
                .setText(text)
                .build();
        ChatProtocol.ClientRequest request = ChatProtocol.ClientRequest.newBuilder()
                .setSendMessage(message)
                .build();
        send(webSocket, request);
    }

    private static void sendPrivateMessage(WebSocket webSocket, String recipient, String text) {
        ChatProtocol.SendMessageRequest message = ChatProtocol.SendMessageRequest.newBuilder()
                .setText(text)
                .setRecipientName(recipient)
                .build();
        ChatProtocol.ClientRequest request = ChatProtocol.ClientRequest.newBuilder()
                .setSendMessage(message)
                .build();
        send(webSocket, request);
    }

    private static void sendMessageWithImage(WebSocket webSocket, String text) {
        byte[] sample = "fake-png-data".getBytes(StandardCharsets.UTF_8);
        ChatProtocol.ImageData image = ChatProtocol.ImageData.newBuilder()
                .setFileName("sample.png")
                .setMimeType("image/png")
                .setData(com.google.protobuf.ByteString.copyFrom(sample))
                .setSizeBytes(sample.length)
                .build();

        ChatProtocol.SendMessageRequest message = ChatProtocol.SendMessageRequest.newBuilder()
                .setText(text)
                .setImage(image)
                .build();
        ChatProtocol.ClientRequest request = ChatProtocol.ClientRequest.newBuilder()
                .setSendMessage(message)
                .build();
        send(webSocket, request);
    }

    private static void send(WebSocket webSocket, ChatProtocol.ClientRequest request) {
        webSocket.sendBinary(ByteBuffer.wrap(request.toByteArray()), true).join();
    }

    private static final class TestListener implements WebSocket.Listener {
        private final CountDownLatch connected;

        private TestListener(CountDownLatch connected) {
            this.connected = connected;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            connected.countDown();
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] payload = new byte[data.remaining()];
            data.get(payload);
            try {
                ChatProtocol.ServerResponse response = ChatProtocol.ServerResponse.parseFrom(payload);
                System.out.println(response);
            } catch (Exception e) {
                System.out.println("Failed to parse ServerResponse: " + e.getMessage());
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            System.out.println("Unexpected text frame: " + data);
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("Closed: " + statusCode + " " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.out.println("WebSocket error: " + error.getMessage());
        }
    }
}
