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

    // --- zachowanie
    public static boolean hideLive = true;
    public static boolean showBoxes = true;
    public static boolean autoStart = true;
    public static int startDelaySec = 5;
    public static boolean beep = true;
    public static boolean showMessage = true;

    // --- wyglad napisow
    public static float scale = 1.0f;
    public static boolean showLabels = true;
    public static boolean showNames = true;
    public static boolean showDistance = true;
    public static boolean showBar = true;
    public static boolean showProgress = true;
    public static boolean showShadow = true;
    public static int bgOpacity = 93;      // 0 = przezroczyste, 100 = pelne
    public static int bgColorIdx = 0;

    public static final int[] BG_COLORS = {0x15151C, 0x000000, 0x0E1A33, 0x24123D, 0x3A1218, 0x12301C};
    public static final String[] BG_NAMES = {"Ciemny", "Czarny", "Granat", "Fiolet", "Bordo", "Zieleń"};

    // pierwszy zaznaczony rog (jeszcze nie utworzony obszar)
    public static BlockPos pending = null;
    public static String pendingWorld = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Wszystko, co zapisuje sie do pliku / do configu. */
    public static class Data {
        List<Waypoint> waypoints = new ArrayList<>();
        float scale = 1.0f;
        boolean hideLive = true;
        boolean showBoxes = true;
        boolean autoStart = true;
        int startDelaySec = 5;
        boolean beep = true;
        boolean showMessage = true;
        boolean showLabels = true;
        boolean showNames = true;
        boolean showDistance = true;
        boolean showBar = true;
        boolean showProgress = true;
        boolean showShadow = true;
        int bgOpacity = 93;
        int bgColorIdx = 0;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("waypoint-timer-areas.json");
    }

    public static Data toData() {
        Data d = new Data();
        d.waypoints = list;
        d.scale = scale;
        d.hideLive = hideLive;
        d.showBoxes = showBoxes;
        d.autoStart = autoStart;
        d.startDelaySec = startDelaySec;
        d.beep = beep;
        d.showMessage = showMessage;
        d.showLabels = showLabels;
        d.showNames = showNames;
        d.showDistance = showDistance;
        d.showBar = showBar;
        d.showProgress = showProgress;
        d.showShadow = showShadow;
        d.bgOpacity = bgOpacity;
        d.bgColorIdx = bgColorIdx;
        return d;
    }

    /** Ustawia same ustawienia (bez listy obszarow). */
    public static void applySettings(Data d) {
        if (d.scale > 0) scale = d.scale;
        hideLive = d.hideLive;
        showBoxes = d.showBoxes;
        autoStart = d.autoStart;
        startDelaySec = Math.max(0, Math.min(60, d.startDelaySec));
        beep = d.beep;
        showMessage = d.showMessage;
        showLabels = d.showLabels;
        showNames = d.showNames;
        showDistance = d.showDistance;
        showBar = d.showBar;
        showProgress = d.showProgress;
        showShadow = d.showShadow;
        bgOpacity = Math.max(0, Math.min(100, d.bgOpacity));
        bgColorIdx = Math.max(0, Math.min(BG_COLORS.length - 1, d.bgColorIdx));
    }

    public static void resetLook() {
        scale = 1.0f;
        showLabels = true;
        showNames = true;
        showDistance = true;
        showBar = true;
        showProgress = true;
        showShadow = true;
        bgOpacity = 93;
        bgColorIdx = 0;
        save();
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Data d = GSON.fromJson(Files.readString(p), Data.class);
                if (d != null) {
                    if (d.waypoints != null) list = d.waypoints;
                    applySettings(d);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(toData()));
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

            // 1) odliczanie dobieglo konca -> powiadomienie
            if (w.endAt > 0 && now >= w.endAt && !w.notified) {
                w.notified = true;
                changed = true;
                if (now - w.endAt < 5000) {
                    if (beep) {
                        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.0f));
                    }
                    if (showMessage) {
                        mc.player.sendMessage(Text.literal("§a" + w.name + " – GOTOWE"), true);
                    }
                }
                if (w.repeat && w.durationSec > 0) {
                    long d = w.durationSec * 1000L;
                    w.endAt += d * ((now - w.endAt) / d + 1);
                    w.notified = false;
                }
            }

            // 2) generator jest zregenerowany (takze gdy jestes daleko)
            //    -> po opoznieniu mod sam zaczyna kolejne odliczanie
            if (autoStart && !w.repeat && w.durationSec > 0 && w.autoStartAt == 0 && w.isReady(now)) {
                if (w.state == 2 && !w.isLive(now)) {
                    w.state = 1;
                }
                if (w.state != 2) {
                    w.autoStartAt = now + startDelaySec * 1000L;
                    changed = true;
                }
            }

            // 3) start odliczania
            if (w.autoStartAt > 0 && now >= w.autoStartAt) {
                w.autoStartAt = 0;
                changed = true;
                if (autoStart && w.durationSec > 0 && w.state != 2) {
                    w.endAt = now + w.durationSec * 1000L;
                    w.notified = false;
                    w.autoCounting = true;
                }
            }
        }
        if (changed) save();
    }
}
