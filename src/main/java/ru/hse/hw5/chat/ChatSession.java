package ru.hse.hw5.chat;

import io.netty.channel.Channel;
import ru.hse.hw5.proto.ChatProtocol;

public final class ChatSession {
    private final Channel channel;
    private String userName;
    private ChatProtocol.ImageData userIcon;
    private boolean joined;

    public ChatSession(Channel channel) {
        this.channel = channel;
    }

    public Channel channel() {
        return channel;
    }

    public String userName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public ChatProtocol.ImageData userIcon() {
        return userIcon;
    }

    public void setUserIcon(ChatProtocol.ImageData userIcon) {
        this.userIcon = userIcon;
    }

    public boolean isJoined() {
        return joined;
    }

    public void setJoined(boolean joined) {
        this.joined = joined;
    }
}
