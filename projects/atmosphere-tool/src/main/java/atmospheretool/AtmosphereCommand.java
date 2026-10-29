package atmospheretool;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;

final class AtmosphereCommand
extends AbstractCommandCollection {
    AtmosphereCommand(AtmosphereService atmosphereService) {
        super("atmosphere", "Atmosphere Tool builder controls");
        this.addAliases("atmo");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new Menu(atmosphereService));
        this.addSubCommand(new Status(atmosphereService));
        this.addSubCommand(new Reset(atmosphereService));
        this.addSubCommand(new Night(atmosphereService));
        this.addSubCommand(new Scalar(atmosphereService, "moon", ScalarKind.MOON));
        this.addSubCommand(new MoonColor(atmosphereService));
        this.addSubCommand(new Scalar(atmosphereService, "ambient", ScalarKind.AMBIENT));
        this.addSubCommand(new Fog(atmosphereService));
    }

    private static abstract class Action
    extends AbstractPlayerCommand {
        final AtmosphereService service;

        Action(AtmosphereService atmosphereService, String string, String string2) {
            super(string, string2);
            this.service = atmosphereService;
        }

        Action(AtmosphereService atmosphereService, String string) {
            super(string);
            this.service = atmosphereService;
        }

        @Override
        protected final void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            try {
                this.run(commandContext, store, ref, playerRef, world);
            }
            catch (RuntimeException runtimeException) {
                playerRef.sendMessage(Message.raw("[Atmosphere] " + runtimeException.getMessage()));
            }
        }

        abstract void run(CommandContext var1, Store<EntityStore> var2, Ref<EntityStore> var3, PlayerRef var4, World var5);
    }

    private static final class ColorCommand
    extends Action {
        private final RequiredArg<Integer> color = this.withRequiredArg("hex", "Fog color", ArgTypes.COLOR);

        ColorCommand(AtmosphereService atmosphereService) {
            super(atmosphereService, "color", "Set fog color as #RRGGBB");
            this.addUsageVariant(new RgbColor(atmosphereService));
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            this.service.setFogColor((int)((Integer)this.color.get(commandContext) & 0xFFFFFF), world, store, ref, playerRef);
        }
    }

    private static final class Fog
    extends AbstractCommandCollection {
        Fog(AtmosphereService atmosphereService) {
            super("fog", "Fog controls");
            this.addSubCommand(new Toggle(atmosphereService, true));
            this.addSubCommand(new Toggle(atmosphereService, false));
            this.addSubCommand(new FogPreset(atmosphereService));
            this.addSubCommand(new ColorCommand(atmosphereService));
            this.addSubCommand(new FogValue(atmosphereService, "density", FogValueKind.DENSITY));
            this.addSubCommand(new FogLevel(atmosphereService));
            this.addSubCommand(new FogValue(atmosphereService, "near", FogValueKind.NEAR));
            this.addSubCommand(new FogValue(atmosphereService, "far", FogValueKind.FAR));
            this.addSubCommand(new FogValue(atmosphereService, "falloff", FogValueKind.FALLOFF));
            this.addSubCommand(new FogValue(atmosphereService, "luminance", FogValueKind.LUMINANCE));
        }
    }

    private static final class FogLevel
    extends Action {
        private final RequiredArg<Integer> level = this.withRequiredArg("level", "Density 1..10", ArgTypes.INTEGER);

        FogLevel(AtmosphereService atmosphereService) {
            super(atmosphereService, "level", "Set atmospheric fog density on the 1..10 scale");
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            this.service.setAtmosphericFogLevel((Integer)this.level.get(commandContext), world, store, ref, playerRef);
        }
    }

    private static final class FogPreset
    extends Action {
        private final RequiredArg<String> preset;

        FogPreset(AtmosphereService atmosphereService) {
            super(atmosphereService, "preset", "Set a fog preset");
            this.preset = this.withRequiredArg("name", "Fog preset", new NamesType("fog preset", atmosphereService.fogPresetNames()));
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            this.service.setFogPreset((String)this.preset.get(commandContext), world, store, ref, playerRef);
        }
    }

    private static final class FogValue
    extends Action {
        private final RequiredArg<Double> value;
        private final FogValueKind kind;

        FogValue(AtmosphereService atmosphereService, String string, FogValueKind fogValueKind) {
            super(atmosphereService, string, "Set fog " + string);
            this.kind = fogValueKind;
            this.value = this.withRequiredArg("value", "Fog " + string, ArgTypes.DOUBLE);
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            double d = (Double)this.value.get(commandContext);
            switch (this.kind) {
                case DENSITY: {
                    this.service.setFogDensity(d, world, store, ref, playerRef);
                    break;
                }
                case NEAR: {
                    this.service.setFogNear(d, world, store, ref, playerRef);
                    break;
                }
                case FAR: {
                    this.service.setFogFar(d, world, store, ref, playerRef);
                    break;
                }
                case FALLOFF: {
                    this.service.setFogFalloff(d, world, store, ref, playerRef);
                    break;
                }
                case LUMINANCE: {
                    this.service.setFogLuminance(d, world, store, ref, playerRef);
                }
            }
        }
    }

    private static enum FogValueKind {
        DENSITY,
        NEAR,
        FAR,
        FALLOFF,
        LUMINANCE;

    }

    private static final class Menu
    extends Action {

        Menu(AtmosphereService atmosphereService) {
            super(atmosphereService, "menu", "Open the Atmosphere Tool panel");
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            AtmosphereMenuPage.open(ref, store, playerRef, this.service);
        }
    }

















    private static final class MoonColor
    extends Action {
        private final RequiredArg<String> color;

        MoonColor(AtmosphereService atmosphereService) {
            super(atmosphereService, "mooncolor", "Set a configured moon color");
            this.color = this.withRequiredArg("name", "Moon color", new NamesType("moon color", atmosphereService.moonColorNames()));
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            this.service.setMoonColor((String)this.color.get(commandContext), world, store, ref, playerRef);
        }
    }

    private static final class NamesType
    extends SingleArgumentType<String> {
        private final List<String> names = new ArrayList<String>();

        NamesType(String string2, Iterable<String> iterable) {
            super(string2, string2, NamesType.toArray(iterable));
            iterable.forEach(string -> {
                boolean bl = this.names.add(string.toLowerCase(Locale.ROOT));
            });
        }

        @Override
        public String parse(@Nonnull String string, @Nonnull ParseResult parseResult) {
            String string2 = string.toLowerCase(Locale.ROOT);
            if (!this.names.contains(string2)) {
                parseResult.fail(Message.raw("Unknown preset: " + string));
                return null;
            }
            return string2;
        }

        @Override
        public void suggest(@Nonnull CommandSender commandSender, @Nonnull String string, int n, @Nonnull SuggestionResult suggestionResult) {
            String string2 = string.toLowerCase(Locale.ROOT);
            for (String string3 : this.names) {
                if (!string3.startsWith(string2)) continue;
                suggestionResult.suggest(string3);
            }
        }

        @Override
        public int getSuggestionValueCount() {
            return this.names.size();
        }

        private static String[] toArray(Iterable<String> iterable) {
            ArrayList<String> arrayList = new ArrayList<>();
            iterable.forEach(arrayList::add);
            return (String[])arrayList.toArray(String[]::new);
        }
    }

    private static final class Night
    extends Action {
        private final RequiredArg<String> preset;

        Night(AtmosphereService atmosphereService) {
            super(atmosphereService, "night", "Set a night preset");
            this.preset = this.withRequiredArg("preset", "Night preset", new NamesType("night preset", atmosphereService.nightPresetNames()));
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            this.service.setNightPreset((String)this.preset.get(commandContext), world, store, ref, playerRef);
        }
    }

    private static final class Reset
    extends Action {
        Reset(AtmosphereService atmosphereService) {
            super(atmosphereService, "reset", "Restore the captured original environment");
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            this.service.reset(world, store, playerRef);
        }
    }

    private static final class RgbColor
    extends Action {
        private final RequiredArg<Integer> red = this.withRequiredArg("red", "Red 0..255", ArgTypes.INTEGER);
        private final RequiredArg<Integer> green = this.withRequiredArg("green", "Green 0..255", ArgTypes.INTEGER);
        private final RequiredArg<Integer> blue = this.withRequiredArg("blue", "Blue 0..255", ArgTypes.INTEGER);

        RgbColor(AtmosphereService atmosphereService) {
            super(atmosphereService, "Set fog color as RGB");
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            int n = (Integer)this.red.get(commandContext);
            int n2 = (Integer)this.green.get(commandContext);
            int n3 = (Integer)this.blue.get(commandContext);
            if (n < 0 || n > 255 || n2 < 0 || n2 > 255 || n3 < 0 || n3 > 255) {
                throw new IllegalArgumentException("RGB components must be 0..255.");
            }
            this.service.setFogColor(n << 16 | n2 << 8 | n3, world, store, ref, playerRef);
        }
    }

    private static final class Scalar
    extends Action {
        private final RequiredArg<Double> value;
        private final ScalarKind kind;

        Scalar(AtmosphereService atmosphereService, String string, ScalarKind scalarKind) {
            super(atmosphereService, string, "Set " + string + " intensity");
            this.kind = scalarKind;
            this.value = this.withRequiredArg("value", string + " value", ArgTypes.DOUBLE);
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            if (this.kind == ScalarKind.MOON) {
                this.service.setMoon((Double)this.value.get(commandContext), world, store, ref, playerRef);
            } else {
                this.service.setAmbient((Double)this.value.get(commandContext), world, store, ref, playerRef);
            }
        }
    }

    private static enum ScalarKind {
        MOON,
        AMBIENT;

    }

    private static final class Status
    extends Action {
        Status(AtmosphereService atmosphereService) {
            super(atmosphereService, "status", "Show Atmosphere Tool state");
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            this.service.status(world, store, ref, playerRef);
        }
    }

    private static final class Toggle
    extends Action {
        private final boolean enabled;

        Toggle(AtmosphereService atmosphereService, boolean bl) {
            super(atmosphereService, bl ? "on" : "off", "Turn fog " + (bl ? "on" : "off"));
            this.enabled = bl;
        }

        @Override
        void run(CommandContext commandContext, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            this.service.setFogEnabled(this.enabled, world, store, ref, playerRef);
        }
    }
}
