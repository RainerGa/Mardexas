package de.richmarkdown.io;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** One sleeping OS watcher per workspace, with explicit disposal and no periodic polling. */
public final class ExternalFileMonitor implements AutoCloseable {
    private final WatchService service;
    private final Map<WatchKey, Path> directories = new HashMap<>();
    private final Set<Path> files = new HashSet<>();
    private final Consumer<Path> onChange;
    private final Thread thread;
    private volatile boolean closed;

    public ExternalFileMonitor(Consumer<Path> onChange) throws IOException {
        this.onChange = java.util.Objects.requireNonNull(onChange);
        service = FileSystems.getDefault().newWatchService();
        thread = new Thread(this::watch, "mardexas-file-watch");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void setFiles(Set<Path> paths) throws IOException {
        if (closed) throw new IOException("File monitor is closed");
        Set<Path> wanted = new HashSet<>();
        for (Path path : paths) {
            Path normalized = path.toAbsolutePath().normalize();
            if (normalized.getParent() != null) wanted.add(normalized);
        }
        Set<Path> parents = new HashSet<>();
        for (Path path : wanted) parents.add(path.getParent());
        var iterator = directories.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!parents.contains(entry.getValue())) { entry.getKey().cancel(); iterator.remove(); }
        }
        for (Path parent : parents) {
            if (!directories.containsValue(parent) && Files.isDirectory(parent)) {
                WatchKey key = parent.register(service, StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
                directories.put(key, parent);
            }
        }
        files.clear(); files.addAll(wanted);
    }

    private void watch() {
        try {
            while (!closed) {
                WatchKey key = service.take();
                Set<Path> changed = new HashSet<>();
                synchronized (this) {
                    Path parent = directories.get(key);
                    if (parent != null) for (var event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) changed.addAll(files);
                        else if (event.context() instanceof Path name) {
                            Path path = parent.resolve(name).normalize();
                            if (files.contains(path)) changed.add(path);
                        }
                    }
                    if (!key.reset()) directories.remove(key);
                }
                for (Path path : changed) {
                    if (closed) break;
                    onChange.accept(path);
                }
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (java.nio.file.ClosedWatchServiceException expected) { /* lifecycle */ }
    }

    @Override public void close() throws IOException {
        closed = true;
        service.close(); thread.interrupt();
        synchronized (this) { directories.clear(); files.clear(); }
    }
}
