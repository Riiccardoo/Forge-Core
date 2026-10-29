package atmospheretool;
import com.hypixel.hytale.server.core.universe.world.chunk.section.*;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.world.*;
import java.nio.file.*;
import java.lang.foreign.MemorySegment;
import java.util.*;
public final class NativeOnlyRegression {
 static int checks;
 static void check(boolean p,String n){if(!p)throw new AssertionError(n);checks++;}
 public static void main(String[] args)throws Exception{
  try { run(args); } catch(Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
 }
 static void run(String[] args)throws Exception{
  Path dir=Path.of(args[0]);Files.createDirectories(dir);
  CustomAtmospheresRegression.run(dir,NativeOnlyRegression::check);
  fogBrightness(dir);
  check(ExactValue.parse("MoonSize","173")==173,"exact size is not rounded to former five-percent slider steps");
  for(var entry:ExactValue.RANGES.entrySet()){
   check(ExactValue.parse(entry.getKey()," "+entry.getValue().min()+" ")==entry.getValue().min(),"numeric minimum "+entry.getKey());
   check(ExactValue.parse(entry.getKey(),Integer.toString(entry.getValue().max()))==entry.getValue().max(),"numeric maximum "+entry.getKey());
   for(String bad:List.of("", "1.5", "NaN", "1e2", "2147483648",Integer.toString(entry.getValue().min()-1),Integer.toString(entry.getValue().max()+1))){
    try{ExactValue.parse(entry.getKey(),bad);throw new AssertionError("invalid numeric input accepted");}catch(IllegalArgumentException expected){}
   }
  }
  check(true,"invalid keyboard values rejected without clamping or overflow");
  for(int angle:new int[]{0,90,180,270,359})for(int height:new int[]{0,37,100,200,500,1000}){
   var sky=SkyOrbit.packet(angle,height);byte[] b=new byte[sky.computeSize()];sky.serialize(MemorySegment.ofArray(b),0);
   var skyCopy=UpdateSunSettings.toObject(MemorySegment.ofArray(b));
   check(sky.equals(skyCopy)&&Math.abs(skyCopy.angleRadians-Math.toRadians(angle))<.000001,"native sky packet degrees/radians "+angle+" "+height);
  }
  var extended=new AtmosphereSettings();extended.skyHeight=100;extended.skyHeightExtended=550;extended.skyOrbitEnabled=true;
  Path skyFile=dir.resolve("sky-extended.json");
  try(var savedSky=new AtmosphereSettingsStore(skyFile)){savedSky.put("sky",extended);}
  try(var savedSky=new AtmosphereSettingsStore(skyFile)){var value=savedSky.get("sky");check(value.effectiveSkyHeight()==550&&value.skyHeight==100,"extended sky height survives restart with valid legacy fallback");}
  var oldSettings=new com.google.gson.Gson().fromJson("{\"fogTouched\":true,\"fogEnabled\":false}",AtmosphereSettings.class);
  check(oldSettings.backgroundFogEnabled&&!oldSettings.fogEnabled,"pre-2.2.5 saves retain their global OFF and load without errors");
  Path separate=dir.resolve("fog-switches.json");
  try(var savedSwitches=new AtmosphereSettingsStore(separate)){
   for(boolean mapOn:new boolean[]{false,true})for(boolean bgOn:new boolean[]{false,true}){
    var s=new AtmosphereSettings();s.fogTouched=true;s.fogEnabled=mapOn;s.backgroundFogEnabled=bgOn;
    savedSwitches.put(mapOn+"/"+bgOn,s);
   }
  }
  try(var savedSwitches=new AtmosphereSettingsStore(separate)){
   for(boolean mapOn:new boolean[]{false,true})for(boolean bgOn:new boolean[]{false,true}){
    var s=savedSwitches.get(mapOn+"/"+bgOn);
    check(s.fogEnabled==mapOn&&s.backgroundFogEnabled==bgOn,"global state and retired compatibility value survive store restart "+mapOn+"/"+bgOn);
   }
  }
  for(String retired:List.of("LocalizedMistService","WeatherZones","FogRepository","NativeSelectionPackets")) {
   try{Class.forName("atmospheretool."+retired);throw new AssertionError("Retired zones class included");}catch(ClassNotFoundException expected){}
  }
  check(true,"zone services and selection listeners absent from runtime");
  String world=UUID.randomUUID().toString();
  Files.writeString(dir.resolve("zones.json"),"{\"version\":1,\"worlds\":{},\"retainedUnknownField\":true}");
  Path legacy=dir.resolve("global-atmosphere.json"),nativeFile=dir.resolve("native-global-atmosphere.json");
  var state=new AtmosphereSettings();state.creativeFullbright=80;state.moonSize=150;
  var root=new com.google.gson.JsonObject();root.addProperty("version",1);var worlds=new com.google.gson.JsonObject();worlds.add(world,new com.google.gson.Gson().toJsonTree(state));root.add("worlds",worlds);root.addProperty("futureField","preserved");
  String legacyJson=root.toString();Files.writeString(legacy,legacyJson);NativeSettingsMigration.copy(legacy,nativeFile);
  check(Files.readString(legacy).equals(legacyJson),"migration leaves original intact");
  check(Files.readString(nativeFile).contains("futureField"),"migration preserves unknown source fields");
  try(var settings=new AtmosphereSettingsStore(nativeFile)){check(settings.get(world).creativeFullbright==0&&settings.get(world).moonSize==150,"migration disables incompatible Creative setting and preserves moon");}
  String migrated=Files.readString(nativeFile);Files.writeString(legacy,"invalid");NativeSettingsMigration.copy(legacy,nativeFile);check(Files.readString(nativeFile).equals(migrated),"migration never overwrites native settings");
  Path badTarget=dir.resolve("bad-native.json");try{NativeSettingsMigration.copy(legacy,badTarget);throw new AssertionError("invalid migration accepted");}catch(com.google.gson.JsonParseException|IllegalStateException expected){check(Files.notExists(badTarget),"invalid migration does not create target");}
  System.out.println("PASS "+checks+" checks");
 }
 static void fogBrightness(Path dir)throws Exception {
  var gson=new com.google.gson.Gson();
  var old=gson.fromJson("{\"nativeWeatherFog\":true,\"fogTouched\":true,\"fogEnabled\":false,\"fogLuminance\":0.7}",AtmosphereSettings.class);
  old.validate();check(old.fogBrightness==100&&old.fogLuminance==.7&&!old.fogEnabled,"old saves default brightness to neutral without altering legacy luminance or OFF");
  var colors=new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor[256];
  for(int i=0;i<256;i++)colors[i]=new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor(i/11f,new Color((byte)i,(byte)i,(byte)i));
  String before=gson.toJson(colors);
  check(FogBrightness.apply(colors,100)==colors,"neutral brightness preserves exact source curve");
  int[] prior=new int[256];
  for(int b=0;b<=200;b++) {
   var adjusted=FogBrightness.apply(colors,b);
   for(int i=0;i<256;i++) {
    int v=adjusted[i].getColor().red&255;
    if(v<prior[i]||v>255||adjusted[i].getHour()!=colors[i].getHour())throw new AssertionError("non-monotonic brightness/time curve");
    if((b==0&&v!=0)||(b==100&&v!=i)||(b==200&&v!=(i==0?0:255)))throw new AssertionError("brightness endpoint");
    prior[i]=v;
   }
  }
  check(true,"all 256 channels and 201 brightness levels are monotonic bounded and preserve time keys");
  check(gson.toJson(colors).equals(before),"brightness never mutates the authored colors");
  check(FogBrightness.apply(null,150)==null&&FogBrightness.apply(new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor[0],150).length==0,"absent fog colors retain native fallback");
  var night=new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor[]{new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor(21,new Color((byte)13,(byte)15,(byte)36))};
  var lifted=FogBrightness.apply(night,150)[0].getColor();
  check((lifted.red&255)==53&&(lifted.green&255)==61&&(lifted.blue&255)==146,"150 percent lifts a dark night palette while preserving channel ratios");
  for(int rgb:new int[]{0x004080,0x1545FF,0xED1851,0x003F00,0xAA00BB,0x000000,0xFFFFFF,0x0D0F24}) {
   int[] c={rgb>>16&255,rgb>>8&255,rgb&255};int max=Math.max(c[0],Math.max(c[1],c[2]));
   var input=new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor[]{new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor(0,new Color((byte)c[0],(byte)c[1],(byte)c[2]))};
   int[] last={0,0,0};
   for(int b=0;b<=200;b++) {
    var out=FogBrightness.apply(input,b)[0].getColor();int[] v={out.red&255,out.green&255,out.blue&255};
    double target=b<=100?max*b/100.0:max+(255-max)*(b-100)/100.0;
    for(int k=0;k<3;k++) {
     double expected=max==0?0:c[k]*target/max;
     if(Math.abs(v[k]-expected)>.500001||v[k]<last[k]||(c[k]==0&&v[k]!=0))throw new AssertionError("hue/saturation gain or monotonicity changed");
     if(b>=100&&max==255&&v[k]!=c[k])throw new AssertionError("saturated input changed hue to increase brightness");last[k]=v[k];
    }
   }
  }
  check(true,"colored RGB ratios preserved within half-byte quantization at every brightness; no whitening or per-channel clipping");
  moonTransparency();
  old.fogBrightness=137;old.fogTransparency=37;old.nativeFogLevel=8;old.fogBaseColor=0x123456;
  Path file=dir.resolve("fog-brightness.json");
  try(var store=new AtmosphereSettingsStore(file)){store.put("saved",old);}
  try(var store=new AtmosphereSettingsStore(file)){var v=store.get("saved");check(v.fogBrightness==137&&v.fogTransparency==37&&v.nativeFogLevel==8&&v.fogBaseColor==0x123456&&!v.fogEnabled,"brightness persists alongside independent saved controls");}
  for(int invalid:new int[]{-1,201,Integer.MAX_VALUE}) {
   old.fogBrightness=invalid;
   try{old.validate();throw new AssertionError("invalid persisted brightness");}catch(IllegalArgumentException expected){}
   try{FogBrightness.apply(null,invalid);throw new AssertionError("invalid curve brightness");}catch(IllegalArgumentException expected){}
  }
  check(true,"invalid persisted and renderer brightness rejected before use");
 }

 static void moonTransparency() {
  var moon=new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColorAlpha[]{
   new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColorAlpha(22,new ColorAlpha((byte)200,(byte)40,(byte)80,(byte)120)),
   new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColorAlpha(2,new ColorAlpha((byte)100,(byte)40,(byte)80,(byte)120))};
  var palette=new MoonTransparency.Palette(moon,moon);var gson=new com.google.gson.Gson();String original=gson.toJson(palette);
  check(MoonTransparency.apply(palette,0,0xFFFFFF)==palette,"zero manual transparency preserves exact native disk and halo curves");
  for(int percent=0;percent<=100;percent++) {
   var faded=MoonTransparency.apply(palette,percent,0xFFFFFF);
   for(int i=0;i<moon.length;i++) {
    var expected=moon[i].getColor();var actual=faded.moon()[i].getColor();
    if((actual.alpha&255)!=Math.round((expected.alpha&255)*(100-percent)/100.0)||actual.red!=expected.red||actual.green!=expected.green||actual.blue!=expected.blue||moon[i].getHour()!=faded.moon()[i].getHour())throw new AssertionError("manual alpha/RGB/time contract");
   }
  }
  check(true,"all 101 manual levels have exact rounded opacity and unchanged RGB/time keys");
  var hidden=MoonTransparency.apply(new MoonTransparency.Palette(null,null),100,0xAABBEA);
  check((hidden.moon()[0].getColor().alpha&255)==0&&(hidden.glow()[0].getColor().alpha&255)==0,"absent native palettes still permit explicitly hiding moon and halo");
  check(gson.toJson(palette).equals(original),"manual transparency never mutates authored curves");
  var old=gson.fromJson("{}",AtmosphereSettings.class);old.validate();
  check(old.moonTransparency==0,"old world settings default to zero extra lunar transparency");
  for(int invalid:new int[]{-1,101,Integer.MAX_VALUE}) {
   old.moonTransparency=invalid;
   try{old.validate();throw new AssertionError("invalid saved lunar value");}catch(IllegalArgumentException expected){}
   try{MoonTransparency.apply(palette,invalid,0);throw new AssertionError("invalid render lunar value");}catch(IllegalArgumentException expected){}
  }
  check(true,"invalid moon transparency is rejected before persistence and rendering");
 }
}
