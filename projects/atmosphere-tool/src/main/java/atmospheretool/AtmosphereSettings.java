package atmospheretool;

/** Serializable settings, without world references. OFF retains editable values. */
class AtmosphereSettings {
    String baseWeatherId, originalWeatherId;
    String nightPreset="vanilla", fogPreset="clear";
    boolean nightTouched, fogTouched, fogEnabled;
    boolean nativeWeatherFog, nativeFogColorOverride;
    boolean backgroundFogEnabled=true; // Retired 2.2.5 field: preserved for rollback only, ignored by rendering.
    int nativeFogLevel=5, fogTransparency;
    int fogBrightness=100; // Neutral for existing saves; separate from legacy preset luminance.
    int creativeFullbright=-1; // Retired: retained only for rollback, never executed.
    boolean savedAtmosphereApplied;
    double moonIntensity=1, ambientIntensity=1;
    int moonSize=100;
    int moonTransparency; // Independent of fog; old saves default to native visibility.
    boolean skyOrbitEnabled;
    int skyAngle,skyHeight=100;
    int skyHeightExtended=-1; // Keep the legacy 0..100 fallback readable by 2.2.2.
    String ambientColor, moonColor;
    int fogBaseColor=0x808080;
    double fogLuminance=1, fogDensity=0, fogNear=-96, fogFar=1024, fogFalloff=2;
    void copyTo(AtmosphereSettings s) {
        s.baseWeatherId=baseWeatherId;s.originalWeatherId=originalWeatherId;
        s.nightPreset=nightPreset;s.fogPreset=fogPreset;s.nightTouched=nightTouched;s.fogTouched=fogTouched;s.fogEnabled=fogEnabled;
        s.moonIntensity=moonIntensity;s.ambientIntensity=ambientIntensity;s.ambientColor=ambientColor;s.moonColor=moonColor;
        s.fogBaseColor=fogBaseColor;s.fogLuminance=fogLuminance;s.fogDensity=fogDensity;s.fogNear=fogNear;s.fogFar=fogFar;s.fogFalloff=fogFalloff;
        s.moonTransparency=moonTransparency;
        s.creativeFullbright=creativeFullbright; s.moonSize=moonSize;
        s.savedAtmosphereApplied=savedAtmosphereApplied;
        s.skyOrbitEnabled=skyOrbitEnabled;s.skyAngle=skyAngle;s.skyHeight=skyHeight;
        s.skyHeightExtended=skyHeightExtended;
        s.nativeWeatherFog=nativeWeatherFog;s.nativeFogColorOverride=nativeFogColorOverride;
        s.nativeFogLevel=nativeFogLevel;s.fogTransparency=fogTransparency;
        s.backgroundFogEnabled=backgroundFogEnabled;s.fogBrightness=fogBrightness;
    }
    double effectiveDensity(){return fogEnabled?fogDensity*(1-fogTransparency/100.0):0;}
    int effectiveSkyHeight(){return skyHeightExtended<0?skyHeight:skyHeightExtended;}
    boolean recoverRuntimeValues(){
        boolean changed=false;
        if(fogNear==1_000_000&&fogFar==1_000_001){
            // These are renderer-only OFF sentinels, never editable distances.
            fogNear=-96;fogFar=1024;changed=true;
        }
        if(isRuntimeWeather(baseWeatherId)){baseWeatherId=null;changed=true;}
        if(isRuntimeWeather(originalWeatherId)){originalWeatherId=null;changed=true;}
        return changed;
    }
    static boolean isRuntimeWeather(String id){return id!=null&&id.startsWith("Atmosphere_Runtime_");}
    void validate() {
        ExactValue.checked("GlobalBrightness",fogBrightness);
        ExactValue.checked("MoonTransparency",moonTransparency);
        if(nativeFogLevel<0||nativeFogLevel>10||fogTransparency<0||fogTransparency>100)
            throw new IllegalArgumentException("Fog density must be 0..10 and transparency 0..100");
        if(moonSize<10||moonSize>400)throw new IllegalArgumentException("Moon size must be 10..400%");
        if(skyHeight<0||skyHeight>100||skyHeightExtended < -1||skyHeightExtended>1000)
            throw new IllegalArgumentException("Invalid sky height");
        SkyOrbit.packet(skyAngle,effectiveSkyHeight());
        if(creativeFullbright < -1||creativeFullbright>100)throw new IllegalArgumentException("Creative Fullbright must be 0..100");
        for(double v:new double[]{moonIntensity,ambientIntensity,fogLuminance,fogDensity,fogNear,fogFar,fogFalloff})
            if(!Double.isFinite(v))throw new IllegalArgumentException("Invalid atmosphere number");
        if(moonIntensity<0||moonIntensity>2||ambientIntensity<0||ambientIntensity>2||fogLuminance<0||fogLuminance>2||fogDensity<0||fogDensity>1.2||fogNear < -384||fogNear>0||fogFar<1||fogFar>1024||fogFalloff<0||fogFalloff>10)
            throw new IllegalArgumentException("Atmosphere setting outside supported range");
        if(ambientColor!=null)RgbValue.canonicalColor(ambientColor);
        if(moonColor!=null)RgbValue.canonicalColor(moonColor);
    }
}
