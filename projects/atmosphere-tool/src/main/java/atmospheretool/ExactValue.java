package atmospheretool;

import java.util.Map;

/** One range contract shared by slider and explicit keyboard input. */
final class ExactValue {
    record Range(int min,int max) {}
    static final Map<String,Range> RANGES=Map.of(
        "MoonSize",new Range(10,400),"MoonTransparency",new Range(0,100),
        "Moon",new Range(1,10),"GlobalDensity",new Range(0,10),
        "GlobalTransparency",new Range(0,100),"SkyAngle",new Range(0,359),
        "SkyHeight",new Range(0,1000),"GlobalBrightness",new Range(0,200));
    static int checked(String id,int value){
        Range r=RANGES.get(id);
        if(r==null)throw new IllegalArgumentException("Unknown numeric control");
        if(value<r.min||value>r.max)throw new IllegalArgumentException("Enter a whole number from "+r.min+" to "+r.max);
        return value;
    }
    static int parse(String id,String text){
        if(text==null||!text.strip().matches("[+-]?[0-9]+"))throw new IllegalArgumentException("Enter a whole number");
        try{return checked(id,Integer.parseInt(text.strip()));}
        catch(NumberFormatException e){throw new IllegalArgumentException("Number is too large");}
    }
}
