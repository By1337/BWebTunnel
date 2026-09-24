package dev.by1337.web;

import dev.by1337.web.network.service.ServiceConnection;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ServiceGroupTest {
    @Test
    void closedConnectionDisappearsFromGroup() {
        ClientList clients = new ClientList();
        EmbeddedChannel firstChannel = new EmbeddedChannel();
        EmbeddedChannel secondChannel = new EmbeddedChannel();
        EmbeddedChannel thirdChannel = new EmbeddedChannel();

        try {
            ServiceConnection first = connect(clients, firstChannel);
            ServiceConnection second = connect(clients, secondChannel);
            ServiceConnection third = connect(clients, thirdChannel);

            assertEquals(3, clients.getGroup("group").size());
            secondChannel.close();

            assertNull(clients.getClient(second.token()));
            assertEquals(2, clients.getGroup("group").size());

            firstChannel.close();
            assertEquals(1, clients.getGroup("group").size());
            assertNotNull(clients.getClient(third.token()));

            thirdChannel.close();
            assertNull(clients.getGroup("group"));

            clients.removeConnection(second);
            assertNull(clients.getGroup("group"));
        } finally {
            firstChannel.finishAndReleaseAll();
            secondChannel.finishAndReleaseAll();
            thirdChannel.finishAndReleaseAll();
        }
    }

    @Test
    void concurrentDisconnectsLeaveNoStaleGroup() throws Exception {
        ClientList clients = new ClientList();
        var channels = new ArrayList<EmbeddedChannel>();
        var services = new ArrayList<ServiceConnection>();

        try {
            for (int i = 0; i < 32; i++) {
                EmbeddedChannel channel = new EmbeddedChannel();
                channels.add(channel);
                services.add(connect(clients, channel));
            }

            try (var executor = Executors.newFixedThreadPool(8)) {
                var removals = new ArrayList<Callable<Void>>();
                for (ServiceConnection service : services) {
                    removals.add(() -> {
                        clients.removeConnection(service);
                        return null;
                    });
                }
                for (var result : executor.invokeAll(removals)) result.get();
            }

            assertNull(clients.getGroup("group"));
            for (ServiceConnection service : services) {
                assertNull(clients.getClient(service.token()));
            }
        } finally {
            for (EmbeddedChannel channel : channels) channel.finishAndReleaseAll();
        }
    }

    private static ServiceConnection connect(ClientList clients, EmbeddedChannel channel) {
        ServiceConnection service = new ServiceConnection("content", clients, channel, 1)
                .setGroupSecret("group");
        channel.pipeline().addLast(service);
        return clients.newConnection(service, true);
    }
}
