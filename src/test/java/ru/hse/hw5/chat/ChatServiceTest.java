package ru.hse.hw5.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.local.LocalChannel;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.hse.hw5.model.ErrorCode;
import ru.hse.hw5.proto.ChatProtocol;

class ChatServiceTest {
    @Test
    void validJoinWorks() {
        ChatService service = new ChatService();
        LocalChannel ch = new LocalChannel();
        List<ChatService.OutboundMessage> out = service.handleClientRequest(ch, joinReq("Alex"));
        assertEquals(1, out.size());
        assertTrue(out.get(0).response().hasJoinSuccess());
    }

    @Test
    void invalidNameRejected() {
        ChatService service = new ChatService();
        LocalChannel ch = new LocalChannel();
        var out = service.handleClientRequest(ch, joinReq("A!"));
        assertError(out, ErrorCode.INVALID_NAME);
    }

    @Test
    void duplicateNameRejected() {
        ChatService service = new ChatService();
        LocalChannel ch1 = new LocalChannel();
        LocalChannel ch2 = new LocalChannel();
        service.handleClientRequest(ch1, joinReq("Alex"));
        var out = service.handleClientRequest(ch2, joinReq("Alex"));
        assertError(out, ErrorCode.NAME_ALREADY_TAKEN);
    }

    @Test
    void repeatJoinRejected() {
        ChatService service = new ChatService();
        LocalChannel ch = new LocalChannel();
        service.handleClientRequest(ch, joinReq("Alex"));
        var out = service.handleClientRequest(ch, joinReq("Alex2"));
        assertError(out, ErrorCode.ALREADY_JOINED);
    }

    @Test
    void limit100UsersEnforced() {
        ChatService service = new ChatService();
        for (int i = 0; i < 100; i++) {
            LocalChannel ch = new LocalChannel();
            service.handleClientRequest(ch, joinReq("U" + i));
        }
        LocalChannel ch101 = new LocalChannel();
        var out = service.handleClientRequest(ch101, joinReq("U101"));
        assertError(out, ErrorCode.TOO_MANY_USERS);
    }

    @Test
    void messageBeforeJoinRejected() {
        ChatService service = new ChatService();
        LocalChannel ch = new LocalChannel();
        var out = service.handleClientRequest(ch, publicMsg("hello"));
        assertError(out, ErrorCode.NOT_JOINED);
    }

    @Test
    void publicMessageBroadcasted() {
        ChatService service = new ChatService();
        LocalChannel a = new LocalChannel();
        LocalChannel b = new LocalChannel();
        service.handleClientRequest(a, joinReq("Alex"));
        service.handleClientRequest(b, joinReq("Bob"));
        var out = service.handleClientRequest(a, publicMsg("hi"));
        assertEquals(2, out.size());
        assertTrue(out.stream().allMatch(m -> m.response().hasMessageEvent()));
    }

    @Test
    void privateMessageDeliveredOnlyToSenderAndRecipient() {
        ChatService service = new ChatService();
        LocalChannel a = new LocalChannel();
        LocalChannel b = new LocalChannel();
        LocalChannel c = new LocalChannel();
        service.handleClientRequest(a, joinReq("Alex"));
        service.handleClientRequest(b, joinReq("Bob"));
        service.handleClientRequest(c, joinReq("Kate"));
        var out = service.handleClientRequest(a, privateMsg("Bob", "secret"));
        assertEquals(2, out.size());
    }

