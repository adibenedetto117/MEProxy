package com.jakeberryman.meproxy.content.grid;

import appeng.api.stacks.AEItemKey;
import com.jakeberryman.meproxy.content.bridge.NetworkBridgeBlockEntity;
import com.jakeberryman.meproxy.entry.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class UniversalGridMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    private final boolean wireless;
    @Nullable
    private final net.minecraft.core.GlobalPos wirelessBridge;

    public UniversalGridMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, pos, false, null);
    }

    public UniversalGridMenu(int containerId, Inventory inventory, net.minecraft.core.GlobalPos linkedBridge) {
        this(containerId, inventory, linkedBridge.pos(), true, linkedBridge);
    }

    public UniversalGridMenu(int containerId, Inventory inventory, net.minecraft.network.FriendlyByteBuf buf) {
        this(containerId, inventory, readPos(buf), false, null);
    }

    private static BlockPos readPos(net.minecraft.network.FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            buf.readResourceKey(net.minecraft.core.registries.Registries.DIMENSION);
        }
        return buf.readBlockPos();
    }

    private UniversalGridMenu(int containerId, Inventory inventory, BlockPos pos, boolean wireless,
                              @Nullable net.minecraft.core.GlobalPos wirelessBridge) {
        super(Registration.UNIVERSAL_GRID_MENU.get(), containerId);
        this.pos = pos;
        this.wireless = wireless;
        this.wirelessBridge = wirelessBridge;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 108 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 166));
        }
    }

    @Nullable
    public NetworkBridgeBlockEntity resolveBridge(Player player) {
        if (wireless) {
            if (wirelessBridge == null || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return null;
            }
            var level = serverPlayer.server.getLevel(wirelessBridge.dimension());
            return level != null && level.getBlockEntity(wirelessBridge.pos()) instanceof NetworkBridgeBlockEntity bridge
                    ? bridge : null;
        }
        if (!player.blockPosition().closerThan(pos, 8)) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof UniversalGridBlockEntity grid ? grid.findBridge() : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (player.level().isClientSide()) {
            return ItemStack.EMPTY;
        }

        NetworkBridgeBlockEntity bridge = resolveBridge(player);
        if (bridge == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return ItemStack.EMPTY;
        }

        long inserted = bridge.insertTo(0, key, stack.getCount());
        if (inserted > 0) {
            stack.shrink((int) inserted);
            slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (wireless) {
            return true;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
