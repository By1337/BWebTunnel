package dev.by1337.web.client;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class RequestParams {
    private final String route;
    private final Map<String, List<String>> params;

    public RequestParams(String route, Map<String, List<String>> params) {
        this.route = cleanupRoute(route);
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

    public @Nullable String getString(String key) {
        var v = params.get(key);
        if (v == null || v.isEmpty()) return null;
        return v.get(0);
    }

    public static String cleanupRoute(String in) {
        if (in.isBlank()) return "/";
        if (!in.startsWith("/")) in = '/' + in;
        if (in.endsWith("/")) return in.substring(0, in.length() - 1);
        return in;
    }

    public String route() {
        return route;
    }

    public Map<String, List<String>> params() {
        return params;
    }

    @Override
    public String toString() {
        return "RequestParams{" +
                "route='" + route + '\'' +
                ", params=" + params +
                '}';
    }
}
