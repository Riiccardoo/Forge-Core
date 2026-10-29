package atmospheretool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AtmosphereConfig {
    public Limits limits = new Limits();
    public String fallbackNightAmbientColor = "#8290A0";
    public String fallbackMoonColor = "#AABBEA";
    public Controls controls = new Controls();
    public Map<String, NightPreset> night = new LinkedHashMap<String, NightPreset>();
    public Map<String, FogPreset> fog = new LinkedHashMap<String, FogPreset>();

    static AtmosphereConfig read(Path path) throws IOException {
        try (BufferedReader bufferedReader = Files.newBufferedReader(path, StandardCharsets.UTF_8);){
            AtmosphereConfig atmosphereConfig = new Gson().fromJson((Reader)bufferedReader, AtmosphereConfig.class);
            if (atmosphereConfig == null || atmosphereConfig.limits == null || atmosphereConfig.night == null || atmosphereConfig.fog == null) {
                throw new IOException("Invalid atmosphere-presets.json");
            }
            Controls controls = new Controls();
            if (atmosphereConfig.controls == null) {
                atmosphereConfig.controls = controls;
            }
            if (atmosphereConfig.controls.atmosphericFogDensity == null || atmosphereConfig.controls.atmosphericFogDensity.length != 10) {
                atmosphereConfig.controls.atmosphericFogDensity = controls.atmosphericFogDensity;
            }
            if (atmosphereConfig.controls.moonIntensity == null || atmosphereConfig.controls.moonIntensity.length != 10) {
                atmosphereConfig.controls.moonIntensity = controls.moonIntensity;
            }
            if (atmosphereConfig.controls.moonColors == null || atmosphereConfig.controls.moonColors.isEmpty()) {
                atmosphereConfig.controls.moonColors = controls.moonColors;
            }
            if (!(atmosphereConfig.night.containsKey("vanilla") && atmosphereConfig.night.containsKey("moonlit") && atmosphereConfig.night.containsKey("bright") && atmosphereConfig.fog.containsKey("miasma"))) {
                throw new IOException("Required presets are missing");
            }
            return atmosphereConfig;
        }
    }

    static void write(Path path, AtmosphereConfig atmosphereConfig) throws IOException {
        Files.writeString(path, (CharSequence)new GsonBuilder().setPrettyPrinting().create().toJson(atmosphereConfig), StandardCharsets.UTF_8, new OpenOption[0]);
    }

    public static final class Controls {
        public double[] atmosphericFogDensity = new double[]{0.02, 0.04, 0.06, 0.08, 0.11, 0.15, 0.2, 0.27, 0.36, 0.48};
        public double[] moonIntensity = new double[]{0.55, 0.7, 0.82, 0.94, 1.05, 1.16, 1.28, 1.42, 1.58, 1.75};
        public Map<String, String> moonColors = Controls.moonPalette();



        private static Map<String, String> moonPalette() {
            LinkedHashMap<String, String> linkedHashMap = new LinkedHashMap<String, String>();
            linkedHashMap.put("slate", "#777D86");
            linkedHashMap.put("silver", "#A8B0BB");
            linkedHashMap.put("pearl", "#D8DCE2");
            linkedHashMap.put("warm-gray", "#B8B0A2");
            linkedHashMap.put("pale-gold", "#D5C79B");
            linkedHashMap.put("gold", "#E0BD70");
            linkedHashMap.put("amber", "#D69A51");
            return linkedHashMap;
        }
    }

    public static final class FogPreset {
        public boolean enabled = true;
        public String color = "#808080";
        public double density = 0.1;
        public double nearDistance = -96.0;
        public double farDistance = 384.0;
        public double heightFalloff = 2.0;
        public double luminance = 1.0;
    }

    public static final class Limits {
        public double moonIntensityMin = 0.0;
        public double moonIntensityMax = 2.0;
        public double ambientIntensityMin = 0.0;
        public double ambientIntensityMax = 2.0;
        public double fogDensityMin = 0.0;
        public double fogDensityMax = 1.2;
        public double fogNearMin = -384.0;
        public double fogNearMax = 0.0;
        public double fogFarMin = 1.0;
        public double fogFarMax = 1024.0;
        public double fogFalloffMin = 0.0;
        public double fogFalloffMax = 10.0;
        public double fogLuminanceMin = 0.0;
        public double fogLuminanceMax = 2.0;
    }

    public static final class NightPreset {
        public double moonIntensity = 1.0;
        public double ambientIntensity = 1.0;
        public String ambientColor;
        public String moonColor;
    }
}
