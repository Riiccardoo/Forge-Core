package atmospheretool;
import com.hypixel.hytale.server.core.universe.world.World;
final class WorldIdentity {
    private WorldIdentity() {}
    static String key(World world){return world.getWorldConfig().getUuid().toString();}
}
