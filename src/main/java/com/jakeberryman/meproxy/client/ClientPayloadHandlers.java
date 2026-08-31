package com.jakeberryman.meproxy.client;

import com.jakeberryman.meproxy.network.BridgePackets;
import net.minecraft.client.Minecraft;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleStatus(BridgePackets.BridgeStatus payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof BridgeStatusScreen screen && screen.getPos().equals(payload.pos())) {
            screen.updateStatus(payload);
        } else if (payload.openScreen()) {
            minecraft.setScreen(new BridgeStatusScreen(payload));
        }
    }

    public static void handleBreakdown(BridgePackets.BreakdownResults payload) {
        if (Minecraft.getInstance().screen instanceof BridgeStatusScreen screen && screen.getPos().equals(payload.pos())) {
            screen.updateBreakdown(payload.entries());
        }
    }

    public static void handleGridList(com.jakeberryman.meproxy.network.GridPackets.GridList payload) {
        if (Minecraft.getInstance().screen instanceof UniversalGridScreen screen && screen.getPos().equals(payload.pos())) {
            screen.updateList(payload.entries());
        }
    }

    public static void handleGridStats(com.jakeberryman.meproxy.network.GridPackets.GridStats payload) {
        if (Minecraft.getInstance().screen instanceof UniversalGridScreen screen && screen.getPos().equals(payload.pos())) {
            screen.updateStats(payload);
        }
    }
}
