package dev.by1337.web.network;

import dev.by1337.web.ClientList;
import dev.by1337.web.ServerWebProtocol;
import dev.by1337.web.client.WebProtocol;
import dev.by1337.web.db.Database;
import dev.by1337.web.network.auth.AuthHandler;
import dev.by1337.web.network.content.GetStaticContentHandler;
import dev.by1337.web.network.service.ServiceConnection;
import dev.by1337.web.network.service.ServiceGroup;
import dev.by1337.web.util.RequestRateLimiter;
import dev.by1337.web.util.StreamJsonWriter;
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

import java.time.Duration;

final class ApiHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(ApiHandler.class);
    private static final RequestRateLimiter RATE_LIMITER = new RequestRateLimiter(20, Duration.ofMinutes(1));

    private final ClientList clientList;
    private final @Nullable GetStaticContentHandler contentHandler;
    private final Database database;
    private final AuthHandler auth;

    ApiHandler(ClientList clientList, @Nullable GetStaticContentHandler contentHandler, Database database, AuthHandler auth) {
        this.clientList = clientList;
        this.contentHandler = contentHandler;
        this.database = database;
        this.auth = auth;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest request) {
            String uri = request.uri();
            if (uri.startsWith("/session/")) {
                auth.on(ctx, request);
                return;
            }
            if (!uri.startsWith("/api/")) {
                if (contentHandler == null)
                    new HttpResponser(ctx).send(HttpResponseStatus.NOT_FOUND);
                else contentHandler.onHttpRequest(ctx, request);
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
                    var channel = ctx.channel();

                    int version = content.readInt();
                    String staticContent = ServerWebProtocol.readUtf8(content, WebProtocol.MAX_STRING_SIZE, ctx);
                    if (staticContent == null) return;
                    var service = new ServiceConnection(staticContent, clientList, channel, version);

                    boolean use128Token = false;
                    if (content.readByte() == 1) { // has group
                        String secret = ServerWebProtocol.readUtf8(content, WebProtocol.MAX_STRING_SIZE, ctx);
                        if (secret == null) return;
                        String desc = ServerWebProtocol.readUtf8(content, WebProtocol.MAX_STRING_SIZE, ctx);
                        if (desc == null) return;
                        if (database.isValidSecret(secret)) {
                            service.setGroupSecret(secret);
                            service.setDescription(desc);
                            use128Token = true;
                        } else {
                            var buf = channel.alloc().buffer();
                            buf.writeByte(WebProtocol.C2S_ERROR_MSG);
                            buf.writeByte(0);
                            //by1337:e1xDrafSMEO6YNlPYsP3fr
                            ServerWebProtocol.writeUtf8(buf, WebProtocol.MAX_STRING_SIZE, "Invalid secret '" + secret + "'");
                            service.write(new BinaryWebSocketFrame(buf));
                        }
                    }

                    clientList.newConnection(service, use128Token);
                    ctx.pipeline().replace(this, "service", service);

                    var buf = ctx.alloc().buffer();
                    buf.writeByte(WebProtocol.S2C_AUTH_STATUS);
                    buf.writeByte(1);
                    ServerWebProtocol.writeUtf8(buf, WebProtocol.MAX_STRING_SIZE, service.token());
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
        if (RATE_LIMITER.isRateLimited(ctx, request)){
            responser.send(HttpResponseStatus.TOO_MANY_REQUESTS);
            return;
        }

        // /api/token/method
        // /api/token/method/
        // /api/token/method?key=value
        // /api/token/method/sub?key=value
        String uri = request.uri();
        //  System.out.println(uri);
        String[] args = uri.split("/", 4);
        if (args.length < 3 || !args[1].equals("api")) {
            responser.send(HttpResponseStatus.BAD_REQUEST);
            return;
        }
        if (args[2].equals("dashboard")) {
            var secret = auth.getSecret(request);
            if (secret == null) {
                RATE_LIMITER.record(ctx, request);
                responser.send(HttpResponseStatus.UNAUTHORIZED);
                return;
            }
            ServiceGroup group = clientList.getGroup(secret);
            if (group == null) {
                responser.sendJson("{\"secret\":\"" + secret + "\",\"services\":[]}");
                return;
            }
            var buf = ctx.alloc().buffer();
            group.toJson(new StreamJsonWriter(buf));
            responser.sendJson(buf);
            return;
        }
        if (args.length < 4) {
            responser.send(HttpResponseStatus.BAD_REQUEST);
            return;
        }
        String token = args[2];
        String method = args[3];
        var client = clientList.getClient(token);
        if (client == null) {
            RATE_LIMITER.record(ctx, request);
            if (method.equals("health")) {
                responser.sendJson("{\"ok\": \"false\"}");
            } else {
                responser.redirect("/404.html", HttpResponseStatus.NOT_FOUND);
            }
        } else if (method.equals("health")) {
            responser.sendJson("{\"ok\": \"true\"}");
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