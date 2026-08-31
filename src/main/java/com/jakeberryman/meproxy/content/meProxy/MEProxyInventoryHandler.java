package com.jakeberryman.meproxy.content.meProxy;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.jakeberryman.meproxy.content.bridge.BridgeGuard;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MEProxyInventoryHandler implements IItemHandler, IFluidHandler {
    private static final int EXTRA_INSERT_SLOTS = 16;

    private final MEProxyBlockEntity blockEntity;

    private long cacheTick = Long.MIN_VALUE;
    private KeyCounter cachedStacks;
    private List<AEItemKey> itemKeys = List.of();
    private List<AEFluidKey> fluidKeys = List.of();

    public MEProxyInventoryHandler(MEProxyBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Nullable
    private MEStorage storage() {
        IGrid grid = blockEntity.getMainNode().getGrid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    private boolean refreshCache() {
        MEStorage storage = storage();
        if (storage == null || blockEntity.getLevel() == null) {
            invalidateCache();
            return false;
        }

        long tick = blockEntity.getLevel().getGameTime();
        if (cachedStacks == null || tick != cacheTick) {
            cachedStacks = storage.getAvailableStacks();
            List<AEItemKey> items = new ArrayList<>();
            List<AEFluidKey> fluids = new ArrayList<>();
            for (var key : cachedStacks.keySet()) {
                if (key instanceof AEItemKey itemKey) {
                    items.add(itemKey);
                } else if (key instanceof AEFluidKey fluidKey) {
                    fluids.add(fluidKey);
                }
            }
            itemKeys = items;
            fluidKeys = fluids;
            cacheTick = tick;
        }
        return true;
    }

    private void invalidateCache() {
        cachedStacks = null;
        itemKeys = List.of();
        fluidKeys = List.of();
    }

    private int cachedAmount(AEKey key) {
        return (int) Math.min(cachedStacks.get(key), Integer.MAX_VALUE);
    }

    @Override
    public int getTanks() {
        if (!BridgeGuard.enter()) {
            return 0;
        }
        try {
            return refreshCache() ? fluidKeys.size() : 0;
        } finally {
            BridgeGuard.exit();
        }
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        if (!BridgeGuard.enter()) {
            return FluidStack.EMPTY;
        }
        try {
            if (!refreshCache() || tank >= fluidKeys.size()) {
                return FluidStack.EMPTY;
            }
            AEFluidKey key = fluidKeys.get(tank);
            return key.toStack(cachedAmount(key));
        } finally {
            BridgeGuard.exit();
        }
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        if (!BridgeGuard.enter()) {
            return false;
        }
        try {
            MEStorage storage = storage();
            if (storage == null || stack.isEmpty()) {
                return false;
            }
            return storage.insert(AEFluidKey.of(stack), stack.getAmount(), Actionable.SIMULATE, IActionSource.empty()) > 0;
        } finally {
            BridgeGuard.exit();
        }
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (!BridgeGuard.enter()) {
            return 0;
        }
        try {
            MEStorage storage = storage();
            if (storage == null || resource.isEmpty()) {
                return 0;
            }
            int filled = (int) storage.insert(AEFluidKey.of(resource), resource.getAmount(), action == FluidAction.EXECUTE ? Actionable.MODULATE : Actionable.SIMULATE, IActionSource.empty());
            if (filled > 0 && action == FluidAction.EXECUTE) {
                invalidateCache();
            }
            return filled;
        } finally {
            BridgeGuard.exit();
        }
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (!BridgeGuard.enter()) {
            return FluidStack.EMPTY;
        }
        try {
            return drainInternal(resource, action);
        } finally {
            BridgeGuard.exit();
        }
    }

    @NotNull
    private FluidStack drainInternal(FluidStack resource, FluidAction action) {
        MEStorage storage = storage();
        if (storage == null || resource.isEmpty()) {
            return FluidStack.EMPTY;
        }

        FluidStack copied = resource.copy();
        copied.setAmount(((int) storage.extract(AEFluidKey.of(resource), resource.getAmount(), action == FluidAction.EXECUTE ? Actionable.MODULATE : Actionable.SIMULATE, IActionSource.empty())));
        if (copied.getAmount() > 0 && action == FluidAction.EXECUTE) {
            invalidateCache();
        }
        return copied.getAmount() > 0 ? copied : FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (!BridgeGuard.enter()) {
            return FluidStack.EMPTY;
        }
        try {
            if (!refreshCache() || fluidKeys.isEmpty()) {
                return FluidStack.EMPTY;
            }
            AEFluidKey key = fluidKeys.get(0);
            FluidStack request = key.toStack(Math.min(maxDrain, cachedAmount(key)));
            if (request.isEmpty()) {
                return FluidStack.EMPTY;
            }
            return drainInternal(request, action);
        } finally {
            BridgeGuard.exit();
        }
    }

    @Override
    public int getSlots() {
        if (!BridgeGuard.enter()) {
            return 0;
        }
        try {
            return refreshCache() ? itemKeys.size() + EXTRA_INSERT_SLOTS : 0;
        } finally {
            BridgeGuard.exit();
        }
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!BridgeGuard.enter()) {
            return ItemStack.EMPTY;
        }
        try {
            if (!refreshCache() || slot >= itemKeys.size()) {
                return ItemStack.EMPTY;
            }
            AEItemKey key = itemKeys.get(slot);
            return key.toStack(cachedAmount(key));
        } finally {
            BridgeGuard.exit();
        }
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (!BridgeGuard.enter()) {
            return stack;
        }
        try {
            return insertInternal(stack, simulate);
        } finally {
            BridgeGuard.exit();
        }
    }

    @NotNull
    private ItemStack insertInternal(@NotNull ItemStack stack, boolean simulate) {
        MEStorage storage = storage();
        if (storage == null || stack.isEmpty()) {
            return stack;
        }

        int inserted = (int) storage.insert(AEItemKey.of(stack), stack.getCount(), simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
        if (inserted <= 0) {
            return stack;
        }

        if (!simulate) {
            invalidateCache();
        }

        if (inserted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        remainder.setCount(stack.getCount() - inserted);
        return remainder;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!BridgeGuard.enter()) {
            return ItemStack.EMPTY;
        }
        try {
            MEStorage storage = storage();
            if (storage == null || !refreshCache() || slot >= itemKeys.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }

            AEItemKey key = itemKeys.get(slot);
            int extracted = (int) storage.extract(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
            if (extracted <= 0) {
                return ItemStack.EMPTY;
            }

            if (!simulate) {
                invalidateCache();
            }

            return key.toStack(extracted);
        } finally {
            BridgeGuard.exit();
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        if (!BridgeGuard.enter()) {
            return false;
        }
        try {
            return insertInternal(stack, true).getCount() == 0;
        } finally {
            BridgeGuard.exit();
        }
    }
}
