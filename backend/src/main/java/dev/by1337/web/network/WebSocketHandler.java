package dev.by1337.web.network;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.Natives;
import dev.by1337.web.ClientList;
import dev.by1337.web.client.WebProtocol;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DataFormatException;

public class WebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);
    private static final long REQUEST_DEADLINE_NANOS = 5_000_000_000L;

    private final Int2ObjectMap<RequestHolder> requests = new Int2ObjectOpenHashMap<>();
    private final PriorityQueue<RequestHolder> requestsQueue = new PriorityQueue<>(256);
    private int requestId;
    private String token;
    private final String staticContent;
    private final ClientList clientList;
    private final Channel channel;
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final ScheduledFuture<?> timeoutTask;
    private volatile boolean closing;
    private final VelocityCompressor compressor;
    private final EventLoop eventLoop;

    public WebSocketHandler(String staticContent, ClientList clientList, Channel channel) {
        this.staticContent = staticContent;
        this.clientList = clientList;
        this.channel = channel;
        eventLoop = channel.eventLoop();
        timeoutTask = eventLoop.scheduleAtFixedRate(
                this::timeoutRequests,
                1,
                1,
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
        buf.writeByte(WebProtocol.GET);
        buf.writeInt(request.id);
        buf.writeBytes(method.getBytes(StandardCharsets.UTF_8));
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
            byte type = content.readByte();
            if (type == WebProtocol.RESPONSE) {
                int uid = content.readInt();
                var request = requests.remove(uid);
                if (request == null) return;

                int uncompressedSize = content.readInt();
                if (uncompressedSize == -1) {
                    request.callback.send(HttpResponseStatus.NOT_FOUND);
                    return;
                }
                if (uncompressedSize < 0 || uncompressedSize > WebProtocol.MAX_PAYLOAD_SIZE) {
                    disconnect(ctx, "Bad response size " + uncompressedSize);
                    return;
                }

                if (uncompressedSize == 0) {
                    request.callback.sendJson(content.retainedSlice());
                } else {
                    request.callback.sendJson(uncompress(uncompressedSize, content));
                }
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

    public WebSocketHandler setToken(String token) {
        this.token = token;
        return this;
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
        closing = true;
        if (ctx.channel().isOpen()) {
            ctx.channel().close();
            log.info("Disconnect connection {}, reason: {}", ctx.channel().remoteAddress(), message);
        }
        clientList.removeConnection(this);
        timeoutTask.cancel(false);
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
