package dev.by1337.web.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class ResourcesUtil {
    public static void extractResources(String resourceDir, Path target) throws IOException {
        URL url = Thread.currentThread()
                .getContextClassLoader()
                .getResource(resourceDir);

        if (url == null) {
            throw new FileNotFoundException(resourceDir);
        }

        if ("file".equals(url.getProtocol())) {
            Path source = Paths.get(url.getPath());

            try (Stream<Path> stream = Files.walk(source)) {
                stream.forEach(path -> {
                    try {
                        Path relative = source.relativize(path);
                        Path dest = target.resolve(relative);

                        if (Files.isDirectory(path)) {
                            Files.createDirectories(dest);
                        } else {
                            Files.createDirectories(dest.getParent());
                            Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            return;
        }

        if ("jar".equals(url.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) url.openConnection();

            try (JarFile jar = connection.getJarFile()) {
                String prefix = resourceDir.endsWith("/")
                        ? resourceDir
                        : resourceDir + "/";

                Enumeration<JarEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();

                    if (!entry.getName().startsWith(prefix)) {
                        continue;
                    }

                    Path dest = target.resolve(
                            entry.getName().substring(prefix.length())
                    );

                    if (entry.isDirectory()) {
                        Files.createDirectories(dest);
                        continue;
                    }

                    Files.createDirectories(dest.getParent());

                    try (InputStream in = jar.getInputStream(entry)) {
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }
}