    @Test
    void privateToUnknownRejected() {
        ChatService service = new ChatService();
        LocalChannel a = new LocalChannel();
        service.handleClientRequest(a, joinReq("Alex"));
        var out = service.handleClientRequest(a, privateMsg("Bob", "secret"));
        assertError(out, ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void emptyRecipientAfterTrimRejectedAsUserNotFound() {
        ChatService service = new ChatService();
        LocalChannel a = new LocalChannel();
        service.handleClientRequest(a, joinReq("Alex"));
        var out = service.handleClientRequest(a, privateMsg("   ", "secret"));
        assertError(out, ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void emptyMessageRejected() {
        ChatService service = new ChatService();
        LocalChannel a = new LocalChannel();
        service.handleClientRequest(a, joinReq("Alex"));
        var out = service.handleClientRequest(a, publicMsg("   "));
        assertError(out, ErrorCode.EMPTY_MESSAGE);
    }

    @Test
    void tooLongMessageRejected() {
        ChatService service = new ChatService();
        LocalChannel a = new LocalChannel();
        service.handleClientRequest(a, joinReq("Alex"));
        var out = service.handleClientRequest(a, publicMsg("12345678901234567890123456"));
        assertError(out, ErrorCode.MESSAGE_TOO_LONG);
    }

    @Test
    void historyLimit50Applied() {
        ChatService service = new ChatService();
        LocalChannel a = new LocalChannel();
        LocalChannel b = new LocalChannel();
        service.handleClientRequest(a, joinReq("Alex"));
        for (int i = 0; i < 60; i++) {
            service.handleClientRequest(a, publicMsg("m" + i));
        }
        var joinOut = service.handleClientRequest(b, joinReq("Bob"));
        var history = joinOut.get(0).response().getJoinSuccess().getHistory().getMessagesList();
        assertEquals(50, history.size());
    }

    @Test
    void privateHistoryVisibilityRespected() {
        ChatService service = new ChatService();
        LocalChannel alex = new LocalChannel();
        LocalChannel bob = new LocalChannel();
        LocalChannel kate = new LocalChannel();
        service.handleClientRequest(alex, joinReq("Alex"));
        service.handleClientRequest(bob, joinReq("Bob"));
        service.handleClientRequest(alex, privateMsg("Bob", "secret"));
        var joinOutKate = service.handleClientRequest(kate, joinReq("Kate"));
        var historyKate = joinOutKate.get(0).response().getJoinSuccess().getHistory().getMessagesList();
        assertTrue(historyKate.stream().noneMatch(ChatProtocol.ChatMessage::getPrivateMessage));
    }

    @Test
    void lastVisibleImageProvidedOnJoin() {
        ChatService service = new ChatService();
        LocalChannel alex = new LocalChannel();
        LocalChannel bob = new LocalChannel();
        service.handleClientRequest(alex, joinReq("Alex"));
        service.handleClientRequest(alex, publicMsgWithImage("img"));
        var joinOut = service.handleClientRequest(bob, joinReq("Bob"));
        assertTrue(joinOut.get(0).response().getJoinSuccess().hasLastImage());
    }

    @Test
    void foreignPrivateImageNotProvidedAsLastImage() {
        ChatService service = new ChatService();
        LocalChannel alex = new LocalChannel();
        LocalChannel bob = new LocalChannel();
        LocalChannel kate = new LocalChannel();
        service.handleClientRequest(alex, joinReq("Alex"));
        service.handleClientRequest(bob, joinReq("Bob"));
        service.handleClientRequest(alex, privateMsgWithImage("Bob", "img"));
        var joinOut = service.handleClientRequest(kate, joinReq("Kate"));
        assertFalse(joinOut.get(0).response().getJoinSuccess().hasLastImage());
    }

    private static ChatProtocol.ClientRequest joinReq(String name) {
        return ChatProtocol.ClientRequest.newBuilder()
                .setJoin(ChatProtocol.JoinRequest.newBuilder().setName(name).build())
                .build();
    }

    private static ChatProtocol.ClientRequest publicMsg(String text) {
        return ChatProtocol.ClientRequest.newBuilder()
                .setSendMessage(ChatProtocol.SendMessageRequest.newBuilder().setText(text).build())
                .build();
    }

    private static ChatProtocol.ClientRequest privateMsg(String recipient, String text) {
        return ChatProtocol.ClientRequest.newBuilder()
                .setSendMessage(
                        ChatProtocol.SendMessageRequest.newBuilder()
                                .setRecipientName(recipient)
                                .setText(text)
                                .build()
                )
                .build();
    }

    private static ChatProtocol.ClientRequest publicMsgWithImage(String text) {
        byte[] image = new byte[] {1, 2, 3};
        return ChatProtocol.ClientRequest.newBuilder()
                .setSendMessage(
                        ChatProtocol.SendMessageRequest.newBuilder()
                                .setText(text)
                                .setImage(ChatProtocol.ImageData.newBuilder()
                                        .setFileName("a.png")
                                        .setMimeType("image/png")
                                        .setData(com.google.protobuf.ByteString.copyFrom(image))
                                        .setSizeBytes(image.length)
                                        .build())
                                .build()
                )
                .build();
    }

    private static ChatProtocol.ClientRequest privateMsgWithImage(String recipient, String text) {
        byte[] image = new byte[] {1, 2, 3};
        return ChatProtocol.ClientRequest.newBuilder()
                .setSendMessage(
                        ChatProtocol.SendMessageRequest.newBuilder()
                                .setRecipientName(recipient)
                                .setText(text)
                                .setImage(ChatProtocol.ImageData.newBuilder()
                                        .setFileName("a.png")
                                        .setMimeType("image/png")
                                        .setData(com.google.protobuf.ByteString.copyFrom(image))
                                        .setSizeBytes(image.length)
                                        .build())
                                .build()
                )
                .build();
    }

    private static void assertError(List<ChatService.OutboundMessage> out, ErrorCode code) {
        assertEquals(1, out.size());
        assertTrue(out.get(0).response().hasError());
        assertEquals(code.code(), out.get(0).response().getError().getCode());
    }
}
