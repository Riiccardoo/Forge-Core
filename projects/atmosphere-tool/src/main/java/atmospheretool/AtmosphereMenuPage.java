package atmospheretool;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.*;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

final class AtmosphereMenuPage extends InteractiveCustomUIPage<AtmosphereMenuPage.Data> {
    private final AtmosphereService service;
    private final String worldId;
    private String activeTab="Fog",savedId="",deleteSaveId;
    private String editingNumber;
    AtmosphereMenuPage(PlayerRef p,AtmosphereService service,String worldId) {
        super(p,CustomPageLifetime.CanDismiss,Data.CODEC);this.service=service;this.worldId=worldId;
    }
    static void open(Ref<EntityStore> ref,Store<EntityStore> store,PlayerRef p,AtmosphereService service) {
        if(service.isClosed())return;
        Player player=store.getComponent(ref,Player.getComponentType());
        if(player==null||player.getGameMode()!=GameMode.Creative)return;
        World w=store.getExternalData().getWorld();
        player.getPageManager().openCustomPage(ref,store,new AtmosphereMenuPage(p,service,WorldIdentity.key(w)));
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder ui,UIEventBuilder events,Store<EntityStore> store) {
        ui.append("Pages/AtmosphereToolPage.ui");
        refreshSaves(ui,true);
        values(ref,store,ui,true);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,"#SavedAtmospheres",
            new EventData().append("Action","SaveChoose").append("@SaveId","#SavedAtmospheres.Value"),false);
        for(String id:List.of("SaveNew","SaveRename"))events.addEventBinding(CustomUIEventBindingType.Activating,"#"+id,
            new EventData().append("Action",id).append("@SaveName","#SaveName.Value"),false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,"#GlobalPreset",
            new EventData().append("Action","GlobalPreset").append("@Style","#GlobalPreset.Value"),false);
        for(String id:ExactValue.RANGES.keySet()) {
            events.addEventBinding(CustomUIEventBindingType.ValueChanged,"#"+id,
                new EventData().append("Action",id).append("@Level","#"+id+".Value"),false);
            events.addEventBinding(CustomUIEventBindingType.FocusGained,"#"+id+"Input",
                new EventData().append("Action","EditValue").append("Setting",id),false);
            for(var event:List.of(CustomUIEventBindingType.Validating,CustomUIEventBindingType.FocusLost))
                events.addEventBinding(event,"#"+id+"Input",new EventData().append("Action","ExactValue")
                    .append("Setting",id).append("@NumberText","#"+id+"Input.Value"),false);
        }
        for(String id:List.of("GlobalColor")) events.addEventBinding(CustomUIEventBindingType.ValueChanged,"#"+id,
            new EventData().append("Action",id).append("@Color","#"+id+".Color"),false);
        for(String id:List.of("TabSaves","TabFog","TabSky","SaveApply","SaveDelete","SaveDeleteConfirm","SaveDeleteCancel","Look","Close","NightVanilla","NightMoonlit","NightBright","GlobalOn","GlobalOff","NativeFogReset","SkyReset","Reset",
            "MoonSlate","MoonSilver","MoonPearl","MoonWarmGray","MoonPaleGold","MoonGold","MoonAmber"))
            events.addEventBinding(CustomUIEventBindingType.Activating,"#"+id,new EventData().append("Action",id),false);
    }
    private void values(Ref<EntityStore> ref,Store<EntityStore> store,UICommandBuilder ui,boolean geometry) {
        for(String tab:List.of("Saves","Fog","Sky"))ui.set("#Panel"+tab+".Visible",tab.equals(activeTab));
        ui.set("#SaveDeletePrompt.Visible",deleteSaveId!=null);
        World w=store.getExternalData().getWorld();
        number(ui,"MoonSize",service.moonSize(w,store,ref));
        number(ui,"MoonTransparency",service.fogSettings(w,store,ref).moonTransparency);
        number(ui,"Moon",service.moonLevel(w,store,ref));
        number(ui,"SkyAngle",service.skyAngle(w,store,ref));
        number(ui,"SkyHeight",service.skyHeight(w,store,ref));
        int level=service.atmosphericFogLevel(w,store,ref);
        var fog=service.fogSettings(w,store,ref);
        number(ui,"GlobalDensity",fog.fogTouched?level:5);
        ui.set("#GlobalStatus.Text",!fog.fogTouched?"Global fog - current environment":
            "Global fog: "+(fog.fogEnabled?"ON":"OFF")+" / "+(fog.nativeWeatherFog?"Zone 1 Foggy Light":fog.fogPreset));
        number(ui,"GlobalTransparency",fog.fogTransparency);
        number(ui,"GlobalBrightness",fog.fogBrightness);
        ui.set("#GlobalColorMode.Text",!fog.fogTouched||fog.nativeWeatherFog&&!fog.nativeFogColorOverride?"Color: native day/night cycle (picker overrides it)":"Color: custom RGB");
        if(geometry){
            var presets=new ArrayList<com.hypixel.hytale.server.core.ui.DropdownEntryInfo>();
            for(String id:List.of("current",NativeWeatherFog.PRESET,"custom"))presets.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(
                com.hypixel.hytale.server.core.ui.LocalizableString.fromString(id.equals("current")?"Current environment":id.equals("custom")?"Custom": "Zone 1 Foggy Light (native)"),id));
            for(String id:service.fogPresetNames())presets.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(
                com.hypixel.hytale.server.core.ui.LocalizableString.fromString(id.substring(0,1).toUpperCase(Locale.ROOT)+id.substring(1)),id));
            ui.set("#GlobalPreset.Entries",presets);
        }
        ui.set("#GlobalPreset.Value",!fog.fogTouched?"current":fog.nativeWeatherFog?NativeWeatherFog.PRESET:fog.fogPreset);
        ui.set("#GlobalColor.Color",service.fogColor(w,store,ref));
    }
    private void refreshSaves(UICommandBuilder ui,boolean name) {
        var saves=service.customSaves().list();
        var entries=new ArrayList<com.hypixel.hytale.server.core.ui.DropdownEntryInfo>();
        entries.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(com.hypixel.hytale.server.core.ui.LocalizableString.fromString("Choose an atmosphere save"),""));
        for(var e:saves)entries.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(com.hypixel.hytale.server.core.ui.LocalizableString.fromString(e.name()),e.id()));
        var selected=saves.stream().filter(e->e.id().equals(savedId)).findFirst().orElse(null);
        if(selected==null){savedId="";deleteSaveId=null;}
        ui.set("#SavedAtmospheres.Entries",entries).set("#SavedAtmospheres.Value",savedId);
        ui.set("#SavedActions.Visible",selected!=null).set("#SaveDeletePrompt.Visible",deleteSaveId!=null);
        String error=service.customSaves().error();
        ui.set("#SavedStatus.Text",error!=null?"Custom saves unavailable; existing file preserved.":selected==null?saves.size()+" atmosphere saves":"Selected: "+selected.name());
        if(name)ui.set("#SaveName.Value",selected==null?"":selected.name());
    }
    private void handleSave(Data d,World w,Store<EntityStore> store,Ref<EntityStore> ref,UICommandBuilder ui) {
        switch(d.action) {
            case "SaveChoose" -> {savedId=d.saveId==null?"":d.saveId;if(!savedId.isEmpty())service.customSaves().get(savedId);ui.set("#Feedback.Text","Select Apply to use this atmosphere");}
            case "SaveNew" -> {savedId=service.saveCustom(d.saveName,w,store,ref).id();ui.set("#Feedback.Text","Current atmosphere saved");}
            case "SaveRename" -> {service.customSaves().rename(savedId,d.saveName);ui.set("#Feedback.Text","Save renamed");}
            case "SaveApply" -> {service.applyCustom(savedId,w,store,ref);ui.set("#Feedback.Text","Applying saved atmosphere");}
            case "SaveDelete" -> {service.customSaves().get(savedId);deleteSaveId=savedId;ui.set("#Feedback.Text","Confirm deletion below");}
            case "SaveDeleteConfirm" -> {
                if(deleteSaveId==null||!deleteSaveId.equals(savedId))throw new IllegalArgumentException("Choose Delete first");
                service.customSaves().delete(savedId);savedId="";deleteSaveId=null;ui.set("#Feedback.Text","Save deleted; current atmosphere kept");
            }
            case "SaveDeleteCancel" -> {deleteSaveId=null;ui.set("#Feedback.Text","Deletion cancelled");}
            default -> throw new IllegalArgumentException("Unknown save action");
        }
    }

    private void number(UICommandBuilder ui,String id,int value){
        ui.set("#"+id+".Value",value);
        if(!id.equals(editingNumber))ui.set("#"+id+"Input.Value",Integer.toString(value));
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data d) {
        if(service.isClosed()){close();return;}
        if(d.action==null||ref==null||!ref.isValid())return;
        World w=store.getExternalData().getWorld();Player player=store.getComponent(ref,Player.getComponentType());
        if(!worldId.equals(WorldIdentity.key(w))||player==null||player.getGameMode()!=GameMode.Creative){close();return;}
        UICommandBuilder update=new UICommandBuilder();
        try {
            if(!d.action.equals("SaveDeleteConfirm"))deleteSaveId=null;
            if(d.action.startsWith("Tab")&&List.of("Saves","Fog","Sky").contains(d.action.substring(3))) {
                activeTab=d.action.substring(3);editingNumber=null;refreshSaves(update,false);
                values(ref,store,update,false);sendUpdate(update,false);return;
            }
            if(d.action.startsWith("Save")) {
                handleSave(d,w,store,ref,update);refreshSaves(update,!d.action.equals("SaveDelete")&&!d.action.equals("SaveDeleteCancel"));
                values(ref,store,update,false);sendUpdate(update,false);return;
            }
            if(d.action.equals("EditValue")){
                if(ExactValue.RANGES.containsKey(d.setting))editingNumber=d.setting;
                sendUpdate(update,false);return;
            }
            if(d.action.equals("ExactValue")){
                d.level=ExactValue.parse(d.setting,d.numberText);d.action=d.setting;editingNumber=null;
            }
            if(ExactValue.RANGES.containsKey(d.action))ExactValue.checked(d.action,d.level);
            switch(d.action) {
                case "Close" -> { close();return; }
                case "Look" -> { close();return; }
                case "Moon" -> service.setMoonLevel(d.level,w,store,ref,null);
                case "MoonSize" -> service.setMoonSize(d.level,w,store,ref);
                case "MoonTransparency" -> service.setMoonTransparency(d.level,w,store,ref);
                case "SkyAngle","SkyHeight" -> service.setSkyOrbit(d.action,d.level,w,store,ref);
                case "SkyReset" -> service.resetSkyOrbit(w,store,ref);
                case "MoonSlate","MoonSilver","MoonPearl","MoonGold","MoonAmber" -> service.setMoonColor(d.action.substring(4).toLowerCase(Locale.ROOT),w,store,ref,null);
                case "MoonWarmGray" -> service.setMoonColor("warm-gray",w,store,ref,null);
                case "MoonPaleGold" -> service.setMoonColor("pale-gold",w,store,ref,null);
                case "NightVanilla","NightMoonlit","NightBright" -> service.setNightPreset(d.action.substring(5).toLowerCase(Locale.ROOT),w,store,ref,null);
                case "GlobalOn" -> service.setGlobalFogOn(w,store,ref);
                case "GlobalOff" -> service.setFogEnabled(false,w,store,ref,null);
                case "NativeFogReset" -> service.setNativeFog(w,store,ref);
                case "GlobalDensity" -> service.setAtmosphericFogLevel(d.level,w,store,ref,null);
                case "GlobalTransparency" -> service.setFogTransparency(d.level,w,store,ref);
                case "GlobalBrightness" -> service.setFogBrightness(d.level,w,store,ref);
                case "GlobalColor" -> service.setFogColor(AtmosphereService.parseHex(RgbValue.pickerColor(d.color)),w,store,ref,null);
                case "GlobalPreset" -> {
                    if(NativeWeatherFog.PRESET.equals(d.style))service.setNativeFog(w,store,ref);
                    else if(!"current".equals(d.style)&&!"custom".equals(d.style))service.setFogPreset(d.style,w,store,ref,null);
                }
                case "Reset" -> service.reset(w,store,null);
                default -> { return; }
            }
            update.set("#Feedback.Text","Saved");
        } catch(RuntimeException e) {
            String message=e.getMessage()==null?"Operation failed":e.getMessage();
            update.set("#Feedback.Text",message);playerRef.sendMessage(Message.raw("[Atmosphere] "+message));
        }
        // Update values only: rebuilding here would interrupt drags and duplicate bindings.
        values(ref,store,update,false);
        sendUpdate(update,false);
    }
    static final class Data {
        String action,color,style,setting,numberText,saveId,saveName;int level;
        static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("@SaveId",Codec.STRING),(d,v)->d.saveId=v,d->d.saveId).add()
            .append(new KeyedCodec<>("@SaveName",Codec.STRING),(d,v)->d.saveName=v,d->d.saveName).add()
            .append(new KeyedCodec<>("Setting",Codec.STRING),(d,v)->d.setting=v,d->d.setting).add()
            .append(new KeyedCodec<>("@NumberText",Codec.STRING),(d,v)->d.numberText=v,d->d.numberText).add()
            .append(new KeyedCodec<>("@Style",Codec.STRING),(d,v)->d.style=v,d->d.style).add()
            .append(new KeyedCodec<>("@Level",Codec.INTEGER),(d,v)->d.level=v,d->d.level).add()
            .append(new KeyedCodec<>("@Color",Codec.STRING),(d,v)->d.color=v,d->d.color).add().build();
    }
}
