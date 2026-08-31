package com.jakeberryman.meproxy.compat;

import com.jakeberryman.meproxy.content.grid.WirelessUniversalGridItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

public final class CuriosCompat {
    private CuriosCompat() {
    }

    public static ItemStack findWirelessGrid(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inventory -> inventory.findFirstCurio(stack -> stack.getItem() instanceof WirelessUniversalGridItem))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }
}
