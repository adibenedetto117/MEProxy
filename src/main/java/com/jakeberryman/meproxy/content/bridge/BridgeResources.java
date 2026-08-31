package com.jakeberryman.meproxy.content.bridge;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class BridgeResources {
    private BridgeResources() {
    }

    @Nullable
    public static ResourceKey toResource(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            return ItemResource.ofItemStack(itemKey.getReadOnlyStack());
        }
        if (key instanceof AEFluidKey fluidKey) {
            FluidStack stack = fluidKey.toStack(1);
            return new FluidResource(stack.getFluid(), stack.getComponentsPatch());
        }
        return null;
    }

    @Nullable
    public static AEKey toAEKey(ResourceKey resource) {
        if (resource instanceof ItemResource itemResource) {
            return AEItemKey.of(itemResource.toItemStack());
        }
        if (resource instanceof FluidResource fluidResource) {
            FluidStack stack = new FluidStack(fluidResource.fluid(), 1);
            if (!fluidResource.components().isEmpty()) {
                stack.applyComponents(fluidResource.components());
            }
            return AEFluidKey.of(stack);
        }
        return null;
    }
}
