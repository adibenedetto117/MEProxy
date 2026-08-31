package com.jakeberryman.meproxy.content.grid;

import com.jakeberryman.meproxy.entry.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class UniversalGridBlock extends Block implements EntityBlock {
    public UniversalGridBlock() {
        super(Properties.of().strength(2.2f, 11.0f).sound(SoundType.METAL).noOcclusion());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UniversalGridBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != Registration.UNIVERSAL_GRID_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> ((UniversalGridBlockEntity) blockEntity).serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof UniversalGridBlockEntity) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new UniversalGridMenu(id, inventory, pos),
                    Component.translatable("block.meproxy.universal_grid")), buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
