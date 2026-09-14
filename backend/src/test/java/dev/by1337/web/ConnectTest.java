package dev.by1337.web;

import com.google.common.hash.Hashing;
import dev.by1337.web.client.RequestParams;
import dev.by1337.web.client.RequestRouter;
import dev.by1337.web.client.WebEndpoint;
import dev.by1337.web.db.FileDatabase;
import dev.by1337.web.db.User;
import dev.by1337.web.network.ConnectionListener;
import dev.by1337.web.network.content.GetStaticContentHandler;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

class ConnectTest {
    private ConnectionListener server;
    private int port;
    private String token;

    // @Test
    void startServer() throws Exception {
        var clients = new ClientList();
        User user = new User("by1337", "password");
        FileDatabase fileDatabase = new FileDatabase("./static/users.json");
        if (!fileDatabase.addUser(user)){
            user = fileDatabase.getUserByLogin("by1337");
        }

       // server = new ConnectionListener(clients, new GetStaticContentHandler(clients), fileDatabase);
       // port = server.startServerListener(4443);// //"ws://localhost:4443/api/ws"

        WebEndpoint webEndpoint = new WebEndpoint(
                new RequestRouter()
                        .route("/hello", h -> {
                            try (var o = h.writer().startObject(null)){
                                h.writer().putString("name", h.params().getString("name", "NoName!"));
                            }
                            h.send();
                        })
                , true,
                "wss://btunnel.bdev.space/api/ws".formatted(port),
                "helloworld",
                null,
                null
        );

        token = webEndpoint.connect().get(5, TimeUnit.SECONDS).getToken();

        System.out.println(token);
        System.out.printf("localhost:%d/api/%s/helloworld/?name=by1337%n", port, token);
        System.out.printf("http://localhost:%d/%s/%s%n", port, token, "helloworld");
        synchronized (this) {
            this.wait();
        }
    }

}