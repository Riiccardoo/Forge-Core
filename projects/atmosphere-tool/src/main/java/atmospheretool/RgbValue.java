package atmospheretool;
import java.util.Locale;
final class RgbValue {
    private RgbValue() {}
    static String canonicalColor(String s) {
        if (s==null || !s.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Color must use #RRGGBB");
        return s.toUpperCase(Locale.ROOT);
    }
    static String pickerColor(String s) {
        if(s==null)throw new IllegalArgumentException("No color supplied");
        s=s.trim();if(!s.startsWith("#"))s="#"+s;
        if(s.matches("#[0-9a-fA-F]{8}"))s=s.substring(0,7);
        if(!s.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid RGB picker value: " + s);
        return canonicalColor(s);
    }

}
