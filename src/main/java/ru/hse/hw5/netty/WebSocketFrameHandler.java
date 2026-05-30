package ru.hse.hw5.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hse.hw5.chat.ChatService;
import ru.hse.hw5.model.ErrorCode;
import ru.hse.hw5.protocol.ErrorFactory;
import ru.hse.hw5.protocol.ProtocolCodec;
import ru.hse.hw5.proto.ChatProtocol;

public final class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private static final Logger log = LoggerFactory.getLogger(WebSocketFrameHandler.class);

    private final ChatService chatService;

    public WebSocketFrameHandler(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("WebSocket client connected: channel={}", ctx.channel().id());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        chatService.handleDisconnect(ctx.channel());
        log.info("WebSocket client disconnected: channel={}", ctx.channel().id());
        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof BinaryWebSocketFrame binaryFrame) {
            handleBinaryFrame(ctx, binaryFrame);
            return;
        }

        if (frame instanceof TextWebSocketFrame) {
            log.warn("Unsupported WebSocket frame: text frame");
            ChatProtocol.ServerResponse error = ErrorFactory.error(
                    ErrorCode.UNSUPPORTED_FRAME_TYPE,
                    "Only binary WebSocket frames are supported"
            );
            writeResponse(ctx, error);
            return;
        }

        // Ping/Pong/Close are handled by WebSocketServerProtocolHandler.
        ctx.fireChannelRead(frame.retain());
    }

    private void handleBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        ByteBuf content = frame.content();
        byte[] bytes = new byte[content.readableBytes()];
        content.getBytes(content.readerIndex(), bytes);

        ProtocolCodec.DecodedRequest decoded = ProtocolCodec.decodeClientRequest(bytes);
        if (!decoded.isSuccess()) {
            writeResponse(ctx, decoded.errorResponse());
            return;
        }

        List<ChatService.OutboundMessage> out = chatService.handleClientRequest(ctx.channel(), decoded.request());
        for (ChatService.OutboundMessage message : out) {
            byte[] encoded = ProtocolCodec.encodeServerResponse(message.response());
            message.channel().writeAndFlush(
                    new BinaryWebSocketFrame(message.channel().alloc().buffer(encoded.length).writeBytes(encoded))
            );
        }
    }

    private static void writeResponse(ChannelHandlerContext ctx, ChatProtocol.ServerResponse response) {
        byte[] bytes = ProtocolCodec.encodeServerResponse(response);
        ctx.writeAndFlush(new BinaryWebSocketFrame(ctx.alloc().buffer(bytes.length).writeBytes(bytes)));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("WebSocket handler exception: {}", cause.getMessage());
        ctx.close();
    }
}
