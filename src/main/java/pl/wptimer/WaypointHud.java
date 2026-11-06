package pl.wptimer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Karty z czasem rysowane na ekranie w miejscu obszaru:
 * widac je z kazdej odleglosci i przez sciany.
 */
public class WaypointHud {
    private static class Item {
        Waypoint w;
        float sx, sy;
        double dist;
    }

    private static final List<Item> items = new ArrayList<>();

    public static void begin() {
        items.clear();
    }

    public static void add(Waypoint w, float sx, float sy, double dist) {
        Item it = new Item();
        it.w = w;
        it.sx = sx;
        it.sy = sy;
        it.dist = dist;
        items.add(it);
    }

    public static void draw(DrawContext dc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        long now = System.currentTimeMillis();
        float s = WaypointStore.scale;

        for (Item it : items) {
            MatrixStack m = dc.getMatrices();
            m.push();
            m.translate(it.sx, it.sy, 0f);
            m.scale(s, s, 1f);
            drawCard(dc, tr, it.w, it.dist, now);
            m.pop();
        }
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
        boolean ready = w.isReady(now);

        Text nameT = Text.literal(w.name)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(w.color)).withBold(w.bold));
        Text distT = Text.literal((int) dist + " m").formatted(Formatting.GRAY);

        MutableText timerT = Text.literal("⌚ ").formatted(Formatting.WHITE);
        if (ready) {
            timerT.append(Text.literal("Gotowy").formatted(Formatting.GREEN, Formatting.BOLD));
        } else if (w.endAt <= 0) {
            timerT.append(Text.literal("Brak czasu").formatted(Formatting.GRAY));
        } else {
            timerT.append(Text.literal("Regeneracja za ").formatted(Formatting.WHITE, Formatting.BOLD));
            timerT.append(Text.literal(w.timerText(now)).formatted(Formatting.YELLOW, Formatting.BOLD));
        }

        boolean names = WaypointStore.showNames;
        boolean dists = WaypointStore.showDistance;
        boolean prog = WaypointStore.showProgress;
        boolean bar = WaypointStore.showBar;

        int wn = names ? tr.getWidth(nameT) : 0;
        int wd = dists ? tr.getWidth(distT) : 0;
        int wt = tr.getWidth(timerT);
        int w1 = names ? wn + (dists ? 10 + wd : 0) : wt + (dists ? 10 + wd : 0);
        int w2 = names ? wt : 0;
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
        if (dists) dc.drawTextWithShadow(tr, distT, half - 6 - wd, y1, 0xFFFFFFFF);
        if (names) {
            dc.drawTextWithShadow(tr, nameT, -half + 6, y1, 0xFFFFFFFF);
            dc.drawTextWithShadow(tr, timerT, -half + 6, y1 + 12, 0xFFFFFFFF);
        } else {
            dc.drawTextWithShadow(tr, timerT, -half + 6, y1, 0xFFFFFFFF);
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
