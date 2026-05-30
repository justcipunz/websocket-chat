(function () {
    "use strict";

    if (!window.protobuf) {
        throw new Error("protobuf runtime is not loaded");
    }

    const protoSchema = `
        syntax = "proto3";
        package hw5.chat;

        message ClientRequest {
          oneof payload {
            JoinRequest join = 1;
            SendMessageRequest send_message = 2;
          }
        }

        message ServerResponse {
          oneof payload {
            JoinSuccess join_success = 1;
            ChatMessageEvent message_event = 2;
            ErrorResponse error = 3;
          }
        }

        message JoinRequest {
          string name = 1;
          ImageData icon = 2;
        }

        message SendMessageRequest {
          string text = 1;
          ImageData image = 2;
          string recipient_name = 3;
        }

        message JoinSuccess {
          string assigned_name = 1;
          History history = 2;
          ImageData last_image = 3;
        }

        message History {
          repeated ChatMessage messages = 1;
        }

        message ChatMessageEvent {
          ChatMessage message = 1;
        }

        message ChatMessage {
          int64 id = 1;
          string sender_name = 2;
          string text = 3;
          int64 timestamp_epoch_millis = 4;
          bool private_message = 5;
          string recipient_name = 6;
          Attachment attachment = 7;
          ImageData sender_icon = 8;

          message Attachment {
            ImageData image = 1;
          }
        }

        message ImageData {
          string file_name = 1;
          string mime_type = 2;
          bytes data = 3;
          int32 size_bytes = 4;
        }

        message ErrorResponse {
          int32 code = 1;
          string error = 2;
          string message = 3;
        }
    `;

    const root = window.protobuf.parse(protoSchema, {keepCase: true}).root;

    const types = {
        ClientRequest: root.lookupType("hw5.chat.ClientRequest"),
        ServerResponse: root.lookupType("hw5.chat.ServerResponse"),
        JoinRequest: root.lookupType("hw5.chat.JoinRequest"),
        SendMessageRequest: root.lookupType("hw5.chat.SendMessageRequest"),
        JoinSuccess: root.lookupType("hw5.chat.JoinSuccess"),
        History: root.lookupType("hw5.chat.History"),
        ChatMessageEvent: root.lookupType("hw5.chat.ChatMessageEvent"),
        ChatMessage: root.lookupType("hw5.chat.ChatMessage"),
        ImageData: root.lookupType("hw5.chat.ImageData"),
        ErrorResponse: root.lookupType("hw5.chat.ErrorResponse")
    };

    window.ChatProto = {
        types: types,
        encodeClientRequest(messageObject) {
            const err = types.ClientRequest.verify(messageObject);
            if (err) {
                throw new Error("ClientRequest verify failed: " + err);
            }
            const msg = types.ClientRequest.create(messageObject);
            return types.ClientRequest.encode(msg).finish();
        },
        decodeServerResponse(buffer) {
            return types.ServerResponse.decode(new Uint8Array(buffer));
        }
    };
})();
