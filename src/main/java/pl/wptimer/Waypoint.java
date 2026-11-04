package pl.wptimer;

public class Waypoint {
    public String name = "Punkt";
    public String world = "";
    public int x, y, z;
    public int durationSec = 60;
    public boolean repeat = false;
    public long endAt = 0;          // epoch ms, 0 = nie uruchomiony
    public boolean notified = false;

    // generator wykryty automatycznie z hologramu
    public boolean generator = false;
    public int color = 0xFFAA00;
    public boolean bold = true;
    public transient long liveAt = 0;

    public boolean isLive(long now) {
        return now - liveAt < 1500;
    }

    public void restart() {
        endAt = System.currentTimeMillis() + durationSec * 1000L;
        notified = false;
    }

    public boolean ready(long now) {
        return endAt > 0 && now >= endAt;
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
        if (endAt <= 0) return "--";
        long ms = endAt - now;
        if (ms <= 0) return "GOTOWE";
        return fmt((ms + 999) / 1000);
    }

    public int timerColor(long now) {
        if (endAt <= 0) return 0xFFAAAAAA;
        if (endAt - now <= 0) return 0xFF55FF55;
        return 0xFFFFFF55;
    }
}
