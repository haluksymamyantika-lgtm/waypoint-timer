package pl.wptimer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Menu moda: ciemny panel z boczna nawigacja i rozowym akcentem. */
public class ListScreen extends Screen {
    private static final int C_DIM = 0xB0000000;
    private static final int C_BG = 0xFF17171E;
    private static final int C_SIDE = 0xFF101015;
    private static final int C_CARD = 0xFF20202A;
    private static final int C_HOVER = 0xFF2B2B38;
    private static final int C_LINE = 0xFF2E2E3A;
    private static final int C_ACC = 0xFFFF4D94;
    private static final int C_TEXT = 0xFFEDEDF2;
    private static final int C_MUTED = 0xFF8C8C9A;

    private static final int TAB_AREAS = 0, TAB_SETTINGS = 1, TAB_KEYS = 2;
    private static int tab = TAB_AREAS;

    private int scroll = 0;
    private Waypoint editing = null;
    private boolean editRepeat = false;
    private KeyBinding listening = null;

    private int px, py, pw, ph, side, cx, cy, cw, visible;
    private List<Waypoint> shown = new ArrayList<>();
    private final List<Btn> btns = new ArrayList<>();
    private final List<Slide> slides = new ArrayList<>();
    private Slide dragging = null;
    private TextFieldWidget nameF, minF, secF;

    public ListScreen() {
        super(Text.literal("Waypoint Timer"));
    }

    // ------------------------------------------------------------ kontrolki

    private static class Btn {
        final int x, y, w, h;
        final Supplier<String> label;
        final int style; // 0 zwykly, 1 akcent, 2 czerwony, 3 zakladka, 4 przelacznik
        final Runnable act;
        BooleanSupplier state;

        Btn(int x, int y, int w, int h, Supplier<String> label, int style, Runnable act) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.style = style;
            this.act = act;
        }

        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static class Slide {
        final int x, y, w;
        final DoubleSupplier get;
        final DoubleConsumer set;
        final Supplier<String> label;
        final Runnable done;

        Slide(int x, int y, int w, DoubleSupplier get, DoubleConsumer set, Supplier<String> label, Runnable done) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.get = get;
            this.set = set;
            this.label = label;
            this.done = done;
        }

        boolean hit(double mx, double my) {
            return mx >= x - 4 && mx <= x + w + 4 && my >= y - 8 && my <= y + 8;
        }

