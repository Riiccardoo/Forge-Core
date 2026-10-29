package atmospheretool;

import com.hypixel.hytale.assetstore.AssetLoadResult;
import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.ColorAlpha;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.world.UpdateWeather;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor;
import com.hypixel.hytale.server.core.asset.type.weather.config.TimeColorAlpha;
import com.hypixel.hytale.server.core.asset.type.weather.config.TimeFloat;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.logging.Level;

final class AtmosphereService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final String assetPack;
    private static final List<String> NIGHT_CYCLE = List.of("vanilla", "moonlit", "bright");
    private static final List<String> FOG_CYCLE = List.of("clear", "mist", "dense", "ground", "miasma");
    private final AtmosphereConfig config;
    private final AtmosphereSettingsStore settings;
    private final CustomAtmospheres customSaves;
    private volatile boolean closed;
    boolean isClosed() { return closed; }
    private final Map<World, State> states = new ConcurrentHashMap<World, State>();
    private final ScheduledExecutorService assetWriter = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AtmosphereTool-AssetWriter");
        thread.setDaemon(true);
        return thread;
    });

    AtmosphereService(AtmosphereConfig atmosphereConfig, java.nio.file.Path dataPath, String assetPack) throws java.io.IOException {
        this.config = atmosphereConfig;
        this.assetPack = assetPack;
        this.settings = new AtmosphereSettingsStore(dataPath);
        this.customSaves=new CustomAtmospheres(dataPath.resolveSibling("custom-atmosphere-saves.json"));
    }

    CustomAtmospheres customSaves(){return customSaves;}
    CustomAtmospheres.Entry saveCustom(String name,World world,Store<EntityStore> store,Ref<EntityStore> ref) {
        State current=state(world,store,ref);AtmosphereSettings saved=AtmosphereSnapshot.copy(current);
        // Capture the numbers currently visible, even when using the world's default sky.
        saved.skyAngle=skyAngle(world,store,ref);saved.skyHeightExtended=skyHeight(world,store,ref);
        saved.skyHeight=Math.min(100,saved.skyHeightExtended);saved.skyOrbitEnabled=true;
        return customSaves.create(name,saved);
    }
    void applyCustom(String id,World world,Store<EntityStore> store,Ref<EntityStore> ref) {
        AtmosphereSettings saved=customSaves.get(id).settings();
        Weather base=Weather.getAssetMap().getAsset(saved.baseWeatherId);
        if(base==null||base==Weather.UNKNOWN)throw new IllegalArgumentException("This save's source weather is unavailable; the save was kept");
        if(saved.nativeWeatherFog)NativeWeatherFog.asset();
        State previous=state(world,store,ref);
        State next=new State(world,base,previous.originalForcedWeatherId);saved.copyTo(next);
        next.originalWeatherId=previous.originalWeatherId;next.savedAtmosphereApplied=true;
        // Historical compatibility data stays local to the target world.
        next.creativeFullbright=previous.creativeFullbright;next.backgroundFogEnabled=previous.backgroundFogEnabled;
        buildWeather(next); // Validate before replacing current state or its pending update.
        states.put(world,next);
        try { apply(next,null); }
        catch(RuntimeException e){states.put(world,previous);throw e;}
        if(previous.pending!=null)previous.pending.cancel(false);
        SkyOrbit.apply(world,next);
    }

    Iterable<String> nightPresetNames() {
        return this.config.night.keySet();
    }

    Iterable<String> fogPresetNames() {
        return this.config.fog.keySet();
    }

    Iterable<String> moonColorNames() {
        return this.config.controls.moonColors.keySet();
    }

    int moonLevel(World world, Store<EntityStore> store, Ref<EntityStore> ref) {
        return AtmosphereService.nearestLevel(this.config.controls.moonIntensity, this.state((World)world, store, ref).moonIntensity);
    }

    int atmosphericFogLevel(World world, Store<EntityStore> store, Ref<EntityStore> ref) {
        State s=state(world,store,ref);
        return s.nativeWeatherFog?s.nativeFogLevel:s.fogDensity<=0?0:AtmosphereService.nearestLevel(config.controls.atmosphericFogDensity,s.fogDensity);
    }

    AtmosphereSettings fogSettings(World world,Store<EntityStore> store,Ref<EntityStore> ref) {
        var copy=new AtmosphereSettings();state(world,store,ref).copyTo(copy);return copy;
    }

    void setNativeFog(World world,Store<EntityStore> store,Ref<EntityStore> ref) {
        Weather nativeWeather=NativeWeatherFog.asset(); // Validate before mutating saved settings.
        State s=state(world,store,ref);NativeWeatherFog.select(s,nativeWeather);apply(s,null);
    }

    void toggleGlobalFog(World world,Store<EntityStore> store,Ref<EntityStore> ref) {
        State s=state(world,store,ref);
        if(!s.fogTouched){setNativeFog(world,store,ref);return;}
        setFogEnabled(!s.fogEnabled,world,store,ref,null);
    }

    void setGlobalFogOn(World w,Store<EntityStore> store,Ref<EntityStore> ref){
        if(!state(w,store,ref).fogTouched)setNativeFog(w,store,ref);
        else setFogEnabled(true,w,store,ref,null);
    }

    int skyAngle(World w,Store<EntityStore> store,Ref<EntityStore> ref){
        State s=state(w,store,ref);return s.skyOrbitEnabled?s.skyAngle:SkyOrbit.angle(w);
    }
    int skyHeight(World w,Store<EntityStore> store,Ref<EntityStore> ref){
        State s=state(w,store,ref);return s.skyOrbitEnabled?s.effectiveSkyHeight():SkyOrbit.height(w);
    }
    void setSkyOrbit(String axis,int value,World w,Store<EntityStore> store,Ref<EntityStore> ref){
        ExactValue.checked(axis,value);State s=state(w,store,ref);
        int angle=s.skyOrbitEnabled?s.skyAngle:SkyOrbit.angle(w),height=s.skyOrbitEnabled?s.effectiveSkyHeight():SkyOrbit.height(w);
        if(axis.equals("SkyAngle"))angle=value;else if(axis.equals("SkyHeight"))height=value;
        else throw new IllegalArgumentException("Unknown sky control");
        if(s.skyOrbitEnabled&&s.skyAngle==angle&&s.effectiveSkyHeight()==height)return;
        SkyOrbit.packet(angle,height);s.skyAngle=angle;s.skyHeight=Math.min(100,height);s.skyHeightExtended=height;s.skyOrbitEnabled=true;
        settings.put(WorldIdentity.key(w),s);SkyOrbit.apply(w,s);
    }
    void resetSkyOrbit(World w,Store<EntityStore> store,Ref<EntityStore> ref){
        State s=state(w,store,ref);s.skyOrbitEnabled=false;
        settings.put(WorldIdentity.key(w),s);SkyOrbit.restore(w);
    }

    void setFogBrightness(int value,World world,Store<EntityStore> store,Ref<EntityStore> ref) {
        ExactValue.checked("GlobalBrightness",value);
        State s=state(world,store,ref);
        if(s.fogBrightness==value)return;
        if(!s.fogTouched)NativeWeatherFog.select(s,NativeWeatherFog.asset());
        s.fogBrightness=value;s.fogTouched=true;apply(s,null);
    }

    void setFogTransparency(int value,World world,Store<EntityStore> store,Ref<EntityStore> ref) {
        if(value<0||value>100)throw new IllegalArgumentException("Transparency must be 0..100");
        State s=state(world,store,ref);
        boolean initialized=!s.fogTouched;
        if(initialized)NativeWeatherFog.select(s,NativeWeatherFog.asset());
        if(!initialized&&s.fogTransparency==value)return;
        s.fogTransparency=value;s.fogTouched=true;apply(s,null);
    }

    void setMoonLevel(int n, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        AtmosphereService.requireLevel(n);
        this.setMoon(this.config.controls.moonIntensity[n - 1], world, store, ref, playerRef);
    }

    void setAtmosphericFogLevel(int n, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        if(n<0||n>10)throw new IllegalArgumentException("Fog density must be 0..10");
        State s=state(world,store,ref);
        boolean initialized=!s.fogTouched;
        if(initialized)NativeWeatherFog.select(s,NativeWeatherFog.asset());
        if(s.nativeWeatherFog){
            if(!initialized&&s.nativeFogLevel==n)return;
            s.nativeFogLevel=n;
            if(n==0)s.fogEnabled=false;
            apply(s,playerRef);return;
        }
        if (n == 0) { setFogEnabled(false, world, store, ref, playerRef); return; }
        AtmosphereService.requireLevel(n);
        this.setFogDensity(this.config.controls.atmosphericFogDensity[n - 1], world, store, ref, playerRef);
    }

    void setMoonColor(String string, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        String string2 = AtmosphereService.normalized(string);
        String string3 = this.config.controls.moonColors.get(string2);
        if (string3 == null) {
            throw new IllegalArgumentException("Unknown moon color: " + string);
        }
        AtmosphereService.parseHex(string3);
        State state = this.state(world, store, ref);
        state.moonColor = string3;
        state.nightPreset = "custom";
        state.nightTouched = true;
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, "Moon color: " + string2.toUpperCase(Locale.ROOT));
    }

    void setNightPreset(String string, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        String string2 = AtmosphereService.normalized(string);
        AtmosphereConfig.NightPreset nightPreset = this.config.night.get(string2);
        if (nightPreset == null) {
            throw new IllegalArgumentException("Unknown night preset: " + string);
        }
        State state = this.state(world, store, ref);
        state.nightPreset = string2;
        state.moonIntensity = AtmosphereService.clamp(nightPreset.moonIntensity, this.config.limits.moonIntensityMin, this.config.limits.moonIntensityMax);
        state.ambientIntensity = AtmosphereService.clamp(nightPreset.ambientIntensity, this.config.limits.ambientIntensityMin, this.config.limits.ambientIntensityMax);
        state.ambientColor = nightPreset.ambientColor;
        state.moonColor = nightPreset.moonColor;
        state.nightTouched = !"vanilla".equals(string2);
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, "Atmosphere: " + string2.toUpperCase(Locale.ROOT));
    }

    void setMoon(double d, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        state.moonIntensity = AtmosphereService.checked(d, this.config.limits.moonIntensityMin, this.config.limits.moonIntensityMax, "moon intensity");
        state.nightPreset = "custom";
        state.nightTouched = true;
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, String.format(Locale.ROOT, "Moon intensity: %.2f", state.moonIntensity));
    }

    void setAmbient(double d, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        state.ambientIntensity = AtmosphereService.checked(d, this.config.limits.ambientIntensityMin, this.config.limits.ambientIntensityMax, "night ambient");
        state.nightPreset = "custom";
        state.nightTouched = true;
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, String.format(Locale.ROOT, "Night ambient: %.2f", state.ambientIntensity));
    }

    void setFogEnabled(boolean bl, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        if(!bl && state.fogTouched && !state.fogEnabled) return;
        state.fogTouched = true;
        state.fogEnabled = bl;
        if(bl&&state.nativeWeatherFog&&state.nativeFogLevel==0)state.nativeFogLevel=5;
        if (bl && state.fogDensity <= 0.0) {
            AtmosphereConfig.FogPreset fogPreset = this.config.fog.get("mist");
            state.fogDensity = fogPreset == null ? 0.08 : fogPreset.density;
        }
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, bl ? "Fog enabled" : "Fog disabled");
    }

    void setFogPreset(String string, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        String string2 = AtmosphereService.normalized(string);
        AtmosphereConfig.FogPreset fogPreset = this.config.fog.get(string2);
        if (fogPreset == null) {
            throw new IllegalArgumentException("Unknown fog preset: " + string);
        }
        State state = this.state(world, store, ref);
        state.nativeWeatherFog=false;
        state.fogTouched = true;
        state.fogPreset = string2;
        state.fogBrightness=100;
        state.fogEnabled = fogPreset.enabled;
        state.fogBaseColor = AtmosphereService.parseHex(fogPreset.color);
        state.fogLuminance = AtmosphereService.checked(fogPreset.luminance, this.config.limits.fogLuminanceMin, this.config.limits.fogLuminanceMax, "fog luminance");
        state.fogDensity = AtmosphereService.checked(fogPreset.density, this.config.limits.fogDensityMin, this.config.limits.fogDensityMax, "fog density");
        state.fogNear = AtmosphereService.checked(fogPreset.nearDistance, this.config.limits.fogNearMin, this.config.limits.fogNearMax, "fog near");
        state.fogFar = AtmosphereService.checked(fogPreset.farDistance, this.config.limits.fogFarMin, this.config.limits.fogFarMax, "fog far");
        state.fogFalloff = AtmosphereService.checked(fogPreset.heightFalloff, this.config.limits.fogFalloffMin, this.config.limits.fogFalloffMax, "fog falloff");
        AtmosphereService.ensureRange(state.fogNear, state.fogFar);
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, "Fog: " + string2.toUpperCase(Locale.ROOT));
    }

    void setFogColor(int n, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        if(!state.fogTouched)NativeWeatherFog.select(state,NativeWeatherFog.asset());
        state.fogTouched = true;
        if(state.nativeWeatherFog)state.nativeFogColorOverride=true;
        else state.fogPreset = "custom";
        state.fogBaseColor = n & 0xFFFFFF;
        state.fogLuminance = 1.0;
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, String.format(Locale.ROOT, "Fog color: #%06X", state.fogBaseColor));
    }

    void setFogDensity(double d, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        state.fogDensity = AtmosphereService.checked(d, this.config.limits.fogDensityMin, this.config.limits.fogDensityMax, "fog density");
        state.fogEnabled = state.fogDensity > 0.0;
        AtmosphereService.touchFog(state);
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, String.format(Locale.ROOT, "Fog density: %.3f", state.fogDensity));
    }

    void setFogNear(double d, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        double d2 = AtmosphereService.checked(d, this.config.limits.fogNearMin, this.config.limits.fogNearMax, "fog near");
        AtmosphereService.ensureRange(d2, state.fogFar);
        state.fogNear = d2;
        AtmosphereService.touchFog(state);
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, String.format(Locale.ROOT, "Fog near: %.1f", d2));
    }

    void setFogFar(double d, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        double d2 = AtmosphereService.checked(d, this.config.limits.fogFarMin, this.config.limits.fogFarMax, "fog far");
        AtmosphereService.ensureRange(state.fogNear, d2);
        state.fogFar = d2;
        AtmosphereService.touchFog(state);
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, String.format(Locale.ROOT, "Fog far: %.1f", d2));
    }

    void setFogFalloff(double d, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        state.fogFalloff = AtmosphereService.checked(d, this.config.limits.fogFalloffMin, this.config.limits.fogFalloffMax, "fog falloff");
        AtmosphereService.touchFog(state);
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, String.format(Locale.ROOT, "Fog height falloff: %.2f", state.fogFalloff));
    }

    void setFogLuminance(double d, World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        state.fogLuminance = AtmosphereService.checked(d, this.config.limits.fogLuminanceMin, this.config.limits.fogLuminanceMax, "fog luminance");
        AtmosphereService.touchFog(state);
        this.apply(state, playerRef);
        AtmosphereService.tell(playerRef, String.format(Locale.ROOT, "Fog luminance (derived RGB): %.2f", state.fogLuminance));
    }

    void cycleNight(World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        this.setNightPreset(AtmosphereService.next(NIGHT_CYCLE, state.nightPreset), world, store, ref, playerRef);
    }

    void cycleFog(World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        this.setFogPreset(AtmosphereService.next(FOG_CYCLE, state.fogPreset), world, store, ref, playerRef);
    }

    void status(World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        State state = this.state(world, store, ref);
        AtmosphereService.tell(playerRef, "World: " + world.getName() + " | Base weather: " + state.base.getId());
        AtmosphereService.tell(playerRef, String.format(Locale.ROOT, "Night: %s | moon %.2f | ambient %.2f", state.nightPreset, state.moonIntensity, state.ambientIntensity));
        AtmosphereService.tell(playerRef, String.format(Locale.ROOT, "Fog: %s/%s | #%06X x %.2f | density %.3f | near %.1f | far %.1f | falloff %.2f", state.fogEnabled ? "on" : "off", state.fogPreset, state.fogBaseColor, state.fogLuminance, state.fogDensity, state.fogNear, state.fogFar, state.fogFalloff));
    }

    void reset(World world, Store<EntityStore> store, PlayerRef playerRef) {
        SkyOrbit.restore(world);
        settings.remove(WorldIdentity.key(world));
        State state = this.states.remove(world);
        if (state == null) {
            AtmosphereService.tell(playerRef, "Already using the original environment.");
            return;
        }
        store.getResource(WeatherResource.getResourceType()).setForcedWeather(state.originalForcedWeatherId);
        AtmosphereService.tell(playerRef, "Original environment restored.");
    }

    void restoreAll() {
        if (closed) return;
        closed = true;
        ArrayList<Map.Entry<World, State>> arrayList = new ArrayList<Map.Entry<World, State>>(this.states.entrySet());
        this.states.clear();
        try {
        for (Map.Entry<World, State> entry : arrayList) {
            World world = entry.getKey();
            State state = entry.getValue();
            if (world == null || !world.isAlive()) continue;
            try {
                world.execute(() -> {
                    if(state.skyOrbitEnabled)SkyOrbit.restore(world);
                    world.getEntityStore().getStore().getResource(WeatherResource.getResourceType()).setForcedWeather(state.originalForcedWeatherId);
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) { /* World already stopped. */ }
        }
        } finally {
            try { PluginExecutors.finish(this.assetWriter); }
            finally { this.settings.close(); }
        }
    }

    void unload(World world) { states.remove(world); }

    String fogColor(World world, Store<EntityStore> store, Ref<EntityStore> ref) {
        return String.format(Locale.ROOT,"#%06X",state(world,store,ref).fogBaseColor);
    }

    void onPlayerReady(com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent event) {
        Ref<EntityStore> ref=event.getPlayerRef();
        if(ref==null||!ref.isValid())return;
        Store<EntityStore> store=ref.getStore();World world=store.getExternalData().getWorld();
        restoreSavedWeather(world,store,ref);
    }

    void restoreSavedWeather(World world,Store<EntityStore> store,Ref<EntityStore> ref) {
        var saved=settings.get(WorldIdentity.key(world));
        if(saved==null)return;
        try {
            if(saved.skyOrbitEnabled)SkyOrbit.apply(world,state(world,store,ref));
            if(saved.savedAtmosphereApplied||saved.nightTouched||saved.fogTouched||saved.moonSize!=100||saved.moonTransparency!=0)apply(state(world,store,ref),null);
        }
        catch(RuntimeException e) { LOGGER.at(Level.WARNING).withCause(e).log("Could not restore atmosphere for %s",world.getName()); }
    }

    private State state(World world, Store<EntityStore> store, Ref<EntityStore> ref) {
        if (closed) throw new IllegalStateException("Atmosphere Tool is stopped");
        State state = this.states.get(world);
        if (state != null) {
            return state;
        }
        AtmosphereSettings saved=settings.get(WorldIdentity.key(world));
        WeatherTracker weatherTracker = ref==null?null:store.getComponent(ref, WeatherTracker.getComponentType());
        Weather weather = saved!=null && saved.baseWeatherId!=null ? Weather.getAssetMap().getAsset(saved.baseWeatherId) : null;
        if(weather==null)weather=Weather.getAssetMap().getAsset(store.getResource(WeatherResource.getResourceType()).getForcedWeatherIndex());
        if(weather==Weather.UNKNOWN)weather=null;
        if(weather==null && weatherTracker!=null)weather=Weather.getAssetMap().getAsset(weatherTracker.getWeatherIndex());
        // A previous runtime asset must never become the base of the next edit.
        if(weather!=null&&AtmosphereSettings.isRuntimeWeather(weather.getId()))weather=Weather.getAssetMap().getAsset("Zone1_Sunny");
        if (weather == null || weather == Weather.UNKNOWN) {
            throw new IllegalStateException("Current Weather asset is not available; retry after the world finishes loading.");
        }
        WeatherResource weatherResource = store.getResource(WeatherResource.getResourceType());
        String string = AtmosphereService.weatherId(weatherResource.getForcedWeatherIndex());
        if(AtmosphereSettings.isRuntimeWeather(string))string=null;
        if(saved!=null&&saved.baseWeatherId!=null) {
            Weather original=Weather.getAssetMap().getAsset(saved.baseWeatherId);
            if(original!=null)weather=original;
            string=saved.originalWeatherId;
        }
        State state2 = new State(world, weather, string);
        this.initializeFog(state2);
        if(saved!=null)saved.copyTo(state2);
        state2.baseWeatherId=weather.getId();state2.originalWeatherId=string;
        State state3 = this.states.putIfAbsent(world, state2);
        return state3 == null ? state2 : state3;
    }

    private void initializeFog(State state) {
        float[] fArray;
        TimeFloat[] timeFloatArray;
        TimeFloat[] timeFloatArray2;
        TimeColor[] timeColorArray = state.base.getFogColors();
        if (timeColorArray != null && timeColorArray.length > 0 && timeColorArray[0].getColor() != null) {
            state.fogBaseColor = AtmosphereService.rgb(timeColorArray[0].getColor());
        }
        if ((timeFloatArray2 = state.base.getFogDensities()) != null && timeFloatArray2.length > 0) {
            state.fogDensity = timeFloatArray2[0].getValue();
        }
        if ((timeFloatArray = state.base.getFogHeightFalloffs()) != null && timeFloatArray.length > 0) {
            state.fogFalloff = timeFloatArray[0].getValue();
        }
        if ((fArray = state.base.getFogDistance()) != null && fArray.length == 2) {
            state.fogNear = fArray[0];
            state.fogFar = fArray[1];
        }
        state.fogEnabled = state.fogDensity > 0.0;
        state.recoverRuntimeValues();
        state.fogDensity=clamp(state.fogDensity,0,1.2);
        state.fogFalloff=clamp(state.fogFalloff,0,10);
        state.fogNear=clamp(state.fogNear,-384,0);
        state.fogFar=clamp(state.fogFar,1,1024);
    }

    private void apply(State state, PlayerRef playerRef) {
        settings.put(WorldIdentity.key(state.world),state);
        Weather weather = this.buildWeather(state);
        long l = ++state.revision;
        AtmosphereService.tell(playerRef, "Applying to world...");
        if(state.pending!=null)state.pending.cancel(false);
        state.pending=this.assetWriter.schedule(() -> {
            block5: {
                try {
                    if(this.states.get(state.world)!=state || state.revision!=l || !state.world.isAlive())return;
                    AssetLoadResult<String, Weather> assetLoadResult = Weather.getAssetStore().loadAssets(assetPack, List.of(weather));
                    if (assetLoadResult.hasFailed()) {
                        throw new IllegalStateException("Hytale rejected the runtime Weather asset.");
                    }
                    int n = Weather.getAssetMap().getIndex(state.runtimeWeatherId);
                    if (n == Integer.MIN_VALUE || n == 0) {
                        throw new IllegalStateException("Runtime Weather was not indexed by Hytale.");
                    }
                    if (!state.world.isAlive()) {
                        return;
                    }
                    state.world.execute(() -> this.finishApply(state, l, n, playerRef));
                }
                catch (RuntimeException runtimeException) {
                    ((HytaleLogger.Api)LOGGER.at(Level.SEVERE).withCause(runtimeException)).log("Failed to apply Atmosphere Tool Weather for world '%s'", state.world.getName());
                    if (!state.world.isAlive()) break block5;
                    state.world.execute(() -> AtmosphereService.tell(playerRef, "Apply failed; see the server log."));
                }
            }
        },80,TimeUnit.MILLISECONDS);
    }

    private void finishApply(State state, long l, int n, PlayerRef playerRef) {
        if (this.states.get(state.world) != state || state.revision != l) {
            return;
        }
        Store<EntityStore> store = state.world.getEntityStore().getStore();
        store.getResource(WeatherResource.getResourceType()).setForcedWeather(state.runtimeWeatherId);
        for (PlayerRef playerRef2 : state.world.getPlayerRefs()) {
            WeatherTracker weatherTracker;
            Ref<EntityStore> ref = playerRef2.getReference();
            if (ref == null || !ref.isValid() || (weatherTracker = store.getComponent(ref, WeatherTracker.getComponentType())) == null) continue;
            weatherTracker.setWeatherIndex(playerRef2, n);
            playerRef2.getPacketHandler().write((ToClientPacket)new UpdateWeather(n, state.fogTouched && NativeWeatherFog.hidden(state) ? 0f : 0.1f));
        }
        LOGGER.at(Level.FINE).log("Applied Atmosphere Tool Weather '%s' (index %d) to world '%s'", state.runtimeWeatherId, n, state.world.getName());
        AtmosphereService.tell(playerRef, "Applied to world " + state.world.getName() + ".");
    }

    int moonSize(World w,Store<EntityStore> store,Ref<EntityStore> ref){return state(w,store,ref).moonSize;}
    void setMoonSize(int size,World w,Store<EntityStore> store,Ref<EntityStore> ref){
        if(size<10||size>400)throw new IllegalArgumentException("Moon size: 10..400%");
        State s=state(w,store,ref);s.moonSize=size;apply(s,null);
    }
    void setMoonTransparency(int percent,World w,Store<EntityStore> store,Ref<EntityStore> ref) {
        ExactValue.checked("MoonTransparency",percent);State s=state(w,store,ref);
        if(s.moonTransparency==percent)return;
        s.moonTransparency=percent;apply(s,null);
    }
    static TimeFloat[] scaledMoon(TimeFloat[] source,int size){
        if(size==100)return source;
        if(source==null||source.length==0)return new TimeFloat[]{new TimeFloat(0,size/100f)};
        return java.util.Arrays.stream(source).map(v->new TimeFloat(v.getHour(),v.getValue()*size/100f)).toArray(TimeFloat[]::new);
    }
    private Weather buildWeather(State state) {
        return buildWeather(state.nativeWeatherFog?NativeWeatherFog.asset():state.base,state,state.runtimeWeatherId);
    }

    Weather buildWeather(Weather weather,AtmosphereSettings state,String id) {
        var moonSource=new MoonTransparency.Palette(weather.getMoonColors(),weather.getMoonGlowColors());
        TimeColorAlpha[] timeColorAlphaArray = moonSource.moon();
        TimeColorAlpha[] timeColorAlphaArray2 = moonSource.glow();
        TimeColor[] timeColorArray = weather.getSunlightColors();
        if (state.nightTouched) {
            timeColorAlphaArray = this.adjustedMoon(timeColorAlphaArray, state.moonColor, state.moonIntensity);
            timeColorAlphaArray2 = this.adjustedMoon(timeColorAlphaArray2, state.moonColor, state.moonIntensity);
            timeColorArray = this.adjustedAmbient(timeColorArray, state.ambientColor, state.ambientIntensity);
        }
        {
            var faded=MoonTransparency.apply(new MoonTransparency.Palette(timeColorAlphaArray,timeColorAlphaArray2),state.moonTransparency,parseHex(config.fallbackMoonColor));
            timeColorAlphaArray=faded.moon();timeColorAlphaArray2=faded.glow();
        }
        TimeColor[] timeColorArray2 = weather.getFogColors();
        TimeFloat[] timeFloatArray = weather.getFogDensities();
        TimeFloat[] timeFloatArray2 = weather.getFogHeightFalloffs();
        float[] fArray = weather.getFogDistance();
        if(state.fogTouched&&state.nativeWeatherFog) {
            timeFloatArray=NativeWeatherFog.densities(weather,state);
            if(state.nativeFogColorOverride)timeColorArray2=new TimeColor[]{new TimeColor(0,AtmosphereService.color(state.fogBaseColor))};
        } else if (state.fogTouched) {
            int n = AtmosphereService.applyLuminance(state.fogBaseColor, state.fogLuminance);
            timeColorArray2 = new TimeColor[]{new TimeColor(0.0f, AtmosphereService.color(n))};
            timeFloatArray = new TimeFloat[]{new TimeFloat(0.0f, (float)state.effectiveDensity())};
            timeFloatArray2 = new TimeFloat[]{new TimeFloat(0.0f, (float)(state.fogEnabled ? state.fogFalloff : 0))};
            fArray = state.fogEnabled ? new float[]{(float)state.fogNear, (float)state.fogFar} : new float[]{1000000f,1000001f};
            if(!state.fogEnabled)timeColorArray2=weather.getFogColors();
        }
        boolean hidden=state.fogTouched&&NativeWeatherFog.hidden(state);
        if(state.fogTouched&&!hidden) {
            timeColorArray2=FogBrightness.apply(timeColorArray2,state.fogBrightness);
        }
        if(hidden){timeFloatArray=new TimeFloat[]{new TimeFloat(0,0)};timeFloatArray2=new TimeFloat[]{new TimeFloat(0,0)};fArray=new float[]{1000000f,1000001f};}
        TimeColorAlpha[] skyBottom=weather.getSkyBottomColors();
        // Global OFF also neutralizes the shared fog palette used by the sky.
        // The retired 2.2.5 background flag is deliberately not consulted.
        if(hidden) {
            var backdrop=NativeWeatherFog.clearBackdrop(weather);
            if(backdrop!=null){timeColorArray2=backdrop.fog();skyBottom=backdrop.bottom();}
        }
        var fogOptions = hidden ? disabledFogOptions() : weather.getFogOptions();
        Weather result=new Weather(id, weather.getMoons(), weather.getClouds(), weather.getSunlightDampingMultiplier(), timeColorArray, weather.getSunColors(), timeColorAlphaArray, weather.getSunGlowColors(), timeColorAlphaArray2, weather.getSunScales(), scaledMoon(weather.getMoonScales(),state.moonSize), weather.getSkyTopColors(), skyBottom, weather.getSkySunsetColors(), timeColorArray2, timeFloatArray2, timeFloatArray, weather.getWaterTints(), fArray, fogOptions, weather.getScreenEffect(), weather.getScreenEffectColors(), weather.getColorFilters(), weather.getStars(), weather.getParticle());
        return result;
    }

    static com.hypixel.hytale.server.core.asset.type.weather.config.FogOptions disabledFogOptions() {
        return com.hypixel.hytale.server.core.asset.type.weather.config.FogOptions.CODEC.decode(
            org.bson.BsonDocument.parse("{\"IgnoreFogLimits\":true,\"FogHeightCameraFixed\":0}"),new com.hypixel.hytale.codec.ExtraInfo());
    }

    private TimeColorAlpha[] adjustedMoon(TimeColorAlpha[] timeColorAlphaArray, String string, double d) {
        if (timeColorAlphaArray == null || timeColorAlphaArray.length == 0) {
            int n = AtmosphereService.parseHex(string == null ? this.config.fallbackMoonColor : string);
            ColorAlpha colorAlpha = AtmosphereService.colorAlpha(n, d);
            return new TimeColorAlpha[]{new TimeColorAlpha(3.0f, colorAlpha), new TimeColorAlpha(21.0f, colorAlpha)};
        }
        TimeColorAlpha[] timeColorAlphaArray2 = new TimeColorAlpha[timeColorAlphaArray.length];
        Integer n = string == null ? null : Integer.valueOf(AtmosphereService.parseHex(string));
        int n2 = 0;
        while (n2 < timeColorAlphaArray.length) {
            ColorAlpha colorAlpha = timeColorAlphaArray[n2].getColor();
            int n3 = n == null ? AtmosphereService.rgb(colorAlpha) : n;
            int n4 = AtmosphereService.clamp255((int)Math.round((double)Byte.toUnsignedInt(colorAlpha.alpha) * d));
            int n5 = AtmosphereService.applyLuminance(n3, d);
            timeColorAlphaArray2[n2] = new TimeColorAlpha(timeColorAlphaArray[n2].getHour(), new ColorAlpha((byte)n4, (byte)(n5 >>> 16 & 0xFF), (byte)(n5 >>> 8 & 0xFF), (byte)(n5 & 0xFF)));
            ++n2;
        }
        return timeColorAlphaArray2;
    }

    private TimeColor[] adjustedAmbient(TimeColor[] timeColorArray, String string, double d) {
        if (timeColorArray == null || timeColorArray.length == 0) {
            int n = AtmosphereService.parseHex(string == null ? this.config.fallbackNightAmbientColor : string);
            Color color = AtmosphereService.color(AtmosphereService.applyLuminance(n, d));
            return new TimeColor[]{new TimeColor(3.0f, color), new TimeColor(5.0f, color), new TimeColor(7.0f, AtmosphereService.color(0xFFFFFF)), new TimeColor(17.0f, AtmosphereService.color(0xFFFFFF)), new TimeColor(19.0f, color), new TimeColor(21.0f, color)};
        }
        TimeColor[] timeColorArray2 = new TimeColor[timeColorArray.length];
        Integer n = string == null ? null : Integer.valueOf(AtmosphereService.parseHex(string));
        int n2 = 0;
        while (n2 < timeColorArray.length) {
            TimeColor timeColor = timeColorArray[n2];
            boolean bl = timeColor.getHour() >= 19.0f || timeColor.getHour() <= 5.0f;
            int n3 = n != null && bl ? n : AtmosphereService.rgb(timeColor.getColor());
            timeColorArray2[n2] = new TimeColor(timeColor.getHour(), AtmosphereService.color(bl ? AtmosphereService.applyLuminance(n3, d) : n3));
            ++n2;
        }
        return timeColorArray2;
    }

    private static void touchFog(State state) {
        state.nativeWeatherFog=false;
        state.fogTouched = true;
        state.fogPreset = "custom";
    }

    private static void ensureRange(double d, double d2) {
        if (!(d < d2)) {
            throw new IllegalArgumentException("Fog near must be lower than fog far.");
        }
    }

    private static double checked(double d, double d2, double d3, String string) {
        if (!Double.isFinite(d) || d < d2 || d > d3) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "%s must be %.2f..%.2f", string, d2, d3));
        }
        return d;
    }

    private static double clamp(double d, double d2, double d3) {
        return Math.max(d2, Math.min(d3, d));
    }

    static int parseHex(String string) {
        if (string == null || !string.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Color must use #RRGGBB.");
        }
        return Integer.parseInt(string.substring(1), 16);
    }

    static int applyLuminance(int n, double d) {
        int n2 = AtmosphereService.clamp255((int)Math.round((double)(n >>> 16 & 0xFF) * d));
        int n3 = AtmosphereService.clamp255((int)Math.round((double)(n >>> 8 & 0xFF) * d));
        int n4 = AtmosphereService.clamp255((int)Math.round((double)(n & 0xFF) * d));
        return n2 << 16 | n3 << 8 | n4;
    }

    private static int clamp255(int n) {
        return Math.max(0, Math.min(255, n));
    }

    private static Color color(int n) {
        return new Color((byte)(n >>> 16), (byte)(n >>> 8), (byte)n);
    }

    private static ColorAlpha colorAlpha(int n, double d) {
        int n2 = AtmosphereService.applyLuminance(n, d);
        return new ColorAlpha((byte)AtmosphereService.clamp255((int)Math.round(255.0 * d)), (byte)(n2 >>> 16), (byte)(n2 >>> 8), (byte)n2);
    }

    private static int rgb(Color color) {
        return Byte.toUnsignedInt(color.red) << 16 | Byte.toUnsignedInt(color.green) << 8 | Byte.toUnsignedInt(color.blue);
    }

    private static int rgb(ColorAlpha colorAlpha) {
        return Byte.toUnsignedInt(colorAlpha.red) << 16 | Byte.toUnsignedInt(colorAlpha.green) << 8 | Byte.toUnsignedInt(colorAlpha.blue);
    }

    private static String weatherId(int n) {
        if (n == 0 || n == Integer.MIN_VALUE) {
            return null;
        }
        Weather weather = Weather.getAssetMap().getAsset(n);
        return weather == null ? null : weather.getId();
    }

    private static String normalized(String string) {
        return string.toLowerCase(Locale.ROOT);
    }

    private static String next(List<String> list, String string) {
        int n = list.indexOf(string);
        return list.get((n + 1) % list.size());
    }

    private static void requireLevel(int n) {
        if (n < 1 || n > 10) {
            throw new IllegalArgumentException("Level must be 1..10.");
        }
    }

    private static int nearestLevel(double[] dArray, double d) {
        int n = 0;
        double d2 = Double.MAX_VALUE;
        int n2 = 0;
        while (n2 < dArray.length) {
            double d3 = Math.abs(dArray[n2] - d);
            if (d3 < d2) {
                d2 = d3;
                n = n2;
            }
            ++n2;
        }
        return n + 1;
    }

    private static void tell(PlayerRef playerRef, String string) {
        if(playerRef==null)return;
        playerRef.sendMessage(Message.raw("[Atmosphere] " + string));
    }

    private static final class State extends AtmosphereSettings {
        final World world;
        final Weather base;
        final String originalForcedWeatherId;
        final String runtimeWeatherId;
        volatile long revision;
        ScheduledFuture<?> pending;

        State(World world, Weather weather, String string) {
            this.world = world;
            this.base = weather;
            this.originalForcedWeatherId = string;
            this.runtimeWeatherId = "Atmosphere_Runtime_" + WorldIdentity.key(world).replace('-','_');
        }
    }
}
