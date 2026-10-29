package atmospheretool;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public final class AtmospherePlugin extends JavaPlugin {
    private AtmosphereService service;

    public AtmospherePlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        long started=System.nanoTime();
        try {
            AtmosphereConfig config = AtmosphereConfig.read(installDefaultConfig());
            Path nativeSettings=getDataDirectory().resolve("native-global-atmosphere.json");
            Path oldSettings=getDataDirectory().resolve("global-atmosphere.json");
            NativeSettingsMigration.copy(oldSettings,nativeSettings);
            service = new AtmosphereService(config, getDataDirectory().resolve("native-global-atmosphere.json"),
                    getIdentifier().toString());
            getCodecRegistry(Interaction.CODEC).register("AtmosphereCycle", AtmosphereInteraction.class,
                    AtmosphereInteraction.codec(service));
            getCommandRegistry().registerCommand(new AtmosphereCommand(service));

            getEventRegistry().registerGlobal(PlayerReadyEvent.class, service::onPlayerReady);
            getEventRegistry().registerGlobal(
                    com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent.class,
                    event -> {
                        if (!event.isCancelled()) {
                            service.unload(event.getWorld());
                        }
                    });
        } catch (IOException | RuntimeException failure) {
            try {
                shutdown();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw new IllegalStateException("Atmosphere Tool setup failed; check config and dependencies",
                    failure);
        }
        getLogger().at(Level.INFO).log(
                "AtmosphereTool registered (global atmosphere and custom saves); setupMs=%.3f", (System.nanoTime()-started)/1_000_000.0);
    }

    @Override
    protected void shutdown() {
        if (service != null) service.restoreAll();
    }

    private Path installDefaultConfig() throws IOException {
        Files.createDirectories(getDataDirectory());
        Path path = getDataDirectory().resolve("atmosphere-presets.json");
        if (Files.notExists(path)) {
            try (InputStream input = AtmospherePlugin.class.getResourceAsStream("/atmosphere-presets.json")) {
                if (input == null) throw new IOException("Bundled atmosphere-presets.json is missing");
                Files.copy(input, path);
            }
        }
        return path;
    }
}
