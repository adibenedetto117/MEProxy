package com.jakeberryman.meproxy.content.bridge;

import com.jakeberryman.meproxy.entry.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class NetworkBridgeBlock extends Block implements EntityBlock {
    public NetworkBridgeBlock() {
        super(Properties.of().strength(2.2f, 11.0f).sound(SoundType.METAL).noOcclusion());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkBridgeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != Registration.NETWORK_BRIDGE_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            NetworkBridgeBlockEntity bridge = (NetworkBridgeBlockEntity) blockEntity;
            bridge.updateActiveness(tickState, null);
            bridge.doWork();
        };
    }
}
