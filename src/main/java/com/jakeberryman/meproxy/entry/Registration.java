package com.jakeberryman.meproxy.entry;

import com.jakeberryman.meproxy.MEProxy;
import com.jakeberryman.meproxy.content.meProxy.MEProxyBlock;
import com.jakeberryman.meproxy.content.meProxy.MEProxyBlockEntity;
import com.jakeberryman.meproxy.content.meProxy.MEProxyBlockItem;
import com.jakeberryman.meproxy.content.meProxy.MEProxyInventoryHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class Registration {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MEProxy.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MEProxy.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MEProxy.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MEProxy.MODID);

    public static final DeferredBlock<MEProxyBlock> ME_PROXY_BLOCK = BLOCKS.register("me_proxy", MEProxyBlock::new);

    public static final DeferredItem<MEProxyBlockItem> ME_PROXY_ITEM = ITEMS.register("me_proxy",
            () -> new MEProxyBlockItem(ME_PROXY_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MEProxyBlockEntity>> ME_PROXY_BLOCK_ENTITY = BLOCK_ENTITIES.register("me_proxy",
            () -> BlockEntityType.Builder.of(
                    (pos, state) -> new MEProxyBlockEntity(Registration.ME_PROXY_BLOCK_ENTITY.get(), pos, state),
                    ME_PROXY_BLOCK.get()
            ).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("meproxy",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.meproxy"))
                    .icon(() -> new ItemStack(ME_PROXY_ITEM.get()))
                    .displayItems((parameters, output) -> output.accept(ME_PROXY_ITEM.get()))
                    .build());

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);

        modEventBus.addListener(Registration::registerCapabilities);
        modEventBus.addListener(Registration::commonSetup);
    }

    private static void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        ME_PROXY_BLOCK.get().setBlockEntity(MEProxyBlockEntity.class, ME_PROXY_BLOCK_ENTITY.get(), null, null);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ME_PROXY_BLOCK_ENTITY.get(),
                (blockEntity, side) -> blockEntity.getHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ME_PROXY_BLOCK_ENTITY.get(),
                (blockEntity, side) -> blockEntity.getHandler());
    }
}
