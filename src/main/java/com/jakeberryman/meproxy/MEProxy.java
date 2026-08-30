package com.jakeberryman.meproxy;

import com.jakeberryman.meproxy.entry.Registration;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(MEProxy.MODID)
public class MEProxy {

    public static final String MODID = "meproxy";

    public MEProxy(IEventBus modEventBus) {
        Registration.register(modEventBus);
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
