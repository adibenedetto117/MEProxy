package com.jakeberryman.meproxy.content.grid;

import com.jakeberryman.meproxy.content.bridge.NetworkBridgeBlockEntity;
import com.jakeberryman.meproxy.entry.Registration;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WirelessUniversalGridItem extends Item {
    public WirelessUniversalGridItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @NotNull
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof NetworkBridgeBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            context.getItemInHand().set(Registration.LINKED_BRIDGE.get(),
                    GlobalPos.of(level.dimension(), context.getClickedPos()));
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.literal("Linked to Network Bridge."), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static void open(ServerPlayer player, ItemStack stack) {
        GlobalPos linked = stack.get(Registration.LINKED_BRIDGE.get());
        if (linked == null) {
            player.displayClientMessage(Component.literal("Not linked. Sneak-right-click a Network Bridge first."), true);
            return;
        }

        ServerLevel targetLevel = player.server.getLevel(linked.dimension());
        if (targetLevel == null
                || !(targetLevel.getBlockEntity(linked.pos()) instanceof NetworkBridgeBlockEntity)) {
            player.displayClientMessage(Component.literal("Linked bridge is gone or its chunk is not loaded."), true);
            return;
        }

        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new UniversalGridMenu(id, inventory, linked),
                Component.translatable("item.meproxy.wireless_universal_grid")), buf -> {
            buf.writeBoolean(true);
            buf.writeResourceKey(linked.dimension());
            buf.writeBlockPos(linked.pos());
        });
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        GlobalPos linked = stack.get(Registration.LINKED_BRIDGE.get());
        if (linked != null) {
            tooltip.add(Component.literal("Linked: " + linked.pos().getX() + ", " + linked.pos().getY() + ", " + linked.pos().getZ())
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.literal("Sneak-right-click a Network Bridge to link").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("Right-click, or press the keybind while equipped, to open").withStyle(ChatFormatting.DARK_GRAY));
    }
}
