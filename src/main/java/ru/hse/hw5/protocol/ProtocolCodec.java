package ru.hse.hw5.protocol;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hse.hw5.model.ErrorCode;
import ru.hse.hw5.proto.ChatProtocol;

public final class ProtocolCodec {
    private static final Logger log = LoggerFactory.getLogger(ProtocolCodec.class);

    private ProtocolCodec() {
    }

    public static DecodedRequest decodeClientRequest(byte[] bytes) {
        try {
            ChatProtocol.ClientRequest request = ChatProtocol.ClientRequest.parseFrom(bytes);
            return DecodedRequest.success(request);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Failed to decode protobuf ClientRequest: {}", e.getMessage());
            return DecodedRequest.failure(
                    ErrorFactory.error(ErrorCode.INVALID_REQUEST, "Invalid protobuf payload")
            );
        }
    }

    public static byte[] encodeServerResponse(ChatProtocol.ServerResponse response) {
        return response.toByteArray();
    }

    public static final class DecodedRequest {
        private final ChatProtocol.ClientRequest request;
        private final ChatProtocol.ServerResponse errorResponse;

        private DecodedRequest(ChatProtocol.ClientRequest request, ChatProtocol.ServerResponse errorResponse) {
            this.request = request;
            this.errorResponse = errorResponse;
        }

        public static DecodedRequest success(ChatProtocol.ClientRequest request) {
            return new DecodedRequest(request, null);
        }

        public static DecodedRequest failure(ChatProtocol.ServerResponse errorResponse) {
            return new DecodedRequest(null, errorResponse);
        }

        public boolean isSuccess() {
            return request != null;
        }

        public ChatProtocol.ClientRequest request() {
            return request;
        }

        public ChatProtocol.ServerResponse errorResponse() {
            return errorResponse;
        }
    }
}
