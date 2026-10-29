package atmospheretool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Stop owned workers before Hytale unloads the plugin class loader. */
final class PluginExecutors {
    private PluginExecutors() {}

    static void finish(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS))
                    throw new IllegalStateException("Atmosphere worker did not terminate");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping Atmosphere worker", e);
        }
    }
}
