package ru.hse.hw5.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import ru.hse.hw5.chat.ChatService;

public final class ChatServerInitializer extends ChannelInitializer<SocketChannel> {
    private final ChatService chatService;

    public ChatServerInitializer(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        WebSocketServerProtocolConfig config = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/chat")
                .maxFramePayloadLength(2 * 1024 * 1024)
                .build();

        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new WebSocketServerProtocolHandler(config));
        pipeline.addLast(new WebSocketFrameAggregator(2 * 1024 * 1024));
        pipeline.addLast(new WebSocketFrameHandler(chatService));
    }
}
