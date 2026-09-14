package dev.by1337.web.client;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class RequestParams {
    private static final Logger log = LoggerFactory.getLogger(RequestParams.class);
    private final String path;
    private final Map<String, List<String>> params;

    public RequestParams(String path, Map<String, List<String>> params) {
        this.path = cleanupPath(path);
        this.params = params;
    }

    public boolean has(String key) {
        return params.containsKey(key);
    }

    public <T> T orDefault(String key, BiFunction<RequestParams, String, T> getter, Supplier<T> def) {
        T t = getter.apply(this, key);
        if (t == null) return def.get();
        return t;
    }

    public List<String> getStringList(String key, List<String> def) {
        return orDefault(key, RequestParams::getStringList, () -> def);
    }

    public @Nullable List<String> getStringList(String key) {
        return params.get(key);
    }

    public String getString(String key, String def) {
        return orDefault(key, RequestParams::getString, () -> def);
    }

    public Long getLong(String key, Long def) {
        var s = getString(key, null);
        if (s == null) return def;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            log.error("Bad number {} {}", key, s, e);
        }
        return def;
    }
    public Integer getInt(String key, Integer def) {
        var s = getString(key, null);
        if (s == null) return def;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            log.error("Bad number {} {}", key, s, e);
        }
        return def;
    }

    public @Nullable String getString(String key) {
        var v = params.get(key);
        if (v == null || v.isEmpty()) return null;
        return v.get(0);
    }

    public static String cleanupPath(String in) {
        if (in.isBlank()) return "/";

        if (!in.startsWith("/"))
            in = '/' + in;

        if (in.length() > 1 && in.endsWith("/"))
            in = in.substring(0, in.length() - 1);

        return in;
    }

    public String route() {
        return path;
    }

    public Map<String, List<String>> params() {
        return params;
    }

    @Override
    public String toString() {
        return "RequestParams{" +
                "route='" + path + '\'' +
                ", params=" + params +
                '}';
    }
}
