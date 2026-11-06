package pl.wptimer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Menu moda: ciemny panel, boczna nawigacja, rozowy akcent, podglad, podpowiedzi, configi. */
public class ListScreen extends Screen {
    private static final int C_DIM = 0xB8000000;
    private static final int C_BG = 0xFF17171E;
    private static final int C_CARD = 0xFF20202A;
    private static final int C_LINE = 0xFF2E2E3A;
    private static final int C_ACC = 0xFFFF4D94;
    private static final int C_TEXT = 0xFFEDEDF2;
    private static final int C_MUTED = 0xFF8C8C9A;

    private static final int T_AREAS = 0, T_LOOK = 1, T_SET = 2, T_CFG = 3, T_KEYS = 4;
    private static final String[] TAB_NAMES = {"Obszary", "Wygląd", "Ustawienia", "Configi", "Klawisze"};
    private static final String[] TAB_DESC = {
            "Zaznaczone generatory i ich czasy",
            "Jak wyglądają napisy z czasem",
            "Zachowanie moda",
            "Zapisane zestawy obszarów i ustawień",
            "Skróty klawiszowe"};

    private static int tab = T_AREAS;
    private static boolean remap = false;
    private static Waypoint PREVIEW;

    private int scroll = 0, contentH = 0, vTop, vBottom;
    private Waypoint editing = null;
    private boolean editRepeat = false;
    private KeyBinding listening = null;
    private String confirmDelete = null;
    private String status = "";
    private long statusUntil = 0;
    private String cfgText = "";
    private List<ConfigProfiles.Info> profiles = new ArrayList<>();
    private List<Waypoint> shown = new ArrayList<>();

    private int px, py, pw, ph, side, cx, cy, cw;
    private int swBase;
    private final List<Btn> btns = new ArrayList<>();
    private final List<Slide> slides = new ArrayList<>();
    private final List<Tip> tips = new ArrayList<>();
    private final List<RowDef> rowDefs = new ArrayList<>();
    private final List<SwDef> swDefs = new ArrayList<>();
    private Slide dragging = null;
    private TextFieldWidget nameF, minF, secF, cfgF;

    public ListScreen() {
        super(Text.literal("Waypoint Timer"));
    }

    // ------------------------------------------------------------ kontrolki

    private static class Btn {
        final int x, y, w, h;
        final Supplier<String> label;
        final int style; // 0 zwykly, 1 akcent, 2 czerwony, 3 zakladka, 4 przelacznik, 5 probka koloru
        final Runnable act;
        BooleanSupplier state;
        boolean scroll;
        String tip;
        int color;
        float hv;

        Btn(int x, int y, int w, int h, Supplier<String> label, int style, Runnable act) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.style = style;
            this.act = act;
        }

        Btn sc() {
            scroll = true;
            return this;
        }

