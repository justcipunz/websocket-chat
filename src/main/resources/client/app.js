const nameInput = document.getElementById("name-input");
const pickIconBtn = document.getElementById("pick-icon-btn");
const iconFileInput = document.getElementById("icon-file-input");
const joinBtn = document.getElementById("join-btn");
const connectionStatus = document.getElementById("connection-status");
const errorText = document.getElementById("error-text");
const messages = document.getElementById("messages");
const messageInput = document.getElementById("message-input");
const pickImageBtn = document.getElementById("pick-image-btn");
const messageImageFileInput = document.getElementById("message-image-file-input");
const sendBtn = document.getElementById("send-btn");
const lastImageBlock = document.getElementById("last-image-block");
const lastImageContainer = document.getElementById("last-image-container");
const iconFileName = document.getElementById("icon-file-name");
const messageImageFileName = document.getElementById("message-image-file-name");

const MAX_IMAGE_SIZE_BYTES = 1_048_576;
const ALLOWED_MIME_TYPES = new Set(["image/jpeg", "image/png"]);
const SERVER_ERROR_TEXTS = {
    UNKNOWN_ERROR: "Непредвиденная ошибка сервера",
    INVALID_REQUEST: "Некорректный запрос",
    UNSUPPORTED_FRAME_TYPE: "Неподдерживаемый тип WebSocket-сообщения",
    NOT_JOINED: "Сначала нужно присоединиться к чату",
    ALREADY_JOINED: "Пользователь уже присоединился",
    INVALID_NAME: "Некорректное имя",
    NAME_ALREADY_TAKEN: "Имя уже занято",
    EMPTY_MESSAGE: "Сообщение пустое",
    MESSAGE_TOO_LONG: "Сообщение длиннее 25 символов",
    INVALID_IMAGE: "Изображение должно быть JPEG или PNG размером до 1 МБ",
    USER_NOT_FOUND: "Получатель приватного сообщения не найден",
    TOO_MANY_USERS: "В чате уже 100 пользователей"
};

let selectedIconFile = null;
let selectedMessageImageFile = null;
let socket = null;
let pendingJoinName = "";
let joinedSuccessfully = false;

let reconnectTimeoutId = null;
let reconnectIntervalId = null;
let reconnectSecondsLeft = 0;
let manualCloseAfterJoinError = false;

const activeObjectUrls = new Set();

function setInitialUiState() {
    nameInput.disabled = false;
    pickIconBtn.disabled = false;
    joinBtn.disabled = false;
    messageInput.disabled = true;
    pickImageBtn.disabled = true;
    sendBtn.disabled = true;

    messages.textContent = "";
    errorText.textContent = "";
    connectionStatus.textContent = "Не подключено";
    messageImageFileName.textContent = "Файл не выбран";
    lastImageBlock.classList.add("hidden");
}

function setConnectingUiState() {
    nameInput.disabled = true;
    pickIconBtn.disabled = true;
    joinBtn.disabled = true;
    messageInput.disabled = true;
    pickImageBtn.disabled = true;
    sendBtn.disabled = true;
    connectionStatus.textContent = "Подключение...";
}

function setJoinedUiState(assignedName) {
    nameInput.disabled = true;
    pickIconBtn.disabled = true;
    joinBtn.disabled = true;
    messageInput.disabled = false;
    pickImageBtn.disabled = false;
    sendBtn.disabled = false;
    connectionStatus.textContent = `Подключено как ${assignedName}`;
}

function setReconnectUiState(seconds) {
    nameInput.disabled = true;
    pickIconBtn.disabled = true;
    joinBtn.disabled = true;
    messageInput.disabled = true;
    pickImageBtn.disabled = true;
    sendBtn.disabled = true;
    connectionStatus.textContent = `Повторное подключение через ${seconds} с`;
}

function showLocalError(message) {
    errorText.textContent = message;
    console.warn("Local error:", message);
}

function clearLocalError() {
    errorText.textContent = "";
}

function clearSelectedMessageImage() {
    selectedMessageImageFile = null;
    messageImageFileInput.value = "";
    messageImageFileName.textContent = "Файл не выбран";
}

function clearReconnectTimers() {
    if (reconnectTimeoutId) {
        clearTimeout(reconnectTimeoutId);
        reconnectTimeoutId = null;
    }
    if (reconnectIntervalId) {
        clearInterval(reconnectIntervalId);
        reconnectIntervalId = null;
    }
    reconnectSecondsLeft = 0;
}

function revokeAllObjectUrls() {
    activeObjectUrls.forEach((url) => URL.revokeObjectURL(url));
    activeObjectUrls.clear();
}

