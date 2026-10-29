package atmospheretool;

import com.hypixel.hytale.server.core.plugin.*;
import com.hypixel.hytale.server.core.command.system.*;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.*;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Isolated server integration probe, excluded from the user JAR. */
public final class NativeOnlyProbe extends JavaPlugin {
    private AtmosphereService global;
    private final List<String> checks=new ArrayList<>();
    public NativeOnlyProbe(JavaPluginInit init){super(init);}
    @Override protected void setup(){
        try{
            var config=new AtmosphereConfig();config.fog.put("mist",new AtmosphereConfig.FogPreset());
            global=new AtmosphereService(config,getDataDirectory().resolve("global.json"),getIdentifier().toString());
            getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register("AtmosphereCycle",AtmosphereInteraction.class,AtmosphereInteraction.codec(global));
        }catch(Exception e){throw new IllegalStateException(e);}
        getCommandRegistry().registerCommand(new CommandBase("weatherqa","Test native weather controls"){
            @Override protected void executeSync(CommandContext context){
                try{probe();Files.writeString(Path.of("weather-qa-pass.txt"),String.join("\n",checks));}
                catch(Throwable t){try{var s=new java.io.StringWriter();t.printStackTrace(new java.io.PrintWriter(s));Files.writeString(Path.of("weather-qa-fail.txt"),s.toString());}catch(Exception ignored){}t.printStackTrace();}
            }
        });
    }
    @Override protected void shutdown(){if(global!=null)global.restoreAll();}
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);System.out.println("WEATHER_QA PASS "+label);}
    static JsonObject packet(Weather weather){var j=new Gson().toJsonTree(weather.toPacket()).getAsJsonObject();j.remove("id");j.remove("tagIndexes");return j;}
    interface Task{void run()throws Exception;}
    static void world(World w,Task task)throws Exception{var done=new java.util.concurrent.CompletableFuture<Void>();w.execute(()->{try{task.run();done.complete(null);}catch(Throwable e){done.completeExceptionally(e);}});done.get(15,java.util.concurrent.TimeUnit.SECONDS);}
    static double percentile(List<Double> v,double p){var sorted=new ArrayList<>(v);Collections.sort(sorted);return sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(sorted.size()*p)-1));}
    static Weather current(World w){return Weather.getAssetMap().getAsset(w.getEntityStore().getStore().getResource(WeatherResource.getResourceType()).getForcedWeatherIndex());}
    static double dayDensity(Weather weather){return weather!=null&&weather.getFogDensities()!=null&&weather.getFogDensities().length>7?weather.getFogDensities()[7].getValue():Double.NaN;}
    static void await(World w,java.util.function.Predicate<Weather> predicate)throws Exception{
        long end=System.nanoTime()+5_000_000_000L;
        do{boolean[] done={false};world(w,()->done[0]=predicate.test(current(w)));if(done[0])return;Thread.sleep(5);}while(System.nanoTime()<end);
        throw new AssertionError("Weather apply timeout");
    }
    void probe()throws Exception{
        World w=Universe.get().getDefaultWorld();check(w!=null,"isolated native world loaded");
        var source=NativeWeatherFog.asset();var original=packet(source);
        var nativeDefaults=new AtmosphereSettings();NativeWeatherFog.select(nativeDefaults,source);
        var fogBaseline=packet(global.buildWeather(source,nativeDefaults,"Atmosphere_QA_ExpectedFog"));
        world(w,()->w.getEntityStore().getStore().getResource(WeatherResource.getResourceType()).setForcedWeather("Zone1_Sunny"));
        var store=w.getEntityStore().getStore();
        world(w,()->{
            var item=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset("Atmosphere_Tool");
            check(item!=null&&"Atmosphere_Tool_Use".equals(item.getInteractions().get(com.hypixel.hytale.protocol.InteractionType.Secondary)),
                "loaded tool Secondary points at Atmosphere_Tool_Use");
            var root=com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction.getAssetMap().getAsset("Atmosphere_Tool_Use");
            check(root!=null&&root.getOperationMax()>0,"right-click root compiles into native operations");
            var interaction=com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.getAssetMap().getAsset(root.getInteractionIds()[0]);
            check(interaction instanceof AtmosphereInteraction,"loaded right-click interaction resolves to plugin codec");
            var serviceField=AtmosphereInteraction.class.getDeclaredField("service");serviceField.setAccessible(true);
            check(serviceField.get(interaction)==global,
                "loaded interaction retains active plugin services");
            var player=new com.hypixel.hytale.server.core.universe.PlayerRef(
                com.hypixel.hytale.server.core.universe.world.storage.EntityStore.REGISTRY.newHolder(),
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000123"),"MenuTest","en-US",null,null);
            var ui=new com.hypixel.hytale.server.core.ui.builder.UICommandBuilder();
            var events=new com.hypixel.hytale.server.core.ui.builder.UIEventBuilder();
            new AtmosphereMenuPage(player,global,WorldIdentity.key(w)).build(null,ui,events,store);
            Files.writeString(Path.of("menu-commands.json"),new GsonBuilder().setPrettyPrinting().create().toJson(ui.getCommands()));
            Files.writeString(Path.of("menu-events.json"),new GsonBuilder().setPrettyPrinting().create().toJson(events.getEvents()));
            check(ui.getCommands().length>10&&events.getEvents().length>10,"menu builds with native controls in real server");
            String bindings=new Gson().toJson(events.getEvents());
            check(bindings.contains("SaveNew")&&bindings.contains("SaveApply")&&bindings.contains("SaveRename")&&bindings.contains("SaveDeleteConfirm")&&!bindings.contains("TabZones")&&!bindings.contains("CreativeFullbright"),"organized menu binds saved atmospheres and excludes retired Fullbright");
            check(bindings.contains("MoonTransparency")&&bindings.contains("#MoonTransparencyInput"),"manual lunar slider and exact input are bound");
            check(bindings.contains("GlobalBrightness")&&bindings.contains("#GlobalBrightnessInput"),"brightness slider and exact input both bind on the server");
            check(bindings.contains("GlobalOn")&&bindings.contains("GlobalOff")&&!bindings.contains("BackgroundOn")&&!bindings.contains("BackgroundOff"),
                "menu binds only global fog switches with no retired background actions");
        });
        String key=WorldIdentity.key(w);
        if(!Files.exists(Path.of("weather-restart-stage.txt")))probeCustomSaves(w);
        if(Files.exists(Path.of("weather-restart-stage.txt"))){
            world(w,()->{
                var s=global.fogSettings(w,store,null);
                check(s.nativeWeatherFog&&s.nativeFogLevel==8&&s.fogTransparency==37&&s.fogBaseColor==0xABCDEF,"native settings survive an actual server restart");
                check(s.moonSize==175,"moon size persists across actual restart");
                check(s.savedAtmosphereApplied,"custom save apply intent survives actual server restart");
                check(s.fogBrightness==137,"fog brightness persists across actual server restart");
                check(global.customSaves().list().stream().anyMatch(e->e.name().equals("QA saved night")),"named custom saves survive an actual server restart");
                check(s.skyOrbitEnabled&&s.skyAngle==217&&s.skyHeight==63,"sky orbit persists across actual restart");
                global.restoreSavedWeather(w,store,null);
            });
            await(w,a->a!=null&&a.getId().startsWith("Atmosphere_Runtime_")&&Math.abs(dayDensity(a)-.85*1.24*.63)<.00001);
            check(true,"player-ready restoration path publishes saved native settings");
            var restoredColor=FogBrightness.apply(new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor[]{new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor(0,new com.hypixel.hytale.protocol.Color((byte)0xAB,(byte)0xCD,(byte)0xEF))},137);
            await(w,a->a!=null&&new Gson().toJson(a.getFogColors()).equals(new Gson().toJson(restoredColor)));
            check(true,"restart publishes the saved adjusted color as well as density");
            world(w,()->{
                int expected=MoonTransparency.apply(new MoonTransparency.Palette(source.getMoonColors(),source.getMoonGlowColors()),63,0xFFFFFF).moon()[0].getColor().alpha&255;
                int actual=current(w).getMoonColors()[0].getColor().alpha&255;
                check(actual==expected&&global.fogSettings(w,store,null).moonTransparency==63,"restart reapplies saved independent lunar transparency");
            });
            world(w,()->global.reset(w,store,null));
            await(w,a->a!=null&&a.getId().equals("Zone1_Sunny"));
            check(true,"restore original atmosphere returns the prior forced weather");
            world(w,()->check(!global.fogSettings(w,store,null).skyOrbitEnabled,"original atmosphere restores native sky path mode"));
            Files.writeString(Path.of("weather-second-boot-pass.txt"),"PASS restart and restoration");return;
        }
        world(w,()->{
            for(boolean enabled:new boolean[]{false,true}){
                JsonObject[] rendered=new JsonObject[2];
                for(int i=0;i<2;i++){
                    // Actual 2.2.5 JSON shape, including its retired background switch.
                    String json="{\"nativeWeatherFog\":true,\"fogTouched\":true,\"fogEnabled\":"+enabled+
                        ",\"backgroundFogEnabled\":"+(i==1)+",\"nativeFogLevel\":5}";
                    var saved=new Gson().fromJson(json,AtmosphereSettings.class);
                    var weather=global.buildWeather(source,saved,"Atmosphere_QA_LegacyGlobal");rendered[i]=packet(weather);
                    check(enabled?rendered[i].equals(fogBaseline):weather.getFogDensities()[0].getValue()==0&&weather.getFogColors().length==source.getSkyTopColors().length,
                        "legacy 2.2.5 settings obey global fog "+enabled+" with retired background "+(i==1));
                }
                check(rendered[0].equals(rendered[1]),"retired background flag cannot change global weather "+enabled);
            }
        });
        world(w,()->{global.setFogBrightness(100,w,store,null);check(!global.fogSettings(w,store,null).fogTouched,"neutral brightness does not activate a new weather override");});
        // First activation is the same visual Weather packet as the native preset,
        // apart from its runtime identity and asset tags.
        world(w,()->global.toggleGlobalFog(w,store,null));
        await(w,a->a!=null&&a.getId().startsWith("Atmosphere_Runtime_")&&packet(a).equals(fogBaseline));
        check(true,"first ON preserves native weather including original moon opacity");
        probeMoonTransparency(w,source);
        probeBrightness(w,source);
        world(w,()->global.setFogTransparency(50,w,store,null));
        await(w,a->a!=null&&Math.abs(dayDensity(a)-.425)<.00001);
        check(true,"transparency attenuates daytime native density by 50 percent");
        world(w,()->check(global.fogSettings(w,store,null).nativeFogLevel==5,"transparency leaves density selection intact"));
        world(w,()->global.setFogColor(0xABCDEF,w,store,null,null));
        await(w,a->a!=null&&a.getFogColors().length==1&&(a.getFogColors()[0].getColor().red&255)==0xAB);
        check(true,"RGB override reaches actual Weather asset");
        world(w,()->global.toggleGlobalFog(w,store,null));
        await(w,a->a!=null&&a.getFogOptions()!=null&&a.getFogOptions().getFogHeightCameraFixed()!=null&&a.getFogOptions().getFogHeightCameraFixed()==0&&a.getFogDistance()[0]==1000000);
        check(true,"OFF suppresses distance and height fog");
        world(w,()->{
            var off=current(w);var top=source.getSkyTopColors();
            check(off.getFogColors().length==top.length&&off.getSkyBottomColors().length==top.length,
                "OFF supplies authored day-night keyframes to the horizon and fog backdrop");
            for(int i=0;i<top.length;i++) {
                var a=top[i].getColor();var b=off.getSkyBottomColors()[i].getColor();var c=off.getFogColors()[i].getColor();
                check(top[i].getHour()==off.getFogColors()[i].getHour()&&top[i].getHour()==off.getSkyBottomColors()[i].getHour()
                    &&(b.alpha&255)==255&&a.red==b.red&&a.green==b.green&&a.blue==b.blue
                    &&a.red==c.red&&a.green==c.green&&a.blue==c.blue,
                    "OFF neutral horizon palette at native hour "+top[i].getHour());
            }
            var before=original.deepCopy();var after=packet(off);
            for(String field:List.of("fogColors","skyBottomColors","fogHeightFalloffs","fogDensities","fog","fogOptions")){
                before.remove(field);after.remove(field);
            }
            check(before.equals(after),"OFF leaves moon sunlight upper-sky sunset clouds stars water and effects unchanged");
            check(packet(source).equals(original),"OFF never mutates source weather arrays");
            Files.writeString(Path.of("fog-off-packet.json"),new GsonBuilder().setPrettyPrinting().create().toJson(off.toPacket()));
        });
        world(w,()->{global.setFogEnabled(false,w,store,null,null);global.setFogEnabled(false,w,store,null,null);
            var s=global.fogSettings(w,store,null);
            check(!s.fogEnabled&&s.fogTransparency==50&&s.fogBaseColor==0xABCDEF&&s.nativeFogLevel==5,
                "explicit OFF is idempotent and preserves density transparency and color");});
        world(w,()->{global.setAtmosphericFogLevel(8,w,store,null,null);check(!global.fogSettings(w,store,null).fogEnabled,"editing density while OFF does not reactivate fog");global.toggleGlobalFog(w,store,null);});
        await(w,a->a!=null&&Math.abs(dayDensity(a)-.85*1.24*.5)<.00001);
        check(true,"ON restores stored density transparency and color");
        world(w,()->{
            var on=packet(current(w));
            check(Objects.equals(on.get("skyBottomColors"),original.get("skyBottomColors"))
                &&Objects.equals(on.get("skyTopColors"),original.get("skyTopColors"))
                &&current(w).getFogColors().length==1&&(current(w).getFogColors()[0].getColor().red&255)==0xAB,
                "ON restores original horizon and stored custom fog color after OFF");
        });
        world(w,()->global.setFogTransparency(100,w,store,null));
        await(w,a->a!=null&&a.getFogDensities()[0].getValue()==0&&a.getFogDistance()[0]==1000000);
        check(true,"100 transparency is fully hidden without changing enabled state");
        world(w,()->global.setNativeFog(w,store,null));
        await(w,a->a!=null&&packet(a).equals(fogBaseline));check(true,"native reset restores weather curves without automatic lunar attenuation");
        world(w,()->{for(int i=1;i<=70;i++)global.setFogTransparency(i,w,store,null);});
        await(w,a->a!=null&&Math.abs(dayDensity(a)-.255)<.00001);
        check(true,"rapid slider edits converge to the newest revision");
        List<Double> ms=new ArrayList<>();
        for(int i=0;i<20;i++){
            int transparency=i+10;double expected=.85*(1-transparency/100.0);long start=System.nanoTime();
            world(w,()->global.setFogTransparency(transparency,w,store,null));
            await(w,a->a!=null&&Math.abs(dayDensity(a)-expected)<.00001);
            ms.add((System.nanoTime()-start)/1e6);
        }
        var metrics=new LinkedHashMap<String,Object>();metrics.put("scope","isolated server edit-to-registered-Weather; excludes client display latency");
        metrics.put("samplesMs",ms);metrics.put("p50Ms",percentile(ms,.5));metrics.put("p95Ms",percentile(ms,.95));
        Files.writeString(Path.of("weather-latency.json"),new GsonBuilder().setPrettyPrinting().create().toJson(metrics));
        world(w,()->{global.setFogTransparency(31,w,store,null);global.reset(w,store,null);});
        Thread.sleep(200);await(w,a->a!=null&&a.getId().equals("Zone1_Sunny"));
        check(true,"reset cancels pending application without reviving weather");
        check(packet(source).equals(original),"native source asset remains unchanged");

        world(w,()->{
            global.setFogPreset("mist",w,store,null,null);global.setFogDensity(0,w,store,null,null);
            check(global.atmosphericFogLevel(w,store,null)==0,"zero-density legacy profiles display zero without losing native control support");
        });
        world(w,()->{
            var originalSky=SkyOrbit.original(w);var before=global.fogSettings(w,store,null);
            global.setSkyOrbit("SkyAngle",217,w,store,null);global.setSkyOrbit("SkyHeight",63,w,store,null);
            check(global.skyAngle(w,store,null)==217&&global.skyHeight(w,store,null)==63,"sky orbit controls store exact angle and height");
            var after=global.fogSettings(w,store,null);
            check(before.fogBaseColor==after.fogBaseColor&&before.fogTransparency==after.fogTransparency&&originalSky.equals(SkyOrbit.original(w)),
                "sky edits leave fog and original world configuration untouched");
            global.resetSkyOrbit(w,store,null);
            check(!global.fogSettings(w,store,null).skyOrbitEnabled&&originalSky.equals(SkyOrbit.original(w)),"sky reset preserves native world settings");
        });
        // Persist a distinctive state for the second real server boot.
        world(w,()->{global.setNativeFog(w,store,null);global.setAtmosphericFogLevel(8,w,store,null,null);global.setFogTransparency(37,w,store,null);global.setFogColor(0xABCDEF,w,store,null,null);global.setFogBrightness(137,w,store,null);global.setMoonSize(175,w,store,null);global.setMoonTransparency(63,w,store,null);});
        world(w,()->{global.setSkyOrbit("SkyAngle",217,w,store,null);global.setSkyOrbit("SkyHeight",63,w,store,null);});
        await(w,a->a!=null&&Math.abs(dayDensity(a)-.85*1.24*.63)<.00001);
        world(w,()->check(Math.abs(current(w).getMoonScales()[0].getValue()-source.getMoonScales()[0].getValue()*1.75)<.0001,"moon scale reaches native asset"));
        world(w,()->{var saved=global.saveCustom("QA saved night",w,store,null);global.applyCustom(saved.id(),w,store,null);});
        await(w,a->a!=null&&Math.abs(dayDensity(a)-.85*1.24*.63)<.00001);
        Files.writeString(Path.of("weather-restart-stage.txt"),key);
    }
    void probeBrightness(World w,Weather source)throws Exception {
        var store=w.getEntityStore().getStore();var baseline=packet(current(w));var sourcePacket=packet(source);var gson=new Gson();
        world(w,()->global.setFogBrightness(150,w,store,null));
        String expected=gson.toJson(FogBrightness.apply(source.getFogColors(),150));
        await(w,a->a!=null&&gson.toJson(a.getFogColors()).equals(expected));
        world(w,()->{
            var bright=packet(current(w));var original=baseline.deepCopy();bright.remove("fogColors");original.remove("fogColors");
            check(bright.equals(original),"brightness changes only fog RGB; density transparency sky moon sunlight water and effects stay identical");
            check(packet(source).equals(sourcePacket),"brightness leaves shared native source immutable");
            var settings=global.fogSettings(w,store,null);
            check(!settings.nativeFogColorOverride&&settings.fogBrightness==150&&settings.nativeFogLevel==5&&settings.fogTransparency==0,"native palette mode and independent controls survive brightness edits");
            var samples=new LinkedHashMap<String,Object>();samples.put("scope","Weather RGB parameters only; not client screenshots or illumination measurements");
            samples.put("source",source.getFogColors());samples.put("brightness150",current(w).getFogColors());
            Files.writeString(Path.of("fog-brightness-sample.json"),new GsonBuilder().setPrettyPrinting().create().toJson(samples));
            for(int mode=0;mode<3;mode++) {
                var s=new AtmosphereSettings();NativeWeatherFog.select(s,source);
                if(mode==0)s.fogEnabled=false;else if(mode==1)s.fogTransparency=100;else s.nativeFogLevel=0;
                var off=packet(global.buildWeather(source,s,"test"));
                for(int b:new int[]{0,150,200}) {s.fogBrightness=b;check(packet(global.buildWeather(source,s,"test")).equals(off),"hidden global weather is unaffected by brightness "+b+" mode "+mode);}
            }
            var legacy=new AtmosphereSettings();legacy.fogTouched=true;legacy.fogEnabled=true;legacy.fogBaseColor=0x808080;legacy.fogLuminance=.5;legacy.fogDensity=.2;legacy.fogBrightness=150;
            check((global.buildWeather(source,legacy,"test").getFogColors()[0].getColor().red&255)==160,"legacy preset luminance composes with the new independent brightness");
        });
        world(w,()->global.setFogEnabled(false,w,store,null,null));
        await(w,a->a!=null&&a.getFogDistance()[0]==1000000);
        world(w,()->{
            global.setFogBrightness(137,w,store,null);var s=global.fogSettings(w,store,null);
            check(!s.fogEnabled&&s.fogBrightness==137&&s.nativeFogLevel==5&&s.fogTransparency==0,"editing brightness while OFF preserves OFF and saved density/transparency");
            global.setFogColor(0x204080,w,store,null,null);
            check(global.fogSettings(w,store,null).fogBrightness==137,"picker changes preserve brightness");
            global.setGlobalFogOn(w,store,null);
        });
        var color=new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor[]{new com.hypixel.hytale.server.core.asset.type.weather.config.TimeColor(0,new com.hypixel.hytale.protocol.Color((byte)0x20,(byte)0x40,(byte)0x80))};
        await(w,a->a!=null&&gson.toJson(a.getFogColors()).equals(gson.toJson(FogBrightness.apply(color,137))));
        check(true,"ON restores brightened custom color after OFF");
        world(w,()->global.setFogBrightness(100,w,store,null));
        await(w,a->a!=null&&gson.toJson(a.getFogColors()).equals(gson.toJson(color)));
        check(true,"neutral brightness restores exact stored picker color");
        world(w,()->global.setNativeFog(w,store,null));
        await(w,a->a!=null&&packet(a).equals(baseline));
        world(w,()->check(global.fogSettings(w,store,null).fogBrightness==100,"native reset returns brightness to neutral"));
        world(w,()->{for(int i=101;i<=180;i++)global.setFogBrightness(i,w,store,null);});
        await(w,a->a!=null&&gson.toJson(a.getFogColors()).equals(gson.toJson(FogBrightness.apply(source.getFogColors(),180))));
        check(true,"rapid brightness edits converge on the last revision");
        List<Double> ms=new ArrayList<>();
        for(int b=0;b<200;b+=10) {
            final int value=b;String wanted=gson.toJson(FogBrightness.apply(source.getFogColors(),value));long start=System.nanoTime();
            world(w,()->global.setFogBrightness(value,w,store,null));
            await(w,a->a!=null&&gson.toJson(a.getFogColors()).equals(wanted));ms.add((System.nanoTime()-start)/1e6);
        }
        Files.writeString(Path.of("fog-brightness-latency.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("scope","isolated server brightness edit to registered Weather; excludes client display and GPU", "samplesMs",ms,"p50Ms",percentile(ms,.5),"p95Ms",percentile(ms,.95))));
        world(w,()->global.setNativeFog(w,store,null));await(w,a->a!=null&&packet(a).equals(baseline));
    }

    static String lunar(Weather w){return new Gson().toJson(new MoonTransparency.Palette(w.getMoonColors(),w.getMoonGlowColors()));}
    void probeMoonTransparency(World w,Weather source)throws Exception {
        var store=w.getEntityStore().getStore();var gson=new Gson();var original=packet(source);
        world(w,()->{
            var s=new AtmosphereSettings();NativeWeatherFog.select(s,source);s.nativeFogLevel=10;s.moonSize=175;
            var dense=global.buildWeather(source,s,"qa");
            check(lunar(dense).equals(lunar(source)),"maximum native fog at zero manual transparency preserves original lunar opacity");
            s.moonTransparency=37;var manual=global.buildWeather(source,s,"qa");String wanted=lunar(manual);
            for(int mode=0;mode<6;mode++) {
                s.fogEnabled=true;s.nativeFogLevel=10;s.fogTransparency=0;s.fogBrightness=100;s.nativeWeatherFog=true;
                if(mode==0)s.nativeFogLevel=1;
                if(mode==1)s.fogEnabled=false;
                if(mode==2)s.fogTransparency=100;
                if(mode==3)s.nativeFogLevel=0;
                if(mode==4){s.fogBrightness=200;s.nativeFogColorOverride=true;s.fogBaseColor=0x123456;}
                if(mode==5){s.nativeWeatherFog=false;s.fogDensity=1.2;}
                check(lunar(global.buildWeather(source,s,"qa")).equals(wanted),"fog settings never change manual disk/halo opacity mode "+mode);
            }
            var p=packet(manual);var b=packet(dense);p.remove("moonColors");p.remove("moonGlowColors");b.remove("moonColors");b.remove("moonGlowColors");
            check(p.equals(b),"lunar control changes only moon and halo alpha, preserving sky weather and approved size");
            s.moonTransparency=100;var hidden=global.buildWeather(source,s,"qa");
            check(Arrays.stream(hidden.getMoonColors()).allMatch(k->(k.getColor().alpha&255)==0)&&Arrays.stream(hidden.getMoonGlowColors()).allMatch(k->(k.getColor().alpha&255)==0),"100 percent explicitly hides both disk and halo");
            Files.writeString(Path.of("moon-transparency-packets.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("source",source.toPacket(),"maximumFogZeroLunarTransparency",dense.toPacket(),"manual37",manual.toPacket(),"manual100",hidden.toPacket())));
        });
        world(w,()->global.setAtmosphericFogLevel(10,w,store,null,null));
        await(w,a->a!=null&&Math.abs(dayDensity(a)-.85*1.4)<.00001&&lunar(a).equals(lunar(source)));
        List<Double> samples=new ArrayList<>();
        for(int value:new int[]{0,25,50,75,100,0,37}) {
            var expected=MoonTransparency.apply(new MoonTransparency.Palette(source.getMoonColors(),source.getMoonGlowColors()),value,0xAABBEA);
            String wanted=gson.toJson(expected);long start=System.nanoTime();
            world(w,()->global.setMoonTransparency(value,w,store,null));
            await(w,a->a!=null&&lunar(a).equals(wanted)&&Math.abs(dayDensity(a)-.85*1.4)<.00001);samples.add((System.nanoTime()-start)/1e6);
        }
        check(true,"registered Weather restores controllable lunar opacity 0..100..0 at maximum fog density");
        world(w,()->global.setNativeFog(w,store,null));
        await(w,a->a!=null&&Math.abs(dayDensity(a)-.85)<.00001);
        world(w,()->check(global.fogSettings(w,store,null).moonTransparency==37,"native fog reset preserves manual lunar setting"));
        world(w,()->global.setMoonTransparency(0,w,store,null));
        await(w,a->a!=null&&lunar(a).equals(lunar(source)));
        check(packet(source).equals(original),"manual lunar control never mutates authored assets");
        Files.writeString(Path.of("moon-transparency-latency.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("scope","isolated server edit to registered Weather at maximum fog density; excludes client display", "samplesMs",samples,"p50Ms",percentile(samples,.5),"p95Ms",percentile(samples,.95))));
    }

    void probeCustomSaves(World w)throws Exception {
        var store=w.getEntityStore().getStore();var ids=new String[2];var gson=new Gson();var originalSky=SkyOrbit.original(w);
        world(w,()->{
            var initial=current(w);ids[0]=global.saveCustom("QA original",w,store,null).id();
            check(current(w)==initial&&!global.fogSettings(w,store,null).fogTouched,"saving a combination does not apply it or activate fog");
            global.setNativeFog(w,store,null);global.setFogColor(0x124578,w,store,null,null);
            global.setFogBrightness(153,w,store,null);global.setAtmosphericFogLevel(7,w,store,null,null);global.setFogTransparency(24,w,store,null);
            global.setMoonSize(173,w,store,null);global.setMoonTransparency(42,w,store,null);global.setSkyOrbit("SkyAngle",87,w,store,null);global.setSkyOrbit("SkyHeight",550,w,store,null);
            global.setFogEnabled(false,w,store,null,null);
            ids[1]=global.saveCustom("QA combination",w,store,null).id();
        });
        await(w,a->a!=null&&a.getFogDistance()[0]==1000000);
        world(w,()->{
            var old=current(w);global.customSaves().rename(ids[1],"QA renamed");
            check(current(w)==old,"renaming a save leaves applied weather untouched");
            global.setFogTransparency(1,w,store,null);global.setGlobalFogOn(w,store,null);
            global.applyCustom(ids[1],w,store,null);
            var saved=global.fogSettings(w,store,null);
            check(!saved.fogEnabled&&saved.fogTransparency==24&&saved.fogBrightness==153&&saved.fogBaseColor==0x124578&&saved.nativeFogLevel==7&&saved.moonSize==173&&saved.moonTransparency==42&&saved.skyAngle==87&&saved.effectiveSkyHeight()==550,"apply restores all selected global values including OFF and exact sky controls");
            var raw=com.google.gson.JsonParser.parseString(Files.readString(getDataDirectory().resolve("custom-atmosphere-saves.json"))).getAsJsonObject();
            check(!raw.toString().contains("originalWeatherId")&&!raw.toString().contains("creativeFullbright")&&!raw.toString().contains("Atmosphere_Runtime_"),"custom library excludes target restore IDs retired lighting and runtime assets");
        });
        await(w,a->a!=null&&a.getFogDistance()[0]==1000000);
        world(w,()->{
            var before=current(w);global.customSaves().delete(ids[1]);
            check(current(w)==before&&!global.fogSettings(w,store,null).fogEnabled,"deleting the applied save preserves current atmosphere");
            var missing=global.fogSettings(w,store,null);missing.baseWeatherId="Weather_That_Does_Not_Exist";
            var entry=global.customSaves().create("QA missing weather",missing);
            String prior=gson.toJson(global.fogSettings(w,store,null));
            try{global.applyCustom(entry.id(),w,store,null);throw new AssertionError("missing source accepted");}catch(IllegalArgumentException expected){}
            check(prior.equals(gson.toJson(global.fogSettings(w,store,null)))&&global.customSaves().get(entry.id())!=null,"missing source rejected before modifying active environment or deleting the saved definition");
            global.customSaves().delete(entry.id());global.applyCustom(ids[0],w,store,null);
        });
        await(w,a->a!=null&&packet(a).equals(packet(Weather.getAssetMap().getAsset("Zone1_Sunny"))));
        world(w,()->{
            check(global.fogSettings(w,store,null).savedAtmosphereApplied,"even an unedited source save has an explicit restart restore intent");
            global.customSaves().delete(ids[0]);global.reset(w,store,null);
        });
        await(w,a->a!=null&&a.getId().equals("Zone1_Sunny"));
        check(originalSky.equals(SkyOrbit.original(w)),"custom saves leave original world sky settings intact");
        world(w,()->check(!global.fogSettings(w,store,null).savedAtmosphereApplied,"original atmosphere restores pre-apply weather and clears snapshot restore intent"));
        // A portable save may come from another world's base weather. Reset must
        // keep this target world's original forced weather, including after reload.
        String[] plainId={null};
        world(w,()->{
            var plain=new AtmosphereSettings();plain.baseWeatherId="Zone1_Foggy_Light";
            plainId[0]=global.customSaves().create("QA unedited source",plain).id();
            global.applyCustom(plainId[0],w,store,null);
        });
        await(w,a->a!=null&&packet(a).equals(packet(NativeWeatherFog.asset())));
        world(w,()->{
            var state=global.fogSettings(w,store,null);
            check(!state.fogTouched&&!state.nightTouched&&state.moonSize==100&&state.savedAtmosphereApplied,"unedited save forces its own source without enabling additional edits");
            check("Zone1_Sunny".equals(state.originalWeatherId),"different saved source cannot replace target world restoration identity");
            global.unload(w);
            store.getResource(WeatherResource.getResourceType()).setForcedWeather("Zone1_Sunny");
            global.restoreSavedWeather(w,store,null);
        });
        await(w,a->a!=null&&packet(a).equals(packet(NativeWeatherFog.asset())));
        world(w,()->{global.reset(w,store,null);global.customSaves().delete(plainId[0]);});
        await(w,a->a!=null&&a.getId().equals("Zone1_Sunny"));
        check(true,"unedited custom atmosphere restores after reload; Reset returns target original weather rather than the saved source");
        world(w,()->global.setMoonTransparency(100,w,store,null));
        await(w,a->a!=null&&a.getMoonColors()!=null&&Arrays.stream(a.getMoonColors()).allMatch(k->(k.getColor().alpha&255)==0));
        world(w,()->{
            var s=global.fogSettings(w,store,null);
            check(!s.fogTouched&&!s.nightTouched&&s.moonSize==100,"editing only lunar transparency leaves fog and night controls untouched");
            global.unload(w);store.getResource(WeatherResource.getResourceType()).setForcedWeather("Zone1_Sunny");
            global.restoreSavedWeather(w,store,null);
        });
        await(w,a->a!=null&&a.getMoonColors()!=null&&Arrays.stream(a.getMoonColors()).allMatch(k->(k.getColor().alpha&255)==0));
        world(w,()->global.reset(w,store,null));
        await(w,a->a!=null&&a.getId().equals("Zone1_Sunny"));
        world(w,()->check(global.fogSettings(w,store,null).moonTransparency==0,"lunar-only setting restores after reload and original reset clears it"));
        Files.writeString(Path.of("custom-saves-checks.txt"),"PASS create/select snapshot/rename/apply/delete/missing-source/restore; client UI acceptance still pending");
    }

}
