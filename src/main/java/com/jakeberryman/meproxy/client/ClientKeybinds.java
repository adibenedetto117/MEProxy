package com.jakeberryman.meproxy.client;

import com.jakeberryman.meproxy.network.GridPackets;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientKeybinds {
    public static final KeyMapping OPEN_WIRELESS_GRID = new KeyMapping(
            "key.meproxy.open_wireless_grid", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.misc");

    private ClientKeybinds() {
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_WIRELESS_GRID);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        while (OPEN_WIRELESS_GRID.consumeClick()) {
            PacketDistributor.sendToServer(new GridPackets.OpenWirelessGrid());
        }
    }
}