function validateImageFile(file) {
    if (!file) {
        return {valid: false, message: "Файл не выбран"};
    }
    if (!ALLOWED_MIME_TYPES.has(file.type)) {
        return {valid: false, message: "Изображение должно быть JPEG или PNG"};
    }
    if (file.size > MAX_IMAGE_SIZE_BYTES) {
        return {valid: false, message: "Изображение должно быть не больше 1 МБ"};
    }
    return {valid: true, message: ""};
}

async function buildImageDataMessage(file) {
    const bytes = new Uint8Array(await file.arrayBuffer());
    return window.ChatProto.types.ImageData.create({
        file_name: file.name,
        mime_type: file.type,
        data: bytes,
        size_bytes: file.size
    });
}

function uint8ArrayToImageUrl(imageDataObject) {
    if (!imageDataObject || !imageDataObject.data || !imageDataObject.mime_type) {
        return null;
    }
    const blob = new Blob([imageDataObject.data], {type: imageDataObject.mime_type});
    const url = URL.createObjectURL(blob);
    activeObjectUrls.add(url);
    return url;
}

function parseServerError(errorObject) {
    if (!errorObject) {
        return "Неизвестная ошибка";
    }
    if (errorObject.message && errorObject.message.trim().length > 0) {
        return errorObject.message;
    }
    if (errorObject.error && errorObject.error.trim().length > 0) {
        return SERVER_ERROR_TEXTS[errorObject.error.trim()] || "Неизвестная ошибка";
    }
    return "Неизвестная ошибка";
}

function createMessageElement(messageObject) {
    const item = document.createElement("div");
    item.className = "message-item";

    const sender = messageObject.sender_name || "unknown";
    const text = (messageObject.text || "").trim();
    const isPrivate = Boolean(messageObject.private_message);
    const recipient = messageObject.recipient_name || "";
    const label = isPrivate ? `личное для ${recipient}` : "публичное";

    const header = document.createElement("div");
    header.className = "message-header";

    if (messageObject.sender_icon) {
        const iconUrl = uint8ArrayToImageUrl(messageObject.sender_icon);
        if (iconUrl) {
            const icon = document.createElement("img");
            icon.src = iconUrl;
            icon.alt = "sender icon";
            icon.style.width = "24px";
            icon.style.height = "24px";
            icon.style.objectFit = "cover";
            icon.style.borderRadius = "50%";
            icon.style.marginRight = "8px";
            header.appendChild(icon);
        }
    }

    const title = document.createElement("span");
    title.textContent = `${sender} (${label})`;
    header.appendChild(title);

    const timestamp = document.createElement("span");
    timestamp.style.marginLeft = "8px";
    timestamp.style.color = "#57606a";
    if (messageObject.timestamp_epoch_millis) {
        timestamp.textContent = new Date(messageObject.timestamp_epoch_millis).toLocaleString();
    }
    header.appendChild(timestamp);
    item.appendChild(header);

    if (text.length > 0) {
        const body = document.createElement("div");
        body.textContent = text;
        item.appendChild(body);
    }

    if (messageObject.attachment && messageObject.attachment.image) {
        const imageUrl = uint8ArrayToImageUrl(messageObject.attachment.image);
        if (imageUrl) {
            const img = document.createElement("img");
            img.src = imageUrl;
            img.alt = "message image";
            img.style.maxWidth = "220px";
            img.style.display = "block";
            img.style.marginTop = "6px";
            item.appendChild(img);
        }
    }

    return item;
}

function renderJoinSuccess(joinSuccessObject) {
    const assignedName = joinSuccessObject.assigned_name || pendingJoinName;
    setJoinedUiState(assignedName);

    revokeAllObjectUrls();
    messages.textContent = "";
    const history = (joinSuccessObject.history && joinSuccessObject.history.messages) || [];
    history.forEach((message) => messages.appendChild(createMessageElement(message)));

    lastImageContainer.textContent = "";
    if (joinSuccessObject.last_image) {
        const imageUrl = uint8ArrayToImageUrl(joinSuccessObject.last_image);
        if (imageUrl) {
            const img = document.createElement("img");
            img.src = imageUrl;
            img.alt = "last visible image";
            img.style.maxWidth = "260px";
            lastImageContainer.appendChild(img);
            lastImageBlock.classList.remove("hidden");
        } else {
            lastImageBlock.classList.add("hidden");
        }
    } else {
        lastImageBlock.classList.add("hidden");
    }
}

