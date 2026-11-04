package pl.wptimer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public class WaypointTimerMod implements ClientModInitializer {
    public static KeyBinding selectKey;
    public static KeyBinding listKey;
    private static int tickCount = 0;

    @Override
    public void onInitializeClient() {
        WaypointStore.load();

        selectKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wptimer.select", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.wptimer"));
        listKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wptimer.list", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.wptimer"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (selectKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null && client.world != null) select(client);
            }
            while (listKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) client.setScreen(new ListScreen());
            }
            tickCount++;
            if (tickCount % 5 == 0) GeneratorScanner.scanHolograms(client);
            GeneratorScanner.scanBlocks(client, tickCount);
            WaypointStore.tick(client);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(WaypointRenderer::render);
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> WaypointHud.draw(drawContext));
    }

    private static void msg(MinecraftClient c, String s) {
        if (c.player != null) c.player.sendMessage(Text.literal(s), true);
    }

    private static void select(MinecraftClient c) {
        String key = WaypointStore.currentKey(c);

        if (c.player.isSneaking() && WaypointStore.pending != null) {
            WaypointStore.pending = null;
            msg(c, "Anulowano zaznaczanie");
            return;
        }

        HitResult hit = c.player.raycast(128.0, 1.0f, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            msg(c, "Wyceluj w blok");
            return;
        }
        BlockPos p = bhr.getBlockPos();

        if (WaypointStore.pending == null || !key.equals(WaypointStore.pendingWorld)) {
            WaypointStore.pending = p;
            WaypointStore.pendingWorld = key;
            msg(c, "Róg 1: " + p.getX() + " " + p.getY() + " " + p.getZ() + " – wskaż drugi róg i naciśnij G");
            return;
        }

        BlockPos a = WaypointStore.pending;
        Waypoint w = new Waypoint();
        w.world = key;
        w.x1 = Math.min(a.getX(), p.getX());
        w.y1 = Math.min(a.getY(), p.getY());
        w.z1 = Math.min(a.getZ(), p.getZ());
        w.x2 = Math.max(a.getX(), p.getX());
        w.y2 = Math.max(a.getY(), p.getY());
        w.z2 = Math.max(a.getZ(), p.getZ());
        WaypointStore.pending = null;

        if (w.volume() > GeneratorScanner.MAX_VOLUME) {
            msg(c, "Obszar za duży (max " + GeneratorScanner.MAX_VOLUME + " bloków)");
            return;
        }

        w.name = "Generator " + (WaypointStore.current(c).size() + 1);
        GeneratorScanner.recordTypes(c, w);
        WaypointStore.list.add(w);
        WaypointStore.save();

        if (w.types.isEmpty()) {
            msg(c, "Dodano obszar (pusty – liczę każdy blok). Czas wczyta się z hologramu.");
        } else {
            msg(c, "Dodano obszar (typów bloków: " + w.types.size() + "). Czas wczyta się z hologramu.");
        }
    }
}
