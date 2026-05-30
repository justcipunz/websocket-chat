package ru.hse.hw5.chat;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hse.hw5.model.ErrorCode;
import ru.hse.hw5.protocol.ErrorFactory;
import ru.hse.hw5.protocol.ProtocolValidator;
import ru.hse.hw5.proto.ChatProtocol;

public final class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_ACTIVE_USERS = 100;

    private final ConcurrentHashMap<ChannelId, ChatSession> sessionsByChannelId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChatSession> sessionsByUserName = new ConcurrentHashMap<>();
    private final AtomicLong messageIdGenerator = new AtomicLong(1);
    private final ChatMessageHistory messageHistory = new ChatMessageHistory();
    private final ImageHistory imageHistory = new ImageHistory();

    public List<OutboundMessage> handleClientRequest(Channel channel, ChatProtocol.ClientRequest request) {
        if (request.getPayloadCase() == ChatProtocol.ClientRequest.PayloadCase.PAYLOAD_NOT_SET) {
            return List.of(errorTo(channel, ErrorCode.INVALID_REQUEST, "ClientRequest payload is required"));
        }

        return switch (request.getPayloadCase()) {
            case JOIN -> handleJoin(channel, request.getJoin());
            case SEND_MESSAGE -> handleSendMessage(channel, request.getSendMessage());
            case PAYLOAD_NOT_SET -> List.of(errorTo(channel, ErrorCode.INVALID_REQUEST, "ClientRequest payload is required"));
        };
    }

    public void handleDisconnect(Channel channel) {
        ChatSession removed = sessionsByChannelId.remove(channel.id());
        if (removed == null || !removed.isJoined()) {
            return;
        }

        sessionsByUserName.remove(removed.userName(), removed);
        log.info("WebSocket client disconnected: user={}", removed.userName());
    }

    private List<OutboundMessage> handleJoin(Channel channel, ChatProtocol.JoinRequest joinRequest) {
        ChatSession session = sessionsByChannelId.computeIfAbsent(channel.id(), id -> new ChatSession(channel));
        if (session.isJoined()) {
            log.warn("Join rejected: already joined");
            return List.of(errorTo(channel, ErrorCode.ALREADY_JOINED, "User is already joined"));
        }

        String normalizedName = joinRequest.getName().trim();
        ChatProtocol.JoinRequest normalizedJoin = ChatProtocol.JoinRequest.newBuilder()
                .setName(normalizedName)
                .build();
        if (joinRequest.hasIcon()) {
            normalizedJoin = normalizedJoin.toBuilder().setIcon(joinRequest.getIcon()).build();
        }

        ProtocolValidator.ValidationResult validation = ProtocolValidator.validateJoinRequest(normalizedJoin);
        if (!validation.isValid()) {
            log.warn("Join rejected: {}", validation.errorCode().error());
            return List.of(errorTo(channel, validation.errorCode(), validation.message()));
        }

        // Check duplicate name first so duplicate-name errors win even on a full server.
        if (sessionsByUserName.get(normalizedName) != null) {
            log.warn("Join rejected: name already taken");
            return List.of(errorTo(channel, ErrorCode.NAME_ALREADY_TAKEN, "Name is already taken"));
        }
        if (sessionsByUserName.size() >= MAX_ACTIVE_USERS) {
            log.warn("Join rejected: too many users");
            return List.of(errorTo(channel, ErrorCode.TOO_MANY_USERS, "Too many users connected"));
        }

        ChatSession existing = sessionsByUserName.putIfAbsent(normalizedName, session);
        if (existing != null) {
            log.warn("Join rejected: name already taken (race)");
            return List.of(errorTo(channel, ErrorCode.NAME_ALREADY_TAKEN, "Name is already taken"));
        }
        if (sessionsByUserName.size() > MAX_ACTIVE_USERS) {
            sessionsByUserName.remove(normalizedName, session);
            log.warn("Join rejected: too many users (race)");
            return List.of(errorTo(channel, ErrorCode.TOO_MANY_USERS, "Too many users connected"));
        }

        session.setUserName(normalizedName);
        session.setJoined(true);
        if (normalizedJoin.hasIcon()) {
            session.setUserIcon(normalizedJoin.getIcon());
        }

        ChatProtocol.History history = ChatProtocol.History.newBuilder()
                .addAllMessages(messageHistory.visibleFor(normalizedName))
                .build();

        ChatProtocol.JoinSuccess.Builder joinSuccess = ChatProtocol.JoinSuccess.newBuilder()
                .setAssignedName(normalizedName)
                .setHistory(history);

        Optional<ChatProtocol.ImageData> lastImage = imageHistory.lastVisibleFor(normalizedName);
        lastImage.ifPresent(joinSuccess::setLastImage);

        ChatProtocol.ServerResponse response = ChatProtocol.ServerResponse.newBuilder()
                .setJoinSuccess(joinSuccess.build())
                .build();

        log.info("Join success: user={}", normalizedName);
        return List.of(new OutboundMessage(channel, response));
    }

    private List<OutboundMessage> handleSendMessage(Channel channel, ChatProtocol.SendMessageRequest sendMessageRequest) {
        ChatSession senderSession = sessionsByChannelId.get(channel.id());
        if (senderSession == null || !senderSession.isJoined()) {
            log.warn("Message rejected: sender is not joined");
            return List.of(errorTo(channel, ErrorCode.NOT_JOINED, "Join is required before sending messages"));
        }

        String normalizedText = sendMessageRequest.getText().trim();
        boolean hasRecipient = sendMessageRequest.hasRecipientName();
        String normalizedRecipient = hasRecipient ? sendMessageRequest.getRecipientName().trim() : "";

        if (hasRecipient && normalizedRecipient.isEmpty()) {
            log.warn("Message rejected: recipient is empty after trim");
            return List.of(errorTo(channel, ErrorCode.USER_NOT_FOUND, "Recipient user not found"));
        }

        ChatProtocol.SendMessageRequest.Builder normalizedBuilder = ChatProtocol.SendMessageRequest.newBuilder()
                .setText(normalizedText);
        if (sendMessageRequest.hasImage()) {
            normalizedBuilder.setImage(sendMessageRequest.getImage());
        }
        if (hasRecipient) {
            normalizedBuilder.setRecipientName(normalizedRecipient);
        }

        ChatProtocol.SendMessageRequest normalizedRequest = normalizedBuilder.build();
        ProtocolValidator.ValidationResult validation = ProtocolValidator.validateSendMessageRequest(normalizedRequest);
        if (!validation.isValid()) {
            log.warn("Message rejected: {}", validation.errorCode().error());
            return List.of(errorTo(channel, validation.errorCode(), validation.message()));
        }

        boolean isPrivate = normalizedRequest.hasRecipientName();
        ChatSession recipientSession = null;
        if (isPrivate) {
            recipientSession = sessionsByUserName.get(normalizedRecipient);
            if (recipientSession == null) {
                log.warn("Private message rejected: recipient not found");
                return List.of(errorTo(channel, ErrorCode.USER_NOT_FOUND, "Recipient user not found"));
            }
        }

        ChatProtocol.ChatMessage.Builder messageBuilder = ChatProtocol.ChatMessage.newBuilder()
                .setId(messageIdGenerator.getAndIncrement())
                .setSenderName(senderSession.userName())
                .setText(normalizedText)
                .setTimestampEpochMillis(Instant.now().toEpochMilli())
                .setPrivateMessage(isPrivate)
                .setRecipientName(isPrivate ? normalizedRecipient : "");

        if (normalizedRequest.hasImage()) {
            messageBuilder.setAttachment(
                    ChatProtocol.ChatMessage.Attachment.newBuilder()
                            .setImage(normalizedRequest.getImage())
                            .build()
            );
        }
        if (senderSession.userIcon() != null) {
            messageBuilder.setSenderIcon(senderSession.userIcon());
        }

        ChatProtocol.ChatMessage chatMessage = messageBuilder.build();
        messageHistory.add(chatMessage);
        imageHistory.addFromMessage(chatMessage);

        ChatProtocol.ServerResponse response = ChatProtocol.ServerResponse.newBuilder()
                .setMessageEvent(ChatProtocol.ChatMessageEvent.newBuilder().setMessage(chatMessage).build())
                .build();

        List<OutboundMessage> out = new ArrayList<>();
        if (isPrivate) {
            out.add(new OutboundMessage(senderSession.channel(), response));
            if (!recipientSession.channel().id().equals(senderSession.channel().id())) {
                out.add(new OutboundMessage(recipientSession.channel(), response));
            }
            log.info("Private message accepted: sender={}, recipient={}", senderSession.userName(), normalizedRecipient);
            return out;
        }

        for (ChatSession session : sessionsByChannelId.values()) {
            if (session.isJoined()) {
                out.add(new OutboundMessage(session.channel(), response));
            }
        }
        log.info("Public message accepted: sender={}", senderSession.userName());
        return out;
    }

    private static OutboundMessage errorTo(Channel channel, ErrorCode code, String message) {
        return new OutboundMessage(channel, ErrorFactory.error(code, message));
    }

    public record OutboundMessage(Channel channel, ChatProtocol.ServerResponse response) {
    }
}
