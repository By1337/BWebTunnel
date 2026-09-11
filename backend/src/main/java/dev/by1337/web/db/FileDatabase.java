package dev.by1337.web.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileDatabase implements Database {
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1);
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final String filePath;
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final Set<String> validSecrets = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public FileDatabase(String filePath) {
        this.filePath = filePath;
        File file = new File(filePath);
        if (file.exists()) {
            try (var fr = new FileReader(file)) {
                Map<String, User> map = GSON.fromJson(fr, new TypeToken<Map<String, User>>() {}.getType());
                users.putAll(map);
                for (User value : map.values()) {
                    validSecrets.add(value.secret);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }else {
            file.getParentFile().mkdirs();
        }
    }

    public boolean isValidSecret(String secret) {
        return validSecrets.contains(secret);
    }

    public @Nullable User getUserByLogin(String login) {
        return users.get(login);
    }

    public boolean addUser(User user) {
        if (users.putIfAbsent(user.login, user) == null) {
            validSecrets.add(user.secret);
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        flushNow();
    }


    public void markDirty() {
        if (flushScheduled.compareAndSet(false, true)) {
            EXECUTOR.schedule(() -> {
                flushScheduled.set(false);
                flush(new HashMap<>(users));
            }, 100, TimeUnit.MILLISECONDS);
        }
    }

    private void flushNow() {
        flush(new HashMap<>(users));
    }

    private void flush(Map<String, User> map) {
        try (var fw = new FileWriter(filePath)) {
            GSON.toJson(map, fw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
