package com.jakeberryman.meproxy.client;

import com.jakeberryman.meproxy.network.BridgePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class BridgeStatusScreen extends Screen {
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 6;

    private final BlockPos pos;
    private BridgePackets.BridgeStatus status;
    private List<BridgePackets.BreakdownEntry> entries = List.of();
    private int scrollOffset;
    private int refreshTicks;

    private EditBox nameBox;
    private EditBox searchBox;

    public BridgeStatusScreen(BridgePackets.BridgeStatus status) {
        super(Component.translatable("block.meproxy.network_bridge"));
        this.pos = status.pos();
        this.status = status;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void updateStatus(BridgePackets.BridgeStatus status) {
        this.status = status;
    }

    public void updateBreakdown(List<BridgePackets.BreakdownEntry> entries) {
        this.entries = entries;
        if (scrollOffset > Math.max(0, entries.size() - VISIBLE_ROWS)) {
            scrollOffset = 0;
        }
    }

    private int left() {
        return width / 2 - 130;
    }

    private int top() {
        return Math.max(10, height / 2 - 120);
    }

    @Override
    protected void init() {
        nameBox = new EditBox(font, left(), top() + 14, 180, 16, Component.literal("name"));
        nameBox.setMaxLength(60);
        nameBox.setValue(status.name());
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.literal("Save"),
                        button -> PacketDistributor.sendToServer(new BridgePackets.SetBridgeName(pos, nameBox.getValue())))
                .bounds(left() + 186, top() + 13, 50, 18)
                .build());

        searchBox = new EditBox(font, left(), top() + 96, 236, 16, Component.literal("search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Search items...").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(text -> {
            scrollOffset = 0;
            PacketDistributor.sendToServer(new BridgePackets.RequestBreakdown(pos, text));
        });
        addRenderableWidget(searchBox);

        PacketDistributor.sendToServer(new BridgePackets.RequestBreakdown(pos, ""));
    }

    @Override
    public void tick() {
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            PacketDistributor.sendToServer(new BridgePackets.RequestStatus(pos));
            PacketDistributor.sendToServer(new BridgePackets.RequestBreakdown(pos, searchBox.getValue()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int x = left();
        int y = top();

        graphics.drawString(font, title.copy().withStyle(ChatFormatting.BOLD)
                .append(Component.literal("  [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")
                        .withStyle(ChatFormatting.GRAY)), x, y, 0xFFFFFF);

        boolean ae2Online = "online".equals(status.ae2Status());
        boolean rsOnline = "online".equals(status.rsStatus());
        graphics.drawString(font, Component.literal("AE2: " + status.ae2Status() + "  (" + status.ae2Types() + " types)")
                .withStyle(ae2Online ? ChatFormatting.GREEN : ChatFormatting.RED), x, y + 38, 0xFFFFFF);
        graphics.drawString(font, Component.literal("RS:  " + status.rsStatus() + "  (" + status.rsTypes() + " types)")
                .withStyle(rsOnline ? ChatFormatting.GREEN : ChatFormatting.RED), x, y + 50, 0xFFFFFF);

        graphics.drawString(font, Component.literal(String.format("To RS: %,d items, %,d mB  (%.0f/s now)",
                status.itemsToRs(), status.fluidsToRs(), status.rateToRs())).withStyle(ChatFormatting.AQUA), x, y + 66, 0xFFFFFF);
        graphics.drawString(font, Component.literal(String.format("To AE2: %,d items, %,d mB  (%.0f/s now)",
                status.itemsToAe2(), status.fluidsToAe2(), status.rateToAe2())).withStyle(ChatFormatting.LIGHT_PURPLE), x, y + 78, 0xFFFFFF);

        int listY = y + 118;
        int end = Math.min(entries.size(), scrollOffset + VISIBLE_ROWS);
        for (int i = scrollOffset; i < end; i++) {
            BridgePackets.BreakdownEntry entry = entries.get(i);
            int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;
            graphics.renderItem(entry.stack(), x, rowY);
            graphics.drawString(font, entry.stack().getHoverName(), x + 20, rowY + 4, 0xFFFFFF);
            String counts = String.format("%,d AE2 / %,d RS", entry.ae2Amount(), entry.rsAmount());
            graphics.drawString(font, Component.literal(counts).withStyle(ChatFormatting.GRAY),
                    x + 236 - font.width(counts), rowY + 4, 0xFFFFFF);
        }
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.literal("No matching items").withStyle(ChatFormatting.DARK_GRAY), x, listY, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxOffset = Math.max(0, entries.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
