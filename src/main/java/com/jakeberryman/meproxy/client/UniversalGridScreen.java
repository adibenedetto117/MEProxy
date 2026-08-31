package com.jakeberryman.meproxy.client;

import com.jakeberryman.meproxy.content.grid.UniversalGridMenu;
import com.jakeberryman.meproxy.network.BridgePackets;
import com.jakeberryman.meproxy.network.GridPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class UniversalGridScreen extends AbstractContainerScreen<UniversalGridMenu> {
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 5;
    private static final String[] TARGET_NAMES = {"Auto", "AE2", "RS"};

    private int tab;
    private int target;
    private int scrollOffset;
    private int refreshTicks = 19;
    private List<GridPackets.GridEntry> entries = List.of();
    private GridPackets.GridStats stats;

    private EditBox searchBox;
    private Button targetButton;

    public UniversalGridScreen(UniversalGridMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 212;
        imageHeight = 234;
        inventoryLabelX = 25;
        inventoryLabelY = 140;
        titleLabelX = 8;
        titleLabelY = 6;
    }

    public void updateList(List<GridPackets.GridEntry> entries) {
        this.entries = entries;
        if (scrollOffset > Math.max(0, entries.size() - VISIBLE_ROWS)) {
            scrollOffset = 0;
        }
    }

    public void updateStats(GridPackets.GridStats stats) {
        this.stats = stats;
    }

    public net.minecraft.core.BlockPos getPos() {
        return menu.pos;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(Button.builder(Component.literal("Items"), b -> switchTab(0))
                .bounds(leftPos + 8, topPos + 16, 46, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Craft"), b -> switchTab(1))
                .bounds(leftPos + 58, topPos + 16, 46, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Stats"), b -> switchTab(2))
                .bounds(leftPos + 108, topPos + 16, 46, 14).build());

        searchBox = new EditBox(font, leftPos + 8, topPos + 34, 196, 14, Component.literal("search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Search...").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(text -> {
            scrollOffset = 0;
            requestList();
        });
        addRenderableWidget(searchBox);

        targetButton = Button.builder(targetLabel(), b -> {
            target = (target + 1) % 3;
            targetButton.setMessage(targetLabel());
            PacketDistributor.sendToServer(new GridPackets.GridSetTarget(menu.pos, target));
        }).bounds(leftPos + 140, topPos + 126, 44, 14).build();
        addRenderableWidget(targetButton);

        requestList();
    }

    private Component targetLabel() {
        return Component.literal("-> " + TARGET_NAMES[target]);
    }

    private void switchTab(int newTab) {
        tab = newTab;
        scrollOffset = 0;
        if (tab == 2) {
            PacketDistributor.sendToServer(new GridPackets.RequestGridStats(menu.pos));
        } else {
            requestList();
        }
    }

    private void requestList() {
        PacketDistributor.sendToServer(new GridPackets.RequestGridList(menu.pos, searchBox == null ? "" : searchBox.getValue()));
    }

    @Override
    protected void containerTick() {
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            if (tab == 2) {
                PacketDistributor.sendToServer(new GridPackets.RequestGridStats(menu.pos));
            } else {
                requestList();
            }
        }
    }

    private List<GridPackets.GridEntry> visibleEntries() {
        if (tab == 1) {
            return entries.stream().filter(e -> e.craftAe2() || e.craftRs()).toList();
        }
        return entries.stream().filter(e -> e.ae2Amount() + e.rsAmount() > 0).toList();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0181818);
        graphics.fill(leftPos + 6, topPos + 50, leftPos + 206, topPos + 50 + VISIBLE_ROWS * ROW_HEIGHT + 4, 0x40000000);
        for (var slot : menu.slots) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0x50FFFFFF);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        int x = leftPos + 8;
        int listY = topPos + 52;

        if (tab == 2) {
            renderStats(graphics, x, listY);
            return;
        }

        List<GridPackets.GridEntry> visible = visibleEntries();
        int end = Math.min(visible.size(), scrollOffset + VISIBLE_ROWS);
        for (int i = scrollOffset; i < end; i++) {
            GridPackets.GridEntry entry = visible.get(i);
            int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;
            graphics.renderItem(entry.stack(), x, rowY);
            graphics.drawString(font, entry.stack().getHoverName(), x + 20, rowY + 4, 0xFFFFFF);
            String info = tab == 1
                    ? (entry.craftAe2() ? "[Craft AE2]" : "") + (entry.craftRs() ? " [Craft RS]" : "")
                    : String.format("%,d AE2 / %,d RS", entry.ae2Amount(), entry.rsAmount());
            graphics.drawString(font, Component.literal(info).withStyle(ChatFormatting.GRAY),
                    leftPos + 204 - font.width(info), rowY + 4, 0xFFFFFF);
        }
        if (visible.isEmpty()) {
            graphics.drawString(font, Component.literal(tab == 1 ? "No craftables" : "No items")
                    .withStyle(ChatFormatting.DARK_GRAY), x, listY, 0xFFFFFF);
        }

        String hint = tab == 1
                ? "Click: craft 1, Shift: 16, Ctrl: 64"
                : "Click row: take stack, Shift: take 1. Slot right: deposit";
        graphics.drawString(font, Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY),
                x, topPos + 52 + VISIBLE_ROWS * ROW_HEIGHT + 6, 0xFFFFFF);
    }

    private void renderStats(GuiGraphics graphics, int x, int y) {
        if (stats == null) {
            graphics.drawString(font, Component.literal("Loading...").withStyle(ChatFormatting.DARK_GRAY), x, y, 0xFFFFFF);
            return;
        }
        graphics.drawString(font, Component.literal(String.format("To RS: %,d items, %,d mB (%d/s)",
                stats.itemsToRs(), stats.fluidsToRs(), stats.rateToRs())).withStyle(ChatFormatting.AQUA), x, y, 0xFFFFFF);
        graphics.drawString(font, Component.literal(String.format("To AE2: %,d items, %,d mB (%d/s)",
                stats.itemsToAe2(), stats.fluidsToAe2(), stats.rateToAe2())).withStyle(ChatFormatting.LIGHT_PURPLE), x, y + 12, 0xFFFFFF);
        graphics.drawString(font, Component.literal("Top transferred:").withStyle(ChatFormatting.GRAY), x, y + 28, 0xFFFFFF);

        List<BridgePackets.BreakdownEntry> top = stats.topTransfers();
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            BridgePackets.BreakdownEntry entry = top.get(i);
            int rowY = y + 40 + i * ROW_HEIGHT;
            graphics.renderItem(entry.stack(), x, rowY);
            String counts = String.format("%,d to AE2 / %,d to RS", entry.ae2Amount(), entry.rsAmount());
            graphics.drawString(font, entry.stack().getHoverName(), x + 20, rowY + 4, 0xFFFFFF);
            graphics.drawString(font, Component.literal(counts).withStyle(ChatFormatting.GRAY),
                    leftPos + 204 - font.width(counts), rowY + 12, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab != 2 && button == 0) {
            int x = leftPos + 8;
            int listY = topPos + 52;
            if (mouseX >= x && mouseX <= leftPos + 204 && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT) {
                int row = scrollOffset + (int) ((mouseY - listY) / ROW_HEIGHT);
                List<GridPackets.GridEntry> visible = visibleEntries();
                if (row >= 0 && row < visible.size()) {
                    GridPackets.GridEntry entry = visible.get(row);
                    if (tab == 0) {
                        int amount = Screen.hasShiftDown() ? 1 : entry.stack().getMaxStackSize();
                        int network = entry.ae2Amount() > 0 ? 1 : 2;
                        PacketDistributor.sendToServer(new GridPackets.GridExtract(menu.pos, entry.stack(), network, amount));
                    } else {
                        int amount = Screen.hasControlDown() ? 64 : Screen.hasShiftDown() ? 16 : 1;
                        int network = entry.craftAe2() ? 1 : 2;
                        PacketDistributor.sendToServer(new GridPackets.GridCraft(menu.pos, entry.stack(), network, amount));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxOffset = Math.max(0, visibleEntries().size() - VISIBLE_ROWS);
        int newOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(scrollY)));
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
