package dev.by1337.web;

import dev.by1337.web.client.RequestParams;
import dev.by1337.web.client.RequestRouter;
import dev.by1337.web.client.WebEndpoint;
import dev.by1337.web.network.ConnectionListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

class ConnectTest {
    private ConnectionListener server;
    private int port;
    private String token;

    @Test
    void startServer() throws Exception {
        server = new ConnectionListener(new ClientList());
        port = server.startServerListener(4443);// //"ws://localhost:4443/api/ws"

        WebEndpoint webEndpoint = new WebEndpoint(
                new RequestRouter()
                .route("/helloworld", p -> "Hello " + p.orDefault("name", RequestParams::getString, () -> "Bob"))
                , true, "ws://localhost:%d/api/ws".formatted(port), "helloworld");

        token = webEndpoint.connect().get(5, TimeUnit.SECONDS).getToken();

        System.out.println(token);
        System.out.printf("localhost:%d/api/%s/helloworld/?name=by1337%n", port, token);
        synchronized (this) {
            this.wait();
        }
    }

}