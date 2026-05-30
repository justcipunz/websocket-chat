package ru.hse.hw5.protocol;

import ru.hse.hw5.model.ErrorCode;
import ru.hse.hw5.proto.ChatProtocol;

public final class ErrorFactory {
    private ErrorFactory() {
    }

    public static ChatProtocol.ServerResponse error(ErrorCode code, String message) {
        ChatProtocol.ErrorResponse errorResponse = ChatProtocol.ErrorResponse.newBuilder()
                .setCode(code.code())
                .setError(code.error())
                .setMessage(message)
                .build();

        return ChatProtocol.ServerResponse.newBuilder()
                .setError(errorResponse)
                .build();
    }

    public static ChatProtocol.ServerResponse error(ErrorCode code) {
        return error(code, code.error());
    }
}
