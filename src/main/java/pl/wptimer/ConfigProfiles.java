package pl.wptimer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Zapisane configi: obszary + wszystkie ustawienia. Pliki: config/waypoint-timer/<nazwa>.json */
public class ConfigProfiles {
    public static class Info {
        public final String name;
        public final int areas;
        public final long modified;

        Info(String name, int areas, long modified) {
            this.name = name;
            this.areas = areas;
            this.modified = modified;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path dir() {
        Path d = FabricLoader.getInstance().getConfigDir().resolve("waypoint-timer");
        try {
            Files.createDirectories(d);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return d;
    }

    public static String clean(String raw) {
        String s = raw == null ? "" : raw.trim().replaceAll("[^\\p{L}\\p{N} _\\-]", "");
        if (s.length() > 24) s = s.substring(0, 24);
        return s.trim();
    }

    private static Path file(String name) {
        return dir().resolve(name + ".json");
    }

    public static List<Info> list() {
        List<Info> out = new ArrayList<>();
        try (Stream<Path> st = Files.list(dir())) {
            st.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    String fn = p.getFileName().toString();
                    String name = fn.substring(0, fn.length() - 5);
                    WaypointStore.Data d = GSON.fromJson(Files.readString(p), WaypointStore.Data.class);
                    int n = d != null && d.waypoints != null ? d.waypoints.size() : 0;
                    out.add(new Info(name, n, Files.getLastModifiedTime(p).toMillis()));
                } catch (Exception ignored) {
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        out.sort((a, b) -> Long.compare(b.modified, a.modified));
        return out;
    }

    public static boolean save(String name) {
        try {
            Files.writeString(file(name), GSON.toJson(WaypointStore.toData()));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean delete(String name) {
        try {
            return Files.deleteIfExists(file(name));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * @param merge  true = dolacz obszary z configu do obecnych (bez zmiany ustawien),
     *               false = zastap obszary i ustawienia
     * @param remap  true = przypisz wczytane obszary do obecnego swiata/serwera
     */
    public static boolean load(String name, boolean remap, String worldKey, boolean merge) {
        try {
            Path p = file(name);
            if (!Files.exists(p)) return false;
            WaypointStore.Data d = GSON.fromJson(Files.readString(p), WaypointStore.Data.class);
            if (d == null) return false;

            List<Waypoint> incoming = d.waypoints != null ? d.waypoints : new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Waypoint w : incoming) {
                if (w.world == null) w.world = "";
                if (remap) w.world = worldKey;
                if (w.endAt <= now) w.notified = true;   // bez pikania po wczytaniu
                w.autoStartAt = 0;
            }

            if (merge) {
                WaypointStore.list.addAll(incoming);
            } else {
                WaypointStore.applySettings(d);
                WaypointStore.list = incoming;
            }
            WaypointStore.invalidate();
            WaypointStore.save();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
