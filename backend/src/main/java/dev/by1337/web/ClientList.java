package dev.by1337.web;

import dev.by1337.web.network.WebSocketHandler;
import dev.by1337.web.util.Base62Converter;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ClientList {
    private final Map<String, WebSocketHandler> clientList = new ConcurrentHashMap<>();

    public WebSocketHandler newConnection(WebSocketHandler client) {
        for (; ; ) {
            String token = Base62Converter.encode(ThreadLocalRandom.current().nextLong());

            if (clientList.putIfAbsent(token, client) == null) {
                client.setToken(token);
                return client;
            }
        }
    }

    public void removeConnection(WebSocketHandler client) {
        clientList.remove(client.token(), client);
    }
    public @Nullable WebSocketHandler getClient(String token){
        return clientList.get(token);
    }

}
