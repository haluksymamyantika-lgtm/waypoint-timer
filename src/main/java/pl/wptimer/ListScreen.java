package pl.wptimer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ListScreen extends Screen {
    private static final int PER = 6;
    private static final int TOP = 30;
    private int page = 0;
    private List<Waypoint> items = new ArrayList<>();
    private int pages = 1;

    public ListScreen() {
        super(Text.literal("Waypointy"));
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

        addDrawableChild(ButtonWidget.builder(Text.literal("Napis -"), b -> {
            WaypointStore.scale = Math.max(0.3f, WaypointStore.scale - 0.1f);
            WaypointStore.save();
        }).dimensions(left, y2, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Napis +"), b -> {
            WaypointStore.scale = Math.min(4.0f, WaypointStore.scale + 0.1f);
            WaypointStore.save();
        }).dimensions(left + 94, y2, 90, 20).build());
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
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(w.name, 92), left, y, 0xFFFFFFFF);
            ctx.drawTextWithShadow(textRenderer, w.timerText(now), left + 100, y, w.timerColor(now));
        }

        int y2 = TOP + PER * 24 + 6 + 24;
        ctx.drawTextWithShadow(textRenderer, (page + 1) + "/" + pages, left + 200, y2 + 6, 0xFFAAAAAA);
    }
}
