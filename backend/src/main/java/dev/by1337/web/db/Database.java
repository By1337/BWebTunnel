package dev.by1337.web.db;

import org.jetbrains.annotations.Nullable;

public interface Database {
    boolean isValidSecret(String secret);
    @Nullable User getUserByLogin(String login);
    boolean addUser(User user);
    void close();
}
