package pl.wptimer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Edycja obszaru: nazwa, czas, powtarzanie. */
public class AddScreen extends Screen {
    private final Waypoint w;
    private TextFieldWidget nameF, minF, secF;
    private boolean repeat;
    private int top;

    public AddScreen(Waypoint w) {
        super(Text.literal("Edytuj obszar"));
        this.w = w;
        this.repeat = w.repeat;
    }

    private TextFieldWidget field(int x, int y, int wd, String text, boolean numeric) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, wd, 20, Text.literal(""));
        f.setMaxLength(numeric ? 7 : 40);
        if (numeric) f.setTextPredicate(s -> s.matches("\\d*"));
        f.setText(text);
        addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        top = height / 2 - 70;

        nameF = field(cx - 100, top + 16, 200, w.name, false);
        minF = field(cx - 100, top + 52, 95, String.valueOf(w.durationSec / 60), true);
        secF = field(cx + 5, top + 52, 95, String.valueOf(w.durationSec % 60), true);

        addDrawableChild(ButtonWidget.builder(repText(), b -> {
            repeat = !repeat;
            b.setMessage(repText());
        }).dimensions(cx - 100, top + 80, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Zapisz"), b -> save(false))
                .dimensions(cx - 100, top + 108, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zapisz i start"), b -> save(true))
                .dimensions(cx + 2, top + 108, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Anuluj"), b -> client.setScreen(new ListScreen()))
                .dimensions(cx - 100, top + 132, 200, 20).build());

        setInitialFocus(nameF);
    }

    private Text repText() {
        return Text.literal("Powtarzaj po zakończeniu: " + (repeat ? "TAK" : "NIE"));
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void save(boolean start) {
        String n = nameF.getText().trim();
        if (n.isEmpty()) {
            w.nameLocked = false;                 // wroci nazwa z hologramu
        } else if (!n.equals(w.name)) {
            w.name = n;
            w.nameLocked = true;                  // wlasna nazwa - hologram jej nie zmieni
        }
        int dur = Math.max(0, parse(minF.getText())) * 60 + Math.max(0, parse(secF.getText()));
        if (dur > 0) w.durationSec = dur;
        w.repeat = repeat;
        if (start) w.restart();
        WaypointStore.save();
        client.setScreen(new ListScreen());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = width / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, title, cx, top - 8, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Nazwa (puste = z hologramu)", cx - 100, top + 6, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Czas regeneracji: minuty", cx - 100, top + 42, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "sekundy", cx + 5, top + 42, 0xFFAAAAAA);
    }
}
