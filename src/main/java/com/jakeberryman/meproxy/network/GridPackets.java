package com.jakeberryman.meproxy.network;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.jakeberryman.meproxy.MEProxy;
import com.jakeberryman.meproxy.client.ClientPayloadHandlers;
import com.jakeberryman.meproxy.content.bridge.NetworkBridgeBlockEntity;
import com.jakeberryman.meproxy.content.grid.UniversalGridMenu;
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
    public static final int EXTRACT_STACK_TO_CARRIED = 0;
    public static final int EXTRACT_HALF_TO_CARRIED = 1;
    public static final int EXTRACT_STACK_TO_INVENTORY = 2;

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

    public record GridExtract(BlockPos pos, ItemStack stack, int mode) implements CustomPacketPayload {
        public static final Type<GridExtract> TYPE = new Type<>(MEProxy.asResource("grid_extract"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GridExtract> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    ItemStack.STREAM_CODEC.encode(buf, p.stack);
                    buf.writeVarInt(p.mode);
                },
                buf -> new GridExtract(buf.readBlockPos(), ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GridInsertCarried(BlockPos pos, boolean single) implements CustomPacketPayload {
        public static final Type<GridInsertCarried> TYPE = new Type<>(MEProxy.asResource("grid_insert_carried"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GridInsertCarried> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeBoolean(p.single);
                },
                buf -> new GridInsertCarried(buf.readBlockPos(), buf.readBoolean()));

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

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(GridList.TYPE, GridList.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandlers.handleGridList(payload)));

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
        registrar.playToServer(GridInsertCarried.TYPE, GridInsertCarried.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    NetworkBridgeBlockEntity bridge = resolveBridge(context.player(), payload.pos);
                    if (bridge != null && context.player() instanceof ServerPlayer serverPlayer) {
                        handleInsertCarried(serverPlayer, bridge, payload.single);
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
    }

    private static void handleExtract(ServerPlayer player, NetworkBridgeBlockEntity bridge, GridExtract payload) {
        AEItemKey key = AEItemKey.of(payload.stack);
        if (key == null) {
            return;
        }

        boolean toCarried = payload.mode != EXTRACT_STACK_TO_INVENTORY;
        if (toCarried && !player.containerMenu.getCarried().isEmpty()) {
            return;
        }

        int amount = payload.stack.getMaxStackSize();
        if (payload.mode == EXTRACT_HALF_TO_CARRIED) {
            amount = Math.max(1, amount / 2);
        }

        long extracted = bridge.extractNative(true, key, amount);
        if (extracted < amount) {
            extracted += bridge.extractNative(false, key, amount - extracted);
        }
        if (extracted <= 0) {
            return;
        }

        ItemStack result = key.toStack((int) extracted);
        if (toCarried) {
            player.containerMenu.setCarried(result);
        } else if (!player.getInventory().add(result) && !result.isEmpty()) {
            long returned = bridge.insertTo(0, key, result.getCount());
            result.shrink((int) returned);
            if (!result.isEmpty()) {
                player.drop(result, false);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    private static void handleInsertCarried(ServerPlayer player, NetworkBridgeBlockEntity bridge, boolean single) {
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        AEItemKey key = AEItemKey.of(carried);
        if (key == null) {
            return;
        }
        int amount = single ? 1 : carried.getCount();
        long inserted = bridge.insertTo(0, key, amount);
        if (inserted > 0) {
            carried.shrink((int) inserted);
            player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        }
        player.containerMenu.broadcastChanges();
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
        return entries.size() > 500 ? entries.subList(0, 500) : entries;
    }

    @Nullable
    private static NetworkBridgeBlockEntity resolveBridge(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        if (player == null
                || !(player.containerMenu instanceof UniversalGridMenu menu)
                || !menu.pos.equals(pos)) {
            return null;
        }
        NetworkBridgeBlockEntity bridge = menu.resolveBridge(player);
        if (bridge == null && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("Universal Grid: no Network Bridge is touching this block."));
        }
        return bridge;
    }
}