        Btn tip(String t) {
            tip = t;
            return this;
        }
    }

    private static class Slide {
        final int x, y, w;
        final DoubleSupplier get;
        final DoubleConsumer set;
        final Supplier<String> label;
        final Runnable done;
        boolean scroll;
        String tip;

        Slide(int x, int y, int w, DoubleSupplier get, DoubleConsumer set, Supplier<String> label, Runnable done) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.get = get;
            this.set = set;
            this.label = label;
            this.done = done;
        }

        Slide sc() {
            scroll = true;
            return this;
        }

        Slide tip(String t) {
            tip = t;
            return this;
        }

        void drag(double mx) {
            double v = (mx - x) / w;
            set.accept(Math.max(0.0, Math.min(1.0, v)));
        }
    }

    private static class Tip {
        final int x, y, w, h;
        final String text;

        Tip(int x, int y, int w, int h, String text) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.text = text;
        }
    }

    private static class RowDef {
        final int ly, h;
        final String label;

        RowDef(int ly, int h, String label) {
            this.ly = ly;
            this.h = h;
            this.label = label;
        }
    }

    private static class SwDef {
        final String label, tip;
        final BooleanSupplier get;
        final Runnable flip;

        SwDef(String label, BooleanSupplier get, Runnable flip, String tip) {
            this.label = label;
            this.get = get;
            this.flip = flip;
            this.tip = tip;
        }
    }

    // -------------------------------------------------------------- pomocnicze

    private int Y(int ly) {
        return vTop + ly - scroll;
    }

    private int viewH() {
        return vBottom - vTop;
    }

    private void clampScroll() {
        int max = Math.max(0, contentH - viewH());
        scroll = Math.max(0, Math.min(max, scroll));
    }

    private void setStatus(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    private void rebuild() {
        if (cfgF != null) cfgText = cfgF.getText();
        clearAndInit();
    }

    private static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int ca = (a >> s) & 255, cb = (b >> s) & 255;
            r |= (((int) (ca + (cb - ca) * t)) & 255) << s;
        }
        return r;
    }

    private static void rr(DrawContext dc, int x, int y, int w, int h, int c) {
        if (w < 4 || h < 4) {
            dc.fill(x, y, x + w, y + h, c);
            return;
        }
        dc.fill(x + 1, y, x + w - 1, y + h, c);
        dc.fill(x, y + 1, x + w, y + h - 1, c);
    }

    private boolean hitB(Btn b, double mx, double my) {
        if (mx < b.x || mx >= b.x + b.w || my < b.y || my >= b.y + b.h) return false;
        return !b.scroll || (my >= vTop && my <= vBottom);
    }

    private boolean hitS(Slide s, double mx, double my) {
        if (mx < s.x - 5 || mx > s.x + s.w + 5 || my < s.y - 8 || my > s.y + 8) return false;
        return !s.scroll || (my >= vTop && my <= vBottom);
    }

    private SwDef sd(String label, BooleanSupplier g, Runnable f, String tip) {
        return new SwDef(label, g, f, tip);
    }

    // ----------------------------------------------------------------- init

    @Override
    protected void init() {
        btns.clear();
        slides.clear();
        tips.clear();
        rowDefs.clear();
        swDefs.clear();
        dragging = null;
        nameF = null;
        minF = null;
        secF = null;
        cfgF = null;

        pw = Math.min(450, width - 16);
        ph = Math.min(280, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        side = 94;
        cx = px + side + 14;
        cw = pw - side - 28;
        cy = py + 46;
        vTop = cy;
        vBottom = py + ph - 22;

        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int t = i;
            Btn b = new Btn(px + 8, py + 52 + i * 26, side - 16, 22, () -> TAB_NAMES[t], 3, () -> {
                tab = t;
                editing = null;
                scroll = 0;
                listening = null;
                confirmDelete = null;
                rebuild();
            });
            b.state = () -> tab == t;
            btns.add(b);
        }
        btns.add(new Btn(px + pw - 24, py + 8, 16, 16, () -> "x", 2, this::close).tip("Zamknij (Esc)"));

        switch (tab) {
            case T_AREAS:
                if (editing != null) buildEdit();
                else buildAreas();
                break;
            case T_LOOK:
                buildLook();
                break;
            case T_SET:
                buildSettings();
                break;
            case T_CFG:
                buildConfigs();
                break;
            default:
                buildKeys();
                break;
        }
    }

    // --- Obszary

    private void buildAreas() {
        shown = WaypointStore.current(client);
        contentH = shown.size() * 40;
        clampScroll();
        int xr = cx + cw - 12;

        for (int i = 0; i < shown.size(); i++) {
            final Waypoint w = shown.get(i);
            int y = Y(i * 40);
            btns.add(new Btn(xr - 20, y + 8, 20, 20, () -> "x", 2, () -> {
                WaypointStore.list.remove(w);
                WaypointStore.save();
                rebuild();
            }).sc().tip("Usuń obszar"));
            btns.add(new Btn(xr - 70, y + 8, 46, 20, () -> "Edytuj", 0, () -> {
                editing = w;
                editRepeat = w.repeat;
                rebuild();
            }).sc().tip("Nazwa, czas regeneracji i powtarzanie"));
            btns.add(new Btn(xr - 118, y + 8, 44, 20, () -> "Bloki", 0, () -> {
                GeneratorScanner.recordTypes(client, w);
                w.state = 0;
                WaypointStore.save();
                setStatus("Zapisano znaczniki lewitującego bloku: " + w.types.size());
            }).sc().tip("Zapamiętaj lewitujący blok nad generatorem\n(kliknij, gdy generator jest pełny)"));
        }
    }

    // --- Edycja obszaru

    private void buildEdit() {
        contentH = 0;
        scroll = 0;
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
        btns.add(rep.tip("Po zakończeniu odliczanie startuje od nowa"));

        int bw = (cw - 8) / 3;
        int by = cy + 104;
        btns.add(new Btn(cx, by, bw, 22, () -> "Anuluj", 0, () -> {
            editing = null;
            rebuild();
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
            w.nameLocked = false;
        } else if (!n.equals(w.name)) {
            w.name = n;
            w.nameLocked = true;
        }
        int dur = Math.max(0, parse(minF.getText())) * 60 + Math.max(0, parse(secF.getText()));
        if (dur > 0) w.durationSec = dur;
        w.repeat = editRepeat;
        if (start) w.restart();
        WaypointStore.save();
        editing = null;
        rebuild();
    }

    // --- Wyglad

    private void addSwitches(int baseLy, SwDef... defs) {
        swBase = baseLy;
        int cwi = cw - 10;
        int colW = (cwi - 4) / 2;
        for (int i = 0; i < defs.length; i++) {
            SwDef d = defs[i];
            swDefs.add(d);
            int x = cx + (i % 2) * (colW + 4);
            int ly = baseLy + (i / 2) * 30;
            Btn b = new Btn(x + colW - 38, Y(ly) + 6, 30, 14, () -> "", 4, d.flip);
            b.state = d.get;
            btns.add(b.sc().tip(d.tip));
            tips.add(new Tip(x, Y(ly), colW, 26, d.tip));
        }
    }

    private void buildLook() {
        int cwi = cw - 10;
        contentH = 168 + 4 * 30;
        clampScroll();
        int sx = cx + cwi - 10 - 120;

        rowDefs.add(new RowDef(72, 28, "Rozmiar"));
        slides.add(new Slide(sx, Y(72) + 14, 120,
                () -> Math.sqrt(Math.max(0.0, Math.min(1.0, (WaypointStore.scale - 0.05) / 5.95))),
                v -> WaypointStore.scale = (float) (0.05 + 5.95 * v * v),
                () -> String.format(Locale.US, "%.2f", WaypointStore.scale),
                WaypointStore::save).sc().tip("Rozmiar napisów w świecie\n(od bardzo małych do dużych)"));

        rowDefs.add(new RowDef(104, 28, "Tło napisów"));
        slides.add(new Slide(sx, Y(104) + 14, 120,
                () -> WaypointStore.bgOpacity / 100.0,
                v -> WaypointStore.bgOpacity = (int) Math.round(v * 100),
                () -> WaypointStore.bgOpacity == 0 ? "przezroczyste" : WaypointStore.bgOpacity + "%",
                WaypointStore::save).sc().tip("Przezroczystość tła karty\n(0% = całkiem przezroczyste)"));

        rowDefs.add(new RowDef(136, 28, "Kolor tła"));
        for (int i = 0; i < WaypointStore.BG_COLORS.length; i++) {
            final int idx = i;
            Btn sw = new Btn(cx + cwi - 10 - 104 + i * 18, Y(136) + 7, 14, 14, () -> "", 5, () -> {
                WaypointStore.bgColorIdx = idx;
                WaypointStore.save();
            });
            sw.color = WaypointStore.BG_COLORS[i];
            sw.state = () -> WaypointStore.bgColorIdx == idx;
            btns.add(sw.sc().tip("Kolor tła: " + WaypointStore.BG_NAMES[i]));
        }

        addSwitches(168,
                sd("Pokaż napisy", () -> WaypointStore.showLabels, () -> {
                    WaypointStore.showLabels = !WaypointStore.showLabels;
                    WaypointStore.save();
                }, "Całe karty z czasem nad obszarami"),
                sd("Pokaż nazwy", () -> WaypointStore.showNames, () -> {
                    WaypointStore.showNames = !WaypointStore.showNames;
                    WaypointStore.save();
                }, "Nazwa generatora na karcie\n(wyłączona = tylko czas)"),
                sd("Pokaż obrysy", () -> WaypointStore.showBoxes, () -> {
                    WaypointStore.showBoxes = !WaypointStore.showBoxes;
                    WaypointStore.save();
                }, "Czerwone / białe ramki obszarów w świecie"),
                sd("Pokaż dystans", () -> WaypointStore.showDistance, () -> {
                    WaypointStore.showDistance = !WaypointStore.showDistance;
                    WaypointStore.save();
                }, "Odległość w metrach na karcie"),
                sd("Pasek statusu", () -> WaypointStore.showBar, () -> {
                    WaypointStore.showBar = !WaypointStore.showBar;
                    WaypointStore.save();
                }, "Kolorowy pasek u góry karty\n(czerwony / biały / szary)"),
                sd("Pasek postępu", () -> WaypointStore.showProgress, () -> {
                    WaypointStore.showProgress = !WaypointStore.showProgress;
                    WaypointStore.save();
                }, "Pasek pokazujący ile czasu zostało"),
                sd("Cień karty", () -> WaypointStore.showShadow, () -> {
                    WaypointStore.showShadow = !WaypointStore.showShadow;
                    WaypointStore.save();
                }, "Delikatny cień wokół karty"),
                sd("Ukryj z bliska", () -> WaypointStore.hideLive, () -> {
                    WaypointStore.hideLive = !WaypointStore.hideLive;
                    WaypointStore.save();
                }, "Chowa moją kartę w promieniu 12 m,\ngdy widać oryginalny hologram"));
    }

    // --- Ustawienia

    private void buildSettings() {
        int cwi = cw - 10;
        contentH = 34 + 2 * 30 + 4 + 22;
        clampScroll();
        int sx = cx + cwi - 10 - 120;

        rowDefs.add(new RowDef(0, 28, "Opóźnienie auto-startu"));
        slides.add(new Slide(sx, Y(0) + 14, 120,
                () -> WaypointStore.startDelaySec / 30.0,
                v -> WaypointStore.startDelaySec = (int) Math.round(v * 30),
                () -> WaypointStore.startDelaySec + " s",
                WaypointStore::save).sc().tip("Po ilu sekundach od regeneracji\nmod zaczyna kolejne odliczanie"));

        addSwitches(34,
                sd("Auto-start", () -> WaypointStore.autoStart, () -> {
                    WaypointStore.autoStart = !WaypointStore.autoStart;
                    WaypointStore.save();
                }, "Po regeneracji mod sam zaczyna liczyć\n(także gdy jesteś daleko)"),
                sd("Dźwięk (pik)", () -> WaypointStore.beep, () -> {
                    WaypointStore.beep = !WaypointStore.beep;
                    WaypointStore.save();
                }, "Pikanie, gdy generator jest gotowy"),
                sd("Komunikat", () -> WaypointStore.showMessage, () -> {
                    WaypointStore.showMessage = !WaypointStore.showMessage;
                    WaypointStore.save();
                }, "Napis „GOTOWE” na pasku akcji"));

        btns.add(new Btn(cx, Y(34 + 60 + 4), cwi, 22, () -> "Przywróć domyślny wygląd napisów", 0, () -> {
            WaypointStore.resetLook();
            setStatus("Przywrócono domyślny wygląd");
        }).sc().tip("Rozmiar, tło i dodatki kart wracają do ustawień fabrycznych"));
    }

    // --- Configi

    private void buildConfigs() {
        profiles = ConfigProfiles.list();
        vTop = cy + 52;
        contentH = profiles.size() * 40;
        clampScroll();

        cfgF = new TextFieldWidget(textRenderer, cx, cy, cw - 108, 20, Text.empty());
        cfgF.setMaxLength(24);
        cfgF.setText(cfgText);
        addDrawableChild(cfgF);

        btns.add(new Btn(cx + cw - 104, cy, 104, 20, () -> "Zapisz config", 1, () -> {
            String n = ConfigProfiles.clean(cfgF.getText());
            if (n.isEmpty()) {
                setStatus("Wpisz nazwę configu");
                return;
            }
            boolean ok = ConfigProfiles.save(n);
            cfgText = "";
            setStatus(ok ? "Zapisano config „" + n + "”" : "Nie udało się zapisać configu");
            rebuild();
        }).tip("Zapisuje obecne obszary i wszystkie ustawienia\npod podaną nazwą (ta sama nazwa = nadpisanie)"));

        Btn rm = new Btn(cx + cw - 30, cy + 29, 30, 14, () -> "", 4, () -> remap = !remap);
        rm.state = () -> remap;
        btns.add(rm.tip("Wczytane obszary zostaną przypisane do\nobecnego serwera / świata, w którym jesteś"));
        tips.add(new Tip(cx, cy + 26, cw, 20, "Wczytane obszary zostaną przypisane do\nobecnego serwera / świata, w którym jesteś"));

        int xr = cx + cw - 12;
        for (int i = 0; i < profiles.size(); i++) {
            final ConfigProfiles.Info in = profiles.get(i);
            int y = Y(i * 40);
            btns.add(new Btn(xr - 20, y + 8, 20, 20,
                    () -> in.name.equals(confirmDelete) ? "?" : "x", 2, () -> {
                if (in.name.equals(confirmDelete)) {
                    ConfigProfiles.delete(in.name);
                    confirmDelete = null;
                    setStatus("Usunięto config „" + in.name + "”");
                    rebuild();
                } else {
                    confirmDelete = in.name;
                }
            }).sc().tip("Usuń config (kliknij dwa razy)"));
            btns.add(new Btn(xr - 74, y + 8, 50, 20, () -> "Nadpisz", 0, () -> {
                ConfigProfiles.save(in.name);
                setStatus("Nadpisano config „" + in.name + "”");
                rebuild();
            }).sc().tip("Zapisz obecny stan do tego configu"));
            btns.add(new Btn(xr - 124, y + 8, 46, 20, () -> "Dołącz", 0, () -> {
                boolean ok = ConfigProfiles.load(in.name, remap, WaypointStore.currentKey(client), true);
                setStatus(ok ? "Dołączono obszary z „" + in.name + "”" : "Nie udało się wczytać");
                rebuild();
            }).sc().tip("Dodaje obszary z configu do obecnych\n(ustawienia zostają bez zmian)"));
            btns.add(new Btn(xr - 176, y + 8, 48, 20, () -> "Wczytaj", 1, () -> {
                boolean ok = ConfigProfiles.load(in.name, remap, WaypointStore.currentKey(client), false);
                setStatus(ok ? "Wczytano config „" + in.name + "”" : "Nie udało się wczytać");
                rebuild();
            }).sc().tip("Zastępuje obecne obszary i ustawienia\nzawartością tego configu"));
        }
    }

    // --- Klawisze

    private void buildKeys() {
        contentH = 36 + 30 + 40;
        clampScroll();
        KeyBinding[] kbs = {WaypointTimerMod.selectKey, WaypointTimerMod.listKey};
        String[] labels = {"Zaznacz róg obszaru", "Otwórz menu"};
        int xr = cx + cw - 10 - 8;
        for (int i = 0; i < kbs.length; i++) {
            final KeyBinding kb = kbs[i];
            int ly = i * 36;
            rowDefs.add(new RowDef(ly, 30, labels[i]));
            Btn key = new Btn(xr - 44 - 110, Y(ly) + 5, 110, 20,
                    () -> listening == kb ? "> naciśnij <" : kb.getBoundKeyLocalizedText().getString(), 0,
                    () -> listening = kb);
            key.state = () -> listening == kb;
            btns.add(key.sc().tip("Kliknij, potem naciśnij nowy klawisz"));
            btns.add(new Btn(xr - 40, Y(ly) + 5, 40, 20, () -> "Reset", 0, () -> {
                kb.setBoundKey(kb.getDefaultKey());
                KeyBinding.updateKeysByCode();
                client.options.write();
                listening = null;
            }).sc().tip("Przywróć klawisz domyślny"));
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
        drawPanel(dc);

        // tresc przewijana (przycieta do okna)
        dc.enableScissor(cx - 6, vTop, px + pw - 2, vBottom);
        drawContent(dc, mx, my);
        for (Btn b : btns) if (b.scroll) drawBtn(dc, b, mx, my);
        for (Slide s : slides) if (s.scroll) drawSlide(dc, s);
        dc.disableScissor();

        // elementy stale
        drawFixed(dc);
        for (Btn b : btns) if (!b.scroll) drawBtn(dc, b, mx, my);
        for (Slide s : slides) if (!s.scroll) drawSlide(dc, s);
        drawScrollbar(dc);
        drawFooter(dc);

        super.render(dc, mx, my, delta);   // pola tekstowe

        if (cfgF != null && cfgF.getText().isEmpty() && !cfgF.isFocused()) {
            dc.drawTextWithShadow(textRenderer, "Nazwa configu…", cfgF.getX() + 5, cfgF.getY() + 6, 0xFF6C6C7A);
        }

        drawTooltip(dc, mx, my);
    }

    private void drawPanel(DrawContext dc) {
        // cien
        dc.fill(px - 4, py - 4, px + pw + 4, py + ph + 4, 0x16000000);
        dc.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, 0x1E000000);
        dc.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, 0x2A000000);
        rr(dc, px - 1, py - 1, pw + 2, ph + 2, C_LINE);
        rr(dc, px, py, pw, ph, C_BG);

        // sidebar
        dc.fillGradient(px + 1, py + 1, px + side, py + ph - 1, 0xFF15151C, 0xFF0C0C11);
        dc.fill(px + side, py + 1, px + side + 1, py + ph - 1, C_LINE);
        dc.fill(px + 3, py, px + pw - 3, py + 2, C_ACC);

        // logo
        dc.fill(px + 10, py + 12, px + 14, py + 28, C_ACC);
        dc.drawTextWithShadow(textRenderer, Text.literal("WP TIMER").formatted(Formatting.BOLD), px + 19, py + 12, C_TEXT);
        dc.drawTextWithShadow(textRenderer, "generatory", px + 19, py + 21, C_MUTED);
        dc.fill(px + 8, py + 44, px + side - 8, py + 45, C_LINE);

        int n = WaypointStore.current(client).size();
        dc.drawTextWithShadow(textRenderer, "Obszary: " + n, px + 10, py + ph - 34, C_MUTED);
        dc.drawTextWithShadow(textRenderer, "v7 · 1.21.4", px + 10, py + ph - 22, 0xFF5E5E6C);

        // naglowek
        String head = editing != null && tab == T_AREAS ? "Edycja obszaru" : TAB_NAMES[tab];
        String desc = editing != null && tab == T_AREAS ? editing.name : TAB_DESC[tab];
        MatrixStack m = dc.getMatrices();
        m.push();
        m.translate(cx, py + 10, 0);
        m.scale(1.25f, 1.25f, 1f);
        dc.drawTextWithShadow(textRenderer, Text.literal(head).formatted(Formatting.BOLD), 0, 0, C_TEXT);
        m.pop();
        dc.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(desc, cw - 30), cx, py + 25, C_MUTED);
        dc.fill(cx, py + 37, px + pw - 12, py + 38, C_LINE);
    }

    private void drawFooter(DrawContext dc) {
        int fy = py + ph - 18;
        dc.fill(px + side + 1, fy, px + pw - 1, fy + 1, C_LINE);
        String text;
        int col = C_MUTED;
        if (System.currentTimeMillis() < statusUntil && !status.isEmpty()) {
            text = status;
            col = C_ACC;
        } else {
            String k = WaypointTimerMod.selectKey.getBoundKeyLocalizedText().getString();
            switch (tab) {
                case T_AREAS:
                    text = k + " = zaznacz róg obszaru · Shift+" + k + " = anuluj";
                    break;
                case T_LOOK:
                    text = "Zmiany widać od razu w świecie";
                    break;
                case T_SET:
                    text = "Najedź na opcję, żeby zobaczyć opis";
                    break;
                case T_CFG:
                    text = "Config = obszary + wszystkie ustawienia";
                    break;
                default:
                    text = "Esc = anuluj przypisywanie";
                    break;
            }
        }
        dc.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(text, cw), cx, fy + 5, col);
    }

    private void drawScrollbar(DrawContext dc) {
        int vh = viewH();
        if (contentH <= vh) return;
        int tx = px + pw - 8;
        dc.fill(tx, vTop, tx + 3, vBottom, 0xFF262631);
        int bh = Math.max(16, vh * vh / contentH);
        int by = vTop + (int) ((long) (vh - bh) * scroll / Math.max(1, contentH - vh));
        dc.fill(tx, by, tx + 3, by + bh, C_ACC);
    }

    private void drawBtn(DrawContext dc, Btn b, int mx, int my) {
        boolean hov = hitB(b, mx, my);
        b.hv += ((hov ? 1f : 0f) - b.hv) * 0.35f;
        boolean st = b.state != null && b.state.getAsBoolean();
        String label = b.label.get();

        if (b.style == 3) {
            int bg = st ? C_CARD : lerp(0xFF111117, 0xFF1F1F29, b.hv);
            rr(dc, b.x, b.y, b.w, b.h, bg);
            if (st) dc.fill(b.x, b.y + 3, b.x + 2, b.y + b.h - 3, C_ACC);
            dc.fill(b.x + 9, b.y + 9, b.x + 13, b.y + 13, st ? C_ACC : 0xFF4A4A58);
            dc.drawTextWithShadow(textRenderer, label, b.x + 19, b.y + (b.h - 8) / 2, st ? C_TEXT : C_MUTED);
        } else if (b.style == 4) {
            int off = lerp(0xFF3A3A48, 0xFF4C4C5E, b.hv);
            rr(dc, b.x, b.y, b.w, b.h, st ? lerp(C_ACC, 0xFFFF70A8, b.hv) : off);
            int kx = st ? b.x + b.w - 12 : b.x + 2;
            rr(dc, kx, b.y + 2, 10, b.h - 4, 0xFFFFFFFF);
        } else if (b.style == 5) {
            rr(dc, b.x - 2, b.y - 2, b.w + 4, b.h + 4, st ? C_ACC : lerp(0xFF3A3A48, 0xFF6A6A7C, b.hv));
            rr(dc, b.x, b.y, b.w, b.h, 0xFF000000 | b.color);
        } else {
            int base, hover;
            int style = st ? 1 : b.style;
            if (style == 1) {
                base = C_ACC;
                hover = 0xFFFF76AB;
            } else if (style == 2) {
                base = 0xFF6E2535;
                hover = 0xFFB03A52;
            } else {
                base = 0xFF2A2A36;
                hover = 0xFF3B3B4D;
            }
            rr(dc, b.x, b.y, b.w, b.h, lerp(base, hover, b.hv));
            int tw = textRenderer.getWidth(label);
            dc.drawTextWithShadow(textRenderer, label, b.x + (b.w - tw) / 2, b.y + (b.h - 8) / 2, 0xFFFFFFFF);
        }
    }

    private void drawSlide(DrawContext dc, Slide s) {
        double v = s.get.getAsDouble();
        int fx = s.x + (int) (s.w * v);
        rr(dc, s.x, s.y - 2, s.w, 4, 0xFF3A3A48);
        if (fx > s.x) rr(dc, s.x, s.y - 2, fx - s.x, 4, C_ACC);
        rr(dc, fx - 4, s.y - 6, 8, 12, 0xFFFFFFFF);
        String lab = s.label.get();
        dc.drawTextWithShadow(textRenderer, lab, s.x - 10 - textRenderer.getWidth(lab), s.y - 4, C_TEXT);
    }

    private void drawFixed(DrawContext dc) {
        if (tab == T_CFG && editing == null) {
            dc.drawTextWithShadow(textRenderer, "Wczytuj do obecnego świata", cx, cy + 29, C_MUTED);
            dc.fill(cx, cy + 48, px + pw - 12, cy + 49, C_LINE);
        }
    }

    private void drawContent(DrawContext dc, int mx, int my) {
        int cwi = cw - 10;
        // karty z etykietami (suwaki, klawisze)
        for (RowDef r : rowDefs) {
            int y = Y(r.ly);
            rr(dc, cx, y, cwi, r.h, C_CARD);
            dc.drawTextWithShadow(textRenderer, r.label, cx + 10, y + (r.h - 8) / 2, C_TEXT);
        }
        // przelaczniki w dwoch kolumnach
        int colW = (cwi - 4) / 2;
        for (int i = 0; i < swDefs.size(); i++) {
            int x = cx + (i % 2) * (colW + 4);
            int y = Y(swBase + (i / 2) * 30);
            rr(dc, x, y, colW, 26, C_CARD);
            dc.drawTextWithShadow(textRenderer, swDefs.get(i).label, x + 8, y + 9, C_TEXT);
        }

        switch (tab) {
            case T_AREAS:
                if (editing != null) drawEdit(dc);
                else drawAreas(dc);
                break;
            case T_LOOK:
                drawLookPreview(dc);
                break;
            case T_CFG:
                drawConfigs(dc);
                break;
            case T_KEYS:
                dc.drawTextWithShadow(textRenderer, "Kliknij pole, potem naciśnij nowy klawisz.", cx, Y(76), C_MUTED);
                dc.drawTextWithShadow(textRenderer, "Esc = anuluj, Reset = klawisz domyślny.", cx, Y(88), C_MUTED);
                break;
            default:
                break;
        }
    }

    private void drawAreas(DrawContext dc) {
        long now = System.currentTimeMillis();
        int cwi = cw - 10;

        if (shown.isEmpty()) {
            String k = WaypointTimerMod.selectKey.getBoundKeyLocalizedText().getString();
            dc.drawCenteredTextWithShadow(textRenderer, Text.literal("Brak obszarów"), cx + cwi / 2, vTop + 44, C_TEXT);
            dc.drawCenteredTextWithShadow(textRenderer, Text.literal("Celuj w róg generatora i naciśnij " + k),
                    cx + cwi / 2, vTop + 58, C_MUTED);
            dc.drawCenteredTextWithShadow(textRenderer, Text.literal("Potem w przeciwny róg i naciśnij " + k + " jeszcze raz"),
                    cx + cwi / 2, vTop + 70, C_MUTED);
            return;
        }

        for (int i = 0; i < shown.size(); i++) {
            Waypoint w = shown.get(i);
            int y = Y(i * 40);
            if (y + 36 < vTop || y > vBottom) continue;

            rr(dc, cx, y, cwi, 36, C_CARD);
            dc.fill(cx, y + 4, cx + 3, y + 32, 0xFF000000 | w.statusColor());

            int maxW = cwi - 130 - 20;
            String n = textRenderer.trimToWidth(w.name, maxW);
            Text nameT = Text.literal(n)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(w.color)).withBold(w.bold));
            dc.drawTextWithShadow(textRenderer, nameT, cx + 12, y + 7, 0xFFFFFFFF);

            MutableText t2 = Text.literal("⌚ ").formatted(Formatting.GRAY);
            if (w.isReady(now)) {
                t2.append(Text.literal("Gotowy").formatted(Formatting.GREEN));
            } else if (w.endAt <= 0) {
                t2.append(Text.literal("brak czasu").formatted(Formatting.GRAY));
            } else {
                t2.append(Text.literal(w.timerText(now)).formatted(Formatting.YELLOW));
            }
            dc.drawTextWithShadow(textRenderer, t2, cx + 12, y + 21, 0xFFFFFFFF);
        }
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
            dc.drawTextWithShadow(textRenderer, "Zapisanych znaczników lewitującego bloku: " + w.types.size(),
                    cx, cy + 146, C_MUTED);
        }
    }

    private void drawLookPreview(DrawContext dc) {
        int cwi = cw - 10;
        int y0 = Y(0);
        long now = System.currentTimeMillis();

        rr(dc, cx, y0, cwi, 66, 0xFF0E0E13);
        // szachownica - widac przezroczystosc tla
        for (int yy = 0; yy < 62; yy += 8) {
            for (int xx = 0; xx < cwi - 4; xx += 8) {
                int col = (((xx / 8) + (yy / 8)) % 2 == 0) ? 0xFF2D2D36 : 0xFF24242C;
                dc.fill(cx + 2 + xx, y0 + 2 + yy, Math.min(cx + 2 + xx + 8, cx + cwi - 2), Math.min(y0 + 2 + yy + 8, y0 + 64), col);
            }
        }
        dc.drawTextWithShadow(textRenderer, "Podgląd", cx + 6, y0 + 5, C_MUTED);

        if (PREVIEW == null) {
            PREVIEW = new Waypoint();
            PREVIEW.name = "Generator Jasnogrzybu";
            PREVIEW.color = 0xFFAA00;
            PREVIEW.bold = true;
            PREVIEW.durationSec = 180;
            PREVIEW.state = 1;
        }
        PREVIEW.endAt = now + 83000;

        MatrixStack m = dc.getMatrices();
        m.push();
        m.translate(cx + cwi / 2f, y0 + 63, 0f);
        float s = Math.min(WaypointStore.scale, 1.4f);
        m.scale(s, s, 1f);
        WaypointHud.drawCard(dc, textRenderer, PREVIEW, 24, now);
        m.pop();
    }

    private void drawConfigs(DrawContext dc) {
        int cwi = cw - 10;
        if (profiles.isEmpty()) {
            dc.drawCenteredTextWithShadow(textRenderer, Text.literal("Brak zapisanych configów"), cx + cwi / 2, vTop + 30, C_TEXT);
            dc.drawCenteredTextWithShadow(textRenderer, Text.literal("Wpisz nazwę wyżej i kliknij „Zapisz config”"),
                    cx + cwi / 2, vTop + 44, C_MUTED);
            return;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM HH:mm");
        for (int i = 0; i < profiles.size(); i++) {
            ConfigProfiles.Info in = profiles.get(i);
            int y = Y(i * 40);
            if (y + 36 < vTop || y > vBottom) continue;
            rr(dc, cx, y, cwi, 36, C_CARD);
            dc.fill(cx, y + 4, cx + 3, y + 32, C_ACC);
            dc.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(in.name, cwi - 200), cx + 12, y + 7, C_TEXT);
            String when = LocalDateTime.ofInstant(Instant.ofEpochMilli(in.modified), ZoneId.systemDefault()).format(fmt);
            dc.drawTextWithShadow(textRenderer, in.areas + " obsz. · " + when, cx + 12, y + 21, C_MUTED);
        }
    }

    private void drawTooltip(DrawContext dc, int mx, int my) {
        String tip = null;
        for (Btn b : btns) {
            if (b.tip != null && hitB(b, mx, my)) {
                tip = b.tip;
                break;
            }
        }
        if (tip == null) {
            for (Slide s : slides) {
                if (s.tip != null && hitS(s, mx, my)) {
                    tip = s.tip;
                    break;
                }
            }
        }
        if (tip == null && my >= vTop && my <= vBottom) {
            for (Tip t : tips) {
                if (mx >= t.x && mx < t.x + t.w && my >= t.y && my < t.y + t.h) {
                    tip = t.text;
                    break;
                }
            }
        }
        if (tip == null) return;

        String[] ls = tip.split("\n");
        int w = 0;
        for (String l : ls) w = Math.max(w, textRenderer.getWidth(l));
        w += 12;
        int h = ls.length * 11 + 6;
        int x = Math.min(mx + 10, width - w - 4);
        int y = Math.min(my + 12, height - h - 4);

        MatrixStack m = dc.getMatrices();
        m.push();
        m.translate(0, 0, 400);
        rr(dc, x - 1, y - 1, w + 2, h + 2, C_ACC);
        rr(dc, x, y, w, h, 0xFF101016);
        for (int i = 0; i < ls.length; i++) {
            dc.drawTextWithShadow(textRenderer, ls[i], x + 6, y + 4 + i * 11, C_TEXT);
        }
        m.pop();
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Btn b : new ArrayList<>(btns)) {
                if (hitB(b, mx, my)) {
                    b.act.run();
                    return true;
                }
            }
            for (Slide s : slides) {
                if (hitS(s, mx, my)) {
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
        if (contentH > viewH()) {
            int ns = scroll - (int) Math.signum(vertical) * 20;
            ns = Math.max(0, Math.min(contentH - viewH(), ns));
            if (ns != scroll) {
                scroll = ns;
                rebuild();
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
