package pl.wptimer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public class WaypointRenderer {
    // obiekty uzywane ponownie - zero alokacji w klatce
    private static final Quaternionf INV = new Quaternionf();
    private static final Vector3f V = new Vector3f();
    private static final Vector4f C = new Vector4f();

    public static void render(WorldRenderContext ctx) {
        long t0 = System.nanoTime();
        WaypointHud.begin();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        List<Waypoint> act = WaypointStore.active(mc);
        BlockPos pend = WaypointStore.pending;
        boolean hasPend = pend != null && WaypointStore.currentKey(mc).equals(WaypointStore.pendingWorld);
        if (act.isEmpty() && !hasPend) return;

        boolean boxes = WaypointStore.showBoxes;
        boolean labels = WaypointStore.showLabels;
        if (!boxes && !labels && !hasPend) return;

        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        Matrix4f proj = ctx.projectionMatrix();
        if (ms == null || consumers == null) return;

        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        long now = System.currentTimeMillis();
        INV.set(cam.getRotation()).conjugate();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int maxDist = WaypointStore.maxDist;
        boolean hideLive = WaypointStore.hideLive;

        MatrixStack.Entry entry = null;
        VertexConsumer lines = null;

        for (int i = 0; i < act.size(); i++) {
            Waypoint w = act.get(i);

            double ax = w.centerX(), ay = w.y2 + 1.5, az = w.centerZ();
            double dx = ax - cp.x, dy = ay - cp.y, dz = az - cp.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (maxDist > 0 && dist > maxDist) continue;

            // pozycja punktu w przestrzeni kamery -> (clip.w > 0 = przed kamera)
            V.set((float) dx, (float) dy, (float) dz);
            INV.transform(V);
            C.set(V.x, V.y, V.z, 1f);
            if (proj != null) proj.transform(C);
            else C.w = 1f;

            double ex = w.x2 - w.x1 + 1, ey = w.y2 - w.y1 + 1, ez = w.z2 - w.z1 + 1;
            float radius = (float) (0.5 * Math.sqrt(ex * ex + ey * ey + ez * ez)) + 2f;
            if (C.w < -radius) continue;   // caly obszar za plecami - nic nie rysuj

            // obrys obszaru: czerwony = jest lewitujacy blok, bialy = brak
            if (boxes && dist < 400) {
                if (lines == null) {
                    entry = ms.peek();
                    lines = consumers.getBuffer(RenderLayer.getLines());
                }
                box(lines, entry, cp, w.x1, w.y1, w.z1, w.x2, w.y2, w.z2, w.statusColor());
            }

            if (!labels) continue;
            if (hideLive && w.isLive(now) && dist < 12) continue;
            if (proj == null || C.w <= 0.05f) continue;
            float nx = C.x / C.w, ny = C.y / C.w;
            if (Math.abs(nx) > 1.3f || Math.abs(ny) > 1.3f) continue;
            WaypointHud.add(w, (nx * 0.5f + 0.5f) * sw, (0.5f - ny * 0.5f) * sh, dist);
        }

        // pierwszy zaznaczony rog (zolty)
        if (hasPend) {
            if (lines == null) {
                entry = ms.peek();
                lines = consumers.getBuffer(RenderLayer.getLines());
            }
            box(lines, entry, cp, pend.getX(), pend.getY(), pend.getZ(), pend.getX(), pend.getY(), pend.getZ(), 0xFFFF55);
        }

        if (lines != null && consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayer.getLines());
        }
        Perf.world(System.nanoTime() - t0);
    }

    private static void box(VertexConsumer vc, MatrixStack.Entry e, Vec3d cp,
                            int bx1, int by1, int bz1, int bx2, int by2, int bz2, int rgb) {
        float x0 = (float) (bx1 - cp.x) - 0.01f;
        float y0 = (float) (by1 - cp.y) - 0.01f;
        float z0 = (float) (bz1 - cp.z) - 0.01f;
        float x1 = (float) (bx2 + 1 - cp.x) + 0.01f;
        float y1 = (float) (by2 + 1 - cp.y) + 0.01f;
        float z1 = (float) (bz2 + 1 - cp.z) + 0.01f;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;

        // krawedzie wzdluz X
        line(vc, e, x0, y0, z0, x1, y0, z0, r, g, bl, 1, 0, 0);
        line(vc, e, x0, y1, z0, x1, y1, z0, r, g, bl, 1, 0, 0);
        line(vc, e, x0, y0, z1, x1, y0, z1, r, g, bl, 1, 0, 0);
        line(vc, e, x0, y1, z1, x1, y1, z1, r, g, bl, 1, 0, 0);
        // krawedzie wzdluz Y
        line(vc, e, x0, y0, z0, x0, y1, z0, r, g, bl, 0, 1, 0);
        line(vc, e, x1, y0, z0, x1, y1, z0, r, g, bl, 0, 1, 0);
        line(vc, e, x0, y0, z1, x0, y1, z1, r, g, bl, 0, 1, 0);
        line(vc, e, x1, y0, z1, x1, y1, z1, r, g, bl, 0, 1, 0);
        // krawedzie wzdluz Z
        line(vc, e, x0, y0, z0, x0, y0, z1, r, g, bl, 0, 0, 1);
        line(vc, e, x1, y0, z0, x1, y0, z1, r, g, bl, 0, 0, 1);
        line(vc, e, x0, y1, z0, x0, y1, z1, r, g, bl, 0, 0, 1);
        line(vc, e, x1, y1, z0, x1, y1, z1, r, g, bl, 0, 0, 1);
    }

    private static void line(VertexConsumer vc, MatrixStack.Entry e,
                             float ax, float ay, float az, float bx, float by, float bz,
                             int r, int g, int b, float nx, float ny, float nz) {
        Matrix4f m = e.getPositionMatrix();
        vc.vertex(m, ax, ay, az).color(r, g, b, 255).normal(e, nx, ny, nz);
        vc.vertex(m, bx, by, bz).color(r, g, b, 255).normal(e, nx, ny, nz);
    }
}
