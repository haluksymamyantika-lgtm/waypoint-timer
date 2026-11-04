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
            Waypoint w = it.w;
            boolean ready = w.isReady(now);

            Text nameT = Text.literal(w.name)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(w.color)).withBold(w.bold));
            Text distT = Text.literal((int) it.dist + " m").formatted(Formatting.GRAY);

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
            int wn = tr.getWidth(nameT), wd = tr.getWidth(distT), wt = tr.getWidth(timerT);
            int inner = names ? Math.max(wn + 10 + wd, wt) : wt + 10 + wd;
            int half = inner / 2 + 5;
            int h = names ? 36 : 26;
            int top = -h - 3;
            int bottom = -3;
            int accent = 0xFF000000 | w.statusColor();

            MatrixStack m = dc.getMatrices();
            m.push();
            m.translate(it.sx, it.sy, 0f);
            m.scale(s, s, 1f);

            // karta
            dc.fill(-half - 1, top - 1, half + 1, bottom + 1, 0x66000000);
            dc.fill(-half, top, half, bottom, 0xEE15151C);
            dc.fill(-half, top, half, top + 2, accent);
            // "strzalka" wskazujaca miejsce
            dc.fill(-3, bottom, 3, bottom + 1, 0xEE15151C);
            dc.fill(-2, bottom + 1, 2, bottom + 2, 0xEE15151C);
            dc.fill(-1, bottom + 2, 1, bottom + 3, accent);

            // tresc
            dc.drawTextWithShadow(tr, distT, half - 5 - wd, top + 5, 0xFFFFFFFF);
            if (names) {
                dc.drawTextWithShadow(tr, nameT, -half + 5, top + 5, 0xFFFFFFFF);
                dc.drawTextWithShadow(tr, timerT, -half + 5, top + 17, 0xFFFFFFFF);
            } else {
                dc.drawTextWithShadow(tr, timerT, -half + 5, top + 5, 0xFFFFFFFF);
            }

            // pasek postepu
            int bx1 = -half + 5, bx2 = half - 5, by = bottom - 5;
            dc.fill(bx1, by, bx2, by + 2, 0x44FFFFFF);
            if (ready) {
                dc.fill(bx1, by, bx2, by + 2, 0xFF55FF55);
            } else if (w.endAt > now && w.durationSec > 0) {
                double frac = Math.min(1.0, (w.endAt - now) / (w.durationSec * 1000.0));
                int fx = bx1 + (int) ((bx2 - bx1) * frac);
                dc.fill(bx1, by, fx, by + 2, 0xFFFFFF55);
            }

            m.pop();
        }
    }
}
