package dev.by1337.web.network;

import dev.by1337.web.ClientList;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;

public class WebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);
    private String token;
    private final String staticContent;
    private final ClientList clientList;
    private final Channel channel;

    public WebSocketHandler(String staticContent, ClientList clientList, Channel channel) {
        this.staticContent = staticContent;
        this.clientList = clientList;
        this.channel = channel;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {

    }

    public String token() {
        return token;
    }

    public WebSocketHandler setToken(String token) {
        this.token = token;
        return this;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        disconnect(ctx, "End of stream");
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
        disconnect(ctx, "connection unregister");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof TimeoutException) {
            this.disconnect(ctx, "Timed out");
        } else {
            this.disconnect(ctx, "Internal Exception: " + cause);
            log.error("An error occurred in the {} connection", ctx.channel().remoteAddress(), cause);
        }
    }

    private void disconnect(ChannelHandlerContext ctx, String message) {
        if (ctx.channel().isOpen()) {
            ctx.channel().close();
            log.info("Disconnect connection {}, reason: {}", ctx.channel().remoteAddress(), message);
        }
        clientList.removeConnection(this);
    }
}
