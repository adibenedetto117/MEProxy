package com.jakeberryman.meproxy.content.bridge;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import com.jakeberryman.meproxy.entry.Registration;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.impl.node.externalstorage.ExternalStorageNetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;
import com.refinedmods.refinedstorage.api.storage.root.RootStorageListener;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public class NetworkBridgeBlockEntity extends AbstractBaseNetworkNodeContainerBlockEntity<ExternalStorageNetworkNode>
        implements IInWorldGridNodeHost, IStorageProvider {

    private static final Set<NetworkBridgeBlockEntity> BRIDGES = Collections.newSetFromMap(new WeakHashMap<>());

    private static final long RS_ENERGY_USAGE = 6L;
    private static final double AE2_IDLE_POWER = 5.0;
    private static final int DETECT_CHANGES_INTERVAL = 10;
    private static final int BRIDGE_PRIORITY = -1000;

    private static final IGridNodeListener<NetworkBridgeBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(NetworkBridgeBlockEntity blockEntity, IGridNode node) {
            blockEntity.setChanged();
        }

        @Override
        public void onStateChanged(NetworkBridgeBlockEntity blockEntity, IGridNode node, State state) {
            blockEntity.remountAe2Storage();
        }
    };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(AE2_IDLE_POWER)
            .setInWorldNode(true);

    private final RsViewMEStorage rsView = new RsViewMEStorage(this);
    private final RootStorageListener rsChangeListener = change -> rsChanged = true;

    @Nullable
    private Network listeningNetwork;
    private volatile boolean rsChanged;
    private int ticksSinceDetect;

    private final java.util.Map<appeng.api.stacks.AEKey, long[]> transferVolume = new java.util.HashMap<>();
    private final List<PendingCraft> pendingCrafts = new ArrayList<>();

    private String bridgeName = "";
    private long itemsToRs;
    private long itemsToAe2;
    private long fluidsToRs;
    private long fluidsToAe2;
    private double rateToRs;
    private double rateToAe2;
    private long prevTotalToRs;
    private long prevTotalToAe2;
    private int rateTicks;

    public NetworkBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.NETWORK_BRIDGE_BLOCK_ENTITY.get(), pos, state,
                new ExternalStorageNetworkNode(RS_ENERGY_USAGE, System::currentTimeMillis));
        mainNode.addService(IStorageProvider.class, this);
        mainNetworkNode.initialize(new AE2ExternalStorageProvider(this));
        mainNetworkNode.getStorageConfiguration().setInsertPriority(BRIDGE_PRIORITY);
        mainNetworkNode.getStorageConfiguration().setExtractPriority(BRIDGE_PRIORITY);
        synchronized (BRIDGES) {
            BRIDGES.add(this);
        }
    }

    @Override
    public void doWork() {
        super.doWork();
        if (level == null || level.isClientSide()) {
            return;
        }

        if (++ticksSinceDetect >= DETECT_CHANGES_INTERVAL) {
            ticksSinceDetect = 0;
            updateRsListener();
            if (mainNetworkNode.isActive() && mainNetworkNode.getNetwork() != null) {
                mainNetworkNode.detectChanges();
            }
        }

        if (rsChanged) {
            rsChanged = false;
            IGrid grid = mainNode.getGrid();
            if (grid != null) {
                grid.getStorageService().invalidateCache();
            }
        }

        pollPendingCrafts();

        if (++rateTicks >= 20) {
            rateTicks = 0;
            long totalToRs = itemsToRs + fluidsToRs;
            long totalToAe2 = itemsToAe2 + fluidsToAe2;
            rateToRs = totalToRs - prevTotalToRs;
            rateToAe2 = totalToAe2 - prevTotalToAe2;
            prevTotalToRs = totalToRs;
            prevTotalToAe2 = totalToAe2;
        }
    }

    void recordTransfer(appeng.api.stacks.AEKey key, boolean towardRs, long amount) {
        if (amount <= 0) {
            return;
        }
        boolean fluid = key instanceof appeng.api.stacks.AEFluidKey;
        if (towardRs) {
            if (fluid) fluidsToRs += amount; else itemsToRs += amount;
        } else {
            if (fluid) fluidsToAe2 += amount; else itemsToAe2 += amount;
        }
        long[] volume = transferVolume.computeIfAbsent(key, k -> new long[2]);
        volume[towardRs ? 0 : 1] += amount;
        if (transferVolume.size() > 256) {
            pruneTransferVolume();
        }
        setChanged();
    }

    private void pruneTransferVolume() {
        List<java.util.Map.Entry<appeng.api.stacks.AEKey, long[]>> sorted = new ArrayList<>(transferVolume.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]));
        transferVolume.clear();
        for (int i = 0; i < Math.min(128, sorted.size()); i++) {
            transferVolume.put(sorted.get(i).getKey(), sorted.get(i).getValue());
        }
    }

    public appeng.api.stacks.KeyCounter nativeStacks(boolean ae2Side) {
        return ae2Side ? ae2NativeStacks() : rsNativeStacks();
    }

    public long extractNative(boolean ae2Side, appeng.api.stacks.AEKey key, long amount) {
        if (!BridgeGuard.enter()) {
            return 0;
        }
        try {
            if (ae2Side) {
                MEStorage storage = getAe2Storage();
                return storage == null ? 0
                        : storage.extract(key, amount, appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
            }
            RootStorage root = getRsRootStorage();
            if (root == null) {
                return 0;
            }
            var resource = BridgeResources.toResource(key);
            if (resource == null) {
                return 0;
            }
            return root.extract(resource, amount, com.refinedmods.refinedstorage.api.core.Action.EXECUTE, RsViewMEStorage.BRIDGE_ACTOR);
        } finally {
            BridgeGuard.exit();
        }
    }

    public long insertTo(int target, appeng.api.stacks.AEKey key, long amount) {
        if (target == 1 || target == 2) {
            if (!BridgeGuard.enter()) {
                return 0;
            }
            try {
                if (target == 1) {
                    MEStorage storage = getAe2Storage();
                    return storage == null ? 0
                            : storage.insert(key, amount, appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
                }
                RootStorage root = getRsRootStorage();
                var resource = BridgeResources.toResource(key);
                return root == null || resource == null ? 0
                        : root.insert(resource, amount, com.refinedmods.refinedstorage.api.core.Action.EXECUTE, RsViewMEStorage.BRIDGE_ACTOR);
            } finally {
                BridgeGuard.exit();
            }
        }

        long done = 0;
        MEStorage storage = getAe2Storage();
        if (storage != null) {
            done = storage.insert(key, amount, appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
        }
        if (done < amount) {
            RootStorage root = getRsRootStorage();
            var resource = BridgeResources.toResource(key);
            if (root != null && resource != null) {
                done += root.insert(resource, amount - done, com.refinedmods.refinedstorage.api.core.Action.EXECUTE, RsViewMEStorage.BRIDGE_ACTOR);
            }
        }
        return done;
    }

    public java.util.Set<appeng.api.stacks.AEKey> ae2Craftables() {
        IGrid grid = mainNode.getGrid();
        return grid == null ? java.util.Set.of()
                : grid.getCraftingService().getCraftables(key -> key instanceof appeng.api.stacks.AEItemKey);
    }

    public java.util.Set<appeng.api.stacks.AEKey> rsCraftables() {
        Network network = mainNetworkNode.getNetwork();
        if (network == null) {
            return java.util.Set.of();
        }
        var autocrafting = network.getComponent(com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent.class);
        java.util.Set<appeng.api.stacks.AEKey> outputs = new java.util.HashSet<>();
        for (var resource : autocrafting.getOutputs()) {
            var key = BridgeResources.toAEKey(resource);
            if (key instanceof appeng.api.stacks.AEItemKey) {
                outputs.add(key);
            }
        }
        return outputs;
    }

    public void requestAe2Craft(net.minecraft.server.level.ServerPlayer player, appeng.api.stacks.AEKey key, long amount) {
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("AE2 network is offline."));
            return;
        }
        var future = grid.getCraftingService().beginCraftingCalculation(
                player.level(), appeng.api.networking.security.IActionSource::empty, key, amount,
                appeng.api.networking.crafting.CalculationStrategy.CRAFT_LESS);
        pendingCrafts.add(new PendingCraft(future, player.getUUID()));
    }

    public void requestRsCraft(net.minecraft.server.level.ServerPlayer player, appeng.api.stacks.AEKey key, long amount) {
        Network network = mainNetworkNode.getNetwork();
        var resource = BridgeResources.toResource(key);
        if (network == null || resource == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("RS network is offline."));
            return;
        }
        var autocrafting = network.getComponent(com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent.class);
        var result = autocrafting.ensureTask(resource, amount,
                RsViewMEStorage.BRIDGE_ACTOR, com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken.NONE);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(switch (result) {
            case TASK_CREATED -> "RS craft started.";
            case TASK_ALREADY_RUNNING -> "RS is already crafting that.";
            case MISSING_RESOURCES -> "RS craft failed: missing resources.";
        }));
    }

    private record PendingCraft(java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> future,
                                java.util.UUID player) {
    }

    private void pollPendingCrafts() {
        if (pendingCrafts.isEmpty() || level == null || level.getServer() == null) {
            return;
        }
        var iterator = pendingCrafts.iterator();
        while (iterator.hasNext()) {
            PendingCraft pending = iterator.next();
            if (!pending.future().isDone()) {
                continue;
            }
            iterator.remove();
            String message;
            try {
                var plan = pending.future().get();
                IGrid grid = mainNode.getGrid();
                if (grid == null) {
                    message = "AE2 craft failed: network went offline.";
                } else {
                    var result = grid.getCraftingService().submitJob(plan, null, null, false,
                            appeng.api.networking.security.IActionSource.empty());
                    message = result.successful() ? "AE2 craft started."
                            : "AE2 craft failed: " + result.errorCode();
                }
            } catch (Exception e) {
                message = "AE2 craft failed: " + e.getMessage();
            }
            var target = level.getServer().getPlayerList().getPlayer(pending.player());
            if (target != null) {
                target.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
            }
        }
    }

    public java.util.List<com.jakeberryman.meproxy.network.BridgePackets.BreakdownEntry> topTransfers(int limit) {
        List<java.util.Map.Entry<appeng.api.stacks.AEKey, long[]>> sorted = new ArrayList<>(transferVolume.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]));
        List<com.jakeberryman.meproxy.network.BridgePackets.BreakdownEntry> result = new ArrayList<>();
        for (var entry : sorted) {
            if (result.size() >= limit) {
                break;
            }
            if (entry.getKey() instanceof appeng.api.stacks.AEItemKey itemKey) {
                result.add(new com.jakeberryman.meproxy.network.BridgePackets.BreakdownEntry(
                        itemKey.toStack(1), entry.getValue()[1], entry.getValue()[0]));
            }
        }
        return result;
    }

    public long[] transferTotals() {
        return new long[] {itemsToRs, itemsToAe2, fluidsToRs, fluidsToAe2, (long) rateToRs, (long) rateToAe2};
    }

    public void setBridgeName(String name) {
        bridgeName = name.length() > 60 ? name.substring(0, 60) : name;
        setChanged();
    }

    String describeAe2Status() {
        IGridNode node = mainNode.getNode();
        if (node == null) {
            return "offline: not connected (ME cable? chunk?)";
        }
        if (!node.isActive()) {
            return "offline: no channel or no AE2 network power";
        }
        return "online";
    }

    String describeRsStatus() {
        return getRsRootStorage() != null ? "online" : "offline: " + describeRsFailure();
    }

    private appeng.api.stacks.KeyCounter ae2NativeStacks() {
        var counter = new appeng.api.stacks.KeyCounter();
        if (BridgeGuard.enter()) {
            try {
                MEStorage storage = getAe2Storage();
                if (storage != null) {
                    storage.getAvailableStacks(counter);
                }
            } finally {
                BridgeGuard.exit();
            }
        }
        return counter;
    }

    private appeng.api.stacks.KeyCounter rsNativeStacks() {
        var counter = new appeng.api.stacks.KeyCounter();
        RootStorage root = getRsRootStorage();
        if (root == null) {
            return counter;
        }
        for (ResourceAmount resourceAmount : root.getAll()) {
            var key = BridgeResources.toAEKey(resourceAmount.resource());
            if (key != null) {
                counter.add(key, resourceAmount.amount());
            }
        }
        for (Collection<ResourceAmount> cache : getAllBridgeCachesOnRsNetwork()) {
            for (ResourceAmount resourceAmount : cache) {
                var key = BridgeResources.toAEKey(resourceAmount.resource());
                if (key != null) {
                    counter.add(key, -resourceAmount.amount());
                }
            }
        }
        counter.removeZeros();
        return counter;
    }

    public com.jakeberryman.meproxy.network.BridgePackets.BridgeStatus buildStatusPayload(boolean openScreen) {
        var ae2Native = ae2NativeStacks();
        var rsNative = rsNativeStacks();
        return new com.jakeberryman.meproxy.network.BridgePackets.BridgeStatus(
                worldPosition, openScreen, bridgeName,
                describeAe2Status(), describeRsStatus(),
                itemsToRs, itemsToAe2, fluidsToRs, fluidsToAe2,
                rateToRs, rateToAe2,
                ae2Native.size(), rsNative.size());
    }

    public List<com.jakeberryman.meproxy.network.BridgePackets.BreakdownEntry> queryBreakdown(String query) {
        var ae2Native = ae2NativeStacks();
        var rsNative = rsNativeStacks();
        String needle = query.toLowerCase(java.util.Locale.ROOT).trim();

        java.util.Set<appeng.api.stacks.AEKey> keys = new java.util.HashSet<>(ae2Native.keySet());
        keys.addAll(rsNative.keySet());

        List<com.jakeberryman.meproxy.network.BridgePackets.BreakdownEntry> entries = new ArrayList<>();
        for (var key : keys) {
            if (!(key instanceof appeng.api.stacks.AEItemKey itemKey)) {
                continue;
            }
            if (!needle.isEmpty()
                    && !itemKey.getReadOnlyStack().getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                continue;
            }
            entries.add(new com.jakeberryman.meproxy.network.BridgePackets.BreakdownEntry(
                    itemKey.toStack(1), ae2Native.get(key), rsNative.get(key)));
        }
        entries.sort((a, b) -> Long.compare(b.ae2Amount() + b.rsAmount(), a.ae2Amount() + a.rsAmount()));
        return entries.size() > 50 ? entries.subList(0, 50) : entries;
    }

    private void updateRsListener() {
        Network current = mainNetworkNode.getNetwork();
        if (current == listeningNetwork) {
            return;
        }
        if (listeningNetwork != null) {
            listeningNetwork.getComponent(StorageNetworkComponent.class).removeListener(rsChangeListener);
        }
        if (current != null) {
            current.getComponent(StorageNetworkComponent.class).addListener(rsChangeListener);
        }
        listeningNetwork = current;
        rsChanged = true;
    }

    @Nullable
    RootStorage getRsRootStorage() {
        Network network = mainNetworkNode.getNetwork();
        if (network == null || !mainNetworkNode.isActive()) {
            return null;
        }
        return network.getComponent(StorageNetworkComponent.class);
    }

    String describeRsFailure() {
        if (mainNetworkNode.getNetwork() == null) {
            return "RS node has no network (bridge not cabled to RS?)";
        }
        if (!mainNetworkNode.isActive()) {
            return "RS node inactive (RS network energy/controller?)";
        }
        return "unknown";
    }

    Collection<ResourceAmount> getBridgeSourceContents() {
        var storage = mainNetworkNode.getStorage();
        return storage == null ? List.of() : storage.getAll();
    }

    List<Collection<ResourceAmount>> getAllBridgeCachesOnRsNetwork() {
        Network network = mainNetworkNode.getNetwork();
        if (network == null) {
            return List.of();
        }
        List<Collection<ResourceAmount>> caches = new ArrayList<>();
        synchronized (BRIDGES) {
            for (NetworkBridgeBlockEntity bridge : BRIDGES) {
                if (bridge.mainNetworkNode.getNetwork() == network) {
                    caches.add(bridge.getBridgeSourceContents());
                }
            }
        }
        return caches;
    }

    List<NetworkBridgeBlockEntity> getBridgesOnSameAe2Grid() {
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return List.of();
        }
        List<NetworkBridgeBlockEntity> result = new ArrayList<>();
        synchronized (BRIDGES) {
            for (NetworkBridgeBlockEntity bridge : BRIDGES) {
                if (bridge.mainNode.getGrid() == grid) {
                    result.add(bridge);
                }
            }
        }
        return result;
    }

    @Nullable
    MEStorage getAe2Storage() {
        IGrid grid = mainNode.getGrid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    void remountAe2Storage() {
        if (mainNode.isReady()) {
            IStorageProvider.requestUpdate(mainNode);
        }
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        mounts.mount(rsView, BRIDGE_PRIORITY);
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            GridHelper.onFirstTick(this, blockEntity -> blockEntity.mainNode.create(level, worldPosition));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        detachRsListener();
        mainNode.destroy();
        synchronized (BRIDGES) {
            BRIDGES.remove(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        detachRsListener();
        mainNode.destroy();
    }

    private void detachRsListener() {
        if (listeningNetwork != null) {
            listeningNetwork.getComponent(StorageNetworkComponent.class).removeListener(rsChangeListener);
            listeningNetwork = null;
        }
    }

    @Override
    public net.minecraft.network.chat.Component getName() {
        return net.minecraft.network.chat.Component.translatable("block.meproxy.network_bridge");
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        mainNode.saveToNBT(tag);
        tag.putString("BridgeName", bridgeName);
        tag.putLong("ItemsToRs", itemsToRs);
        tag.putLong("ItemsToAe2", itemsToAe2);
        tag.putLong("FluidsToRs", fluidsToRs);
        tag.putLong("FluidsToAe2", fluidsToAe2);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        mainNode.loadFromNBT(tag);
        bridgeName = tag.getString("BridgeName");
        itemsToRs = tag.getLong("ItemsToRs");
        itemsToAe2 = tag.getLong("ItemsToAe2");
        fluidsToRs = tag.getLong("FluidsToRs");
        fluidsToAe2 = tag.getLong("FluidsToAe2");
    }
}
