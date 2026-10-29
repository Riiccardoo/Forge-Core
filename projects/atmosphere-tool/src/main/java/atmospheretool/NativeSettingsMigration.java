package atmospheretool;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;

/** Copies global settings once. Old global and localized-volume files remain untouched. */
final class NativeSettingsMigration {
    static void copy(Path oldSettings,Path nativeSettings)throws IOException{
        if(Files.exists(nativeSettings)||Files.notExists(oldSettings))return;
        JsonObject root=JsonParser.parseString(Files.readString(oldSettings)).getAsJsonObject();
        if(!root.has("version")||root.get("version").getAsInt()!=1||!root.has("worlds")||!root.get("worlds").isJsonObject())
            throw new IOException("Unsupported legacy atmosphere settings; original preserved");
        for(var entry:root.getAsJsonObject("worlds").entrySet()){
            var value=entry.getValue().getAsJsonObject();
            value.addProperty("creativeFullbright",0); // Retired legacy value; no active lighting backend.
            var settings=new Gson().fromJson(value,AtmosphereSettings.class);
            settings.recoverRuntimeValues();settings.validate();
        }
        Path temp=Files.createTempFile(nativeSettings.toAbsolutePath().getParent(),"native-migration-",".tmp");
        try{
            Files.writeString(temp,root.toString());
            try{Files.move(temp,nativeSettings,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,nativeSettings);}
        }finally{Files.deleteIfExists(temp);}
    }
}
