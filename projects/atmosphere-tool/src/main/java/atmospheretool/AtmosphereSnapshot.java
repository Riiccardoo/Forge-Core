package atmospheretool;

import com.google.gson.*;
import java.util.*;

/** Explicit portable global controls. Never carries target-world restoration
 * identities, area geometry, retired Fullbright or runtime asset identifiers. */
final class AtmosphereSnapshot {
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private static final Set<String> FIELDS=Set.of("baseWeatherId","nightPreset","fogPreset",
        "nightTouched","fogTouched","fogEnabled","nativeWeatherFog","nativeFogColorOverride",
        "nativeFogLevel","fogTransparency","fogBrightness","moonIntensity","ambientIntensity",
        "moonSize","moonTransparency","skyOrbitEnabled","skyAngle","skyHeight","skyHeightExtended","ambientColor",
        "moonColor","fogBaseColor","fogLuminance","fogDensity","fogNear","fogFar","fogFalloff");
    private AtmosphereSnapshot() {}
    static JsonObject encode(AtmosphereSettings source) {
        JsonObject json=JSON.toJsonTree(source,AtmosphereSettings.class).getAsJsonObject();
        for(String key:new HashSet<>(json.keySet()))if(!FIELDS.contains(key))json.remove(key);
        decode(json);return json;
    }
    static AtmosphereSettings decode(JsonObject json) {return decode(json,2);}
    static AtmosphereSettings decode(JsonObject json,int version) {
        var expected=new HashSet<>(FIELDS);
        if(version==1)expected.remove("moonTransparency");
        if(!json.keySet().equals(expected))throw new IllegalArgumentException("Unsupported saved atmosphere fields");
        AtmosphereSettings s=JSON.fromJson(json,AtmosphereSettings.class);s.validate();
        if(s.baseWeatherId==null||s.baseWeatherId.isBlank()||s.baseWeatherId.length()>160
            ||AtmosphereSettings.isRuntimeWeather(s.baseWeatherId)||s.baseWeatherId.startsWith("Atmosphere_Zone_"))
            throw new IllegalArgumentException("A save requires a reusable source weather");
        if(s.fogBaseColor<0||s.fogBaseColor>0xFFFFFF)throw new IllegalArgumentException("Invalid saved fog color");
        if(s.nightPreset==null||s.fogPreset==null)throw new IllegalArgumentException("Missing saved profile");
        return s;
    }
    static AtmosphereSettings copy(AtmosphereSettings source){return decode(encode(source));}
}
