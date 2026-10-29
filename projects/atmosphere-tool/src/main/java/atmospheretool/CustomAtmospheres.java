package atmospheretool;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

/** Named global presets, committed before acknowledging UI actions. */
final class CustomAtmospheres {
    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER=com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();
    static final int LIMIT=256;
    record Entry(String id,String name,AtmosphereSettings settings) {}
    private static final Gson JSON=new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private final Path path;
    private Map<String,Entry> entries=new LinkedHashMap<>();
    private String failure;
    CustomAtmospheres(Path path) {
        this.path=path;
        if(!Files.exists(path))return;
        try {
            if(Files.size(path)>4_194_304)throw new IOException("Saved atmosphere file is too large");
            JsonObject root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            int version=root.get("version").getAsInt();
            if(version!=1&&version!=2)throw new IOException("Unsupported saves version");
            var loaded=new LinkedHashMap<String,Entry>();var names=new HashSet<String>();
            for(JsonElement element:root.getAsJsonArray("saves")) {
                JsonObject item=element.getAsJsonObject();String id=item.get("id").getAsString();
                if(!UUID.fromString(id).toString().equals(id))throw new IOException("Invalid saved ID");
                String name=name(item.get("name").getAsString());
                if(loaded.containsKey(id)||!names.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Duplicate saved atmosphere");
                loaded.put(id,new Entry(id,name,AtmosphereSnapshot.decode(item.getAsJsonObject("settings"),version)));
            }
            if(loaded.size()>LIMIT)throw new IOException("Too many saved atmospheres");
            entries=loaded;
        } catch(IOException|RuntimeException e) {
            // Preserve damaged/future files, keep the rest of the tool usable.
            failure="Custom saves could not be loaded. Existing file preserved; see server log.";
            LOGGER.at(java.util.logging.Level.WARNING).withCause(e).log("Cannot load custom atmosphere saves: %s",path);
        }
    }
    synchronized String error(){return failure;}
    private void ready(){if(failure!=null)throw new IllegalStateException(failure);}
    synchronized List<Entry> list(){return entries.values().stream().sorted(Comparator.comparing(Entry::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id)).map(CustomAtmospheres::copy).toList();}
    synchronized Entry get(String id){ready();Entry entry=entries.get(id);if(entry==null)throw new IllegalArgumentException("Choose an existing atmosphere save");return copy(entry);}
    synchronized Entry create(String name,AtmosphereSettings settings) {
        ready();name=name(name);unique(name,null);
        if(entries.size()>=LIMIT)throw new IllegalArgumentException("At most 256 atmosphere saves; delete one before adding another");
        Entry e=new Entry(UUID.randomUUID().toString(),name,AtmosphereSnapshot.copy(settings));
        var next=new LinkedHashMap<>(entries);next.put(e.id,e);commit(next);return copy(e);
    }
    synchronized Entry rename(String id,String name) {
        ready();Entry old=get(id);name=name(name);unique(name,id);
        if(old.name.equals(name))return old;
        Entry e=new Entry(id,name,old.settings);var next=new LinkedHashMap<>(entries);next.put(id,e);commit(next);return copy(e);
    }
    synchronized void delete(String id){ready();get(id);var next=new LinkedHashMap<>(entries);next.remove(id);commit(next);}
    private void unique(String name,String except){if(entries.values().stream().anyMatch(e->!e.id.equals(except)&&e.name.equalsIgnoreCase(name)))throw new IllegalArgumentException("That name already exists; choose a different name");}
    static String name(String value) {
        if(value==null)throw new IllegalArgumentException("Enter a save name");
        String name=Normalizer.normalize(value.strip(),Normalizer.Form.NFC);
        if(name.isEmpty()||name.length()>64||name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Save names must contain 1 to 64 characters, without line breaks");
        return name;
    }
    private static Entry copy(Entry e){return new Entry(e.id,e.name,AtmosphereSnapshot.copy(e.settings));}
    private void commit(Map<String,Entry> next) {
        JsonObject root=new JsonObject();root.addProperty("version",2);JsonArray saves=new JsonArray();
        for(Entry e:next.values()) {
            JsonObject item=new JsonObject();item.addProperty("id",e.id);item.addProperty("name",e.name);
            item.add("settings",AtmosphereSnapshot.encode(e.settings));saves.add(item);
        }
        root.add("saves",saves);
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Path temp=path.resolveSibling(path.getFileName()+".tmp");
            byte[] bytes=JSON.toJson(root).getBytes(StandardCharsets.UTF_8);
            try(FileChannel out=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())out.write(b);out.force(true);
            }
            if(Files.exists(path))Files.copy(path,path.resolveSibling(path.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
            entries=next;
        } catch(IOException e) {throw new IllegalStateException("Could not write atmosphere saves. No changes accepted.",e);}
    }
}
