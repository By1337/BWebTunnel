package dev.by1337.web.network.content;

import dev.by1337.web.ClientList;
import dev.by1337.web.network.HttpResponser;
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

public class GetStaticContentHandler {
    private static final Logger log = LoggerFactory.getLogger(GetStaticContentHandler.class);
    private final ClientList clientList;

    public GetStaticContentHandler(ClientList clientList) {
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


        String[] args = uri.split("/", 4);

        if (args.length >= 3) { // /token/bauction/index.html
            String token = args[1];
            String staticContent = args[2];

            var client = clientList.getClient(token);
            if (client == null) {
                responser.send(HttpResponseStatus.BAD_REQUEST);
                return;
            }

            String content;
            if (args.length == 3 || args[3].isEmpty()) {
                content = "index.html";
            } else {
                content = args[3];
            }

            sendFile(responser, Path.of("./static", staticContent, content));
        } else if (args.length == 2 && !args[1].isBlank()) {
            sendFile(responser, Path.of("./static", args[1]));
        } else {
            sendFile(responser, Path.of("./static/login.html"));
        }
    }

    private void sendFile(HttpResponser responser, Path path) {
        System.out.println("get " + path);
        if (Files.isRegularFile(path)) {
            try {
                responser.send(Unpooled.wrappedBuffer(Files.readAllBytes(path)), HttpResponser.contentType(path.getFileName().toString()));
            } catch (Exception e) {
                responser.send(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                log.error("Failed to send static {}", path.toAbsolutePath(), e);
            }
        } else {
            responser.send(HttpResponseStatus.NOT_FOUND);
        }
    }


}
