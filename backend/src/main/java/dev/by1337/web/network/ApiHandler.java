package dev.by1337.web.network;

import dev.by1337.web.ClientList;
import dev.by1337.web.client.WebProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

final class ApiHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(ApiHandler.class);
    private final ClientList clientList;

    ApiHandler(ClientList clientList) {
        this.clientList = clientList;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest request) {
            //  handleHttp(ctx, request);
            return;
        }

        if (msg instanceof WebSocketFrame frame) {
            if (frame instanceof BinaryWebSocketFrame binary) {
                var content = binary.content();
                if (content.readableBytes() < 1) {
                    close(ctx, "bad payload!");
                    return;
                }
                byte type = content.readByte();
                if (type == WebProtocol.HELLO) {
                    int size = content.readableBytes();
                    if (size <= 0 || size >= 256) {
                        close(ctx, "bad payload size");
                        return;
                    }
                    byte[] array = new byte[size];
                    content.readBytes(array);
                    String staticContent = new String(array, StandardCharsets.UTF_8);
                    var channel = ctx.channel();
                    var ws = new WebSocketHandler(staticContent, clientList, channel);
                    clientList.newConnection(ws);
                    ctx.pipeline().replace(this, "wss", ws);

                    var buf = ctx.alloc().buffer();
                    buf.writeByte(WebProtocol.AUTH_STATUS);
                    buf.writeByte(1);
                    buf.writeBytes("/api/%s/%s".formatted(ws.token(), staticContent).getBytes(StandardCharsets.UTF_8));
                    ctx.writeAndFlush(new BinaryWebSocketFrame(buf));
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("error in pipeline", cause);
        ctx.close();
    }

    public void close(ChannelHandlerContext ctx, String cuz) {
        log.info("disconnect {}", cuz);
        ctx.close();
    }
}