function handleServerResponseArrayBuffer(arrayBuffer) {
    let decodedResponse;
    try {
        decodedResponse = window.ChatProto.decodeServerResponse(arrayBuffer);
    } catch (e) {
        showLocalError("Не удалось прочитать ответ сервера");
        console.error("ServerResponse decode failed:", e.message);
        return;
    }

    const responseObject = window.ChatProto.types.ServerResponse.toObject(decodedResponse, {
        longs: Number,
        bytes: Uint8Array,
        defaults: false,
        oneofs: true
    });

    if (responseObject.join_success) {
        joinedSuccessfully = true;
        clearLocalError();
        renderJoinSuccess(responseObject.join_success);
        console.log("JoinSuccess received");
        return;
    }

    if (responseObject.message_event && responseObject.message_event.message) {
        messages.appendChild(createMessageElement(responseObject.message_event.message));
        messages.scrollTop = messages.scrollHeight;
        console.log("Message received");
        return;
    }

    if (responseObject.error) {
        const text = parseServerError(responseObject.error);
        if (!joinedSuccessfully) {
            if (socket && socket.readyState === WebSocket.OPEN) {
                manualCloseAfterJoinError = true;
                socket.close();
            }
            setInitialUiState();
            socket = null;
            clearReconnectTimers();
        }
        showLocalError(text);
        console.warn("Server error received");
    }
}

async function buildJoinRequestPayload(name, iconFile) {
    const joinObject = {name: name};
    if (iconFile) {
        joinObject.icon = await buildImageDataMessage(iconFile);
    }
    return {join: joinObject};
}

async function sendJoinRequest() {
    const joinPayload = await buildJoinRequestPayload(pendingJoinName, selectedIconFile);
    const bytes = window.ChatProto.encodeClientRequest(joinPayload);
    socket.send(bytes);
    console.log("JoinRequest sent");
}

function scheduleReconnect() {
    if (manualCloseAfterJoinError) {
        return;
    }
    if (!pendingJoinName) {
        return;
    }
    if (reconnectTimeoutId || reconnectIntervalId) {
        return;
    }

    reconnectSecondsLeft = 10;
    setReconnectUiState(reconnectSecondsLeft);
    console.log("Reconnect scheduled");

    reconnectIntervalId = setInterval(() => {
        reconnectSecondsLeft -= 1;
        if (reconnectSecondsLeft <= 0) {
            clearInterval(reconnectIntervalId);
            reconnectIntervalId = null;
            return;
        }
        setReconnectUiState(reconnectSecondsLeft);
    }, 1000);

    reconnectTimeoutId = setTimeout(() => {
        reconnectTimeoutId = null;
        connectAndJoin();
    }, 10000);
}

function connectAndJoin() {
    joinedSuccessfully = false;
    clearReconnectTimers();
    setConnectingUiState();
    console.log("Connecting to WebSocket");

    try {
        socket = new WebSocket("ws://localhost:8080/chat");
        socket.binaryType = "arraybuffer";

        socket.addEventListener("open", async () => {
            clearReconnectTimers();
            console.log("WebSocket opened");
            try {
                await sendJoinRequest();
            } catch (e) {
                showLocalError("Не удалось отправить JoinRequest");
                console.error("Join send failed:", e.message);
            }
        });

        socket.addEventListener("message", (event) => {
            if (!(event.data instanceof ArrayBuffer)) {
                showLocalError("Некорректный формат ответа сервера");
                return;
            }
            handleServerResponseArrayBuffer(event.data);
        });

        socket.addEventListener("error", () => {
            console.warn("WebSocket error");
        });

        socket.addEventListener("close", () => {
            socket = null;
            console.warn("WebSocket closed");
            if (manualCloseAfterJoinError) {
                manualCloseAfterJoinError = false;
                return;
            }
            showLocalError("Ошибка подключения");
            scheduleReconnect();
        });
    } catch (e) {
        showLocalError("Ошибка подключения");
        console.error("WebSocket create failed:", e.message);
        scheduleReconnect();
    }
}

function parsePrivateInput(rawText) {
    const trimmedStart = rawText.trimStart();
    if (!trimmedStart.startsWith("@")) {
        return {isPrivate: false, text: rawText.trim(), recipientName: ""};
    }

    const withoutAt = trimmedStart.slice(1);
    if (withoutAt.length === 0) {
        return {isPrivate: true, error: "Укажите имя получателя"};
    }

    const firstSpaceIdx = withoutAt.indexOf(" ");
    if (firstSpaceIdx === -1) {
        const recipientOnly = withoutAt.trim();
        if (!recipientOnly) {
            return {isPrivate: true, error: "Укажите имя получателя"};
        }
        return {isPrivate: true, recipientName: recipientOnly, text: ""};
    }

    const recipient = withoutAt.slice(0, firstSpaceIdx).trim();
    const text = withoutAt.slice(firstSpaceIdx + 1).trim();
    if (!recipient) {
        return {isPrivate: true, error: "Укажите имя получателя"};
    }
    return {isPrivate: true, recipientName: recipient, text: text};
}

