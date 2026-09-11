package dev.by1337.web.client;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class RequestParams {
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

    public @Nullable String getString(String key) {
        var v = params.get(key);
        if (v == null || v.isEmpty()) return null;
        return v.get(0);
    }

    public static String cleanupPath(String in) {
        if (in.isBlank()) return "/";
        if (!in.startsWith("/")) in = '/' + in;
        if (in.endsWith("/")) return in.substring(0, in.length() - 1);
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
