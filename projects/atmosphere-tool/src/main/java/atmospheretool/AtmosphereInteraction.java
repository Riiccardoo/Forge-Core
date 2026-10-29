package atmospheretool;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class AtmosphereInteraction
extends SimpleInstantInteraction {
    private final AtmosphereService service;

    private AtmosphereInteraction(AtmosphereService atmosphereService) {
        service = atmosphereService;
    }

    static BuilderCodec<AtmosphereInteraction> codec(AtmosphereService service) {
        return BuilderCodec.builder(AtmosphereInteraction.class,
            () -> new AtmosphereInteraction(service), SimpleInstantInteraction.CODEC).build();
    }

    @Override
    protected void firstRun(InteractionType interactionType, InteractionContext interactionContext, CooldownHandler cooldownHandler) {
        AtmosphereService atmosphereService = service;
        Ref<EntityStore> ref = interactionContext.getOwningEntity();
        if (atmosphereService == null || ref == null || !ref.isValid()) {
            return;
        }
        // Open the UI after the interaction system finishes processing ECS.
        var buffer = interactionContext.getCommandBuffer();
        if (buffer == null) return;
        buffer.run(store -> use(interactionType, ref, store));
    }

    private void use(InteractionType interactionType, Ref<EntityStore> ref, Store<EntityStore> store) {
        AtmosphereService atmosphereService = service;
        if (!ref.isValid() || ref.getStore() != store || atmosphereService.isClosed()) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (player.getGameMode() != GameMode.Creative) {
            playerRef.sendMessage(Message.raw("[Atmosphere] Creative mode only."));
            return;
        }
        try {
            if (interactionType == InteractionType.Secondary) {
                AtmosphereMenuPage.open(ref, store, playerRef, atmosphereService);
            }
        }
        catch (RuntimeException runtimeException) {
            playerRef.sendMessage(Message.raw("[Atmosphere] " + runtimeException.getMessage()));
        }
    }
}
