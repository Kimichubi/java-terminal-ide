package com.terminalide.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class FileManager {

    public String readFile(Path path) throws IOException {
        return Files.readString(path);
    }

    public void writeFile(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

    public List<Path> listDirectory(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .sorted((a, b) -> {
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir != bDir) return aDir ? -1 : 1;
                        return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                    })
                    .toList();
        }
    }

    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    public Path createFile(Path parent, String name) throws IOException {
        Path p = parent.resolve(name);
        Files.createFile(p);
        return p;
    }

    public Path createDirectory(Path parent, String name) throws IOException {
        Path p = parent.resolve(name);
        Files.createDirectories(p);
        return p;
    }

    public void delete(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var s = Files.walk(path)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
            }
        } else {
            Files.delete(path);
        }
    }

    public Path rename(Path path, String newName) throws IOException {
        Path target = path.resolveSibling(newName);
        Files.move(path, target);
        return target;
    }

    public boolean exists(Path path) {
        return Files.exists(path);
    }
}
