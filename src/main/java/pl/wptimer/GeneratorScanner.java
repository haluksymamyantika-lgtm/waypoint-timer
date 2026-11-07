package pl.wptimer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jeden przebieg po encjach (tylko tych w poblizu zaznaczonych obszarow):
 *  - lewitujacy blok-hologram nad generatorem (czerwony / bialy),
 *  - hologram "Generator ..." + "Regeneracja za ..." (nazwa i czas).
 */
public class GeneratorScanner {
    public static final int MAX_VOLUME = 200000;

    private static final Pattern DAYS = Pattern.compile("(\\d+)\\s*d\\b");
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*(?:h|godz)\\b");
    private static final Pattern MINS = Pattern.compile("(\\d+)\\s*m(?:in)?\\b");
    private static final Pattern SECS = Pattern.compile("(\\d+)\\s*s\\b");

    private static final Map<Item, String> ARMOR_SIG = new IdentityHashMap<>();
    private static final Map<Item, String> ITEM_SIG = new IdentityHashMap<>();

    private static class Line {
        final double x, y, z;
        final String text;
        final Text raw;
        final String sig;

        Line(double x, double y, double z, String text, Text raw, String sig) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.text = text;
            this.raw = raw;
            this.sig = sig;
        }
    }

    // listy uzywane ponownie (bez alokacji kontenerow w kazdym skanie)
    private static final List<Line> names = new ArrayList<>();
    private static final List<Line> timers = new ArrayList<>();
    private static final List<Line> floats = new ArrayList<>();

    // ------------------------------------------------- lewitujacy blok (hologram)

    /** Podpis lewitujacego "bloku" (encja), albo null jesli encja to nie jest lewitujacy blok/przedmiot. */
    private static String floatSig(Entity e) {
        EntityType<?> t = e.getType();
        if (t == EntityType.BLOCK_DISPLAY) return "block_display";
        if (t == EntityType.ITEM_DISPLAY) return "item_display";
        if (e instanceof ArmorStandEntity as) {
            ItemStack st = as.getEquippedStack(EquipmentSlot.HEAD);
            if (st.isEmpty()) st = as.getEquippedStack(EquipmentSlot.MAINHAND);
            if (st.isEmpty()) return null;
            Item it = st.getItem();
            String s = ARMOR_SIG.get(it);
            if (s == null) {
                s = "armor_stand:" + Registries.ITEM.getId(it);
                ARMOR_SIG.put(it, s);
            }
            return s;
        }
        if (e instanceof ItemEntity ie && e.hasNoGravity()) {
            Item it = ie.getStack().getItem();
            String s = ITEM_SIG.get(it);
            if (s == null) {
                s = "item:" + Registries.ITEM.getId(it);
                ITEM_SIG.put(it, s);
            }
            return s;
        }
        return null;
    }

    private static boolean inRegion(Waypoint w, double x, double y, double z) {
        return x >= w.x1 - 0.5 && x <= w.x2 + 1.5
                && z >= w.z1 - 0.5 && z <= w.z2 + 1.5
                && y >= w.y1 - 1 && y <= w.y2 + 4;
    }

    /** Zapamietuje, jaki lewitujacy blok jest teraz nad generatorem (= znacznik generatora). */
    public static void recordTypes(MinecraftClient mc, Waypoint w) {
        if (mc.world == null) return;
        Set<String> found = new TreeSet<>();
        for (Entity e : mc.world.getEntities()) {
            String sig = floatSig(e);
            if (sig != null && inRegion(w, e.getX(), e.getY(), e.getZ())) found.add(sig);
        }
        w.setTypes(new ArrayList<>(found));
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

    private static double d2(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean processHologram(Waypoint w, long now) {
        // hologram "w obszarze" (z zapasem, bo wisi nad blokami)
        double minX = w.x1 - 2, maxX = w.x2 + 3;
        double minY = w.y1 - 2, maxY = w.y2 + 5;
        double minZ = w.z1 - 2, maxZ = w.z2 + 3;
        double cx = w.centerX(), cy = w.y2 + 1.5, cz = w.centerZ();

        Line timer = null;
        double bd = 1e18;
        for (int i = 0; i < timers.size(); i++) {
            Line t = timers.get(i);
            if (t.x < minX || t.x > maxX || t.y < minY || t.y > maxY || t.z < minZ || t.z > maxZ) continue;
            double d = d2(t.x, t.y, t.z, cx, cy, cz);
            if (d < bd) { bd = d; timer = t; }
        }

        Line name = null;
        bd = 1e18;
        for (int i = 0; i < names.size(); i++) {
            Line n = names.get(i);
            if (n.x < minX || n.x > maxX || n.y < minY || n.y > maxY || n.z < minZ || n.z > maxZ) continue;
            double d;
            if (timer != null) {
                double dx = n.x - timer.x, dz = n.z - timer.z, dy = n.y - timer.y;
                if (Math.abs(dx) > 0.8 || Math.abs(dz) > 0.8 || Math.abs(dy) > 1.3) continue;
                d = dx * dx + dy * dy + dz * dz;
            } else {
                d = d2(n.x, n.y, n.z, cx, cy, cz);
            }
            if (d < bd) { bd = d; name = n; }
        }

        if (name == null && timer == null) return false;

        boolean changed = false;
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
                if (w.endAt <= now) w.autoCounting = false;
            } else if (wasLive && w.endAt > now) {
                w.endAt = now;
                w.notified = false;
                w.autoStartAt = now + WaypointStore.startDelaySec * 1000L;
            }
        }
        return changed;
    }

    private static boolean processFloating(MinecraftClient mc, Waypoint w, long now) {
        Set<String> ts = w.typeSet();
        boolean found = false;
        for (int i = 0; i < floats.size(); i++) {
            Line f = floats.get(i);
            if (!inRegion(w, f.x, f.y, f.z)) continue;
            if (ts.isEmpty() || ts.contains(f.sig)) { found = true; break; }
        }

        int ns;
        if (found) {
            w.absentSince = 0;
            ns = 1;
        } else {
            // "brak" stwierdzamy tylko gdy jestes blisko (encje zaladowane) i nie tuz po wejsciu do swiata
            double dx = w.centerX() - mc.player.getX();
            double dy = w.y2 + 1 - mc.player.getY();
            double dz = w.centerZ() - mc.player.getZ();
            boolean near = dx * dx + dy * dy + dz * dz < 48 * 48;
            if (!near || mc.player.age < 100) {
                w.absentSince = 0;
                return false;
            }
            if (w.absentSince == 0) w.absentSince = now;
            if (now - w.absentSince < 1500) return false;
            ns = 2;
        }

        if (ns == w.state) return false;
        int prev = w.state;
        w.state = ns;

        if (prev == 1 && ns == 2) {
            // lewitujacy blok zniknal -> zaczela sie regeneracja (jesli hologram nie podal czasu)
            w.autoStartAt = 0;
            w.autoCounting = false;
            if (!w.isLive(now) && w.durationSec > 0) {
                w.endAt = now + w.durationSec * 1000L;
                w.notified = false;
            }
        } else if (prev == 2 && ns == 1) {
            // lewitujacy blok wrocil -> generator gotowy
            if (w.endAt > now) {
                w.endAt = now;
                w.notified = false;
            }
            w.autoCounting = false;
            w.autoStartAt = now + WaypointStore.startDelaySec * 1000L;
        }
        return true;
    }

    // ---------------------------------------------------------------- skan

    public static void scan(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        List<Waypoint> act = WaypointStore.active(mc);
        if (act.isEmpty()) return;
        long t0 = System.nanoTime();

        // obwiednia wszystkich obszarow - encje poza nia sa odrzucane od razu
        double minX = 1e18, minY = 1e18, minZ = 1e18, maxX = -1e18, maxY = -1e18, maxZ = -1e18;
        for (int i = 0; i < act.size(); i++) {
            Waypoint w = act.get(i);
            minX = Math.min(minX, w.x1 - 3);
            minY = Math.min(minY, w.y1 - 3);
            minZ = Math.min(minZ, w.z1 - 3);
            maxX = Math.max(maxX, w.x2 + 4);
            maxY = Math.max(maxY, w.y2 + 6);
            maxZ = Math.max(maxZ, w.z2 + 4);
        }

        names.clear();
        timers.clear();
        floats.clear();
        for (Entity e : mc.world.getEntities()) {
            double x = e.getX(), y = e.getY(), z = e.getZ();
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) continue;

            String sig = floatSig(e);
            if (sig != null) floats.add(new Line(x, y, z, null, null, sig));

            if (e.hasCustomName()) {
                Text n = e.getCustomName();
                if (n != null) {
                    String s = n.getString().trim();
                    if (!s.isEmpty()) {
                        String low = s.toLowerCase(Locale.ROOT);
                        if (low.contains("regeneracja")) timers.add(new Line(x, y, z, s, n, null));
                        else if (low.contains("generator")) names.add(new Line(x, y, z, s, n, null));
                    }
                }
            }
        }

        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = 0; i < act.size(); i++) {
            Waypoint w = act.get(i);
            if (processHologram(w, now)) changed = true;
            if (processFloating(mc, w, now)) changed = true;
        }
        if (changed) WaypointStore.save();

        Perf.scan(System.nanoTime() - t0);
    }
}
