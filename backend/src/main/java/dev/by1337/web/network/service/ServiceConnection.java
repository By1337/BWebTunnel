package dev.by1337.web.network.service;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.Natives;
import dev.by1337.web.ClientList;
import dev.by1337.web.ServerWebProtocol;
import dev.by1337.web.client.WebProtocol;
import dev.by1337.web.network.HttpResponser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.concurrent.ScheduledFuture;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DataFormatException;

public class ServiceConnection extends SimpleChannelInboundHandler<WebSocketFrame> {
    private static final Logger log = LoggerFactory.getLogger(ServiceConnection.class);
    private static final long REQUEST_DEADLINE_NANOS = 5_000_000_000L;
    private static final int IDLE_TIMEOUT_MS = 60_000;

    private final Int2ObjectMap<RequestHolder> requests = new Int2ObjectOpenHashMap<>();
    private final PriorityQueue<RequestHolder> requestsQueue = new PriorityQueue<>(256);
    private int requestId;
    private final ClientList clientList;
    private final Channel channel;
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final ScheduledFuture<?> timeoutTask;
    private final ScheduledFuture<?> idleTimeoutTask;
    private volatile boolean closing;
    private final VelocityCompressor compressor;
    private final EventLoop eventLoop;
    private final int version;
    private long latestInboundTimestamp;

    private String token;
    private final String staticContent;
    private @Nullable String description;
    private @Nullable String groupSecret;

    public ServiceConnection(String staticContent, ClientList clientList, Channel channel, int version) {
        latestInboundTimestamp = System.currentTimeMillis();
        this.staticContent = staticContent;
        this.clientList = clientList;
        this.channel = channel;
        eventLoop = channel.eventLoop();
        this.version = version;
        timeoutTask = eventLoop.scheduleAtFixedRate(
                this::timeoutRequests,
                1,
                1,
                TimeUnit.SECONDS
        );
        idleTimeoutTask = eventLoop.scheduleAtFixedRate(
                () -> {
                    if (System.currentTimeMillis() - latestInboundTimestamp > IDLE_TIMEOUT_MS) {
                        disconnect(channel, "idle timeout");
                        return;
                    }
                    var buf = channel.alloc().buffer();
                    buf.writeByte(WebProtocol.S2C_KEEPALIVE_PING);
                    write(new BinaryWebSocketFrame(buf));
                },
                15,
                15,
                TimeUnit.SECONDS
        );
        compressor = Natives.compress.get().create(6);
    }

    private void timeoutRequests() {
        if (closing) return;
        if (!requests.isEmpty()) {
            long now = System.nanoTime();
            while (true) {
                var request = requestsQueue.peek();
                if (request == null || request.timeoutAt > now) break;
                requestsQueue.poll();
                if (requests.remove(request.id) != null) {
                    try {
                        request.callback.send(HttpResponseStatus.GATEWAY_TIMEOUT);
                    } catch (Exception err) {
                        log.error("Failed to accept response", err);
                    }
                }
            }
        }
    }

    public void request(HttpResponser responser, String method) {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> request(responser, method));
            return;
        }
        long nanos = System.nanoTime();
        RequestHolder request = new RequestHolder(requestId++, nanos + REQUEST_DEADLINE_NANOS, responser);
        requests.put(request.id, request);
        requestsQueue.add(request);

        var buf = channel.alloc().buffer();
        buf.writeByte(WebProtocol.S2C_GET);
        buf.writeInt(request.id);
        ServerWebProtocol.writeUtf8(buf, WebProtocol.MAX_URI_SIZE, method);
        write(new BinaryWebSocketFrame(buf));
    }

    public void write(WebSocketFrame frame) {
        channel.write(frame);
        if (flushScheduled.compareAndSet(false, true)) {
            channel.eventLoop().schedule(() -> {
                flushScheduled.set(false);
                channel.flush();
            }, 2, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
        if (msg instanceof BinaryWebSocketFrame frame) {
            var content = frame.content();
            if (content.readableBytes() < 1) {
                disconnect(ctx, "empty payload");
                return;
            }
            latestInboundTimestamp = System.currentTimeMillis();
            byte type = content.readByte();
            if (type == WebProtocol.C2S_RESPONSE) {
                int uid = content.readInt();
                var request = requests.remove(uid);
                if (request == null) return;

                int compressType = content.readInt();
                if (compressType == -1) {
                    request.callback.send(HttpResponseStatus.NOT_FOUND);
                    return;
                }
                int size = compressType < 0 ? -1 : compressType == 0 ? content.readableBytes() : compressType;
                if (size < 0 || size > WebProtocol.MAX_PAYLOAD_SIZE) {
                    disconnect(ctx, "Bad response size " + compressType + " readableBytes " + content.readableBytes());
                    return;
                }
                if (compressType == 0) {
                    request.callback.sendJson(content.retainedSlice());
                } else {
                    request.callback.sendJson(uncompress(compressType, content));
                }
            } else if (type == WebProtocol.C2S_KEEPALIVE_PONG) {
                latestInboundTimestamp = System.currentTimeMillis();
            } else {
                disconnect(ctx, "Unknown packet type " + type);
            }
        }
    }

    private ByteBuf uncompress(int uncompressedSize, ByteBuf in) throws DataFormatException {
        ByteBuf compatibleIn = com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible(channel.alloc(), this.compressor, in);
        ByteBuf uncompressed = com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer(channel.alloc(), this.compressor, uncompressedSize);
        try {
            this.compressor.inflate(compatibleIn, uncompressed, uncompressedSize);
            return uncompressed;
        } catch (Exception e) {
            uncompressed.release();
            throw e;
        } finally {
            compatibleIn.release();
        }
    }

    public String token() {
        return token;
    }

    public ServiceConnection setToken(String token) {
        this.token = token;
        return this;
    }

    public String staticContent() {
        return staticContent;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        disconnect(ctx, "End of stream");
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
        disconnect(ctx, "connection unregister");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof TimeoutException) {
            this.disconnect(ctx, "Timed out");
        } else {
            this.disconnect(ctx, "Internal Exception: " + cause);
            log.error("An error occurred in the {} connection", ctx.channel().remoteAddress(), cause);
        }
    }

    private void disconnect(ChannelHandlerContext ctx, String message) {
        disconnect(ctx.channel(), message);
    }

    public @Nullable String description() {
        return description;
    }

    public ServiceConnection setDescription(@Nullable String description) {
        this.description = description;
        return this;
    }

    public @Nullable String groupSecret() {
        return groupSecret;
    }

    public ServiceConnection setGroupSecret(@Nullable String groupSecret) {
        this.groupSecret = groupSecret;
        return this;
    }

    private void disconnect(Channel channel, String message) {
        closing = true;
        if (channel.isOpen()) {
            channel.close();
            log.info("Disconnect connection {}, reason: {}", channel.remoteAddress(), message);
        }
        clientList.removeConnection(this);
        timeoutTask.cancel(false);
        idleTimeoutTask.cancel(false);
    }

    public static class RequestHolder implements Comparable<RequestHolder> {
        private final int id;
        private final long timeoutAt;
        private final HttpResponser callback;

        public RequestHolder(int id, long timeoutAt, HttpResponser callback) {
            this.id = id;
            this.timeoutAt = timeoutAt;
            this.callback = callback;
        }

        @Override
        public int compareTo(@NotNull RequestHolder o) {
            return Long.compare(timeoutAt, o.timeoutAt);
        }
    }
}
