package atmospheretool;

import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import com.google.gson.*;

final class CustomAtmospheresRegression {
    static void run(Path dir,BiConsumer<Boolean,String> check)throws Exception {
        Path path=dir.resolve("custom-atmosphere-saves.json");var saves=new CustomAtmospheres(path);
        var source=new AtmosphereSettings();source.baseWeatherId="Zone1_Foggy_Light";
        source.originalWeatherId="World_private_restore_weather";source.creativeFullbright=82;
        source.fogTouched=true;source.fogEnabled=false;source.fogBrightness=137;source.fogTransparency=31;
        source.fogBaseColor=0x124578;source.moonSize=173;source.moonTransparency=63;source.skyOrbitEnabled=true;source.skyAngle=87;source.skyHeightExtended=550;
        var a=saves.create("  Blue night  ",source);source.fogBrightness=12;
        check.accept(a.name().equals("Blue night")&&saves.get(a.id()).settings().fogBrightness==137,"custom save snapshots are independent of later edits");
        var returned=saves.get(a.id());returned.settings().fogBrightness=42;
        check.accept(saves.get(a.id()).settings().fogBrightness==137,"returned save objects cannot mutate stored data");
        var after=new CustomAtmospheres(path);var b=after.get(a.id());
        check.accept(b.settings().fogBrightness==137&&b.settings().skyHeightExtended==550&&!b.settings().fogEnabled&&b.settings().moonSize==173&&b.settings().moonTransparency==63,"complete named settings persist before the operation returns");
        String text=Files.readString(path);
        check.accept(!text.contains("originalWeatherId")&&!text.contains("World_private_restore_weather")&&!text.contains("creativeFullbright")&&!text.contains("backgroundFogEnabled"),"portable file excludes world restoration and retired fields");
        var legacy=JsonParser.parseString(text).getAsJsonObject();legacy.addProperty("version",1);
        for(var entry:legacy.getAsJsonArray("saves"))entry.getAsJsonObject().getAsJsonObject("settings").remove("moonTransparency");
        Path oldFile=dir.resolve("custom-v1.json");String oldBytes=legacy.toString();Files.writeString(oldFile,oldBytes);
        var migrated=new CustomAtmospheres(oldFile);var oldEntry=migrated.get(a.id());
        check.accept(oldEntry.settings().moonTransparency==0&&oldEntry.settings().fogBrightness==137&&oldEntry.name().equals("Blue night")&&Files.readString(oldFile).equals(oldBytes),"2.3.0 library loads all entries unchanged with zero lunar transparency, without rewriting source");
        migrated.rename(a.id(),"Migrated name");
        check.accept(JsonParser.parseString(Files.readString(oldFile)).getAsJsonObject().get("version").getAsInt()==2&&Files.readString(oldFile.resolveSibling("custom-v1.json.bak")).equals(oldBytes),"first edited legacy library commits version 2 and preserves original version 1 backup");
        check.accept(new CustomAtmospheres(oldFile).get(a.id()).settings().moonTransparency==0,"migrated library reloads after restart");
        var malformed=JsonParser.parseString(text).getAsJsonObject();
        malformed.getAsJsonArray("saves").get(0).getAsJsonObject().getAsJsonObject("settings").remove("moonTransparency");
        Path missing=dir.resolve("custom-v2-missing.json");Files.writeString(missing,malformed.toString());
        check.accept(new CustomAtmospheres(missing).error()!=null,"version 2 missing required lunar field is preserved and reported, not silently defaulted");
        var renamed=after.rename(a.id(),"Nuit \"bleue\" - luna");
        check.accept(renamed.id().equals(a.id())&&new CustomAtmospheres(path).get(a.id()).name().equals(renamed.name()),"rename preserves stable ID and supports quoted Unicode names");
        byte[] before=Files.readAllBytes(path);
        for(String name:Arrays.asList("", " ", "x".repeat(65), "two\nlines",null)) {
            try{after.create(name,b.settings());throw new AssertionError("invalid name accepted");}catch(IllegalArgumentException expected){}
        }
        try{after.create(renamed.name().toUpperCase(Locale.ROOT),b.settings());throw new AssertionError("duplicate accepted");}catch(IllegalArgumentException expected){}
        check.accept(Arrays.equals(before,Files.readAllBytes(path)),"bad and duplicate names never change committed data");
        var second=after.create("second",b.settings());before=Files.readAllBytes(path);
        try{after.rename(second.id(),renamed.name());throw new AssertionError("rename duplicate accepted");}catch(IllegalArgumentException expected){}
        check.accept(Arrays.equals(before,Files.readAllBytes(path)),"rename collision cannot overwrite another saved definition");
        after.delete(a.id());after=new CustomAtmospheres(path);
        check.accept(after.list().size()==1&&after.get(second.id())!=null&&Files.exists(path.resolveSibling(path.getFileName()+".bak")),"delete survives restart and keeps other entries plus previous-file backup");
        Path broken=dir.resolve("custom-broken.json");Files.writeString(broken,"broken original");var readonly=new CustomAtmospheres(broken);
        check.accept(readonly.error()!=null&&readonly.list().isEmpty(),"damaged library is reported without blocking the whole tool");
        try{readonly.create("new",b.settings());throw new AssertionError("damaged file overwritten");}catch(IllegalStateException expected){}
        check.accept(Files.readString(broken).equals("broken original"),"damaged library is never silently replaced");
        Path future=dir.resolve("custom-future.json");Files.writeString(future,"{\"version\":99,\"saves\":[]}");
        check.accept(new CustomAtmospheres(future).error()!=null,"unknown future schema remains read-only");
        Path blocked=dir.resolve("file-instead-of-directory");Files.writeString(blocked,"keep");var unwritable=new CustomAtmospheres(blocked.resolve("saves.json"));
        try{unwritable.create("new",b.settings());throw new AssertionError("write failure acknowledged");}catch(IllegalStateException expected){}
        check.accept(unwritable.list().isEmpty()&&Files.readString(blocked).equals("keep"),"failed disk write is rejected without accepting in-memory changes");
        source.baseWeatherId="Atmosphere_Runtime_fake";
        try{saves.create("runtime",source);throw new AssertionError("runtime weather captured");}catch(IllegalArgumentException expected){}
        check.accept(true,"runtime asset identities cannot enter portable saves");
        try{Class.forName("atmospheretool.ServerFullbright");throw new AssertionError("retired backend packaged");}catch(ClassNotFoundException expected){}
        try{Class.forName("atmospheretool.LightPacketFloor");throw new AssertionError("retired light converter packaged");}catch(ClassNotFoundException expected){}
        check.accept(!ExactValue.RANGES.containsKey("CreativeFullbright"),"retired Fullbright backend converter and menu numeric contract are absent");
    }
}
