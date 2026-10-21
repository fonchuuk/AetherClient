package com.aetherglass.client.mixin;

import com.aetherglass.client.gui.inventory.InventoryTheme;
import com.aetherglass.client.gui.inventory.InventoryTransferAnimator;
import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.InventoryModule;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Перехват всех экранов контейнеров ({@link AbstractContainerScreen}: инвентарь, сундук, верстак,
 * печка, бочка, шалкер, эндер-сундук) для реализации Блока 6.1–6.3:
 * <ul>
 *   <li>6.1: Плавный перелёт предмета по дуге при {@code Shift-Click} поверх интерфейса;</li>
 *   <li>6.2: Быстрый перенос зажатием ({@code Quick Move / Drag}, «Лассо» при зажатом Shift);</li>
 *   <li>6.3: Темы оформления инвентаря (Светлый, Тёмный, Матовый Liquid Glass).</li>
 * </ul>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    protected int imageWidth;
    @Shadow
    protected int imageHeight;
    @Shadow
    @Final
    protected T menu;
    @Shadow
    protected Slot hoveredSlot;

    @Shadow
    protected abstract void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type);

    @Unique
    private List<ItemStack> aetherglass$beforeQuickMoveSnapshot;
    @Unique
    private ItemStack aetherglass$quickMoveSourceStack = ItemStack.EMPTY;
    @Unique
    private int aetherglass$quickMoveSourceX;
    @Unique
    private int aetherglass$quickMoveSourceY;
    @Unique
    private int aetherglass$quickMoveSourceIndex = -1;
    @Unique
    private int aetherglass$lastLassoSlotIndex = -1;

    @Inject(
            method = "renderBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"
            )
    )
    private void aetherglass$renderContainerDropShadow(GuiGraphics graphics, int mouseX, int mouseY,
                                                       float partialTick, CallbackInfo info) {
        InventoryModule module = ModuleManager.get().typed("inventory", InventoryModule.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        InventoryTheme theme = module.theme();
        if (theme != null && theme.replacesContainerTexture()) {
            // Мягкая тень ПОД текстурой контейнера (сама текстура меняется в GuiGraphicsMixin)
            DrawUtil.drawSoftShadow(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, 6, 10, 0.52F);
        }
    }

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void aetherglass$beforeRenderSlot(GuiGraphics graphics, Slot slot, int mouseX, int mouseY,
                                              CallbackInfo info) {
        if (slot == null) {
            return;
        }
        InventoryModule module = ModuleManager.get().typed("inventory", InventoryModule.class);
        if (module != null && module.isEnabled()) {
            InventoryTheme theme = module.theme();
            if (theme != null && theme.replacesContainerTexture() && this.hoveredSlot == slot) {
                int accent = ColorController.currentAccent();
                int fill = ColorUtil.fade(accent, 0.22F);
                int border = ColorUtil.fade(accent, 0.85F);
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fill);
                graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y, border);
                graphics.fill(slot.x - 1, slot.y + 16, slot.x + 17, slot.y + 17, border);
                graphics.fill(slot.x - 1, slot.y, slot.x, slot.y + 16, border);
                graphics.fill(slot.x + 16, slot.y, slot.x + 17, slot.y + 16, border);
            }
            if (module.shiftClickFlight() && InventoryTransferAnimator.isSlotInFlight(slot.index)) {
                info.cancel();
            }
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"))
    private void aetherglass$beforeSlotClicked(Slot slot, int slotId, int mouseButton,
                                               ClickType clickType, CallbackInfo info) {
        this.aetherglass$beforeQuickMoveSnapshot = null;
        this.aetherglass$quickMoveSourceStack = ItemStack.EMPTY;
        this.aetherglass$quickMoveSourceIndex = -1;

        InventoryModule module = ModuleManager.get().typed("inventory", InventoryModule.class);
        if (module == null || !module.shiftClickFlight()) {
            return;
        }
        if (clickType == ClickType.QUICK_MOVE && slot != null && slot.hasItem() && this.menu != null) {
            this.aetherglass$quickMoveSourceStack = slot.getItem().copy();
            this.aetherglass$quickMoveSourceX = this.leftPos + slot.x;
            this.aetherglass$quickMoveSourceY = this.topPos + slot.y;
            this.aetherglass$quickMoveSourceIndex = slot.index;

            int count = this.menu.slots.size();
            List<ItemStack> snapshot = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Slot s = this.menu.slots.get(i);
                snapshot.add(s != null && s.hasItem() ? s.getItem().copy() : ItemStack.EMPTY);
            }
            this.aetherglass$beforeQuickMoveSnapshot = snapshot;
        }
    }

    @Inject(method = "slotClicked", at = @At("RETURN"))
    private void aetherglass$afterSlotClicked(Slot slot, int slotId, int mouseButton,
                                              ClickType clickType, CallbackInfo info) {
        List<ItemStack> before = this.aetherglass$beforeQuickMoveSnapshot;
        ItemStack movedStack = this.aetherglass$quickMoveSourceStack;
        this.aetherglass$beforeQuickMoveSnapshot = null;
        this.aetherglass$quickMoveSourceStack = ItemStack.EMPTY;

        if (before == null || movedStack.isEmpty() || this.menu == null) {
            return;
        }
        InventoryModule module = ModuleManager.get().typed("inventory", InventoryModule.class);
        if (module == null || !module.shiftClickFlight()) {
            return;
        }

        int count = Math.min(before.size(), this.menu.slots.size());
        for (int i = 0; i < count; i++) {
            if (i == this.aetherglass$quickMoveSourceIndex) {
                continue;
            }
            Slot dest = this.menu.slots.get(i);
            if (dest == null || !dest.hasItem()) {
                continue;
            }
            ItemStack oldStack = before.get(i);
            ItemStack newStack = dest.getItem();
            boolean increased = oldStack.isEmpty() || (newStack.getCount() > oldStack.getCount());
            if (increased && ItemStack.isSameItemSameComponents(movedStack, newStack)) {
                int transferredCount = oldStack.isEmpty()
                        ? newStack.getCount()
                        : (newStack.getCount() - oldStack.getCount());
                ItemStack visualStack = movedStack.copyWithCount(Math.max(1, transferredCount));
                InventoryTransferAnimator.spawnFlight(
                        visualStack,
                        this.aetherglass$quickMoveSourceX,
                        this.aetherglass$quickMoveSourceY,
                        this.leftPos + dest.x,
                        this.topPos + dest.y,
                        dest.index,
                        module.flightDuration()
                );
                break;
            }
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"))
    private void aetherglass$onLassoMouseDragged(MouseButtonEvent event, double dragX, double dragY,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (event == null || event.button() != 0 || !event.hasShiftDown()) {
            return;
        }
        InventoryModule module = ModuleManager.get().typed("inventory", InventoryModule.class);
        if (module == null || !module.quickMoveDrag()) {
            return;
        }
        Slot hovered = this.hoveredSlot;
        if (hovered != null && hovered.hasItem() && hovered.index != this.aetherglass$lastLassoSlotIndex) {
            this.aetherglass$lastLassoSlotIndex = hovered.index;
            this.slotClicked(hovered, hovered.index, 0, ClickType.QUICK_MOVE);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void aetherglass$onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        this.aetherglass$lastLassoSlotIndex = -1;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void aetherglass$renderFlyingItemsOnTop(GuiGraphics graphics, int mouseX, int mouseY,
                                                    float partialTick, CallbackInfo info) {
        InventoryTransferAnimator.renderFlights(graphics);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void aetherglass$onScreenClosed(CallbackInfo info) {
        InventoryTransferAnimator.clear();
        this.aetherglass$lastLassoSlotIndex = -1;
    }
}
