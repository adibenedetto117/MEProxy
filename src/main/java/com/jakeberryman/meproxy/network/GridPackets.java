package com.jakeberryman.meproxy.network;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.jakeberryman.meproxy.MEProxy;
import com.jakeberryman.meproxy.client.ClientPayloadHandlers;
import com.jakeberryman.meproxy.content.bridge.NetworkBridgeBlockEntity;
import com.jakeberryman.meproxy.content.grid.UniversalGridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GridPackets {
    private GridPackets() {
    }

    public record GridEntry(ItemStack stack, long ae2Amount, long rsAmount, boolean craftAe2, boolean craftRs) {
    }

    public record GridList(BlockPos pos, List<GridEntry> entries) implements CustomPacketPayload {
        public static final Type<GridList> TYPE = new Type<>(MEProxy.asResource("grid_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GridList> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeVarInt(p.entries.size());
                    for (GridEntry entry : p.entries) {
                        ItemStack.STREAM_CODEC.encode(buf, entry.stack);
                        buf.writeVarLong(entry.ae2Amount);
                        buf.writeVarLong(entry.rsAmount);
                        buf.writeBoolean(entry.craftAe2);
                        buf.writeBoolean(entry.craftRs);
                    }
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    int size = buf.readVarInt();
                    List<GridEntry> entries = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        entries.add(new GridEntry(ItemStack.STREAM_CODEC.decode(buf),
                                buf.readVarLong(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean()));
                    }
                    return new GridList(pos, entries);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestGridList(BlockPos pos, String query) implements CustomPacketPayload {
        public static final Type<RequestGridList> TYPE = new Type<>(MEProxy.asResource("request_grid_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestGridList> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeUtf(p.query);
                },
                buf -> new RequestGridList(buf.readBlockPos(), buf.readUtf(128)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GridExtract(BlockPos pos, ItemStack stack, int network, int amount) implements CustomPacketPayload {
        public static final Type<GridExtract> TYPE = new Type<>(MEProxy.asResource("grid_extract"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GridExtract> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    ItemStack.STREAM_CODEC.encode(buf, p.stack);
                    buf.writeVarInt(p.network);
                    buf.writeVarInt(p.amount);
                },
                buf -> new GridExtract(buf.readBlockPos(), ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GridCraft(BlockPos pos, ItemStack stack, int network, int amount) implements CustomPacketPayload {
        public static final Type<GridCraft> TYPE = new Type<>(MEProxy.asResource("grid_craft"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GridCraft> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    ItemStack.STREAM_CODEC.encode(buf, p.stack);
                    buf.writeVarInt(p.network);
                    buf.writeVarInt(p.amount);
                },
                buf -> new GridCraft(buf.readBlockPos(), ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GridSetTarget(BlockPos pos, int target) implements CustomPacketPayload {
        public static final Type<GridSetTarget> TYPE = new Type<>(MEProxy.asResource("grid_set_target"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GridSetTarget> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeVarInt(p.target);
                },
                buf -> new GridSetTarget(buf.readBlockPos(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestGridStats(BlockPos pos) implements CustomPacketPayload {
        public static final Type<RequestGridStats> TYPE = new Type<>(MEProxy.asResource("request_grid_stats"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestGridStats> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBlockPos(p.pos),
                buf -> new RequestGridStats(buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GridStats(BlockPos pos, long itemsToRs, long itemsToAe2, long fluidsToRs, long fluidsToAe2,
                            long rateToRs, long rateToAe2,
                            List<BridgePackets.BreakdownEntry> topTransfers) implements CustomPacketPayload {
        public static final Type<GridStats> TYPE = new Type<>(MEProxy.asResource("grid_stats"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GridStats> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeVarLong(p.itemsToRs);
                    buf.writeVarLong(p.itemsToAe2);
                    buf.writeVarLong(p.fluidsToRs);
                    buf.writeVarLong(p.fluidsToAe2);
                    buf.writeVarLong(p.rateToRs);
                    buf.writeVarLong(p.rateToAe2);
                    buf.writeVarInt(p.topTransfers.size());
                    for (BridgePackets.BreakdownEntry entry : p.topTransfers) {
                        ItemStack.STREAM_CODEC.encode(buf, entry.stack());
                        buf.writeVarLong(entry.ae2Amount());
                        buf.writeVarLong(entry.rsAmount());
                    }
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    long itemsToRs = buf.readVarLong();
                    long itemsToAe2 = buf.readVarLong();
                    long fluidsToRs = buf.readVarLong();
                    long fluidsToAe2 = buf.readVarLong();
                    long rateToRs = buf.readVarLong();
                    long rateToAe2 = buf.readVarLong();
                    int size = buf.readVarInt();
                    List<BridgePackets.BreakdownEntry> top = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        top.add(new BridgePackets.BreakdownEntry(ItemStack.STREAM_CODEC.decode(buf),
                                buf.readVarLong(), buf.readVarLong()));
                    }
                    return new GridStats(pos, itemsToRs, itemsToAe2, fluidsToRs, fluidsToAe2, rateToRs, rateToAe2, top);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(GridList.TYPE, GridList.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandlers.handleGridList(payload)));
        registrar.playToClient(GridStats.TYPE, GridStats.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandlers.handleGridStats(payload)));

        registrar.playToServer(RequestGridList.TYPE, RequestGridList.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    NetworkBridgeBlockEntity bridge = resolveBridge(context.player(), payload.pos);
                    if (bridge != null && context.player() instanceof ServerPlayer serverPlayer) {
                        PacketDistributor.sendToPlayer(serverPlayer,
                                new GridList(payload.pos, buildGridList(bridge, payload.query)));
                    }
                }));
        registrar.playToServer(GridExtract.TYPE, GridExtract.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    NetworkBridgeBlockEntity bridge = resolveBridge(context.player(), payload.pos);
                    if (bridge != null && context.player() instanceof ServerPlayer serverPlayer) {
                        handleExtract(serverPlayer, bridge, payload);
                    }
                }));
        registrar.playToServer(GridCraft.TYPE, GridCraft.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    NetworkBridgeBlockEntity bridge = resolveBridge(context.player(), payload.pos);
                    if (bridge != null && context.player() instanceof ServerPlayer serverPlayer) {
                        AEItemKey key = AEItemKey.of(payload.stack);
                        if (key == null || payload.amount <= 0) {
                            return;
                        }
                        if (payload.network == 2) {
                            bridge.requestRsCraft(serverPlayer, key, payload.amount);
                        } else {
                            bridge.requestAe2Craft(serverPlayer, key, payload.amount);
                        }
                    }
                }));
        registrar.playToServer(GridSetTarget.TYPE, GridSetTarget.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    UniversalGridBlockEntity grid = resolveGrid(context.player(), payload.pos);
                    if (grid != null) {
                        grid.setInsertTarget(payload.target);
                    }
                }));
        registrar.playToServer(RequestGridStats.TYPE, RequestGridStats.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    NetworkBridgeBlockEntity bridge = resolveBridge(context.player(), payload.pos);
                    if (bridge != null && context.player() instanceof ServerPlayer serverPlayer) {
                        long[] totals = bridge.transferTotals();
                        PacketDistributor.sendToPlayer(serverPlayer, new GridStats(payload.pos,
                                totals[0], totals[1], totals[2], totals[3], totals[4], totals[5],
                                bridge.topTransfers(20)));
                    }
                }));
    }

    private static void handleExtract(ServerPlayer player, NetworkBridgeBlockEntity bridge, GridExtract payload) {
        AEItemKey key = AEItemKey.of(payload.stack);
        if (key == null) {
            return;
        }
        int amount = Math.max(1, Math.min(payload.amount, payload.stack.getMaxStackSize()));
        boolean fromAe2 = payload.network != 2;
        long extracted = bridge.extractNative(fromAe2, key, amount);
        if (extracted <= 0 && payload.network == 0) {
            fromAe2 = false;
            extracted = bridge.extractNative(false, key, amount);
        }
        if (extracted <= 0) {
            return;
        }
        ItemStack give = key.toStack((int) extracted);
        if (!player.getInventory().add(give) && !give.isEmpty()) {
            long returned = bridge.insertTo(fromAe2 ? 1 : 2, key, give.getCount());
            give.shrink((int) returned);
            if (!give.isEmpty()) {
                player.drop(give, false);
            }
        }
    }

    private static List<GridEntry> buildGridList(NetworkBridgeBlockEntity bridge, String query) {
        var ae2Native = bridge.nativeStacks(true);
        var rsNative = bridge.nativeStacks(false);
        Set<AEKey> craftAe2 = bridge.ae2Craftables();
        Set<AEKey> craftRs = bridge.rsCraftables();
        String needle = query.toLowerCase(Locale.ROOT).trim();

        Set<AEKey> keys = new HashSet<>(ae2Native.keySet());
        keys.addAll(rsNative.keySet());
        keys.addAll(craftAe2);
        keys.addAll(craftRs);

        List<GridEntry> entries = new ArrayList<>();
        for (AEKey key : keys) {
            if (!(key instanceof AEItemKey itemKey)) {
                continue;
            }
            if (!needle.isEmpty()
                    && !itemKey.getReadOnlyStack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            entries.add(new GridEntry(itemKey.toStack(1), ae2Native.get(key), rsNative.get(key),
                    craftAe2.contains(key), craftRs.contains(key)));
        }
        entries.sort((a, b) -> Long.compare(b.ae2Amount() + b.rsAmount(), a.ae2Amount() + a.rsAmount()));
        return entries.size() > 100 ? entries.subList(0, 100) : entries;
    }

    @Nullable
    private static UniversalGridBlockEntity resolveGrid(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        if (player == null || !player.blockPosition().closerThan(pos, 8)) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof UniversalGridBlockEntity grid ? grid : null;
    }

    @Nullable
    private static NetworkBridgeBlockEntity resolveBridge(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        UniversalGridBlockEntity grid = resolveGrid(player, pos);
        if (grid == null) {
            return null;
        }
        NetworkBridgeBlockEntity bridge = grid.findBridge();
        if (bridge == null && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("Universal Grid: no Network Bridge is touching this block."));
        }
        return bridge;
    }
}
