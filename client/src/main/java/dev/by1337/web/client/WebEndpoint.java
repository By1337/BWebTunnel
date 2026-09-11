package dev.by1337.web.client;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WebEndpoint {
    private static final Executor WS_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r);
        t.setName("WebEndpoint");
        return t;
    });
    private static final int PROTOCOL_VERSION = 1;
    private static final Logger log = LoggerFactory.getLogger("WebEndpoint");
    private final boolean debug;
    private final String url;
    private final HttpClient httpClient;
    private WebSocketConnection webSocket;
    private final RequestRouter router;

    private final String staticContent;
    private final @Nullable String secret;
    private final @Nullable String description;

    private final byte[] staticContentBytes;
    private final byte @Nullable [] secretBytes;
    private final byte @Nullable [] descriptionBytes;
    private final int helloPayloadSize;
    private final boolean autoConnect;

    public WebEndpoint(RequestRouter router, String url, String staticContent, @Nullable String secret, @Nullable String description) {
        this(router, false, url, staticContent, description, secret);
    }

    public WebEndpoint(RequestRouter router, boolean debug, String url, String staticContent, @Nullable String secret, @Nullable String description) {
        this.router = router;
        this.debug = debug;
        this.url = url;
        this.staticContent = staticContent;
        this.secret = secret;
        this.description = description;
        staticContentBytes = staticContent.getBytes(StandardCharsets.UTF_8);
        secretBytes = secret == null ? null : secret.getBytes(StandardCharsets.UTF_8);
        descriptionBytes = description == null ? null : description.getBytes(StandardCharsets.UTF_8);
        helloPayloadSize = staticContentBytes.length + 4 + (secretBytes == null ? 0 : secretBytes.length + 4) + (descriptionBytes == null ? 0 : descriptionBytes.length + 4);
        httpClient = HttpClient.newBuilder().executor(WS_EXECUTOR).build();
        autoConnect = secret != null && description != null;
        if (autoConnect) {
            connect();
        }
    }

    public CompletableFuture<@Nullable Connection> connect() {
        synchronized (WebEndpoint.this) {
            if (webSocket != null) return webSocket.authFuture;
            var conn = webSocket = new WebSocketConnection();
            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(url), webSocket)
                    .whenComplete((ws, error) -> {
                        if (error != null) {
                            log.error("Failed to connect!", error);
                            synchronized (WebEndpoint.this) {
                                if (conn == webSocket) {
                                    webSocket = null;
                                }
                                conn.close();
                            }
                        }
                    });
            return conn.authFuture;
        }
    }

    private void onDisconnected(WebSocketConnection conn, @Nullable Throwable e, @Nullable String msg) {
        synchronized (WebEndpoint.this) {
            if (conn == webSocket) {
                webSocket = null;
            }
            if (e != null) {
                log.error("Exceptional connection closed! {}", msg, e);
            } else if (debug) {
                log.warn("Connection closed cuz {}", msg);
            }
            conn.close();
            if (autoConnect) {
                connect();
            }
        }
    }

    public void close() {
        synchronized (WebEndpoint.this) {
            if (webSocket != null) webSocket.close();
            webSocket = null;
            httpClient.close();
        }
    }

    public interface Connection {
        String getToken();
    }

    private class WebSocketConnection implements Closeable, WebSocket.Listener, Connection {
        private WebSocket socket;
        private volatile State state = State.CONNECTING;
        private final CompletableFuture<Connection> authFuture = new CompletableFuture<>();
        private String token;
        private final BufCompressor compressor = new BufCompressor(6);

        @Override
        public void onOpen(WebSocket webSocket) {
            if (state == State.CLOSED) {
                sendClosed(webSocket);
                return;
            }
            socket = webSocket;
            setState(State.AUTHENTICATING);
            webSocket.request(1);
        }

        private void setState(State next) {
            if (next == State.AUTHENTICATING) {
                if (state != State.CONNECTING)
                    throw new IllegalStateException("not allowed state " + state + " next " + next);
                state = State.AUTHENTICATING;

                ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 1 + helloPayloadSize);
                buffer.put(WebProtocol.C2S_HELLO);
                buffer.putInt(PROTOCOL_VERSION);
                WebProtocol.writeUtf8(buffer, WebProtocol.MAX_STRING_SIZE, staticContentBytes);
                if (secretBytes != null && descriptionBytes != null) {
                    buffer.put((byte) 1);
                    WebProtocol.writeUtf8(buffer, WebProtocol.MAX_STRING_SIZE, secretBytes);
                    WebProtocol.writeUtf8(buffer, WebProtocol.MAX_STRING_SIZE, descriptionBytes);
                } else {
                    buffer.put((byte) 0);
                }
                buffer.flip();
                socket.sendBinary(buffer, true);
            } else {
                state = next;
            }
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer buf, boolean last) {
            if (!last) throw new IllegalStateException("not allowed method BINARY fragmented");
            if (buf.remaining() < 1) throw new IllegalStateException("Bad payload size!");
            byte type = buf.get();
            if (type == WebProtocol.S2C_AUTH_STATUS) {
                //System.out.println("IN AUTH_STATUS");
                if (state != State.AUTHENTICATING)
                    throw new IllegalStateException("not allowed state " + state + " bot got AUTH_STATUS packet");
                byte status = buf.get();
                if (status != 1) throw new IllegalStateException("Authentication error " + status);
                token = WebProtocol.readUtf8(buf, WebProtocol.MAX_STRING_SIZE);
                setState(State.READY);
                authFuture.complete(this);
            } else if (type == WebProtocol.S2C_GET) {
                //System.out.println("IN GET");
                if (state != State.READY)
                    throw new IllegalStateException("not allowed state " + state + " bot got GET packet");
                int uid = buf.getInt();
                var uri = WebProtocol.readUtf8(buf, WebProtocol.MAX_URI_SIZE);
                if (debug) {
                    log.info("handle {}", uri);
                }
                @Nullable String response = router.handle(uri);
                byte @Nullable [] result = response == null ? null : response.getBytes(StandardCharsets.UTF_8);
                ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 4 + (result == null ? 0 : result.length));
                buffer.put(WebProtocol.C2S_RESPONSE);
                buffer.putInt(uid);
                if (result == null) {
                    buffer.putInt(-1);
                } else if (result.length < 1024) {
                    buffer.putInt(0);
                    buffer.put(result);
                } else {
                    buffer.putInt(result.length);
                    compressor.deflate(result, buffer);
                }
                buffer.flip();
                webSocket.sendBinary(buffer, true);
            } else if (type == WebProtocol.S2C_KEEPALIVE_PING) {
                //System.out.println("IN KEEPALIVE_PING");
                ByteBuffer buffer = ByteBuffer.allocate(1);
                buffer.put(WebProtocol.C2S_KEEPALIVE_PONG);
                buffer.flip();
                webSocket.sendBinary(buffer, true);
            } else if (type == WebProtocol.C2S_ERROR_MSG) {
                byte fatal = buf.get();
                String msg = WebProtocol.readUtf8(buf, WebProtocol.MAX_STRING_SIZE);
                if (fatal == 1) {
                    onDisconnected(this, null, msg);
                } else {
                    log.error("Error {}", msg);
                }
            } else {
                throw new IllegalStateException("Unknown packet " + type);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            throw new IllegalStateException("not allowed method TEXT");
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            onDisconnected(this, null, reason + " code " + statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            onDisconnected(this, error, null);
        }

        @Override
        public void close() {
            state = State.CLOSED;
            var v = socket;
            socket = null;
            if (v != null) {
                sendClosed(v);
            }
            authFuture.complete(null);
        }

        private void sendClosed(WebSocket socket) {
            socket.sendClose(
                    WebSocket.NORMAL_CLOSURE,
                    "client shutdown"
            );
        }

        @Override
        public String getToken() {
            return token;
        }

        public enum State {
            //DISCONNECTED,
            CONNECTING,
            AUTHENTICATING,
            READY,
            CLOSED
        }
    }
}
