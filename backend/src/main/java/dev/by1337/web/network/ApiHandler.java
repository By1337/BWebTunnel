package dev.by1337.web.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApiHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(ApiHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest request) {
          //  handleHttp(ctx, request);
            return;
        }

        if (msg instanceof WebSocketFrame frame) {
            if (frame instanceof TextWebSocketFrame v){
                System.out.println("in " + v.text());
            }
           // handleWebSocket(ctx, frame);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("error in pipeline", cause);
        ctx.close();
    }
}