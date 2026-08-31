package com.jakeberryman.meproxy.client;

import com.jakeberryman.meproxy.content.grid.UniversalGridMenu;
import com.jakeberryman.meproxy.network.GridPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UniversalGridScreen extends AbstractContainerScreen<UniversalGridMenu> {
    private static final ResourceLocation GRID_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("refinedstorage", "textures/gui/grid.png");
    private static final ResourceLocation ROW_SPRITE =
            ResourceLocation.fromNamespaceAndPath("refinedstorage", "grid/row");
    private static final ResourceLocation SCROLLER_SPRITE =
            ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");

    private static final int COLUMNS = 9;
    private static final int VISIBLE_ROWS = 4;
    private static final int HEADER_HEIGHT = 19;
    private static final int BOTTOM_TEXTURE_Y = 73;
    private static final int BOTTOM_HEIGHT = 99;
    private static final int SLOT_AREA_X = 8;
    private static final int SLOT_AREA_Y = 20;

    private List<GridPackets.GridEntry> entries = List.of();
    private int scrollRow;
    private int refreshTicks = 15;
    private EditBox searchBox;

    public UniversalGridScreen(UniversalGridMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 193;
        imageHeight = HEADER_HEIGHT + VISIBLE_ROWS * 18 + BOTTOM_HEIGHT;
    }

    public BlockPos getPos() {
        return menu.pos;
    }

    public void updateList(List<GridPackets.GridEntry> entries) {
        this.entries = entries;
        clampScroll();
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(scrollRow, maxScrollRow()));
    }

    private int maxScrollRow() {
        return Math.max(0, (entries.size() + COLUMNS - 1) / COLUMNS - VISIBLE_ROWS);
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, leftPos + 81, topPos + 6, 88, 9, Component.literal("search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setResponder(text -> {
            scrollRow = 0;
            requestList();
        });
        addRenderableWidget(searchBox);
        setFocused(searchBox);
        requestList();
    }

    private void requestList() {
        PacketDistributor.sendToServer(new GridPackets.RequestGridList(menu.pos,
                searchBox == null ? "" : searchBox.getValue()));
    }

    @Override
    protected void containerTick() {
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            requestList();
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(GRID_TEXTURE, leftPos, topPos, 0, 0, imageWidth, HEADER_HEIGHT);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            graphics.blitSprite(ROW_SPRITE, leftPos + 7, topPos + HEADER_HEIGHT + row * 18, 162, 18);
        }
        graphics.blit(GRID_TEXTURE, leftPos, topPos + HEADER_HEIGHT + VISIBLE_ROWS * 18,
                0, BOTTOM_TEXTURE_Y, imageWidth, BOTTOM_HEIGHT);

        int trackX = leftPos + 174;
        int trackY = topPos + SLOT_AREA_Y;
        int trackHeight = VISIBLE_ROWS * 18 - 15;
        int thumbY = maxScrollRow() == 0 ? trackY
                : trackY + (int) ((float) scrollRow / maxScrollRow() * trackHeight);
        graphics.blitSprite(SCROLLER_SPRITE, trackX, thumbY, 12, 15);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 7, 7, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, 7, imageHeight - 94, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int start = scrollRow * COLUMNS;
        for (int i = 0; i < VISIBLE_ROWS * COLUMNS; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                break;
            }
            GridPackets.GridEntry entry = entries.get(index);
            int x = leftPos + SLOT_AREA_X + (i % COLUMNS) * 18;
            int y = topPos + SLOT_AREA_Y + (i / COLUMNS) * 18;

            graphics.renderItem(entry.stack(), x, y);

            long total = entry.ae2Amount() + entry.rsAmount();
            String label = total <= 0 ? "Craft" : abbreviate(total);
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x + 17 - font.width(label) * 0.5f, y + 13, 200);
            pose.scale(0.5f, 0.5f, 1);
            graphics.drawString(font, label, 0, 0, total <= 0 ? 0xFFB864 : 0xFFFFFF, true);
            pose.popPose();
        }

        int hovered = hoveredEntryIndex(mouseX, mouseY);
        if (hovered >= 0 && menu.getCarried().isEmpty()) {
            GridPackets.GridEntry entry = entries.get(hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(entry.stack().getHoverName());
            lines.add(Component.literal(String.format(Locale.ROOT, "AE2: %,d", entry.ae2Amount()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(Component.literal(String.format(Locale.ROOT, "RS: %,d", entry.rsAmount()))
                    .withStyle(ChatFormatting.AQUA));
            if (entry.craftAe2() || entry.craftRs()) {
                lines.add(Component.literal("Craftable in " + (entry.craftAe2() && entry.craftRs() ? "AE2 and RS"
                        : entry.craftAe2() ? "AE2" : "RS")).withStyle(ChatFormatting.GOLD));
                if (entry.ae2Amount() + entry.rsAmount() <= 0) {
                    lines.add(Component.literal("Click: craft 1, Shift: 16, Ctrl: 64").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private static String abbreviate(long amount) {
        if (amount >= 1_000_000_000) {
            return String.format(Locale.ROOT, "%.1fB", amount / 1_000_000_000.0);
        }
        if (amount >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        }
        if (amount >= 10_000) {
            return String.format(Locale.ROOT, "%.0fK", amount / 1_000.0);
        }
        if (amount >= 1_000) {
            return String.format(Locale.ROOT, "%.1fK", amount / 1_000.0);
        }
        return Long.toString(amount);
    }

    private int hoveredEntryIndex(double mouseX, double mouseY) {
        int localX = (int) mouseX - leftPos - SLOT_AREA_X;
        int localY = (int) mouseY - topPos - SLOT_AREA_Y;
        if (localX < 0 || localX >= COLUMNS * 18 || localY < 0 || localY >= VISIBLE_ROWS * 18) {
            return -1;
        }
        int index = scrollRow * COLUMNS + (localY / 18) * COLUMNS + localX / 18;
        return index < entries.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int localX = (int) mouseX - leftPos - SLOT_AREA_X;
        int localY = (int) mouseY - topPos - SLOT_AREA_Y;
        boolean inStorageArea = localX >= 0 && localX < COLUMNS * 18 && localY >= 0 && localY < VISIBLE_ROWS * 18;

        if (inStorageArea) {
            if (!menu.getCarried().isEmpty()) {
                PacketDistributor.sendToServer(new GridPackets.GridInsertCarried(menu.pos, button == 1));
                return true;
            }

            int index = hoveredEntryIndex(mouseX, mouseY);
            if (index >= 0) {
                GridPackets.GridEntry entry = entries.get(index);
                long total = entry.ae2Amount() + entry.rsAmount();
                if (total > 0) {
                    int mode = button == 1 ? GridPackets.EXTRACT_HALF_TO_CARRIED
                            : Screen.hasShiftDown() ? GridPackets.EXTRACT_STACK_TO_INVENTORY
                            : GridPackets.EXTRACT_STACK_TO_CARRIED;
                    PacketDistributor.sendToServer(new GridPackets.GridExtract(menu.pos, entry.stack(), mode));
                } else if (entry.craftAe2() || entry.craftRs()) {
                    int amount = Screen.hasControlDown() ? 64 : Screen.hasShiftDown() ? 16 : 1;
                    int network = entry.craftAe2() ? 1 : 2;
                    PacketDistributor.sendToServer(new GridPackets.GridCraft(menu.pos, entry.stack(), network, amount));
                }
                return true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int newRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) Math.signum(scrollY)));
        if (newRow != scrollRow) {
            scrollRow = newRow;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused() && keyCode != 256) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers) || searchBox.canConsumeInput()
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
