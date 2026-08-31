package com.jakeberryman.meproxy.content.meProxy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MEProxyBlockItem extends BlockItem {
    public MEProxyBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        String key = getDescriptionId().replaceFirst("^block\\.", "tooltip.");
        tooltip.add(Component.translatable(key).setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));

        String usageKey = key + ".usage";
        if (net.minecraft.locale.Language.getInstance().has(usageKey)) {
            tooltip.add(Component.translatable(usageKey).setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)));
        }
    }
}
