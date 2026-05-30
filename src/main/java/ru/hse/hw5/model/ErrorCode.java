package ru.hse.hw5.model;

public enum ErrorCode {
    UNKNOWN_ERROR(0, "UNKNOWN_ERROR"),
    INVALID_REQUEST(1, "INVALID_REQUEST"),
    UNSUPPORTED_FRAME_TYPE(2, "UNSUPPORTED_FRAME_TYPE"),
    NOT_JOINED(3, "NOT_JOINED"),
    ALREADY_JOINED(4, "ALREADY_JOINED"),
    INVALID_NAME(5, "INVALID_NAME"),
    NAME_ALREADY_TAKEN(6, "NAME_ALREADY_TAKEN"),
    EMPTY_MESSAGE(7, "EMPTY_MESSAGE"),
    MESSAGE_TOO_LONG(8, "MESSAGE_TOO_LONG"),
    INVALID_IMAGE(9, "INVALID_IMAGE"),
    USER_NOT_FOUND(10, "USER_NOT_FOUND"),
    TOO_MANY_USERS(11, "TOO_MANY_USERS");

    private final int code;
    private final String error;

    ErrorCode(int code, String error) {
        this.code = code;
        this.error = error;
    }

    public int code() {
        return code;
    }

    public String error() {
        return error;
    }
}
