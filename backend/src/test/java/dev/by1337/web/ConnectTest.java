package dev.by1337.web;

import dev.by1337.web.client.WebEndpoint;
import dev.by1337.web.network.ConnectionListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConnectTest {
    private ConnectionListener server;
    private int port;
    private String urlPath;

    @Test
    void startServer() throws Exception{
        server = new ConnectionListener(new ClientList());
        port = server.startServerListener(0);// //"ws://localhost:4443/api/ws"

        WebEndpoint webEndpoint = new WebEndpoint(true, "ws://localhost:%d/api/ws".formatted(port), "hello");
        urlPath = webEndpoint.connect().get(5, TimeUnit.SECONDS).getUrlPath();

        System.out.println(urlPath);
    }

}