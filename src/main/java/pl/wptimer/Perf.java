package pl.wptimer;

/** Lekki pomiar kosztu moda (wygladzona srednia, ms). */
public class Perf {
    public static double scanMs = 0;
    public static double worldMs = 0;
    public static double hudMs = 0;

    public static void scan(long ns) {
        scanMs = scanMs * 0.8 + (ns / 1e6) * 0.2;
    }

    public static void world(long ns) {
        worldMs = worldMs * 0.97 + (ns / 1e6) * 0.03;
    }

    public static void hud(long ns) {
        hudMs = hudMs * 0.97 + (ns / 1e6) * 0.03;
    }
}
