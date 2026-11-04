package pl.wptimer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class AddScreen extends Screen {
    private final Waypoint edit;
    private TextFieldWidget nameF, xF, yF, zF, minF, secF;
    private ButtonWidget repBtn;
    private boolean repeat;
    private int top;

    public AddScreen(Waypoint edit) {
        super(Text.literal(edit == null ? "Nowy waypoint" : "Edytuj waypoint"));
        this.edit = edit;
        this.repeat = edit != null && edit.repeat;
    }

    private TextFieldWidget field(int x, int y, int w, String text, boolean numeric, boolean negative) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(""));
        f.setMaxLength(numeric ? 7 : 32);
        if (numeric) f.setTextPredicate(s -> s.matches(negative ? "-?\\d*" : "\\d*"));
        f.setText(text);
        addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        top = height / 2 - 85;

        String name;
        BlockPos p = client.player != null ? client.player.getBlockPos() : BlockPos.ORIGIN;
        int x = p.getX(), y = p.getY(), z = p.getZ();
        int min = 5, sec = 0;
        if (edit != null) {
            name = edit.name;
            x = edit.x; y = edit.y; z = edit.z;
            min = edit.durationSec / 60;
            sec = edit.durationSec % 60;
        } else {
            name = "Punkt " + (WaypointStore.current(client).size() + 1);
        }

        nameF = field(cx - 100, top + 16, 200, name, false, false);
        xF = field(cx - 100, top + 52, 64, String.valueOf(x), true, true);
        yF = field(cx - 32, top + 52, 64, String.valueOf(y), true, true);
        zF = field(cx + 36, top + 52, 64, String.valueOf(z), true, true);
        minF = field(cx - 100, top + 88, 95, String.valueOf(min), true, false);
        secF = field(cx + 5, top + 88, 95, String.valueOf(sec), true, false);

        repBtn = ButtonWidget.builder(repText(), b -> {
            repeat = !repeat;
            b.setMessage(repText());
        }).dimensions(cx - 100, top + 116, 200, 20).build();
        addDrawableChild(repBtn);

        addDrawableChild(ButtonWidget.builder(Text.literal("Zapisz"), b -> save())
                .dimensions(cx - 100, top + 144, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Anuluj"), b -> close())
                .dimensions(cx + 2, top + 144, 98, 20).build());

        setInitialFocus(nameF);
    }

    private Text repText() {
        return Text.literal("Powtarzaj po zakończeniu: " + (repeat ? "TAK" : "NIE"));
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private void save() {
        int dur = Math.max(1, Math.max(0, parse(minF.getText(), 0)) * 60 + Math.max(0, parse(secF.getText(), 0)));
        Waypoint w = edit != null ? edit : new Waypoint();
        String n = nameF.getText().trim();
        w.name = n.isEmpty() ? "Punkt" : n;
        w.x = parse(xF.getText(), 0);
        w.y = parse(yF.getText(), 0);
        w.z = parse(zF.getText(), 0);
        w.durationSec = dur;
        w.repeat = repeat;
        if (edit == null) w.world = WaypointStore.currentKey(client);
        w.restart();
        if (edit == null) WaypointStore.list.add(w);
        WaypointStore.save();
        client.setScreen(null);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = width / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, title, cx, top, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Nazwa", cx - 100, top + 6, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "X", cx - 100, top + 42, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Y", cx - 32, top + 42, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Z", cx + 36, top + 42, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Minuty", cx - 100, top + 78, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Sekundy", cx + 5, top + 78, 0xFFAAAAAA);
    }
}
