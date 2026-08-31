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
    private static final int PANEL_WIDTH = 256;
    private static final int PANEL_HEIGHT = 240;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 5;

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
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return Math.max(8, (height - PANEL_HEIGHT) / 2);
    }

    @Override
    protected void init() {
        nameBox = new EditBox(font, left() + 10, top() + 18, 176, 16, Component.literal("name"));
        nameBox.setMaxLength(60);
        nameBox.setValue(status.name());
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.literal("Save"),
                        button -> PacketDistributor.sendToServer(new BridgePackets.SetBridgeName(pos, nameBox.getValue())))
                .bounds(left() + 192, top() + 17, 54, 18)
                .build());

        searchBox = new EditBox(font, left() + 10, top() + 116, 236, 14, Component.literal("search"));
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
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        graphics.fill(x + 2, y + 2, x + w - 2, y + 3, 0xFFFFFFFF);
        graphics.fill(x + 2, y + 2, x + 3, y + h - 2, 0xFFFFFFFF);
        graphics.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, 0xFF555555);
        graphics.fill(x + w - 3, y + 2, x + w - 2, y + h - 2, 0xFF555555);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int x = left();
        int y = top();
        drawPanel(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT);

        graphics.drawString(font, title.copy()
                .append(Component.literal("  [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")), x + 10, y + 7, 0x404040, false);

        boolean ae2Online = "online".equals(status.ae2Status());
        boolean rsOnline = "online".equals(status.rsStatus());
        graphics.drawString(font, "AE2: " + status.ae2Status() + "  (" + status.ae2Types() + " types)",
                x + 10, y + 42, ae2Online ? 0x2E7D32 : 0xB71C1C, false);
        graphics.drawString(font, "RS: " + status.rsStatus() + "  (" + status.rsTypes() + " types)",
                x + 10, y + 54, rsOnline ? 0x2E7D32 : 0xB71C1C, false);

        graphics.drawString(font, String.format("To RS: %,d items, %,d mB  (%.0f/s)",
                status.itemsToRs(), status.fluidsToRs(), status.rateToRs()), x + 10, y + 72, 0x1565C0, false);
        graphics.drawString(font, String.format("To AE2: %,d items, %,d mB  (%.0f/s)",
                status.itemsToAe2(), status.fluidsToAe2(), status.rateToAe2()), x + 10, y + 84, 0x6A1B9A, false);

        graphics.fill(x + 8, y + 134, x + PANEL_WIDTH - 8, y + 136 + VISIBLE_ROWS * ROW_HEIGHT, 0xFF8B8B8B);

        int listY = y + 136;
        int end = Math.min(entries.size(), scrollOffset + VISIBLE_ROWS);
        for (int i = scrollOffset; i < end; i++) {
            BridgePackets.BreakdownEntry entry = entries.get(i);
            int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;
            graphics.renderItem(entry.stack(), x + 10, rowY);
            graphics.drawString(font, entry.stack().getHoverName(), x + 30, rowY + 4, 0x2B2B2B, false);
            String counts = String.format("%,d AE2 / %,d RS", entry.ae2Amount(), entry.rsAmount());
            graphics.drawString(font, counts, x + PANEL_WIDTH - 12 - font.width(counts), rowY + 4, 0x404040, false);
        }
        if (entries.isEmpty()) {
            graphics.drawString(font, "No matching items", x + 12, listY + 4, 0x555555, false);
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
