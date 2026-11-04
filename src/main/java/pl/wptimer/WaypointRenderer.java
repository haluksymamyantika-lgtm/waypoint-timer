package pl.wptimer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class WaypointRenderer {
    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.options.hudHidden) return;

        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        if (ms == null || consumers == null) return;

        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        TextRenderer tr = mc.textRenderer;
        String key = WaypointStore.currentKey(mc);
        long now = System.currentTimeMillis();

        for (Waypoint w : WaypointStore.list) {
            if (!key.equals(w.world)) continue;

            double dx = w.x + 0.5 - cp.x;
            double dy = w.y + 1.5 - cp.y;
            double dz = w.z + 0.5 - cp.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double k = dist > 96 ? 96 / dist : 1.0;
            double d = dist * k;
            float s = 0.025f * WaypointStore.scale * (float) Math.max(1.0, d / 8.0);

            ms.push();
            ms.translate(dx * k, dy * k, dz * k);
            ms.multiply(cam.getRotation());
            ms.scale(-s, -s, s);
            Matrix4f mat = ms.peek().getPositionMatrix();

            drawCentered(tr, w.name, -12, 0xFFFFFFFF, mat, consumers);
            drawCentered(tr, w.timerText(now), 0, w.timerColor(now), mat, consumers);
            drawCentered(tr, (int) dist + " m", 12, 0xFFAAAAAA, mat, consumers);

            ms.pop();
        }

        if (consumers instanceof VertexConsumerProvider.Immediate imm) imm.draw();
    }

    private static void drawCentered(TextRenderer tr, String text, float y, int color,
                                     Matrix4f mat, VertexConsumerProvider consumers) {
        float x = -tr.getWidth(text) / 2f;
        tr.draw(text, x, y, color, false, mat, consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }
}
