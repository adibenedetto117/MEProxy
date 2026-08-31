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

    public NetworkBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.NETWORK_BRIDGE_BLOCK_ENTITY.get(), pos, state,
                new ExternalStorageNetworkNode(RS_ENERGY_USAGE, System::currentTimeMillis));
        mainNode.addService(IStorageProvider.class, this);
        mainNetworkNode.initialize(new AE2ExternalStorageProvider(this));
        mainNetworkNode.getStorageConfiguration().setInsertPriority(BRIDGE_PRIORITY);
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
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        mainNode.loadFromNBT(tag);
    }
}
