package pl.wptimer;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 1) sprawdza czy w zaznaczonym obszarze sa bloki generatora (czerwony / bialy),
 * 2) czyta hologram "Generator ..." + "Regeneracja za ..." lezacy w obszarze.
 */
public class GeneratorScanner {
    public static final int MAX_VOLUME = 200000;

    private static final Pattern DAYS = Pattern.compile("(\\d+)\\s*d\\b");
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*(?:h|godz)\\b");
    private static final Pattern MINS = Pattern.compile("(\\d+)\\s*m(?:in)?\\b");
    private static final Pattern SECS = Pattern.compile("(\\d+)\\s*s\\b");

    private static class Line {
        final Vec3d pos;
        final String text;
        final Text raw;

        Line(Vec3d pos, String text, Text raw) {
            this.pos = pos;
            this.text = text;
            this.raw = raw;
        }
    }

    // ---------------------------------------------------------------- bloki

    /** Zapamietuje typy blokow, ktore teraz sa w obszarze (= bloki generatora). */
    public static void recordTypes(MinecraftClient mc, Waypoint w) {
        ClientWorld world = mc.world;
        if (world == null) return;
        Set<String> found = new TreeSet<>();
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int x = w.x1; x <= w.x2; x++) {
            for (int z = w.z1; z <= w.z2; z++) {
                if (world.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) == null) continue;
                for (int y = w.y1; y <= w.y2; y++) {
                    BlockState st = world.getBlockState(p.set(x, y, z));
                    if (st.isAir()) continue;
                    found.add(Registries.BLOCK.getId(st.getBlock()).toString());
                }
            }
        }
        w.setTypes(new ArrayList<>(found));
    }

    /** 0 = nieznany (niezaladowane chunki), 1 = sa bloki, 2 = brak blokow. */
    private static int checkState(ClientWorld world, Waypoint w) {
        Set<String> ts = w.typeSet();
        boolean unloaded = false;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int x = w.x1; x <= w.x2; x++) {
            for (int z = w.z1; z <= w.z2; z++) {
                if (world.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) == null) {
                    unloaded = true;
                    continue;
                }
                for (int y = w.y1; y <= w.y2; y++) {
                    BlockState st = world.getBlockState(p.set(x, y, z));
                    if (st.isAir()) continue;
                    if (ts.isEmpty() || ts.contains(Registries.BLOCK.getId(st.getBlock()).toString())) return 1;
                }
            }
        }
        return unloaded ? 0 : 2;
    }

    public static void scanBlocks(MinecraftClient mc, int tick) {
        if (mc.world == null || mc.player == null) return;
        String key = WaypointStore.currentKey(mc);
        long now = System.currentTimeMillis();
        boolean changed = false;
        int i = 0;
        for (Waypoint w : WaypointStore.list) {
            if (!key.equals(w.world)) continue;
            i++;
            if ((tick + i) % 20 != 0) continue;
            if (w.volume() > MAX_VOLUME) continue;
            int ns = checkState(mc.world, w);
            if (ns == 0 || ns == w.state) continue;
            int prev = w.state;
            w.state = ns;
            changed = true;
            if (prev == 1 && ns == 2) {
                // bloki zniknely -> zaczela sie regeneracja (jesli hologram nie podal czasu)
                w.autoStartAt = 0;
                w.autoCounting = false;
                if (!w.isLive(now) && w.durationSec > 0) {
                    w.endAt = now + w.durationSec * 1000L;
                    w.notified = false;
                }
            } else if (prev == 2 && ns == 1) {
                // bloki wrocily -> generator gotowy
                if (w.endAt > now) {
                    w.endAt = now;
                    w.notified = false;
                }
                w.autoCounting = false;
                w.autoStartAt = now + WaypointStore.startDelaySec * 1000L;
            }
        }
        if (changed) WaypointStore.save();
    }

    // ------------------------------------------------------------ hologramy

    private static long parseMs(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        int i = t.indexOf("za ");
        String rest = i >= 0 ? t.substring(i + 2) : t;
        long sec = 0;
        boolean any = false;
        Matcher m;
        m = DAYS.matcher(rest);
        if (m.find()) { sec += Long.parseLong(m.group(1)) * 86400; any = true; }
        m = HOURS.matcher(rest);
        if (m.find()) { sec += Long.parseLong(m.group(1)) * 3600; any = true; }
        m = MINS.matcher(rest);
        if (m.find()) { sec += Long.parseLong(m.group(1)) * 60; any = true; }
        m = SECS.matcher(rest);
        if (m.find()) { sec += Long.parseLong(m.group(1)); any = true; }
        return any ? sec * 1000L : -1;
    }

    private static boolean applyStyle(Text t, Waypoint w) {
        TextColor c = t.getStyle().getColor();
        boolean bold = t.getStyle().isBold();
        for (Text sib : t.getSiblings()) {
            Style s = sib.getStyle();
            if (c == null && s.getColor() != null) c = s.getColor();
            if (s.isBold()) bold = true;
        }
        int rgb = c != null ? c.getRgb() : 0xFFAA00;
        boolean diff = rgb != w.color || bold != w.bold;
        w.color = rgb;
        w.bold = bold;
        return diff;
    }

    private static double dist2(Vec3d p, double x, double y, double z) {
        double dx = p.x - x, dy = p.y - y, dz = p.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static void scanHolograms(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        String key = WaypointStore.currentKey(mc);
        boolean any = false;
        for (Waypoint w : WaypointStore.list) {
            if (key.equals(w.world)) { any = true; break; }
        }
        if (!any) return;

        List<Line> names = new ArrayList<>();
        List<Line> timers = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            Text n = e.getCustomName();
            if (n == null) continue;
            String s = n.getString().trim();
            if (s.isEmpty()) continue;
            String low = s.toLowerCase(Locale.ROOT);
            Line l = new Line(e.getPos(), s, n);
            if (low.contains("regeneracja")) timers.add(l);
            else if (low.contains("generator")) names.add(l);
        }
        if (names.isEmpty() && timers.isEmpty()) return;

        long now = System.currentTimeMillis();
        boolean changed = false;

        for (Waypoint w : WaypointStore.list) {
            if (!key.equals(w.world)) continue;

            // hologram "w obszarze" (z zapasem, bo wisi nad blokami)
            double minX = w.x1 - 2, maxX = w.x2 + 3;
            double minY = w.y1 - 2, maxY = w.y2 + 5;
            double minZ = w.z1 - 2, maxZ = w.z2 + 3;
            double cx = w.centerX(), cy = w.y2 + 1.5, cz = w.centerZ();

            Line timer = null;
            double bd = 1e18;
            for (Line t : timers) {
                Vec3d p = t.pos;
                if (p.x < minX || p.x > maxX || p.y < minY || p.y > maxY || p.z < minZ || p.z > maxZ) continue;
                double d = dist2(p, cx, cy, cz);
                if (d < bd) { bd = d; timer = t; }
            }

            Line name = null;
            bd = 1e18;
            for (Line n : names) {
                Vec3d p = n.pos;
                if (p.x < minX || p.x > maxX || p.y < minY || p.y > maxY || p.z < minZ || p.z > maxZ) continue;
                double d;
                if (timer != null) {
                    double dx = p.x - timer.pos.x, dz = p.z - timer.pos.z, dy = p.y - timer.pos.y;
                    if (Math.abs(dx) > 0.8 || Math.abs(dz) > 0.8 || Math.abs(dy) > 1.3) continue;
                    d = dx * dx + dy * dy + dz * dz;
                } else {
                    d = dist2(p, cx, cy, cz);
                }
                if (d < bd) { bd = d; name = n; }
            }

            if (name == null && timer == null) continue;

            boolean wasLive = w.isLive(now);
            w.liveAt = now;

            if (name != null) {
                if (!w.nameLocked && !name.text.equals(w.name)) { w.name = name.text; changed = true; }
                if (applyStyle(name.raw, w)) changed = true;
            }

            if (timer != null) {
                long ms = parseMs(timer.text);
                if (ms >= 0) {
                    w.autoCounting = false;
                    w.autoStartAt = 0;
                    long newEnd = now + ms;
                    if (w.endAt <= 0 || Math.abs(newEnd - w.endAt) > 1200) w.endAt = newEnd;
                    if (ms > 0) w.notified = false;
                    int sec = (int) ((ms + 999) / 1000);
                    if (sec > w.durationSec) { w.durationSec = sec; changed = true; }
                }
            } else {
                // jest nazwa generatora, ale nie ma linii "Regeneracja" -> gotowy
                if (w.endAt <= 0) {
                    w.endAt = now;
                    w.notified = true;
                } else if (w.autoCounting) {
                    // odliczanie wystartowal mod (po regeneracji) - nie przerywaj go
                    if (w.endAt <= now) w.autoCounting = false;
                } else if (wasLive && w.endAt > now) {
                    w.endAt = now;
                    w.notified = false;
                    w.autoStartAt = now + WaypointStore.startDelaySec * 1000L;
                }
            }
        }
        if (changed) WaypointStore.save();
    }
}
