package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.crosshair.CrosshairRenderer;
import com.aetherglass.client.render.crosshair.CrosshairStyle;
import com.aetherglass.client.render.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Настраиваемый прицел (Блок 4.3): поддерживает минимальный физический размер
 * буквально в 1–2 пикселя, тонкую настройку толщины, зазора, прозрачности и формы,
 * чтобы прицел был аккуратным, еле заметным и удобным.
 */
public final class CrosshairModule extends Module {
    private static final int HOSTILE_COLOR = 0xFFFF6E80;

    private final EnumSetting<CrosshairStyle> style = register(new EnumSetting<>("style", "Форма прицела",
            "Точка 1–2px, тонкий крестик, кольцо или уголки", CrosshairStyle.CROSS, CrosshairStyle.values(),
            setting -> settingChanged(setting)));
    private final FloatSetting size = register(new FloatSetting("size", "Размер (пикс.)",
            "Длина лучей или диаметр точки в физических пикселях (от 1.0px)",
            2.5F, 1.0F, 16.0F, 0.5F,
            setting -> settingChanged(setting)));
    private final FloatSetting thickness = register(new FloatSetting("thickness", "Толщина (пикс.)",
            "Толщина линий или точки в физических пикселях (от 1.0px)",
            1.0F, 1.0F, 6.0F, 0.5F,
            setting -> settingChanged(setting)));
    private final FloatSetting gap = register(new FloatSetting("gap", "Зазор в центре",
            "Отступ лучей от центра (0 — сплошной микро-прицел)",
            1.5F, 0.0F, 10.0F, 0.5F,
            setting -> settingChanged(setting)));
    private final FloatSetting opacity = register(new FloatSetting("opacity", "Непрозрачность",
            "Позволяет сделать прицел полупрозрачным и еле заметным",
            0.82F, 0.15F, 1.00F, 0.05F,
            setting -> settingChanged(setting)));
    private final BooleanSetting centerDot = register(new BooleanSetting("centerDot", "Центральная точка",
            "Рисует микро-точку 1–2px в самом центре прицела",
            false, setting -> settingChanged(setting)));
    private final BooleanSetting outline = register(new BooleanSetting("outline", "Тонкая тень-контур",
            "Контрастная тень 1px для читаемости на светлом небе и снегу",
            true, setting -> settingChanged(setting)));
    private final BooleanSetting dynamic = register(new BooleanSetting("dynamic", "Отклик на цель",
            "Лёгкое расширение при ударе и подсветка при наведении на сущность",
            true, setting -> settingChanged(setting)));
    private final ColorSetting color = register(new ColorSetting("color", "Цвет прицела",
            "Основной цвет прицела", 0xFFAA3DFF, setting -> settingChanged(setting)));

    private boolean installed;
    private float targetSpread;
    private float currentSpread;
    private float targetEmphasis;
    private float currentEmphasis;

    public CrosshairModule() {
        super("crosshair", "Свой прицел",
                "Микро-прицел от 1–2 пикселей с настройкой толщины, зазора, прозрачности и формы",
                ModuleCategory.INTERFACE);
    }

    /** Подключает прицел к реестру элементов экрана Fabric API. */
    public void install() {
        if (this.installed) {
            return;
        }
        this.installed = true;
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original ->
                (GuiGraphics context, net.minecraft.client.DeltaTracker tickCounter) -> {
                    if (isEnabled() && shouldDraw()) {
                        draw(context);
                    } else {
                        original.render(context, tickCounter);
                    }
                });
    }

    private boolean shouldDraw() {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null || client.options.hideGui || client.player == null) {
            return false;
        }
        return client.options.getCameraType().isFirstPerson();
    }

    @Override
    protected void onDisable() {
        this.targetSpread = 0.0F;
        this.currentSpread = 0.0F;
        this.targetEmphasis = 0.0F;
        this.currentEmphasis = 0.0F;
    }

    @Override
    public void tick(float deltaSeconds) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (this.dynamic.isEnabled()) {
            float cooldown = client.player.getAttackStrengthScale(0.0F);
            float swing = 1.0F - cooldown;
            HitResult hit = client.hitResult;
            boolean onEntity = hit instanceof EntityHitResult;
            this.targetSpread = swing * 2.5F + (onEntity ? 1.0F : 0.0F);
            this.targetEmphasis = onEntity ? 1.0F : 0.0F;
        } else {
            this.targetSpread = 0.0F;
            this.targetEmphasis = 0.0F;
        }
        float step = Math.min(1.0F, deltaSeconds * 14.0F);
        this.currentSpread += (this.targetSpread - this.currentSpread) * step;
        this.currentEmphasis += (this.targetEmphasis - this.currentEmphasis) * step;
    }

    private void draw(GuiGraphics context) {
        int base = ColorController.resolve(this.color.get());
        int blended = ColorUtil.lerp(base, HOSTILE_COLOR, this.currentEmphasis);
        int finalColor = ColorUtil.fade(blended, this.opacity.get());

        CrosshairRenderer.draw(
                context,
                context.guiWidth(),
                context.guiHeight(),
                this.style.get(),
                finalColor,
                this.size.get(),
                this.thickness.get(),
                this.gap.get(),
                this.currentSpread,
                this.centerDot.isEnabled(),
                this.outline.isEnabled()
        );
    }

    public String activity() {
        return this.style.get().toString().toLowerCase();
    }
}
