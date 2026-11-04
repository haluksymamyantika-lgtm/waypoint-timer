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
 * Napisy rysowane na ekranie (HUD) w miejscu, gdzie w swiecie jest obszar.
 * Dzieki temu sa czytelne z dowolnej odleglosci i przez sciany.
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

            Text nameT = Text.literal(w.name)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(w.color)).withBold(w.bold));

            MutableText timerT = Text.literal("⌚ ").formatted(Formatting.WHITE);
            if (w.isReady(now)) {
                timerT.append(Text.literal("Gotowy").formatted(Formatting.GREEN, Formatting.BOLD));
            } else if (w.endAt <= 0) {
                timerT.append(Text.literal("Brak czasu").formatted(Formatting.GRAY));
            } else {
                timerT.append(Text.literal("Regeneracja za ").formatted(Formatting.WHITE, Formatting.BOLD));
                timerT.append(Text.literal(w.timerText(now)).formatted(Formatting.YELLOW, Formatting.BOLD));
            }

            Text distT = Text.literal((int) it.dist + " m").formatted(Formatting.GRAY);

            int wn = tr.getWidth(nameT), wt = tr.getWidth(timerT), wd = tr.getWidth(distT);
            int half = Math.max(wn, Math.max(wt, wd)) / 2 + 3;

            MatrixStack m = dc.getMatrices();
            m.push();
            m.translate(it.sx, it.sy, 0f);
            m.scale(s, s, 1f);

            dc.fill(-half, -34, half, 0, 0x77000000);
            dc.fill(-half, -34, half, -32, 0xFF000000 | w.statusColor());   // czerwony / bialy / szary pasek
            dc.drawTextWithShadow(tr, nameT, -wn / 2, -30, 0xFFFFFFFF);
            dc.drawTextWithShadow(tr, timerT, -wt / 2, -20, 0xFFFFFFFF);
            dc.drawTextWithShadow(tr, distT, -wd / 2, -10, 0xFFFFFFFF);

            m.pop();
        }
    }
}
