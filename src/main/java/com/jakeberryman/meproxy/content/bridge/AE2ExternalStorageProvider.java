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
import com.refinedmods.refinedstorage.api.storage.external.ExternalStorageProvider;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class AE2ExternalStorageProvider implements ExternalStorageProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static long lastFailureLog;

    private final NetworkBridgeBlockEntity blockEntity;

    static void logFailure(String message, Object... args) {
        long now = System.currentTimeMillis();
        if (now - lastFailureLog > 1000) {
            lastFailureLog = now;
            LOGGER.info(message, args);
        }
    }

    public AE2ExternalStorageProvider(NetworkBridgeBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public Iterator<ResourceAmount> iterator() {
        if (!BridgeGuard.enter()) {
            return Collections.emptyIterator();
        }
        try {
            MEStorage storage = blockEntity.getAe2Storage();
            if (storage == null) {
                return Collections.emptyIterator();
            }
            KeyCounter counter = new KeyCounter();
            storage.getAvailableStacks(counter);
            List<ResourceAmount> resources = new ArrayList<>(counter.size());
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                ResourceKey resource = BridgeResources.toResource(entry.getKey());
                if (resource != null) {
                    resources.add(new ResourceAmount(resource, amount));
                }
            }
            return resources.iterator();
        } finally {
            BridgeGuard.exit();
        }
    }

    @Override
    public long insert(ResourceKey resource, long amount, Action action, Actor actor) {
        if (!BridgeGuard.enter()) {
            return 0;
        }
        try {
            MEStorage storage = blockEntity.getAe2Storage();
            if (storage == null) {
                return 0;
            }
            AEKey key = BridgeResources.toAEKey(resource);
            if (key == null) {
                return 0;
            }
            return storage.insert(key, amount, toActionable(action), IActionSource.empty());
        } finally {
            BridgeGuard.exit();
        }
    }

    @Override
    public long extract(ResourceKey resource, long amount, Action action, Actor actor) {
        if (!BridgeGuard.enter()) {
            logFailure("[meproxy debug] RS->AE2 extract of {} x{} blocked by re-entrancy guard", resource, amount);
            return 0;
        }
        try {
            MEStorage storage = blockEntity.getAe2Storage();
            if (storage == null) {
                logFailure("[meproxy debug] RS->AE2 extract of {} x{} failed: AE2 grid not available (channel/power?)", resource, amount);
                return 0;
            }
            AEKey key = BridgeResources.toAEKey(resource);
            if (key == null) {
                logFailure("[meproxy debug] RS->AE2 extract of {} x{} failed: resource not convertible to AE key", resource, amount);
                return 0;
            }
            long extracted = storage.extract(key, amount, toActionable(action), IActionSource.empty());
            if (extracted == 0 && amount > 0) {
                logFailure("[meproxy debug] RS->AE2 extract of {} x{} returned 0 from AE2 storage (key {})", resource, amount, key);
            }
            return extracted;
        } finally {
            BridgeGuard.exit();
        }
    }

    private static Actionable toActionable(Action action) {
        return action == Action.EXECUTE ? Actionable.MODULATE : Actionable.SIMULATE;
    }
}
