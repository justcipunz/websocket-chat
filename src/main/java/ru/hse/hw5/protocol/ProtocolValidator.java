package ru.hse.hw5.protocol;

import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hse.hw5.model.ErrorCode;
import ru.hse.hw5.proto.ChatProtocol;

public final class ProtocolValidator {
    private static final Logger log = LoggerFactory.getLogger(ProtocolValidator.class);

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z\\u0410-\\u042F\\u0430-\\u044F0-9_-]+$");
    private static final int MAX_NAME_LENGTH = 20;
    private static final int MAX_TEXT_LENGTH = 25;
    private static final int MAX_IMAGE_BYTES = 1_048_576;
    private static final Set<String> ALLOWED_IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png");

    private ProtocolValidator() {
    }

    public static ValidationResult validateJoinRequest(ChatProtocol.JoinRequest request) {
        String name = request.getName();
        if (name.isEmpty()) {
            log.warn("Join request rejected: empty name");
            return ValidationResult.error(ErrorCode.INVALID_NAME, "Name must not be empty");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            log.warn("Join request rejected: name is too long");
            return ValidationResult.error(ErrorCode.INVALID_NAME, "Name length must be from 1 to 20");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            log.warn("Join request rejected: invalid name format");
            return ValidationResult.error(ErrorCode.INVALID_NAME, "Name has invalid format");
        }

        if (request.hasIcon()) {
            ValidationResult imageValidation = validateImageData(request.getIcon());
            if (!imageValidation.isValid()) {
                log.warn("Join request rejected: invalid icon");
                return imageValidation;
            }
        }

        return ValidationResult.valid();
    }

    public static ValidationResult validateSendMessageRequest(ChatProtocol.SendMessageRequest request) {
        String text = request.getText();
        boolean hasImage = request.hasImage();

        if (text.length() > MAX_TEXT_LENGTH) {
            log.warn("Message rejected: text is too long");
            return ValidationResult.error(ErrorCode.MESSAGE_TOO_LONG, "Message length must be at most 25");
        }

        if (text.isEmpty() && !hasImage) {
            log.warn("Message rejected: empty message without image");
            return ValidationResult.error(ErrorCode.EMPTY_MESSAGE, "Message must contain text or image");
        }

        if (hasImage) {
            ValidationResult imageValidation = validateImageData(request.getImage());
            if (!imageValidation.isValid()) {
                log.warn("Message rejected: invalid image");
                return imageValidation;
            }
        }

        return ValidationResult.valid();
    }

    public static ValidationResult validateImageData(ChatProtocol.ImageData imageData) {
        String mimeType = imageData.getMimeType();
        int declaredSize = imageData.getSizeBytes();
        int actualSize = imageData.getData().size();

        if (!ALLOWED_IMAGE_MIME_TYPES.contains(mimeType)) {
            log.warn("Image rejected: unsupported mime type");
            return ValidationResult.error(ErrorCode.INVALID_IMAGE, "Unsupported image MIME type");
        }
        if (declaredSize < 1 || declaredSize > MAX_IMAGE_BYTES) {
            log.warn("Image rejected: size out of range");
            return ValidationResult.error(ErrorCode.INVALID_IMAGE, "Image size must be from 1 to 1048576 bytes");
        }
        if (actualSize == 0) {
            log.warn("Image rejected: empty data");
            return ValidationResult.error(ErrorCode.INVALID_IMAGE, "Image data must not be empty");
        }
        if (actualSize != declaredSize) {
            log.warn("Image rejected: declared size does not match data size");
            return ValidationResult.error(ErrorCode.INVALID_IMAGE, "Image data size does not match size_bytes");
        }

        return ValidationResult.valid();
    }

    public static final class ValidationResult {
        private final boolean valid;
        private final ErrorCode errorCode;
        private final String message;

        private ValidationResult(boolean valid, ErrorCode errorCode, String message) {
            this.valid = valid;
            this.errorCode = errorCode;
            this.message = message;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult error(ErrorCode errorCode, String message) {
            return new ValidationResult(false, errorCode, message);
        }

        public boolean isValid() {
            return valid;
        }

        public ErrorCode errorCode() {
            return errorCode;
        }

        public String message() {
            return message;
        }
    }
}
