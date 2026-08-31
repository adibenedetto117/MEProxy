package com.jakeberryman.meproxy.network;

import com.jakeberryman.meproxy.MEProxy;
import com.jakeberryman.meproxy.client.ClientPayloadHandlers;
import com.jakeberryman.meproxy.content.bridge.NetworkBridgeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

public final class BridgePackets {
    private BridgePackets() {
    }

    public record BridgeStatus(BlockPos pos, boolean openScreen, String name,
                               String ae2Status, String rsStatus,
                               long itemsToRs, long itemsToAe2,
                               long fluidsToRs, long fluidsToAe2,
                               double rateToRs, double rateToAe2,
                               int ae2Types, int rsTypes) implements CustomPacketPayload {
        public static final Type<BridgeStatus> TYPE = new Type<>(MEProxy.asResource("bridge_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BridgeStatus> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeBoolean(p.openScreen);
                    buf.writeUtf(p.name);
                    buf.writeUtf(p.ae2Status);
                    buf.writeUtf(p.rsStatus);
                    buf.writeVarLong(p.itemsToRs);
                    buf.writeVarLong(p.itemsToAe2);
                    buf.writeVarLong(p.fluidsToRs);
                    buf.writeVarLong(p.fluidsToAe2);
                    buf.writeDouble(p.rateToRs);
                    buf.writeDouble(p.rateToAe2);
                    buf.writeVarInt(p.ae2Types);
                    buf.writeVarInt(p.rsTypes);
                },
                buf -> new BridgeStatus(buf.readBlockPos(), buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                        buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BreakdownEntry(ItemStack stack, long ae2Amount, long rsAmount) {
    }

    public record BreakdownResults(BlockPos pos, List<BreakdownEntry> entries) implements CustomPacketPayload {
        public static final Type<BreakdownResults> TYPE = new Type<>(MEProxy.asResource("breakdown_results"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BreakdownResults> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeVarInt(p.entries.size());
                    for (BreakdownEntry entry : p.entries) {
                        ItemStack.STREAM_CODEC.encode(buf, entry.stack);
                        buf.writeVarLong(entry.ae2Amount);
                        buf.writeVarLong(entry.rsAmount);
                    }
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    int size = buf.readVarInt();
                    List<BreakdownEntry> entries = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        entries.add(new BreakdownEntry(ItemStack.STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong()));
                    }
                    return new BreakdownResults(pos, entries);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestStatus(BlockPos pos) implements CustomPacketPayload {
        public static final Type<RequestStatus> TYPE = new Type<>(MEProxy.asResource("request_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestStatus> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBlockPos(p.pos),
                buf -> new RequestStatus(buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestBreakdown(BlockPos pos, String query) implements CustomPacketPayload {
        public static final Type<RequestBreakdown> TYPE = new Type<>(MEProxy.asResource("request_breakdown"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestBreakdown> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeUtf(p.query);
                },
                buf -> new RequestBreakdown(buf.readBlockPos(), buf.readUtf(128)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetBridgeName(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<SetBridgeName> TYPE = new Type<>(MEProxy.asResource("set_bridge_name"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetBridgeName> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeUtf(p.name);
                },
                buf -> new SetBridgeName(buf.readBlockPos(), buf.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        GridPackets.register(registrar);

        registrar.playToClient(BridgeStatus.TYPE, BridgeStatus.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandlers.handleStatus(payload)));
        registrar.playToClient(BreakdownResults.TYPE, BreakdownResults.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandlers.handleBreakdown(payload)));

        registrar.playToServer(RequestStatus.TYPE, RequestStatus.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    NetworkBridgeBlockEntity bridge = resolve(context.player(), payload.pos);
                    if (bridge != null && context.player() instanceof ServerPlayer serverPlayer) {
                        PacketDistributor.sendToPlayer(serverPlayer, bridge.buildStatusPayload(false));
                    }
                }));
        registrar.playToServer(RequestBreakdown.TYPE, RequestBreakdown.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    NetworkBridgeBlockEntity bridge = resolve(context.player(), payload.pos);
                    if (bridge != null && context.player() instanceof ServerPlayer serverPlayer) {
                        PacketDistributor.sendToPlayer(serverPlayer,
                                new BreakdownResults(payload.pos, bridge.queryBreakdown(payload.query)));
                    }
                }));
        registrar.playToServer(SetBridgeName.TYPE, SetBridgeName.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    NetworkBridgeBlockEntity bridge = resolve(context.player(), payload.pos);
                    if (bridge != null) {
                        bridge.setBridgeName(payload.name);
                    }
                }));
    }

    private static NetworkBridgeBlockEntity resolve(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        if (player == null || !player.blockPosition().closerThan(pos, 8)) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof NetworkBridgeBlockEntity bridge ? bridge : null;
    }
}
