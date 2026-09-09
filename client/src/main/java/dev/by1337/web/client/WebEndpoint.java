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

public class WebEndpoint {
    private static final Logger log = LoggerFactory.getLogger("WebEndpoint");
    private final boolean debug;
    private final String url;
    private final String staticContent;
    private final HttpClient httpClient;
    private WebSocketConnection webSocket;

    public WebEndpoint(boolean debug, String url, String staticContent) {
        this.debug = debug;
        this.url = url;
        this.staticContent = staticContent;
        httpClient = HttpClient.newHttpClient();
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
        String getUrlPath();
    }

    private class WebSocketConnection implements Closeable, WebSocket.Listener, Connection {
        private WebSocket socket;
        private volatile State state = State.CONNECTING;
        private final CompletableFuture<Connection> authFuture = new CompletableFuture<>();
        private String urlPath;

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

                var payload = staticContent.getBytes(StandardCharsets.UTF_8);
                ByteBuffer buffer = ByteBuffer.allocate(1 + payload.length);
                buffer.put(WebProtocol.HELLO);
                buffer.put(payload);
                buffer.flip();
                socket.sendBinary(buffer, true);
            }
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer buf, boolean last) {
            if (!last) throw new IllegalStateException("not allowed method BINARY fragmented");
            if (buf.remaining() < 1) throw new IllegalStateException("Bad payload size!");
            byte type = buf.get();
            if (type == WebProtocol.AUTH_STATUS) {
                if (state != State.AUTHENTICATING)
                    throw new IllegalStateException("not allowed state " + state + " bot got AUTH_STATUS packet");
                byte status = buf.get();
                if (status != 1) throw new IllegalStateException("Authentication error " + status);
                int size = buf.remaining();
                if (size <= 0 || size >= 256) throw new IllegalStateException("Bad payload size " + size);
                byte[] url = new byte[size];
                buf.get(url);
                urlPath = new String(url);
                setState(State.READY);
                authFuture.complete(this);
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
        public String getUrlPath() {
            return urlPath;
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
