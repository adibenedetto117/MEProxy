package com.jakeberryman.meproxy.content.bridge;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;
import net.minecraft.network.chat.Component;

public class RsViewMEStorage implements MEStorage {
    static final Actor BRIDGE_ACTOR = () -> "meproxy_bridge";

    private final NetworkBridgeBlockEntity blockEntity;

    public RsViewMEStorage(NetworkBridgeBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!BridgeGuard.enter()) {
            return;
        }
        try {
            RootStorage root = blockEntity.getRsRootStorage();
            if (root == null) {
                return;
            }
            for (ResourceAmount resourceAmount : root.getAll()) {
                AEKey key = BridgeResources.toAEKey(resourceAmount.resource());
                if (key != null) {
                    out.add(key, resourceAmount.amount());
                }
            }
            for (var bridgeCache : blockEntity.getAllBridgeCachesOnRsNetwork()) {
                for (ResourceAmount resourceAmount : bridgeCache) {
                    AEKey key = BridgeResources.toAEKey(resourceAmount.resource());
                    if (key != null) {
                        out.add(key, -resourceAmount.amount());
                    }
                }
            }
            out.removeZeros();
        } finally {
            BridgeGuard.exit();
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!BridgeGuard.enter()) {
            return 0;
        }
        try {
            RootStorage root = blockEntity.getRsRootStorage();
            if (root == null) {
                return 0;
            }
            ResourceKey resource = BridgeResources.toResource(what);
            if (resource == null) {
                return 0;
            }
            return root.insert(resource, amount, toAction(mode), BRIDGE_ACTOR);
        } finally {
            BridgeGuard.exit();
        }
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!BridgeGuard.enter()) {
            return 0;
        }
        try {
            RootStorage root = blockEntity.getRsRootStorage();
            if (root == null) {
                return 0;
            }
            ResourceKey resource = BridgeResources.toResource(what);
            if (resource == null) {
                return 0;
            }
            return root.extract(resource, amount, toAction(mode), BRIDGE_ACTOR);
        } finally {
            BridgeGuard.exit();
        }
    }

    private static Action toAction(Actionable mode) {
        return mode == Actionable.MODULATE ? Action.EXECUTE : Action.SIMULATE;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("block.meproxy.network_bridge");
    }
}
