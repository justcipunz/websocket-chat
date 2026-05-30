package ru.hse.hw5.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import ru.hse.hw5.model.ErrorCode;
import ru.hse.hw5.proto.ChatProtocol;

class ProtocolValidatorTest {
    @Test
    void validNameAccepted() {
        ChatProtocol.JoinRequest req = ChatProtocol.JoinRequest.newBuilder().setName("Alex_1").build();
        assertTrue(ProtocolValidator.validateJoinRequest(req).isValid());
    }

    @Test
    void invalidNameRejected() {
        ChatProtocol.JoinRequest req = ChatProtocol.JoinRequest.newBuilder().setName("A!").build();
        var result = ProtocolValidator.validateJoinRequest(req);
        assertFalse(result.isValid());
        assertEquals(ErrorCode.INVALID_NAME, result.errorCode());
    }

    @Test
    void emptyMessageRejected() {
        ChatProtocol.SendMessageRequest req = ChatProtocol.SendMessageRequest.newBuilder().setText("").build();
        var result = ProtocolValidator.validateSendMessageRequest(req);
        assertFalse(result.isValid());
        assertEquals(ErrorCode.EMPTY_MESSAGE, result.errorCode());
    }

    @Test
    void tooLongMessageRejected() {
        ChatProtocol.SendMessageRequest req = ChatProtocol.SendMessageRequest.newBuilder()
                .setText("12345678901234567890123456")
                .build();
        var result = ProtocolValidator.validateSendMessageRequest(req);
        assertFalse(result.isValid());
        assertEquals(ErrorCode.MESSAGE_TOO_LONG, result.errorCode());
    }

    @Test
    void validImageAccepted() {
        byte[] bytes = "abc".getBytes();
        ChatProtocol.ImageData image = ChatProtocol.ImageData.newBuilder()
                .setFileName("x.png")
                .setMimeType("image/png")
                .setData(ByteString.copyFrom(bytes))
                .setSizeBytes(bytes.length)
                .build();
        assertTrue(ProtocolValidator.validateImageData(image).isValid());
    }

    @Test
    void invalidMimeTypeRejected() {
        byte[] bytes = "abc".getBytes();
        ChatProtocol.ImageData image = ChatProtocol.ImageData.newBuilder()
                .setFileName("x.gif")
                .setMimeType("image/gif")
                .setData(ByteString.copyFrom(bytes))
                .setSizeBytes(bytes.length)
                .build();
        var result = ProtocolValidator.validateImageData(image);
        assertFalse(result.isValid());
        assertEquals(ErrorCode.INVALID_IMAGE, result.errorCode());
    }

    @Test
    void imageOverLimitRejected() {
        ChatProtocol.ImageData image = ChatProtocol.ImageData.newBuilder()
                .setFileName("x.png")
                .setMimeType("image/png")
                .setData(ByteString.copyFrom(new byte[] {1}))
                .setSizeBytes(1_048_577)
                .build();
        var result = ProtocolValidator.validateImageData(image);
        assertFalse(result.isValid());
        assertEquals(ErrorCode.INVALID_IMAGE, result.errorCode());
    }
}
