package dev.by1337.web;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class ServerWebProtocol {
    private static final Logger log = LoggerFactory.getLogger(ServerWebProtocol.class);

    public static @Nullable String readUtf8(ByteBuf buf, int maxSize, ChannelHandlerContext ctx){
        int size = buf.readInt();
        if (size <= 0 || size >= maxSize) {
            log.info("disconnect bad string size, max {} but got {}", maxSize, size);
            ctx.close();
            return null;
        }
        byte[] array = new byte[size];
        buf.readBytes(array);
        return new String(array, StandardCharsets.UTF_8);
    }
    public static void writeUtf8(ByteBuf buf, int maxSize, String value){
        byte[] array = value.getBytes(StandardCharsets.UTF_8);
        if (array.length > maxSize){
            throw new IllegalArgumentException("string max size " + maxSize + " but got " + array.length);
        }
        buf.writeInt(array.length);
        buf.writeBytes(array);
    }
}
