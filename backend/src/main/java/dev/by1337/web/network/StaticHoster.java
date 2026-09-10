package dev.by1337.web.network;

import dev.by1337.web.ClientList;
import dev.by1337.web.util.ResourcesUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class StaticHoster {
    private static final Logger log = LoggerFactory.getLogger(StaticHoster.class);
    private final ClientList clientList;

    public StaticHoster(ClientList clientList) {
        this.clientList = clientList;
        try {
            //System.out.println(Path.of("./static").toAbsolutePath());
            ResourcesUtil.extractResources("static", Path.of("./static"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void onHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        HttpResponser responser = new HttpResponser(ctx);
        if (request.method() != HttpMethod.GET) {
            responser.send(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
        String uri = request.uri();

        // /token/bauction/index.html
        String[] args = uri.split("/", 4);

        if (args.length < 3) {
            responser.send(HttpResponseStatus.BAD_REQUEST);
            return;
        }

        String token = args[1];
        String staticContent = args[2];

        var client = clientList.getClient(token);
        if (client == null) {
            responser.send(HttpResponseStatus.BAD_REQUEST);
            return;
        }

        if (args.length == 3 || args[3].isEmpty()) {
            responser.redirect("/" + token + "/" + staticContent + "/index.html");
            return;
        }

        String content = args[3];

        Path path = Path.of("./static", staticContent, content);

        if (Files.exists(path)) {
            try {
                responser.send(Unpooled.wrappedBuffer(Files.readAllBytes(path)), HttpResponser.contentType(content));
            } catch (Exception e) {
                responser.send(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                log.error("Failed to send static {}", content, e);
            }
        } else {
            responser.send(HttpResponseStatus.NOT_FOUND);
        }

    }


}
