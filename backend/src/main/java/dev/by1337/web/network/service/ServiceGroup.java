package dev.by1337.web.network.service;

import dev.by1337.web.util.StreamJsonWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ServiceGroup {
    private final String secret;
    private final List<ServiceConnection> services;

    public ServiceGroup(String secret, List<ServiceConnection> services) {
        this.secret = secret;
        this.services = services;
        ByteBuf buf = Unpooled.buffer();
        toJson(new StreamJsonWriter(buf));
        byte[] arr = new byte[buf.readableBytes()];
        buf.readBytes(arr);
        System.out.println(new String(arr, StandardCharsets.UTF_8));
    }

    public static ServiceGroup merge(ServiceGroup v, ServiceGroup v1){
        List<ServiceConnection> newList = new ArrayList<>(v.services);
        newList.addAll(v1.services);
        return new ServiceGroup(v.secret, newList);
    }

    public @Nullable ServiceGroup remove(ServiceConnection connection){
        List<ServiceConnection> newList = new ArrayList<>(services);
        newList.remove(connection);
        if (newList.isEmpty()) return null;
        return new ServiceGroup(secret, services);
    }


    public void toJson(StreamJsonWriter writer){
        writer.startObject();
        writer.appendField("secret", secret);
        writer.appendComma();
        writer.string("services");
        writer.writeChar(':');
        writer.writeChar('[');
        boolean empty = true;
        for (ServiceConnection service : services) {
            if (!empty) writer.writeChar(',');
            empty = false;
            writeService(service, writer);
        }
        writer.writeChar(']');
        writer.endObject();
    }

    private void writeService(ServiceConnection service, StreamJsonWriter writer){
        writer.startObject();
        writer.appendField("token", service.token());
        writer.appendComma();
        writer.appendField("content", service.staticContent());
        var description = service.description();
        if (description != null){
            writer.appendComma();
            writer.appendField("description", description);
        }
        writer.endObject();
    }
}
