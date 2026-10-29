package atmospheretool;

import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor;

/** Adjust HSV value through one common RGB gain: never mix with white or clip
 * channels separately. 100 preserves the source; 200 uses available RGB headroom.
 * A black color has no hue or headroom; already-maximal colors cannot get brighter.
 * Native 8-bit quantization remains. This is fog color brightness, not HDR light.
 */
final class FogBrightness {
    private FogBrightness() {}
    static TimeColor[] apply(TimeColor[] source,int percent) {
        ExactValue.checked("GlobalBrightness",percent);
        if(source==null||percent==100)return source;
        TimeColor[] result=new TimeColor[source.length];
        for(int i=0;i<source.length;i++) {
            TimeColor key=source[i];
            if(key==null||key.getColor()==null){result[i]=key;continue;}
            Color c=key.getColor();int max=Math.max(c.red&255,Math.max(c.green&255,c.blue&255));
            int target100=percent<=100?max*percent:max*100+(255-max)*(percent-100);
            result[i]=new TimeColor(key.getHour(),new Color(channel(c.red,max,target100),channel(c.green,max,target100),channel(c.blue,max,target100)));
        }
        return result;
    }
    private static byte channel(byte input,int max,int target100) {
        if(max==0)return 0;
        return (byte)(((input&255)*target100+max*50)/(max*100));
    }
}
