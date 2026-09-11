package dev.by1337.web.network;

import dev.by1337.web.network.auth.CookieBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;

public class HttpResponser {
    private final ChannelHandlerContext ctx;

    public HttpResponser(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public void sendJson(String json) {
        ByteBuf content = Unpooled.copiedBuffer(
                json,
                StandardCharsets.UTF_8
        );
        sendJson(content);
    }

    public void sendJson(ByteBuf content) {
        send(content, "application/json; charset=UTF-8");
    }

    public void send(ByteBuf content, String contentType) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                content
        );

        response.headers().set(
                HttpHeaderNames.CONTENT_TYPE,
                contentType
        );

        response.headers().setInt(
                HttpHeaderNames.CONTENT_LENGTH,
                content.readableBytes()
        );

        ctx.writeAndFlush(response);
    }

    public void send(HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status
        );

        response.headers().set(
                HttpHeaderNames.CONTENT_LENGTH,
                0
        );

        ctx.writeAndFlush(response);
    }

    public static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    public void sendJson(String json, HttpResponseStatus status) {
        ByteBuf content = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);
        FullHttpResponse r = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        r.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        r.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        ctx.writeAndFlush(r);
    }

    public void sendJsonStatus(HttpResponseStatus status, String json) {
        sendJson(json, status);
    }

    public void sendJsonWithCookie(String json, String name, String value, CookieBuilder opts) {
        ByteBuf content = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);
        FullHttpResponse r = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
        r.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
                .add(HttpHeaderNames.SET_COOKIE, opts.build(name, value))
                .setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        ctx.writeAndFlush(r);
    }

    public void redirect(String location) {
        redirect(location, HttpResponseStatus.SEE_OTHER);
    }

    public void redirect(String location, HttpResponseStatus status) {
        FullHttpResponse r = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        r.headers().set(HttpHeaderNames.LOCATION, location)
                .setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(r);
    }

    public void redirectWithCookie(String location, String name, String value, CookieBuilder opts) {
        FullHttpResponse r = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SEE_OTHER);
        r.headers().set(HttpHeaderNames.LOCATION, location)
                .add(HttpHeaderNames.SET_COOKIE, opts.build(name, value))
                .setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(r);
    }
}
