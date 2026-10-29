package atmospheretool;

import com.hypixel.hytale.protocol.packets.world.UpdateSunSettings;
import com.hypixel.hytale.server.core.universe.world.ClientEffectWorldSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Objects;

/** Official sky-path packet. This is not an independent moon-position API. */
final class SkyOrbit {
    static UpdateSunSettings original(World w) {
        var effects=w.getWorldConfig().getClientEffects();
        return (effects==null?new ClientEffectWorldSettings():effects).createSunSettingsPacket();
    }
    static UpdateSunSettings packet(int angle,int height) {
        if(angle<0||angle>359||height<0||height>1000)
            throw new IllegalArgumentException("Sky angle: 0..359 degrees; height: 0..1000%");
        return new UpdateSunSettings(height,(float)Math.toRadians(angle));
    }
    static int angle(World w){return Math.floorMod(Math.round((float)Math.toDegrees(original(w).angleRadians)),360);}
    static int height(World w){return Math.max(0,Math.min(1000,Math.round(original(w).heightPercentage)));}
    static void apply(World w,AtmosphereSettings settings){
        send(w,settings.skyOrbitEnabled?packet(settings.skyAngle,settings.effectiveSkyHeight()):original(w));
    }
    static void restore(World w){send(w,original(w));}
    private static void send(World w,UpdateSunSettings packet){
        for(var p:w.getPlayerRefs())if(Objects.equals(p.getWorldUuid(),w.getWorldConfig().getUuid()))
            p.getPacketHandler().writeNoCache(packet);
    }
}
