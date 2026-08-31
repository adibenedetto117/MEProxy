package com.jakeberryman.meproxy.content.grid;

import com.jakeberryman.meproxy.content.bridge.NetworkBridgeBlockEntity;
import com.jakeberryman.meproxy.entry.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class UniversalGridBlockEntity extends BlockEntity {
    public UniversalGridBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.UNIVERSAL_GRID_BLOCK_ENTITY.get(), pos, state);
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

}
