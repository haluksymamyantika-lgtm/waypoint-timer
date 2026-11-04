package pl.wptimer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class WaypointTimerMod implements ClientModInitializer {
    public static KeyBinding addKey;
    public static KeyBinding listKey;

    @Override
    public void onInitializeClient() {
        WaypointStore.load();

        addKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wptimer.add", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.wptimer"));
        listKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wptimer.list", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.wptimer"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (addKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) client.setScreen(new AddScreen(null));
            }
            while (listKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) client.setScreen(new ListScreen());
            }
            WaypointStore.tick(client);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(WaypointRenderer::render);
    }
}
