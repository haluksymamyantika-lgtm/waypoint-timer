package pl.wptimer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ListScreen extends Screen {
    private static final int PER = 5;
    private static final int TOP = 30;
    private int page = 0;
    private List<Waypoint> items = new ArrayList<>();
    private int pages = 1;

    public ListScreen() {
        super(Text.literal("Waypointy"));
    }

    private static class ScaleSlider extends SliderWidget {
        ScaleSlider(int x, int y, int w) {
            super(x, y, w, 20, Text.empty(),
                    Math.max(0.0, Math.min(1.0, (WaypointStore.scale - 0.3) / 5.7)));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Rozmiar napisów: " + String.format("%.1f", 0.3 + this.value * 5.7)));
        }

        @Override
        protected void applyValue() {
            WaypointStore.scale = (float) (0.3 + this.value * 5.7);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            WaypointStore.save();
        }
    }

    private static Text autoText() {
        return Text.literal("Auto-wykrywanie: " + (WaypointStore.autoDetect ? "TAK" : "NIE"));
    }

    private static Text hideText() {
        return Text.literal("Ukryj przy oryginale: " + (WaypointStore.hideLive ? "TAK" : "NIE"));
    }

    @Override
    protected void init() {
        items = WaypointStore.current(client);
        pages = Math.max(1, (items.size() + PER - 1) / PER);
        if (page >= pages) page = pages - 1;
        int left = width / 2 - 150;

        for (int i = 0; i < PER; i++) {
            int idx = page * PER + i;
            if (idx >= items.size()) break;
            Waypoint w = items.get(idx);
            int y = TOP + i * 24;
            addDrawableChild(ButtonWidget.builder(Text.literal("Restart"), b -> {
                w.restart();
                WaypointStore.save();
            }).dimensions(left + 168, y, 52, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Edytuj"),
                    b -> client.setScreen(new AddScreen(w))).dimensions(left + 224, y, 48, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
                WaypointStore.list.remove(w);
                WaypointStore.save();
                clearAndInit();
            }).dimensions(left + 276, y, 24, 20).build());
        }

        int y1 = TOP + PER * 24 + 6;
        int y2 = y1 + 24;
        int y3 = y2 + 24;

        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            if (page > 0) page--;
            clearAndInit();
        }).dimensions(left, y1, 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            if (page < pages - 1) page++;
            clearAndInit();
        }).dimensions(left + 44, y1, 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Dodaj"),
                b -> client.setScreen(new AddScreen(null))).dimensions(left + 88, y1, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zamknij"),
                b -> close()).dimensions(left + 192, y1, 108, 20).build());

        addDrawableChild(new ScaleSlider(left, y2, 300));

        addDrawableChild(ButtonWidget.builder(autoText(), b -> {
            WaypointStore.autoDetect = !WaypointStore.autoDetect;
            WaypointStore.save();
            b.setMessage(autoText());
        }).dimensions(left, y3, 148, 20).build());
        addDrawableChild(ButtonWidget.builder(hideText(), b -> {
            WaypointStore.hideLive = !WaypointStore.hideLive;
            WaypointStore.save();
            b.setMessage(hideText());
        }).dimensions(left + 152, y3, 148, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int left = width / 2 - 150;
        long now = System.currentTimeMillis();

        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);

        if (items.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Brak waypointów w tym świecie"), width / 2, TOP + 20, 0xFFAAAAAA);
        }

        for (int i = 0; i < PER; i++) {
            int idx = page * PER + i;
            if (idx >= items.size()) break;
            Waypoint w = items.get(idx);
            int y = TOP + i * 24 + 6;
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(w.name, 100), left, y, 0xFF000000 | w.color);
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(w.timerText(now), 60), left + 104, y, w.timerColor(now));
        }

        int y1 = TOP + PER * 24 + 6;
        ctx.drawTextWithShadow(textRenderer, (page + 1) + "/" + pages, left + 120, y1 - 9, 0xFFAAAAAA);
    }
}
