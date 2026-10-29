package atmospheretool;
import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

final class AtmosphereSettingsStore implements AutoCloseable {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private final Map<String,AtmosphereSettings> worlds=new LinkedHashMap<>();
    private final AtomicJsonFile file;
    AtmosphereSettingsStore(Path path) throws IOException {
        boolean recovered=false;
        if(Files.exists(path)) {
            JsonObject root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if(root.get("version").getAsInt()!=1)throw new IOException("Unsupported global atmosphere schema");
            for(var entry:root.getAsJsonObject("worlds").entrySet()) {
                AtmosphereSettings s=GSON.fromJson(entry.getValue(),AtmosphereSettings.class);
                recovered|=s.recoverRuntimeValues();s.validate();worlds.put(entry.getKey(),s);
            }
        }
        file=new AtomicJsonFile(path);
        if(recovered){
            Path backup=path.resolveSibling(path.getFileName()+".runtime-values.bak");
            if(!Files.exists(backup))Files.copy(path,backup);
            save();
        }
    }
    synchronized AtmosphereSettings get(String key){AtmosphereSettings s=worlds.get(key);if(s==null)return null;AtmosphereSettings copy=new AtmosphereSettings();s.copyTo(copy);return copy;}
    synchronized void put(String key,AtmosphereSettings s){AtmosphereSettings copy=new AtmosphereSettings();s.copyTo(copy);copy.validate();worlds.put(key,copy);save();}
    synchronized void remove(String key){worlds.remove(key);save();}
    private void save(){JsonObject root=new JsonObject();root.addProperty("version",1);root.add("worlds",GSON.toJsonTree(worlds));file.save(GSON.toJson(root));}
    public void close(){file.close();}
}