        void drag(double mx) {
            double v = (mx - x) / w;
            set.accept(Math.max(0.0, Math.min(1.0, v)));
        }
    }

    // ----------------------------------------------------------------- init

    @Override
    protected void init() {
        btns.clear();
        slides.clear();
        dragging = null;
        nameF = null;
        minF = null;
        secF = null;

        pw = Math.min(380, width - 16);
        ph = Math.min(236, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        side = 84;
        cx = px + side + 12;
        cw = pw - side - 24;
        cy = py + 36;

        String[] names = {"Obszary", "Ustawienia", "Klawisze"};
        for (int i = 0; i < 3; i++) {
            final int t = i;
            Btn b = new Btn(px + 6, py + 44 + i * 24, side - 12, 20, () -> names[t], 3, () -> {
                tab = t;
                editing = null;
                scroll = 0;
                listening = null;
                clearAndInit();
            });
            b.state = () -> tab == t;
            btns.add(b);
        }
        btns.add(new Btn(px + pw - 22, py + 8, 14, 14, () -> "x", 2, this::close));

        if (tab == TAB_AREAS) {
            if (editing != null) buildEdit();
            else buildAreas();
        } else if (tab == TAB_SETTINGS) {
            buildSettings();
        } else {
            buildKeys();
        }
    }

    private void buildAreas() {
        shown = WaypointStore.current(client);
        int bottom = py + ph - 26;
        visible = Math.max(1, (bottom - cy + 4) / 38);
        int maxScroll = Math.max(0, shown.size() - visible);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;

        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx >= shown.size()) break;
            Waypoint w = shown.get(idx);
            int y = cy + i * 38;
            int xr = cx + cw - 6;
            btns.add(new Btn(xr - 20, y + 7, 20, 20, () -> "x", 2, () -> {
                WaypointStore.list.remove(w);
                WaypointStore.save();
                clearAndInit();
            }));
            btns.add(new Btn(xr - 24 - 44, y + 7, 44, 20, () -> "Edytuj", 0, () -> {
                editing = w;
                editRepeat = w.repeat;
                clearAndInit();
            }));
            btns.add(new Btn(xr - 28 - 44 - 40, y + 7, 40, 20, () -> "Bloki", 0, () -> {
                GeneratorScanner.recordTypes(client, w);
                w.state = 0;
                WaypointStore.save();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Zapisano znaczniki lewitującego bloku: " + w.types.size()), true);
                }
            }));
        }
    }

    private void buildEdit() {
        nameF = new TextFieldWidget(textRenderer, cx, cy + 10, cw, 20, Text.empty());
        nameF.setMaxLength(40);
        nameF.setText(editing.name);
        addDrawableChild(nameF);

        int half = cw / 2 - 4;
        minF = new TextFieldWidget(textRenderer, cx, cy + 46, half, 20, Text.empty());
        minF.setMaxLength(5);
        minF.setTextPredicate(s -> s.matches("\\d*"));
        minF.setText(String.valueOf(editing.durationSec / 60));
        addDrawableChild(minF);

        secF = new TextFieldWidget(textRenderer, cx + cw - half, cy + 46, half, 20, Text.empty());
        secF.setMaxLength(5);
        secF.setTextPredicate(s -> s.matches("\\d*"));
        secF.setText(String.valueOf(editing.durationSec % 60));
        addDrawableChild(secF);

        Btn rep = new Btn(cx + cw - 30, cy + 79, 30, 14, () -> "", 4, () -> editRepeat = !editRepeat);
        rep.state = () -> editRepeat;
        btns.add(rep);

        int bw = (cw - 8) / 3;
        int by = cy + 104;
        btns.add(new Btn(cx, by, bw, 22, () -> "Anuluj", 0, () -> {
            editing = null;
            clearAndInit();
        }));
        btns.add(new Btn(cx + bw + 4, by, bw, 22, () -> "Zapisz", 0, () -> saveEdit(false)));
        btns.add(new Btn(cx + 2 * (bw + 4), by, bw, 22, () -> "Zapisz i start", 1, () -> saveEdit(true)));

        setInitialFocus(nameF);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void saveEdit(boolean start) {
        Waypoint w = editing;
        if (w == null || nameF == null) return;
        String n = nameF.getText().trim();
        if (n.isEmpty()) {
            w.nameLocked = false;               // wroci nazwa z hologramu
        } else if (!n.equals(w.name)) {
            w.name = n;
            w.nameLocked = true;                // wlasna nazwa - hologram jej nie zmieni
        }
        int dur = Math.max(0, parse(minF.getText())) * 60 + Math.max(0, parse(secF.getText()));
        if (dur > 0) w.durationSec = dur;
        w.repeat = editRepeat;
        if (start) w.restart();
        WaypointStore.save();
        editing = null;
        clearAndInit();
    }

    private void sw(int col, int row, BooleanSupplier get, Runnable flip) {
        int colW = (cw - 4) / 2;
        int x = cx + col * (colW + 4);
        int y = cy + 68 + row * 30;
        Btn b = new Btn(x + colW - 38, y + 6, 30, 14, () -> "", 4, flip);
        b.state = get;
        btns.add(b);
    }

    private void buildSettings() {
        int rx = cx + cw - 8;

        // rozmiar napisow (od bardzo malych)
        slides.add(new Slide(rx - 100, cy + 15, 100,
                () -> Math.sqrt(Math.max(0.0, Math.min(1.0, (WaypointStore.scale - 0.05) / 5.95))),
                v -> WaypointStore.scale = (float) (0.05 + 5.95 * v * v),
                () -> String.format(Locale.US, "%.2f", WaypointStore.scale),
                WaypointStore::save));

        // opoznienie auto-startu
        slides.add(new Slide(rx - 100, cy + 34 + 15, 100,
                () -> WaypointStore.startDelaySec / 30.0,
                v -> WaypointStore.startDelaySec = (int) Math.round(v * 30),
                () -> WaypointStore.startDelaySec + " s",
                WaypointStore::save));

        sw(0, 0, () -> WaypointStore.showLabels, () -> { WaypointStore.showLabels = !WaypointStore.showLabels; WaypointStore.save(); });
        sw(1, 0, () -> WaypointStore.showNames, () -> { WaypointStore.showNames = !WaypointStore.showNames; WaypointStore.save(); });
        sw(0, 1, () -> WaypointStore.showBoxes, () -> { WaypointStore.showBoxes = !WaypointStore.showBoxes; WaypointStore.save(); });
        sw(1, 1, () -> WaypointStore.hideLive, () -> { WaypointStore.hideLive = !WaypointStore.hideLive; WaypointStore.save(); });
        sw(0, 2, () -> WaypointStore.autoStart, () -> { WaypointStore.autoStart = !WaypointStore.autoStart; WaypointStore.save(); });
        sw(1, 2, () -> WaypointStore.beep, () -> { WaypointStore.beep = !WaypointStore.beep; WaypointStore.save(); });
    }

    private void buildKeys() {
        KeyBinding[] kbs = {WaypointTimerMod.selectKey, WaypointTimerMod.listKey};
        int rx = cx + cw - 8;
        for (int i = 0; i < kbs.length; i++) {
            final KeyBinding kb = kbs[i];
            int y = cy + i * 36;
            Btn key = new Btn(rx - 44 - 100, y + 5, 100, 20,
                    () -> listening == kb ? "> naciśnij <" : kb.getBoundKeyLocalizedText().getString(), 0,
                    () -> listening = kb);
            key.state = () -> listening == kb;
            btns.add(key);
            btns.add(new Btn(rx - 40, y + 5, 40, 20, () -> "Reset", 0, () -> {
                kb.setBoundKey(kb.getDefaultKey());
                KeyBinding.updateKeysByCode();
                client.options.write();
                listening = null;
            }));
        }
    }

    // --------------------------------------------------------------- render

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // wlasne tlo rysuje render()
    }

    @Override
    public void render(DrawContext dc, int mx, int my, float delta) {
        dc.fill(0, 0, width, height, C_DIM);

        // panel
        dc.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, C_LINE);
        dc.fill(px, py, px + pw, py + ph, C_BG);
        dc.fill(px, py, px + side, py + ph, C_SIDE);

        // logo
        dc.drawTextWithShadow(textRenderer, Text.literal("WP TIMER").formatted(Formatting.BOLD), px + 10, py + 12, C_ACC);
        dc.drawTextWithShadow(textRenderer, "generatory", px + 10, py + 24, C_MUTED);

        // naglowek
        String head = tab == TAB_AREAS ? (editing != null ? "Edycja obszaru" : "Obszary generatorów")
                : tab == TAB_SETTINGS ? "Ustawienia" : "Klawisze";
        dc.drawTextWithShadow(textRenderer, Text.literal(head).formatted(Formatting.BOLD), cx, py + 12, C_TEXT);
        dc.fill(cx, py + 27, px + pw - 12, py + 28, C_LINE);

        if (tab == TAB_AREAS) {
            if (editing != null) drawEdit(dc);
            else drawAreas(dc);
        } else if (tab == TAB_SETTINGS) {
            drawSettings(dc);
        } else {
            drawKeys(dc);
        }

        for (Btn b : btns) drawBtn(dc, b, mx, my);
        for (Slide s : slides) drawSlide(dc, s);

        super.render(dc, mx, my, delta);   // pola tekstowe
    }

    private void drawBtn(DrawContext dc, Btn b, int mx, int my) {
        boolean hov = b.hit(mx, my);
        boolean st = b.state != null && b.state.getAsBoolean();
        String label = b.label.get();

        if (b.style == 3) {
            if (st) {
                dc.fill(b.x, b.y, b.x + b.w, b.y + b.h, C_CARD);
                dc.fill(b.x, b.y, b.x + 2, b.y + b.h, C_ACC);
            } else if (hov) {
                dc.fill(b.x, b.y, b.x + b.w, b.y + b.h, C_HOVER);
            }
            dc.drawTextWithShadow(textRenderer, label, b.x + 10, b.y + (b.h - 8) / 2, st ? C_TEXT : C_MUTED);
        } else if (b.style == 4) {
            dc.fill(b.x, b.y, b.x + b.w, b.y + b.h, st ? C_ACC : (hov ? 0xFF4A4A5A : 0xFF3A3A48));
            int kx = st ? b.x + b.w - 12 : b.x + 2;
            dc.fill(kx, b.y + 2, kx + 10, b.y + b.h - 2, 0xFFFFFFFF);
        } else {
            int style = st ? 1 : b.style;
            int bg;
            if (style == 1) bg = hov ? 0xFFFF6BA6 : C_ACC;
            else if (style == 2) bg = hov ? 0xFFB23A52 : 0xFF7A2B3A;
            else bg = hov ? 0xFF3C3C4C : 0xFF2E2E3C;
            dc.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
            int tw = textRenderer.getWidth(label);
            dc.drawTextWithShadow(textRenderer, label, b.x + (b.w - tw) / 2, b.y + (b.h - 8) / 2, 0xFFFFFFFF);
        }
    }

    private void drawSlide(DrawContext dc, Slide s) {
        double v = s.get.getAsDouble();
        int fx = s.x + (int) (s.w * v);
        dc.fill(s.x, s.y - 2, s.x + s.w, s.y + 2, 0xFF3A3A48);
        dc.fill(s.x, s.y - 2, fx, s.y + 2, C_ACC);
        dc.fill(fx - 3, s.y - 6, fx + 3, s.y + 6, 0xFFFFFFFF);
        String lab = s.label.get();
        dc.drawTextWithShadow(textRenderer, lab, s.x - 8 - textRenderer.getWidth(lab), s.y - 4, C_TEXT);
    }

    private void drawAreas(DrawContext dc) {
        long now = System.currentTimeMillis();

        if (shown.isEmpty()) {
            String k = WaypointTimerMod.selectKey.getBoundKeyLocalizedText().getString();
            dc.drawCenteredTextWithShadow(textRenderer, Text.literal("Brak obszarów"), cx + cw / 2, cy + 40, C_TEXT);
            dc.drawCenteredTextWithShadow(textRenderer, Text.literal("Celuj w róg generatora i naciśnij " + k),
                    cx + cw / 2, cy + 54, C_MUTED);
        }

        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx >= shown.size()) break;
            Waypoint w = shown.get(idx);
            int y = cy + i * 38;

            dc.fill(cx, y, cx + cw, y + 34, C_CARD);
            dc.fill(cx, y, cx + 3, y + 34, 0xFF000000 | w.statusColor());

            int maxW = cw - 10 - 28 - 44 - 40 - 28;
            String n = textRenderer.trimToWidth(w.name, maxW);
            Text nameT = Text.literal(n)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(w.color)).withBold(w.bold));
            dc.drawTextWithShadow(textRenderer, nameT, cx + 10, y + 7, 0xFFFFFFFF);

            MutableText t2 = Text.literal("⌚ ").formatted(Formatting.GRAY);
            if (w.isReady(now)) {
                t2.append(Text.literal("Gotowy").formatted(Formatting.GREEN));
            } else if (w.endAt <= 0) {
                t2.append(Text.literal("brak czasu").formatted(Formatting.GRAY));
            } else {
                t2.append(Text.literal(w.timerText(now)).formatted(Formatting.YELLOW));
            }
            dc.drawTextWithShadow(textRenderer, t2, cx + 10, y + 20, 0xFFFFFFFF);
        }

        // pasek przewijania
        if (shown.size() > visible) {
            int trackTop = cy, trackBot = cy + visible * 38 - 4;
            int th = trackBot - trackTop;
            int barH = Math.max(12, th * visible / shown.size());
            int maxScroll = shown.size() - visible;
            int barY = trackTop + (maxScroll == 0 ? 0 : (th - barH) * scroll / maxScroll);
            dc.fill(px + pw - 6, trackTop, px + pw - 4, trackBot, 0xFF2A2A34);
            dc.fill(px + pw - 6, barY, px + pw - 4, barY + barH, C_ACC);
        }

        String k = WaypointTimerMod.selectKey.getBoundKeyLocalizedText().getString();
        dc.drawTextWithShadow(textRenderer, k + " = zaznacz róg · Shift+" + k + " = anuluj", cx, py + ph - 16, C_MUTED);
    }

    private void drawEdit(DrawContext dc) {
        dc.drawTextWithShadow(textRenderer, "Nazwa (puste = z hologramu)", cx, cy, C_MUTED);
        dc.drawTextWithShadow(textRenderer, "Czas regeneracji: minuty", cx, cy + 35, C_MUTED);
        dc.drawTextWithShadow(textRenderer, "sekundy", cx + cw - (cw / 2 - 4), cy + 35, C_MUTED);
        dc.drawTextWithShadow(textRenderer, "Powtarzaj po zakończeniu", cx, cy + 82, C_TEXT);

        Waypoint w = editing;
        if (w != null) {
            dc.drawTextWithShadow(textRenderer,
                    "Obszar: " + w.x1 + " " + w.y1 + " " + w.z1 + " - " + w.x2 + " " + w.y2 + " " + w.z2,
                    cx, cy + 134, C_MUTED);
            dc.drawTextWithShadow(textRenderer, "Zapisanych znaczników lewitującego bloku: " + w.types.size(), cx, cy + 146, C_MUTED);
        }
    }

    private void drawSettings(DrawContext dc) {
        String[] sl = {"Rozmiar napisów", "Opóźnienie startu"};
        for (int i = 0; i < sl.length; i++) {
            int y = cy + i * 34;
            dc.fill(cx, y, cx + cw, y + 30, C_CARD);
            dc.drawTextWithShadow(textRenderer, sl[i], cx + 10, y + 11, C_TEXT);
        }
        String[] sws = {"Pokaż napisy", "Pokaż nazwy", "Pokaż obrysy", "Ukryj z bliska", "Auto-start", "Dźwięk (pik)"};
        int colW = (cw - 4) / 2;
        for (int i = 0; i < sws.length; i++) {
            int x = cx + (i % 2) * (colW + 4);
            int y = cy + 68 + (i / 2) * 30;
            dc.fill(x, y, x + colW, y + 26, C_CARD);
            dc.drawTextWithShadow(textRenderer, sws[i], x + 8, y + 9, C_TEXT);
        }
    }

    private void drawKeys(DrawContext dc) {
        String[] labels = {"Zaznacz róg obszaru", "Otwórz menu"};
        for (int i = 0; i < labels.length; i++) {
            int y = cy + i * 36;
            dc.fill(cx, y, cx + cw, y + 30, C_CARD);
            dc.drawTextWithShadow(textRenderer, labels[i], cx + 10, y + 11, C_TEXT);
        }
        dc.drawTextWithShadow(textRenderer, "Kliknij pole, potem naciśnij nowy klawisz.", cx, cy + 84, C_MUTED);
        dc.drawTextWithShadow(textRenderer, "Esc = anuluj, Reset = klawisz domyślny.", cx, cy + 96, C_MUTED);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Btn b : new ArrayList<>(btns)) {
                if (b.hit(mx, my)) {
                    b.act.run();
                    return true;
                }
            }
            for (Slide s : slides) {
                if (s.hit(mx, my)) {
                    dragging = s;
                    s.drag(mx);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging.done.run();
            dragging = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (tab == TAB_AREAS && editing == null) {
            int maxScroll = Math.max(0, shown.size() - visible);
            int ns = scroll - (int) Math.signum(vertical);
            ns = Math.max(0, Math.min(maxScroll, ns));
            if (ns != scroll) {
                scroll = ns;
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening != null) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                listening.setBoundKey(InputUtil.fromKeyCode(keyCode, scanCode));
                KeyBinding.updateKeysByCode();
                client.options.write();
            }
            listening = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
