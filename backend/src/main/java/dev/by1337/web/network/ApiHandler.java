package dev.by1337.web.network;

import dev.by1337.web.ClientList;
import dev.by1337.web.client.WebProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

final class ApiHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(ApiHandler.class);
    private final ClientList clientList;
    private final @Nullable StaticHoster staticHoster;

    ApiHandler(ClientList clientList, @Nullable StaticHoster staticHoster) {
        this.clientList = clientList;
        this.staticHoster = staticHoster;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest request) {
            String uri = request.uri();
            if (!uri.startsWith("/api/")) {
                if (staticHoster == null)
                    new HttpResponser(ctx).send(HttpResponseStatus.NOT_FOUND);
                else staticHoster.onHttpRequest(ctx, request);
                return;
            }
            onHttpRequest(ctx, request);
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
                if (type == WebProtocol.C2S_HELLO) {
                    int version = content.readInt();

                    int size = content.readableBytes();
                    if (size <= 0 || size >= 256) {
                        close(ctx, "bad payload size");
                        return;
                    }
                    byte[] array = new byte[size];
                    content.readBytes(array);
                    String staticContent = new String(array, StandardCharsets.UTF_8);
                    var channel = ctx.channel();
                    var ws = new WebSocketHandler(staticContent, clientList, channel, version);
                    clientList.newConnection(ws);
                    ctx.pipeline().replace(this, "wss", ws);

                    var buf = ctx.alloc().buffer();
                    buf.writeByte(WebProtocol.S2C_AUTH_STATUS);
                    buf.writeByte(1);
                    buf.writeBytes(ws.token().getBytes(StandardCharsets.UTF_8));
                    ctx.writeAndFlush(new BinaryWebSocketFrame(buf));
                }
            }
        }
    }


    private void onHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        HttpResponser responser = new HttpResponser(ctx);
        if (request.method() != HttpMethod.GET) {
            responser.send(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        // /api/token/method
        // /api/token/method/
        // /api/token/method?key=value
        // /api/token/method/sub?key=value
        String uri = request.uri();
      //  System.out.println(uri);
        String[] args = uri.split("/", 4);
        if (args.length < 4 || !args[1].equals("api")) {
            responser.send(HttpResponseStatus.BAD_REQUEST);
            return;
        }
        String token = args[2];
        String method = args[3];
        var client = clientList.getClient(token);
        if (client == null) {
            //todo rate limit
            if (method.equals("health")) {
                responser.sendJson("{\"ok\": \"false\"}");
            } else {
                responser.send(HttpResponseStatus.NOT_FOUND);
            }
        } else {
            client.request(responser, method);
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