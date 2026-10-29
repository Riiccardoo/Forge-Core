package atmospheretool;

import com.hypixel.hytale.protocol.ColorAlpha;
import com.hypixel.hytale.server.core.asset.type.weather.config.TimeColorAlpha;

/** User-controlled extra lunar transparency. No fog inputs; original RGB,
 * authored time keys and native opacity are preserved at zero. */
final class MoonTransparency {
    record Palette(TimeColorAlpha[] moon,TimeColorAlpha[] glow) {}
    private MoonTransparency() {}
    static Palette apply(Palette source,int percent,int fallbackRgb) {
        ExactValue.checked("MoonTransparency",percent);
        if(percent==0)return source;
        return new Palette(fade(source.moon,percent,fallbackRgb),fade(source.glow,percent,fallbackRgb));
    }
    private static TimeColorAlpha[] fade(TimeColorAlpha[] source,int percent,int rgb) {
        if(source==null||source.length==0)source=new TimeColorAlpha[]{
            new TimeColorAlpha(0,new ColorAlpha((byte)255,(byte)(rgb>>16),(byte)(rgb>>8),(byte)rgb))};
        var result=new TimeColorAlpha[source.length];
        for(int i=0;i<source.length;i++) {
            var key=source[i];var c=key.getColor();
            result[i]=new TimeColorAlpha(key.getHour(),new ColorAlpha(
                (byte)Math.round((c.alpha&255)*(100-percent)/100.0),c.red,c.green,c.blue));
        }
        return result;
    }
}
