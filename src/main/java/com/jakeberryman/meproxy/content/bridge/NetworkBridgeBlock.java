package com.jakeberryman.meproxy.content.bridge;

import com.jakeberryman.meproxy.entry.Registration;
import com.jakeberryman.meproxy.network.BridgePackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof NetworkBridgeBlockEntity bridge) {
            if (serverPlayer.connection.hasChannel(BridgePackets.BridgeStatus.TYPE)) {
                PacketDistributor.sendToPlayer(serverPlayer, bridge.buildStatusPayload(true));
            } else {
                serverPlayer.sendSystemMessage(Component.literal("Update your meproxy mod to open the bridge status screen."));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
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
