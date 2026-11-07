package pl.wptimer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Karty z czasem rysowane na ekranie w miejscu obszaru (widac je z kazdej odleglosci).
 * Teksty sa budowane tylko gdy sie zmienia (raz na sekunde), a nie w kazdej klatce.
 */
public class WaypointHud {
    /** Gotowe, zbuforowane teksty karty. */
    public static class Card {
        String name;
        int color;
        boolean bold;
        long sec = Long.MIN_VALUE;
        int distI = -1;
        OrderedText nameO, timerO, distO;
        int wn, wt, wd;
    }

    private static class Item {
        Waypoint w;
        float sx, sy;
        double dist;
    }

    private static final List<Item> items = new ArrayList<>();
    private static int used = 0;

    public static void begin() {
        used = 0;
    }

    public static void add(Waypoint w, float sx, float sy, double dist) {
        Item it;
        if (used < items.size()) {
            it = items.get(used);
        } else {
            it = new Item();
            items.add(it);
        }
        used++;
        it.w = w;
        it.sx = sx;
        it.sy = sy;
        it.dist = dist;
    }

    public static void draw(DrawContext dc) {
        if (used == 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.options.hudHidden) return;
        long t0 = System.nanoTime();
        TextRenderer tr = mc.textRenderer;
        long now = System.currentTimeMillis();
        float s = WaypointStore.scale;
        MatrixStack m = dc.getMatrices();

        for (int i = 0; i < used; i++) {
            Item it = items.get(i);
            m.push();
            m.translate(it.sx, it.sy, 0f);
            m.scale(s, s, 1f);
            drawCard(dc, tr, it.w, it.dist, now);
            m.pop();
        }
        Perf.hud(System.nanoTime() - t0);
    }

    private static void rr(DrawContext dc, int x1, int y1, int x2, int y2, int c) {
        if (x2 - x1 < 4 || y2 - y1 < 4) {
            dc.fill(x1, y1, x2, y2, c);
            return;
        }
        dc.fill(x1 + 1, y1, x2 - 1, y2, c);
        dc.fill(x1, y1 + 1, x2, y2 - 1, c);
    }

    /** Rysuje karte tak, ze dolny srodek strzalki jest w punkcie (0,0). */
    public static void drawCard(DrawContext dc, TextRenderer tr, Waypoint w, double dist, long now) {
        Card c = w.card;
        if (c == null) {
            c = new Card();
            w.card = c;
        }

        boolean ready = w.isReady(now);
        long sec = ready ? -1 : (w.endAt <= 0 ? -2 : (w.endAt - now + 999) / 1000);

        // nazwa - budowana tylko przy zmianie
        if (c.nameO == null || !w.name.equals(c.name) || w.color != c.color || w.bold != c.bold) {
            c.name = w.name;
            c.color = w.color;
            c.bold = w.bold;
            c.nameO = Text.literal(w.name)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(w.color)).withBold(w.bold))
                    .asOrderedText();
            c.wn = tr.getWidth(c.nameO);
        }

        // czas - budowany raz na sekunde
        if (c.timerO == null || sec != c.sec) {
            c.sec = sec;
            MutableText t = Text.literal("⌚ ").formatted(Formatting.WHITE);
            if (sec == -1) {
                t.append(Text.literal("Gotowy").formatted(Formatting.GREEN, Formatting.BOLD));
            } else if (sec == -2) {
                t.append(Text.literal("Brak czasu").formatted(Formatting.GRAY));
            } else {
                t.append(Text.literal("Regeneracja za ").formatted(Formatting.WHITE, Formatting.BOLD));
                t.append(Text.literal(Waypoint.fmt(sec)).formatted(Formatting.YELLOW, Formatting.BOLD));
            }
            c.timerO = t.asOrderedText();
            c.wt = tr.getWidth(c.timerO);
        }

        // dystans - budowany gdy zmieni sie pelny metr
        int di = (int) dist;
        if (c.distO == null || di != c.distI) {
            c.distI = di;
            c.distO = Text.literal(di + " m").formatted(Formatting.GRAY).asOrderedText();
            c.wd = tr.getWidth(c.distO);
        }

        boolean names = WaypointStore.showNames;
        boolean dists = WaypointStore.showDistance;
        boolean prog = WaypointStore.showProgress;
        boolean bar = WaypointStore.showBar;

        int w1 = names ? c.wn + (dists ? 10 + c.wd : 0) : c.wt + (dists ? 10 + c.wd : 0);
        int w2 = names ? c.wt : 0;
        int half = Math.max(w1, w2) / 2 + 6;
        int lines = names ? 2 : 1;
        int h = prog ? 12 + lines * 12 : 7 + lines * 12;
        int top = -h - 3;
        int bottom = -3;
        int accent = 0xFF000000 | w.statusColor();

        int a = Math.round(WaypointStore.bgOpacity * 2.55f);
        int bg = (a << 24) | (WaypointStore.BG_COLORS[WaypointStore.bgColorIdx] & 0xFFFFFF);

        // tlo karty (a = 0 -> przezroczyste)
        if (a > 0) {
            if (WaypointStore.showShadow) {
                rr(dc, -half - 1, top - 1, half + 1, bottom + 1, (Math.min(0x66, a / 2) << 24));
            }
            rr(dc, -half, top, half, bottom, bg);
            dc.fill(-3, bottom, 3, bottom + 1, bg);
            dc.fill(-2, bottom + 1, 2, bottom + 2, bg);
        }
        if (bar) dc.fill(-half + 1, top, half - 1, top + 2, accent);
        dc.fill(-1, bottom + (a > 0 ? 2 : 0), 1, bottom + 3, accent);

        // tresc
        int y1 = top + 5;
        if (dists) dc.drawTextWithShadow(tr, c.distO, half - 6 - c.wd, y1, 0xFFFFFFFF);
        if (names) {
            dc.drawTextWithShadow(tr, c.nameO, -half + 6, y1, 0xFFFFFFFF);
            dc.drawTextWithShadow(tr, c.timerO, -half + 6, y1 + 12, 0xFFFFFFFF);
        } else {
            dc.drawTextWithShadow(tr, c.timerO, -half + 6, y1, 0xFFFFFFFF);
        }

        // pasek postepu
        if (prog) {
            int bx1 = -half + 6, bx2 = half - 6, by = bottom - 7;
            dc.fill(bx1, by, bx2, by + 2, 0x55FFFFFF);
            if (ready) {
                dc.fill(bx1, by, bx2, by + 2, 0xFF55FF55);
            } else if (w.endAt > now && w.durationSec > 0) {
                double frac = Math.min(1.0, (w.endAt - now) / (w.durationSec * 1000.0));
                int fx = bx1 + (int) ((bx2 - bx1) * frac);
                dc.fill(bx1, by, fx, by + 2, 0xFFFFFF55);
            }
        }
    }
}
