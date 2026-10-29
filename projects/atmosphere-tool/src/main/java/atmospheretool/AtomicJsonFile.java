package atmospheretool;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;

/** One ordered writer, coalescing slider updates; close always flushes the newest snapshot. */
final class AtomicJsonFile implements AutoCloseable {
    private final Path path;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AtmosphereTool-save"); t.setDaemon(true); return t;
    });
    private String pending;
    private boolean scheduled, closed;
    private volatile IOException failure;

    AtomicJsonFile(Path path) { this.path = path; }

    synchronized void save(String json) {
        if (closed) throw new IllegalStateException("Storage is closed");
        if (failure != null) throw new IllegalStateException("Could not save " + path, failure);
        pending = json;
        if (!scheduled) { scheduled = true; writer.execute(this::drain); }
    }

    private void drain() {
        while (true) {
            String json;
            synchronized (this) {
                json = pending; pending = null;
                if (json == null) { scheduled = false; return; }
            }
            try {
                Files.createDirectories(path.toAbsolutePath().getParent());
                Path temp = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(temp, json);
                try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException e) {
                failure = e;
                java.util.logging.Logger.getLogger("AtmosphereTool").log(java.util.logging.Level.SEVERE, "Cannot save " + path, e);
            }
        }
    }

    @Override public void close() {
        synchronized (this) { if (closed) return; closed = true; writer.shutdown(); }
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) throw new IllegalStateException("Save did not finish: " + path);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        if (failure != null) throw new IllegalStateException("Save failed: " + path, failure);
    }
}
