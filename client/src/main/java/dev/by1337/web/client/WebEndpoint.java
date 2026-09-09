package dev.by1337.web.client;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
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
        String getToken();
    }

    private class WebSocketConnection implements Closeable, WebSocket.Listener, Connection {
        private WebSocket socket;
        private volatile State state = State.CONNECTING;
        private final CompletableFuture<Connection> authFuture = new CompletableFuture<>();

        @Override
        public void onOpen(WebSocket webSocket) {
            if (state == State.CLOSED) {
                sendClosed(webSocket);
                return;
            }
            state = State.AUTHENTICATING;
            socket = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return null;
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
            return "";
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
