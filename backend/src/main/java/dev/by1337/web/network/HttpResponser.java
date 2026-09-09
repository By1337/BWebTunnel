package dev.by1337.web.network;

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
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                content
        );

        response.headers().set(
                HttpHeaderNames.CONTENT_TYPE,
                "application/json; charset=UTF-8"
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
}
