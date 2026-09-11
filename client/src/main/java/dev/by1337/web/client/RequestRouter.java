package dev.by1337.web.client;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class RequestRouter {
    private static final Logger log = LoggerFactory.getLogger(RequestRouter.class);
    private final Map<String, Function<RequestParams, @Nullable String>> handlers = new HashMap<>();

    public @Nullable String handle(String input) {
        try {
            var params = RequestParser.parse(input);
            var v = handlers.get(params.route());
            if (v == null) {
                log.warn("Not handled request {}", input);
                return null;
            }
            return v.apply(params);
        } catch (Exception e) {
            log.error("Failed to handle {}", input, e);
            return null;
        }
    }

    public RequestRouter route(String path, Function<RequestParams, @Nullable String> f) {
        handlers.put(RequestParams.cleanupPath(path), f);
        return this;
    }
}
