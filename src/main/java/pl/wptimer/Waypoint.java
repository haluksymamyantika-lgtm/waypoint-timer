package pl.wptimer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Zaznaczony przez gracza obszar generatora + jego timer. */
public class Waypoint {
    public String name = "Generator";
    public boolean nameLocked = false;   // true = nazwa wpisana recznie, hologram jej nie nadpisuje
    public String world = "";
    public int x1, y1, z1, x2, y2, z2;   // min/max wlacznie
    public int durationSec = 0;          // najdluzszy zaobserwowany czas regeneracji
    public boolean repeat = false;
    public long endAt = 0;               // epoch ms, 0 = brak czasu
    public boolean notified = true;
    public int color = 0xFFAA00;         // kolor nazwy z hologramu
    public boolean bold = true;
    public long autoStartAt = 0;         // kiedy wystartowac auto-odliczanie po regeneracji (0 = nie)
    public boolean autoCounting = false; // true = obecne odliczanie wystartowal mod, nie hologram
    public int state = 0;                // 0 = nieznany, 1 = sa bloki generatora (czerwony), 2 = brak blokow (bialy)
    public List<String> types = new ArrayList<>();

    public transient long liveAt = 0;
    public transient long absentSince = 0;
    public transient WaypointHud.Card card;
    private transient Set<String> typeSet;

    public Set<String> typeSet() {
        if (typeSet == null) typeSet = new HashSet<>(types);
        return typeSet;
    }

    public void setTypes(List<String> t) {
        types = t;
        typeSet = null;
    }

    public long volume() {
        return (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
    }

    public double centerX() { return (x1 + x2 + 1) / 2.0; }
    public double centerZ() { return (z1 + z2 + 1) / 2.0; }

    public int statusColor() {
        return state == 1 ? 0xFF2222 : (state == 2 ? 0xFFFFFF : 0xAAAAAA);
    }

    public boolean isLive(long now) {
        return now - liveAt < 1500;
    }

    public void restart() {
        if (durationSec <= 0) return;
        endAt = System.currentTimeMillis() + durationSec * 1000L;
        notified = false;
    }

    public boolean isReady(long now) {
        return endAt > 0 ? now >= endAt : state == 1;
    }

    /** Format jak na generatorach: "7s", "2min, 33s", "1h, 2min". */
    public static String fmt(long sec) {
        long d = sec / 86400, h = (sec % 86400) / 3600, m = (sec % 3600) / 60, s = sec % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d");
        if (h > 0) { if (sb.length() > 0) sb.append(", "); sb.append(h).append("h"); }
        if (m > 0) { if (sb.length() > 0) sb.append(", "); sb.append(m).append("min"); }
        if (s > 0 || sb.length() == 0) { if (sb.length() > 0) sb.append(", "); sb.append(s).append("s"); }
        return sb.toString();
    }

    public String timerText(long now) {
        if (endAt <= 0) return state == 1 ? "GOTOWE" : "--";
        long ms = endAt - now;
        if (ms <= 0) return "GOTOWE";
        return fmt((ms + 999) / 1000);
    }

    public int timerColor(long now) {
        if (isReady(now)) return 0xFF55FF55;
        if (endAt <= 0) return 0xFFAAAAAA;
        return 0xFFFFFF55;
    }
}
