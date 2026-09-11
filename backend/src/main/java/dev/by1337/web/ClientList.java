package dev.by1337.web;

import dev.by1337.web.network.service.ServiceConnection;
import dev.by1337.web.network.service.ServiceGroup;
import dev.by1337.web.util.Base62Converter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ClientList {
    private final Map<String, ServiceConnection> clientList = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ServiceGroup> groupMap = new ConcurrentHashMap<>();

    public ServiceConnection newConnection(ServiceConnection client, boolean use128Token) {
        for (; ; ) {
            String token = use128Token ? gen128Token() : gen64Token();

            if (clientList.putIfAbsent(token, client) == null) {
                client.setToken(token);
                var groupSecret = client.groupSecret();
                if (groupSecret != null) {
                    groupMap.merge(groupSecret, new ServiceGroup(groupSecret, List.of(client)), ServiceGroup::merge);
                }
                return client;
            }
        }
    }

    public void removeConnection(ServiceConnection client) {
        clientList.remove(client.token(), client);
        var secret = client.groupSecret();
        if (secret != null) {
            var group = groupMap.get(secret);
            if (group != null) {
                var newGroup = group.remove(client);
                if (newGroup == null) groupMap.remove(secret, group);
                else groupMap.put(secret, newGroup);
            }
        }
    }

    public @Nullable ServiceGroup getGroup(String secret){
        return groupMap.get(secret);
    }

    public @Nullable ServiceConnection getClient(String token) {
        return clientList.get(token);
    }

    public static String gen64Token() {
        return Base62Converter.encode(ThreadLocalRandom.current().nextLong());
    }

    public static String gen128Token() {
        return gen64Token() + gen64Token();
    }

    public static String gen256Token() {
        return gen128Token() + gen128Token();
    }

}
