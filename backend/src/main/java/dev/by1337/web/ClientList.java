package dev.by1337.web;

import dev.by1337.web.network.service.ServiceConnection;
import dev.by1337.web.network.service.ServiceGroup;
import dev.by1337.web.util.Base62Converter;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientList {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, ServiceConnection> clientList = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ServiceGroup> groupMap = new ConcurrentHashMap<>();

    public synchronized ServiceConnection newConnection(ServiceConnection client, boolean use128Token) {
        for (; ; ) {
            String token = use128Token ? gen128Token() : gen64Token();

            client.setToken(token);
            if (clientList.putIfAbsent(token, client) == null) {
                var groupSecret = client.groupSecret();
                if (groupSecret != null) {
                    groupMap.merge(groupSecret, new ServiceGroup(groupSecret, List.of(client)), ServiceGroup::merge);
                }
                return client;
            }
        }
    }

    public synchronized void removeConnection(ServiceConnection client) {
        String token = client.token();
        if (token == null || !clientList.remove(token, client)) return;
        var secret = client.groupSecret();
        if (secret != null) {
            groupMap.computeIfPresent(secret, (key, group) -> group.remove(client));
        }
    }

    public @Nullable ServiceGroup getGroup(String secret){
        return groupMap.get(secret);
    }

    public @Nullable ServiceConnection getClient(String token) {
        return clientList.get(token);
    }

    public static String gen64Token() {
        return Base62Converter.encode(RANDOM.nextLong());
    }

    public static String gen128Token() {
        return gen64Token() + gen64Token();
    }

    public static String gen256Token() {
        return gen128Token() + gen128Token();
    }

}
