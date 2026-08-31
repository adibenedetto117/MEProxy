package com.jakeberryman.meproxy.content.grid;

import appeng.api.stacks.AEItemKey;
import com.jakeberryman.meproxy.content.bridge.NetworkBridgeBlockEntity;
import com.jakeberryman.meproxy.entry.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class UniversalGridBlockEntity extends BlockEntity {
    private final SimpleContainer buffer = new SimpleContainer(1);
    private int insertTarget;

    public UniversalGridBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.UNIVERSAL_GRID_BLOCK_ENTITY.get(), pos, state);
    }

    public SimpleContainer getBuffer() {
        return buffer;
    }

    public int getInsertTarget() {
        return insertTarget;
    }

    public void setInsertTarget(int target) {
        insertTarget = Math.floorMod(target, 3);
        setChanged();
    }

    @Nullable
    public NetworkBridgeBlockEntity findBridge() {
        if (level == null) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof NetworkBridgeBlockEntity bridge) {
                return bridge;
            }
        }
        return null;
    }

    public void serverTick() {
        ItemStack stack = buffer.getItem(0);
        if (stack.isEmpty()) {
            return;
        }
        NetworkBridgeBlockEntity bridge = findBridge();
        if (bridge == null) {
            return;
        }
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return;
        }
        long inserted = bridge.insertTo(insertTarget, key, stack.getCount());
        if (inserted > 0) {
            stack.shrink((int) inserted);
            buffer.setItem(0, stack);
            setChanged();
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("InsertTarget", insertTarget);
        if (!buffer.getItem(0).isEmpty()) {
            tag.put("Buffer", buffer.getItem(0).save(provider));
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        insertTarget = tag.getInt("InsertTarget");
        if (tag.contains("Buffer")) {
            buffer.setItem(0, ItemStack.parseOptional(provider, tag.getCompound("Buffer")));
        }
    }
}
