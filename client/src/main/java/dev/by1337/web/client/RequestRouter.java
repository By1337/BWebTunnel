package dev.by1337.web.client;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RequestRouter {
    private static final Logger log = LoggerFactory.getLogger(RequestRouter.class);
    private final Map<String, Consumer<Handler>> handlers = new HashMap<>();

    public void handle(String input, Consumer<@Nullable StreamJsonWriter> c) {
        try {
            var params = RequestParser.parse(input);
            var v = handlers.get(params.route());
            if (v == null) {
                log.warn("Not handled request {}", input);
                c.accept(null);
                return;
            }
            v.accept(new Handler() {
                private StreamJsonWriter writer = new StreamJsonWriter();

                @Override
                public RequestParams params() {
                    return params;
                }

                @Override
                public StreamJsonWriter writer() {
                    return writer;
                }

                @Override
                public void send() {
                    var v = writer;
                    writer = null;
                    if (v == null) {
                        throw new IllegalStateException("duplicate send!");
                    }
                    c.accept(v);
                }
            });
        } catch (Exception e) {
            log.error("Failed to handle {}", input, e);
            c.accept(null);
            return;
        }
    }

    public RequestRouter route(String path, Consumer<Handler> f) {
        handlers.put(RequestParams.cleanupPath(path), f);
        return this;
    }

    public interface Handler {
        RequestParams params();

        StreamJsonWriter writer();

        void send();
    }
}
