package pl.wptimer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WaypointStore {
    public static List<Waypoint> list = new ArrayList<>();
    public static float scale = 1.0f;
    public static boolean hideLive = true;
    public static boolean showBoxes = true;
    public static boolean autoStart = true;
    public static boolean showLabels = true;
    public static boolean showNames = true;
    public static boolean beep = true;
    public static int startDelaySec = 5;

    // pierwszy zaznaczony rog (jeszcze nie utworzony obszar)
    public static BlockPos pending = null;
    public static String pendingWorld = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class Data {
        List<Waypoint> waypoints = new ArrayList<>();
        float scale = 1.0f;
        boolean hideLive = true;
        boolean showBoxes = true;
        boolean autoStart = true;
        boolean showLabels = true;
        boolean showNames = true;
        boolean beep = true;
        int startDelaySec = 5;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("waypoint-timer-areas.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Data d = GSON.fromJson(Files.readString(p), Data.class);
                if (d != null) {
                    if (d.waypoints != null) list = d.waypoints;
                    if (d.scale > 0) scale = d.scale;
                    hideLive = d.hideLive;
                    showBoxes = d.showBoxes;
                    autoStart = d.autoStart;
                    showLabels = d.showLabels;
                    showNames = d.showNames;
                    beep = d.beep;
                    startDelaySec = Math.max(0, Math.min(60, d.startDelaySec));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Data d = new Data();
            d.waypoints = list;
            d.scale = scale;
            d.hideLive = hideLive;
            d.showBoxes = showBoxes;
            d.autoStart = autoStart;
            d.showLabels = showLabels;
            d.showNames = showNames;
            d.beep = beep;
            d.startDelaySec = startDelaySec;
            Files.writeString(path(), GSON.toJson(d));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String currentKey(MinecraftClient mc) {
        String srv = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "singleplayer";
        String dim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
        return srv + "|" + dim;
    }

    public static List<Waypoint> current(MinecraftClient mc) {
        String key = currentKey(mc);
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : list) if (key.equals(w.world)) out.add(w);
        return out;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        String key = currentKey(mc);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Waypoint w : list) {
            if (!key.equals(w.world)) continue;

            // po regeneracji generatora: po kilku sekundach mod sam zaczyna odliczanie jak hologram
            if (w.autoStartAt > 0 && now >= w.autoStartAt) {
                w.autoStartAt = 0;
                if (autoStart && w.durationSec > 0 && w.state != 2) {
                    w.endAt = now + w.durationSec * 1000L;
                    w.notified = false;
                    w.autoCounting = true;
                }
                changed = true;
            }

            if (w.endAt <= 0) continue;
            if (now >= w.endAt && !w.notified) {
                w.notified = true;
                changed = true;
                if (now - w.endAt < 5000) {
                    if (beep) {
                        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.0f));
                    }
                    mc.player.sendMessage(Text.literal("§a" + w.name + " – GOTOWE"), true);
                }
                if (w.repeat && w.durationSec > 0) {
                    long d = w.durationSec * 1000L;
                    w.endAt += d * ((now - w.endAt) / d + 1);
                    w.notified = false;
                }
            }
        }
        if (changed) save();
    }
}
