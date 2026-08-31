package com.jakeberryman.meproxy.content.meProxy;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
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

    private int cachedAmount(appeng.api.stacks.AEKey key) {
        return (int) Math.min(cachedStacks.get(key), Integer.MAX_VALUE);
    }

    @Override
    public int getTanks() {
        return refreshCache() ? fluidKeys.size() : 0;
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        if (!refreshCache() || tank >= fluidKeys.size())
            return FluidStack.EMPTY;

        AEFluidKey key = fluidKeys.get(tank);
        return key.toStack(cachedAmount(key));
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        MEStorage storage = storage();
        if (storage == null || stack.isEmpty())
            return false;

        return storage.insert(AEFluidKey.of(stack), stack.getAmount(), Actionable.SIMULATE, IActionSource.empty()) > 0;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        MEStorage storage = storage();
        if (storage == null || resource.isEmpty())
            return 0;

        int filled = (int) storage.insert(AEFluidKey.of(resource), resource.getAmount(), action == FluidAction.EXECUTE ? Actionable.MODULATE : Actionable.SIMULATE, IActionSource.empty());
        if (filled > 0 && action == FluidAction.EXECUTE)
            invalidateCache();
        return filled;
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        MEStorage storage = storage();
        if (storage == null || resource.isEmpty())
            return FluidStack.EMPTY;

        FluidStack copied = resource.copy();
        copied.setAmount(((int) storage.extract(AEFluidKey.of(resource), resource.getAmount(), action == FluidAction.EXECUTE ? Actionable.MODULATE : Actionable.SIMULATE, IActionSource.empty())));
        if (copied.getAmount() > 0 && action == FluidAction.EXECUTE)
            invalidateCache();
        return copied.getAmount() > 0 ? copied : FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack first = getFluidInTank(0);
        if (first.isEmpty())
            return FluidStack.EMPTY;

        FluidStack request = first.copy();
        request.setAmount(Math.min(maxDrain, first.getAmount()));
        return drain(request, action);
    }

    @Override
    public int getSlots() {
        return refreshCache() ? itemKeys.size() + EXTRA_INSERT_SLOTS : 0;
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!refreshCache() || slot >= itemKeys.size())
            return ItemStack.EMPTY;

        AEItemKey key = itemKeys.get(slot);
        return key.toStack(cachedAmount(key));
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        MEStorage storage = storage();
        if (storage == null || stack.isEmpty())
            return stack;

        int inserted = (int) storage.insert(AEItemKey.of(stack), stack.getCount(), simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
        if (inserted <= 0)
            return stack;

        if (!simulate)
            invalidateCache();

        if (inserted >= stack.getCount())
            return ItemStack.EMPTY;

        ItemStack remainder = stack.copy();
        remainder.setCount(stack.getCount() - inserted);
        return remainder;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        MEStorage storage = storage();
        if (storage == null || !refreshCache() || slot >= itemKeys.size() || amount <= 0)
            return ItemStack.EMPTY;

        AEItemKey key = itemKeys.get(slot);
        int extracted = (int) storage.extract(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
        if (extracted <= 0)
            return ItemStack.EMPTY;

        if (!simulate)
            invalidateCache();

        return key.toStack(extracted);
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        MEStorage storage = storage();
        if (storage == null || stack.isEmpty())
            return false;

        return storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.SIMULATE, IActionSource.empty()) >= stack.getCount();
    }
}
