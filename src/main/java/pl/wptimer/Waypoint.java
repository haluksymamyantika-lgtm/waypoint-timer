package pl.wptimer;

public class Waypoint {
    public String name = "Punkt";
    public String world = "";
    public int x, y, z;
    public int durationSec = 60;
    public boolean repeat = false;
    public long endAt = 0;          // epoch ms, 0 = nie uruchomiony
    public boolean notified = false;

    public void restart() {
        endAt = System.currentTimeMillis() + durationSec * 1000L;
        notified = false;
    }

    public String timerText(long now) {
        if (endAt <= 0) return "--";
        long ms = endAt - now;
        if (ms <= 0) return "GOTOWE";
        long sec = (ms + 999) / 1000;
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    public int timerColor(long now) {
        if (endAt <= 0) return 0xFFAAAAAA;
        long ms = endAt - now;
        if (ms <= 0) return 0xFF55FF55;
        if (ms < 10_000) return 0xFFFF5555;
        return 0xFFFFFF55;
    }
}
