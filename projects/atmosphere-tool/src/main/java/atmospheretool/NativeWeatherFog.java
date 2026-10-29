package atmospheretool;

import com.hypixel.hytale.server.core.asset.type.weather.config.*;

/** Native Weather parameters only. No texture substitution or client shader hooks. */
final class NativeWeatherFog {
    static final String ID="Zone1_Foggy_Light";
    static final String PRESET="zone1_foggy_light";
    private NativeWeatherFog() {}

    static Weather asset() {
        Weather weather=Weather.getAssetMap().getAsset(ID);
        if(weather==null||weather==Weather.UNKNOWN)
            throw new IllegalStateException("Native Zone1_Foggy_Light weather is unavailable on this game build");
        return weather;
    }

    static void select(AtmosphereSettings s,Weather nativeWeather) {
        s.nativeWeatherFog=true;s.nativeFogLevel=5;s.nativeFogColorOverride=false;
        s.fogTransparency=0;s.fogTouched=true;s.fogEnabled=true;s.fogPreset=PRESET;
        // Picker starts with the native daytime color. The complete time curve
        // stays active until the user explicitly edits that picker.
        TimeColor[] colors=nativeWeather.getFogColors();
        if(colors!=null&&colors.length>0) {
            TimeColor chosen=colors[0];
            for(TimeColor c:colors)if(c.getHour()<=12&&c.getHour()>=chosen.getHour())chosen=c;
            var c=chosen.getColor();
            if(c!=null)s.fogBaseColor=((c.red&255)<<16)|((c.green&255)<<8)|(c.blue&255);
        }
        s.fogLuminance=1;s.fogBrightness=100;
    }

    static double strength(int level) {
        if(level<0||level>10)throw new IllegalArgumentException("Fog density must be 0..10");
        // 5 matches the native preset, 10 remains within its supported density range.
        return level<=5?level/5.0:1+(level-5)*.08;
    }

    static boolean hidden(AtmosphereSettings s) {
        return !s.fogEnabled||s.fogTransparency==100||(s.nativeWeatherFog&&s.nativeFogLevel==0);
    }

    /** Fog color also supplies the native sky backdrop, even at zero density.
     * Match its horizon to the authored upper-sky RGB curve while global fog is
     * hidden. Retain the hour keys; do not replace day/night with a fixed color.
     * An opaque bottom curve prevents the pale fog backdrop from bleeding in.
     * These are new arrays: ON can restore the untouched source weather.
     */
    record ClearBackdrop(TimeColor[] fog, TimeColorAlpha[] bottom) {}

    static ClearBackdrop clearBackdrop(Weather source) {
        TimeColorAlpha[] sky=source.getSkyTopColors();
        if(sky==null||sky.length==0)sky=source.getSkyBottomColors();
        if(sky==null||sky.length==0)return null; // No authored sky: retain native fallback.
        TimeColor[] fog=new TimeColor[sky.length];
        TimeColorAlpha[] bottom=new TimeColorAlpha[sky.length];
        for(int i=0;i<sky.length;i++) {
            var c=sky[i].getColor();
            if(c==null)return null;
            fog[i]=new TimeColor(sky[i].getHour(),new com.hypixel.hytale.protocol.Color(c.red,c.green,c.blue));
            bottom[i]=new TimeColorAlpha(sky[i].getHour(),new com.hypixel.hytale.protocol.ColorAlpha((byte)255,c.red,c.green,c.blue));
        }
        return new ClearBackdrop(fog,bottom);
    }

    static TimeFloat[] densities(Weather source,AtmosphereSettings s) {
        TimeFloat[] input=source.getFogDensities();
        double multiplier=hidden(s)?0:strength(s.nativeFogLevel)*(1-s.fogTransparency/100.0);
        if(input==null||multiplier==1)return input;
        TimeFloat[] result=new TimeFloat[input.length];
        for(int i=0;i<input.length;i++)result[i]=new TimeFloat(input[i].getHour(),(float)(input[i].getValue()*multiplier));
        return result;
    }
}
