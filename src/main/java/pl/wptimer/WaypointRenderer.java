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

public class WaypointRenderer {

    public static void render(WorldRenderContext ctx) {
        WaypointHud.begin();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        Matrix4f proj = ctx.projectionMatrix();
        if (ms == null || consumers == null) return;

        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        String key = WaypointStore.currentKey(mc);
        long now = System.currentTimeMillis();

        MatrixStack.Entry entry = ms.peek();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        Quaternionf inv = new Quaternionf(cam.getRotation()).conjugate();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (Waypoint w : WaypointStore.list) {
            if (!key.equals(w.world)) continue;

            double ax = w.centerX(), ay = w.y2 + 1.5, az = w.centerZ();
            double dx = ax - cp.x, dy = ay - cp.y, dz = az - cp.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // obrys obszaru: czerwony = sa bloki generatora, bialy = brak blokow
            if (WaypointStore.showBoxes && dist < 400) {
                box(lines, entry, cp, w.x1, w.y1, w.z1, w.x2, w.y2, w.z2, w.statusColor());
            }

            // napis na ekranie (widac z daleka, przez sciany)
            if (WaypointStore.hideLive && w.isLive(now) && dist < 12) continue;
            if (proj == null) continue;

            Vector3f v = new Vector3f((float) dx, (float) dy, (float) dz);
            inv.transform(v);
            Vector4f c = new Vector4f(v.x, v.y, v.z, 1f);
            proj.transform(c);
            if (c.w <= 0.05f) continue;
            float nx = c.x / c.w, ny = c.y / c.w;
            if (Math.abs(nx) > 1.3f || Math.abs(ny) > 1.3f) continue;
            float sx = (nx * 0.5f + 0.5f) * sw;
            float sy = (0.5f - ny * 0.5f) * sh;
            WaypointHud.add(w, sx, sy, dist);
        }

        // pierwszy zaznaczony rog (zolty)
        BlockPos pend = WaypointStore.pending;
        if (pend != null && key.equals(WaypointStore.pendingWorld)) {
            box(lines, entry, cp, pend.getX(), pend.getY(), pend.getZ(), pend.getX(), pend.getY(), pend.getZ(), 0xFFFF55);
        }

        if (consumers instanceof VertexConsumerProvider.Immediate imm) imm.draw();
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