async function sendMessage() {
    clearLocalError();

    if (!socket || socket.readyState !== WebSocket.OPEN) {
        showLocalError("Соединение с сервером не установлено");
        return;
    }

    const parsed = parsePrivateInput(messageInput.value);
    if (parsed.error) {
        showLocalError(parsed.error);
        return;
    }

    if (parsed.text.length > 25) {
        showLocalError("Сообщение длиннее 25 символов");
        return;
    }

    if (parsed.text.length === 0 && !selectedMessageImageFile) {
        showLocalError("Введите сообщение или прикрепите изображение");
        return;
    }

    const sendMessagePayload = {text: parsed.text};
    if (parsed.isPrivate) {
        sendMessagePayload.recipient_name = parsed.recipientName;
    }
    if (selectedMessageImageFile) {
        sendMessagePayload.image = await buildImageDataMessage(selectedMessageImageFile);
    }

    const bytes = window.ChatProto.encodeClientRequest({
        send_message: sendMessagePayload
    });
    socket.send(bytes);
    console.log(parsed.isPrivate ? "Private message sent" : "Public message sent");

    messageInput.value = "";
    clearSelectedMessageImage();
}

pickIconBtn.addEventListener("click", () => iconFileInput.click());
pickImageBtn.addEventListener("click", () => messageImageFileInput.click());

iconFileInput.addEventListener("change", () => {
    clearLocalError();
    const file = iconFileInput.files && iconFileInput.files[0];
    const validation = validateImageFile(file);
    if (!validation.valid) {
        selectedIconFile = null;
        if (file) {
            showLocalError(validation.message);
            console.warn("Icon file rejected");
        }
        return;
    }
    selectedIconFile = file;
    iconFileName.textContent = file.name;
    console.log("Icon file selected");
});

messageImageFileInput.addEventListener("change", () => {
    clearLocalError();
    const file = messageImageFileInput.files && messageImageFileInput.files[0];
    const validation = validateImageFile(file);
    if (!validation.valid) {
        clearSelectedMessageImage();
        if (file) {
            showLocalError(validation.message);
            console.warn("Message image file rejected");
        }
        return;
    }
    selectedMessageImageFile = file;
    messageImageFileName.textContent = file.name;
    console.log("Message image file selected");
});

joinBtn.addEventListener("click", () => {
    clearLocalError();

    const normalizedName = nameInput.value.trim();
    if (!normalizedName) {
        showLocalError("Введите имя пользователя");
        return;
    }
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
        showLocalError("Соединение уже устанавливается или открыто");
        return;
    }

    pendingJoinName = normalizedName;
    joinedSuccessfully = false;
    manualCloseAfterJoinError = false;
    connectAndJoin();
});

sendBtn.addEventListener("click", async () => {
    try {
        await sendMessage();
    } catch (e) {
        showLocalError("Не удалось отправить сообщение");
        console.error("Send message failed:", e.message);
    }
});

function runProtobufSelfTest() {
    if (!window.ChatProto || !window.ChatProto.types) {
        console.error("Protobuf self-test failed: ChatProto is not initialized");
        return;
    }
    try {
        const joinRequest = window.ChatProto.types.JoinRequest.create({name: "Alex"});
        const sendMessageRequest = window.ChatProto.types.SendMessageRequest.create({text: "hello"});
        const clientRequestJoinBytes = window.ChatProto.encodeClientRequest({join: joinRequest});
        const clientRequestSendBytes = window.ChatProto.encodeClientRequest({send_message: sendMessageRequest});
        const serverResponseBytes = window.ChatProto.types.ServerResponse.encode(
            window.ChatProto.types.ServerResponse.create({
                join_success: {assigned_name: "Alex", history: {messages: []}}
            })
        ).finish();
        const decodedServerResponse = window.ChatProto.decodeServerResponse(serverResponseBytes);
        console.log("Protobuf self-test passed", {
            joinBytesLength: clientRequestJoinBytes.length,
            sendBytesLength: clientRequestSendBytes.length,
            decodedServerResponse: window.ChatProto.types.ServerResponse.toObject(decodedServerResponse, {
                longs: Number,
                bytes: Uint8Array,
                defaults: false,
                oneofs: true
            })
        });
    } catch (e) {
        console.error("Protobuf self-test failed:", e.message);
    }
}

setInitialUiState();
console.log("Client page loaded");
runProtobufSelfTest();

window.ClientFileState = {
    getSelectedIconFile() {
        return selectedIconFile;
    },
    getSelectedMessageImageFile() {
        return selectedMessageImageFile;
    },
    buildImageDataMessage: buildImageDataMessage
};


