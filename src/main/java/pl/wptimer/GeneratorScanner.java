package pl.wptimer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Czyta hologramy generatorow (nazwa "Generator ..." + linia "Regeneracja za ...")
 * i synchronizuje z nich waypointy.
 */
public class GeneratorScanner {
    private static final Pattern DAYS = Pattern.compile("(\\d+)\\s*d\\b");
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*(?:h|godz)\\b");
    private static final Pattern MINS = Pattern.compile("(\\d+)\\s*m(?:in)?\\b");
    private static final Pattern SECS = Pattern.compile("(\\d+)\\s*s\\b");

    private static long lastSave = 0;

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

    public static void scan(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;

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
        if (names.isEmpty()) return;

        String key = WaypointStore.currentKey(mc);
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (Line n : names) {
            Line best = null;
            double bd = 1e9;
            for (Line t : timers) {
                double dx = t.pos.x - n.pos.x, dz = t.pos.z - n.pos.z, dy = t.pos.y - n.pos.y;
                if (Math.abs(dx) > 0.8 || Math.abs(dz) > 0.8 || Math.abs(dy) > 1.3) continue;
                double dd = Math.abs(dy) + Math.hypot(dx, dz);
                if (dd < bd) { bd = dd; best = t; }
            }

            int bx = (int) Math.floor(n.pos.x), by = (int) Math.floor(n.pos.y), bz = (int) Math.floor(n.pos.z);
            Waypoint w = null;
            for (Waypoint c : WaypointStore.list) {
                if (c.generator && key.equals(c.world)
                        && Math.abs(c.x - bx) <= 1 && Math.abs(c.z - bz) <= 1 && Math.abs(c.y - by) <= 3) {
                    w = c;
                    break;
                }
            }

            boolean isNew = false;
            if (w == null) {
                if (!WaypointStore.autoDetect) continue;
                w = new Waypoint();
                w.generator = true;
                w.world = key;
                w.x = bx;
                w.y = by;
                w.z = bz;
                w.durationSec = 0;
                w.notified = true;
                WaypointStore.list.add(w);
                isNew = true;
                changed = true;
            }

            boolean wasLive = w.isLive(now);
            if (!n.text.equals(w.name)) { w.name = n.text; changed = true; }
            if (applyStyle(n.raw, w)) changed = true;
            w.liveAt = now;

            if (best != null) {
                long ms = parseMs(best.text);
                if (ms >= 0) {
                    long newEnd = now + ms;
                    if (w.endAt <= 0 || Math.abs(newEnd - w.endAt) > 1200) {
                        w.endAt = newEnd;
                    }
                    if (ms > 0) w.notified = false;
                    int sec = (int) ((ms + 999) / 1000);
                    if (sec > w.durationSec) { w.durationSec = sec; changed = true; }
                }
            } else {
                // jest nazwa, ale nie ma linii "Regeneracja" -> generator gotowy
                if (isNew || w.endAt <= 0) {
                    w.endAt = now;
                    w.notified = true;
                } else if (wasLive && w.endAt > now) {
                    w.endAt = now;
                    w.notified = false;
                }
            }
        }

        if (changed || now - lastSave > 20000) {
            WaypointStore.save();
            lastSave = now;
        }
    }
}
