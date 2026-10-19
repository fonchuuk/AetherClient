package com.aetherglass.client.gui.screen;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.gui.widget.AuroraButton;
import com.aetherglass.client.gui.widget.AuroraLabel;
import com.aetherglass.client.gui.widget.AuroraList;
import com.aetherglass.client.gui.widget.AuroraSlider;
import com.aetherglass.client.gui.widget.AuroraToggle;
import com.aetherglass.client.gui.widget.AuroraWidget;
import com.aetherglass.client.gui.widget.ColorPicker;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.BlockEffectsModule;
import com.aetherglass.client.modules.impl.HandEffectsModule;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.render.shader.GuiShaderPreviewRenderer;
import com.aetherglass.client.render.shader.ShaderEffect;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Экран 16 уникальных шейдер-эффектов для {@code BlockShader} и {@code HandsShader}
 * в стиле Liquid Glass / iOS 26.
 *
 * <p>Слева расположен прокручиваемый список из 16 карточек эффектов (каждая с живым
 * анимированным превью, номером, названием, описанием и индикаторами активности для
 * блоков и рук). Справа — два раздельных живых окна предпросмотра (3D-блок и рука
 * с предметом от первого лица), независимое переключение {@code BlockShader} /
 * {@code HandsShader}, выбор цвета, ползунки интенсивности, скорости анимации и
 * прозрачности, а также кнопки сброса эффекта и восстановления стандартных параметров.</p>
 */
public final class ShaderEffectsScreen extends AuroraScreen {
    /** Цель редактирования в инспекторе справа. */
    public enum Target {
        BLOCK("Блоки (BlockShader)"),
        HANDS("Руки (HandsShader)");

        private final String label;

        Target(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }

    private final Screen parent;
    private final AuroraList inspectorList = new AuroraList(0, 0, 10, 10);
    private Target activeTarget = Target.BLOCK;
    private boolean syncBothTargets = false;

    private int leftWidth;
    private int rightLeft;
    private int rightWidth;
    private int previewBoxHeight;

    public ShaderEffectsScreen(Screen parent) {
        this(parent, Target.BLOCK);
    }

    public ShaderEffectsScreen(Screen parent, Target initialTarget) {
        super(
                Component.literal("Шейдер-эффекты (16)"),
                Component.literal("Независимая настройка BlockShader и HandsShader в стиле Liquid Glass")
        );
        this.parent = parent;
        this.activeTarget = initialTarget != null ? initialTarget : Target.BLOCK;
    }

    private BlockEffectsModule blocks() {
        return ModuleManager.get().typed("blocks", BlockEffectsModule.class);
    }

    private HandEffectsModule hands() {
        return ModuleManager.get().typed("hands", HandEffectsModule.class);
    }

    @Override
    protected void build() {
        this.inspectorList.clear();

        int x = contentLeft();
        int top = contentTop();
        int totalWidth = contentWidth();
        int totalHeight = contentHeight();

        this.leftWidth = Math.max(195, Math.min(280, Math.round(totalWidth * 0.45F)));
        this.rightLeft = x + this.leftWidth + PANEL_GAP;
        this.rightWidth = Math.max(170, totalWidth - this.leftWidth - PANEL_GAP);
        this.previewBoxHeight = Math.max(66, Math.min(86, totalHeight / 4));

        // ─── Верхняя панель управления целью и сбросом ───
        int tabW = Math.max(88, (this.leftWidth - 6) / 2);
        addWidget(new AuroraButton(x, top, tabW, 20,
                Component.literal(this.activeTarget == Target.BLOCK ? "● Блоки (Block)" : "Блоки (Block)"),
                btn -> {
                    this.activeTarget = Target.BLOCK;
                    rebuild();
                }));
        addWidget(new AuroraButton(x + tabW + 6, top, this.leftWidth - tabW - 6, 20,
                Component.literal(this.activeTarget == Target.HANDS ? "● Руки (Hands)" : "Руки (Hands)"),
                btn -> {
                    this.activeTarget = Target.HANDS;
                    rebuild();
                }));

        int actionBtnW = Math.max(76, (this.rightWidth - 12) / 3);
        addWidget(new AuroraButton(this.rightLeft, top, actionBtnW, 20,
                Component.literal("Сбросить эффект"),
                btn -> resetCurrentTargetEffect()));
        addWidget(new AuroraButton(this.rightLeft + actionBtnW + 6, top, actionBtnW, 20,
                Component.literal("По умолчанию"),
                btn -> resetAllToDefaults()));
        addWidget(new AuroraButton(this.rightLeft + (actionBtnW + 6) * 2, top,
                this.rightWidth - (actionBtnW + 6) * 2, 20,
                Component.literal("Назад"),
                btn -> close()));

        // ─── Левая колонка: 16 карточек эффектов с живым превью ───
        int listTop = top + 26;
        int listHeight = Math.max(60, totalHeight - 26);
        this.list.setBounds(x, listTop, this.leftWidth, listHeight);

        for (ShaderEffect effect : ShaderEffect.values()) {
            this.list.add(new EffectCardWidget(x + 2, 0, this.leftWidth - 8, effect), 6);
        }

        // ─── Правая колонка: раздельное превью Блока и Руки + настройки ───
        int inspectorTop = listTop + this.previewBoxHeight + 10;
        int inspectorHeight = Math.max(50, listHeight - this.previewBoxHeight - 10);
        this.inspectorList.setBounds(this.rightLeft, inspectorTop, this.rightWidth, inspectorHeight);

        buildInspectorControls();
    }

    private void buildInspectorControls() {
        BlockEffectsModule blockMod = blocks();
        HandEffectsModule handMod = hands();
        if (blockMod == null || handMod == null) {
            return;
        }

        int x = this.rightLeft + 6;
        int width = Math.max(130, this.rightWidth - 16);
        int controlWidth = Math.min(156, Math.max(96, width - 88));
        int controlX = x + width - controlWidth;

        boolean isBlock = this.activeTarget == Target.BLOCK;
        ShaderEffect selectedEffect = isBlock ? blockMod.shaderEffect() : handMod.shaderEffect();

        this.inspectorList.add(new AuroraLabel(x, 0, width,
                Component.literal(this.activeTarget.label() + " · " + selectedEffect.cardTitle()),
                AuroraLabel.Style.TITLE), 6);
        this.inspectorList.add(new AuroraLabel(x, 0, width,
                Component.literal(selectedEffect.description()),
                AuroraLabel.Style.MUTED), 4);

        // 1. Независимые переключатели активности BlockShader и HandsShader
        boolean targetEnabled = isBlock
                ? (blockMod.isEnabled() && blockMod.useShaderEffect())
                : (handMod.isEnabled() && handMod.useShaderEffect());

        this.inspectorList.add(new AuroraLabel(x, 0, width - 50,
                Component.literal(isBlock ? "Включить BlockShader" : "Включить HandsShader"),
                AuroraLabel.Style.BODY), 8);
        this.inspectorList.add(new AuroraToggle(x + width - 44, 0, targetEnabled, value -> {
            if (isBlock) {
                blockMod.setEnabled(value);
                blockMod.setUseShaderEffect(value);
            } else {
                handMod.setEnabled(value);
                handMod.setUseShaderEffect(value);
            }
            ModuleManager.get().saveNow();
        }), -14);

        this.inspectorList.add(new AuroraLabel(x, 0, width - 50,
                Component.literal("Применять выбор сразу к Блокам и Рукам"),
                AuroraLabel.Style.MUTED), 8);
        this.inspectorList.add(new AuroraToggle(x + width - 44, 0, this.syncBothTargets, value -> {
            this.syncBothTargets = value;
        }), -14);

        // 2. Ползунки: Интенсивность, Скорость анимации, Прозрачность
        FloatSetting intensitySetting = isBlock ? blockMod.shaderIntensitySetting() : handMod.shaderIntensitySetting();
        FloatSetting speedSetting = isBlock ? blockMod.shaderSpeedSetting() : handMod.shaderSpeedSetting();
        FloatSetting opacitySetting = isBlock ? blockMod.shaderOpacitySetting() : handMod.shaderOpacitySetting();
        ColorSetting colorSetting = isBlock ? blockMod.shaderColorSetting() : handMod.shaderColorSetting();

        addSliderRow("Интенсивность", intensitySetting, x, controlX, controlWidth - 34, () -> {
            if (this.syncBothTargets) {
                if (isBlock) {
                    handMod.setShaderIntensity(intensitySetting.get());
                } else {
                    blockMod.setShaderIntensity(intensitySetting.get());
                }
            }
        });

        addSliderRow("Скорость анимации", speedSetting, x, controlX, controlWidth - 34, () -> {
            if (this.syncBothTargets) {
                if (isBlock) {
                    handMod.setShaderSpeed(speedSetting.get());
                } else {
                    blockMod.setShaderSpeed(speedSetting.get());
                }
            }
        });

        addSliderRow("Прозрачность", opacitySetting, x, controlX, controlWidth - 34, () -> {
            if (this.syncBothTargets) {
                if (isBlock) {
                    handMod.setShaderOpacity(opacitySetting.get());
                } else {
                    blockMod.setShaderOpacity(opacitySetting.get());
                }
            }
        });

        // 3. Выбор цвета (ColorPicker)
        this.inspectorList.add(new AuroraLabel(x, 0, width,
                Component.literal("Основной цвет эффекта"),
                AuroraLabel.Style.BODY), 10);
        this.inspectorList.add(new ColorPicker(x, 0, width - 6, colorSetting, newColor -> {
            if (this.syncBothTargets) {
                if (isBlock) {
                    handMod.setShaderColor(newColor);
                } else {
                    blockMod.setShaderColor(newColor);
                }
            }
        }), 6);

        // 4. Кнопка применения эталонного цвета и параметров выбранного эффекта
        this.inspectorList.add(new AuroraButton(x, 0, width - 6, 20,
                Component.literal("Эталонные параметры «" + selectedEffect.displayName() + "»"),
                btn -> resetCurrentTargetEffect()), 8);
    }

    private void addSliderRow(String title, FloatSetting setting, int labelX, int sliderX, int sliderW, Runnable onChange) {
        int safeSliderW = Math.max(64, sliderW);
        this.inspectorList.add(new AuroraLabel(labelX, 0, Math.max(50, sliderX - labelX - 6),
                Component.literal(title), AuroraLabel.Style.BODY), 10);
        this.inspectorList.add(new AuroraSlider(sliderX, 0, safeSliderW,
                AuroraSlider.forFloat(setting), setting.min(), setting.max(), setting.step(),
                val -> onChange.run()), -14);
    }

    private void selectEffect(ShaderEffect effect, boolean applyPresetDefaults) {
        BlockEffectsModule blockMod = blocks();
        HandEffectsModule handMod = hands();
        if (blockMod == null || handMod == null) {
            return;
        }
        if (this.activeTarget == Target.BLOCK || this.syncBothTargets) {
            blockMod.setEnabled(true);
            blockMod.setUseShaderEffect(true);
            if (applyPresetDefaults) {
                blockMod.selectShaderPreset(effect);
            } else {
                blockMod.setShaderEffect(effect);
            }
        }
        if (this.activeTarget == Target.HANDS || this.syncBothTargets) {
            handMod.setEnabled(true);
            handMod.setUseShaderEffect(true);
            if (applyPresetDefaults) {
                handMod.selectShaderPreset(effect);
            } else {
                handMod.setShaderEffect(effect);
            }
        }
        ModuleManager.get().saveNow();
        rebuild();
    }

    private void resetCurrentTargetEffect() {
        BlockEffectsModule blockMod = blocks();
        HandEffectsModule handMod = hands();
        if (blockMod != null && (this.activeTarget == Target.BLOCK || this.syncBothTargets)) {
            blockMod.resetCurrentShaderEffect();
        }
        if (handMod != null && (this.activeTarget == Target.HANDS || this.syncBothTargets)) {
            handMod.resetCurrentShaderEffect();
        }
        ModuleManager.get().saveNow();
        rebuild();
    }

    private void resetAllToDefaults() {
        BlockEffectsModule blockMod = blocks();
        HandEffectsModule handMod = hands();
        if (blockMod != null) {
            blockMod.resetShaderDefaults();
        }
        if (handMod != null) {
            handMod.resetShaderDefaults();
        }
        ModuleManager.get().saveNow();
        rebuild();
    }

    @Override
    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentLeft();
        int top = contentTop() + 26;
        int height = contentHeight() - 26;
        int radius = Math.round(AuroraTheme.radius() + 2);

        // Стеклянные подложки левой (16 карточек) и правой (превью + инспектор) колонок
        DrawUtil.fillGlass(graphics, x - 4, top - 4, this.leftWidth + 8, height + 8, radius);
        DrawUtil.fillGlass(graphics, this.rightLeft - 4, top - 4, this.rightWidth + 8, height + 8, radius);

        // ─── Два раздельных живых окна предпросмотра: Блок (BlockShader) и Рука (HandsShader) ───
        BlockEffectsModule blockMod = blocks();
        HandEffectsModule handMod = hands();
        float time = FrameClock.elapsedSeconds();
        Font font = Minecraft.getInstance().font;

        int previewGap = 8;
        int singlePreviewW = Math.max(60, (this.rightWidth - previewGap) / 2);
        int blockPreviewX = this.rightLeft;
        int handPreviewX = this.rightLeft + singlePreviewW + previewGap;
        int previewY = top + 2;

        if (blockMod != null) {
            boolean blockActive = blockMod.isEnabled() && blockMod.useShaderEffect();
            GuiShaderPreviewRenderer.renderBlockPreview(
                    graphics, blockPreviewX, previewY, singlePreviewW, this.previewBoxHeight,
                    blockMod.shaderEffect(), blockActive, blockMod.shaderColor(),
                    blockMod.shaderIntensity(), blockMod.shaderSpeed(), blockMod.shaderOpacity(),
                    time, 1.0F
            );
            String blockBadge = "Блок: " + blockMod.shaderEffect().displayName();
            graphics.drawString(font, font.plainSubstrByWidth(blockBadge, singlePreviewW - 10),
                    blockPreviewX + 5, previewY + 4,
                    this.activeTarget == Target.BLOCK ? AuroraTheme.accent() : AuroraTheme.textPrimary(), false);
            String stateText = blockActive ? "ВКЛ" : "ВЫКЛ";
            int stateCol = blockActive ? 0xFF4ADE9B : AuroraTheme.textMuted();
            graphics.drawString(font, stateText,
                    blockPreviewX + singlePreviewW - font.width(stateText) - 5,
                    previewY + this.previewBoxHeight - font.lineHeight - 3, stateCol, false);
        }

        if (handMod != null) {
            boolean handActive = handMod.isEnabled() && handMod.useShaderEffect();
            GuiShaderPreviewRenderer.renderHandPreview(
                    graphics, handPreviewX, previewY, this.rightWidth - singlePreviewW - previewGap, this.previewBoxHeight,
                    handMod.shaderEffect(), handActive, handMod.shaderColor(),
                    handMod.shaderIntensity(), handMod.shaderSpeed(), handMod.shaderOpacity(),
                    time, 1.0F
            );
            int handBoxW = this.rightWidth - singlePreviewW - previewGap;
            String handBadge = "Руки: " + handMod.shaderEffect().displayName();
            graphics.drawString(font, font.plainSubstrByWidth(handBadge, handBoxW - 10),
                    handPreviewX + 5, previewY + 4,
                    this.activeTarget == Target.HANDS ? AuroraTheme.accent() : AuroraTheme.textPrimary(), false);
            String stateText = handActive ? "ВКЛ" : "ВЫКЛ";
            int stateCol = handActive ? 0xFF4ADE9B : AuroraTheme.textMuted();
            graphics.drawString(font, stateText,
                    handPreviewX + handBoxW - font.width(stateText) - 5,
                    previewY + this.previewBoxHeight - font.lineHeight - 3, stateCol, false);
        }

        this.inspectorList.update(FrameClock.deltaSeconds());
        this.inspectorList.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.inspectorList.mouseClicked(event, doubleClick)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.inspectorList.mouseReleased(event)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.inspectorList.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.inspectorList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void close() {
        ModuleManager.get().saveNow();
        client().setScreen(this.parent);
    }

    @Override
    public void removed() {
        ModuleManager.get().saveNow();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        close();
    }

    /**
     * Карточка одного из 16 шейдер-эффектов с живым анимированным превью, заголовком,
     * кратким описанием и бейджами активности для блоков и рук.
     */
    private final class EffectCardWidget extends AuroraWidget {
        private static final int CARD_HEIGHT = 48;
        private static final int SWATCH_WIDTH = 52;

        private final ShaderEffect effect;

        private EffectCardWidget(int x, int y, int width, ShaderEffect effect) {
            super(x, y, width, CARD_HEIGHT, Component.literal(effect.cardTitle()));
            this.effect = effect;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            float alpha = entranceAlpha();
            if (alpha <= 0.002F) {
                return;
            }
            BlockEffectsModule blockMod = blocks();
            HandEffectsModule handMod = hands();
            boolean isBlockEffect = blockMod != null && blockMod.shaderEffect() == this.effect;
            boolean isHandEffect = handMod != null && handMod.shaderEffect() == this.effect;
            boolean isFocusedTarget = (activeTarget == Target.BLOCK && isBlockEffect)
                    || (activeTarget == Target.HANDS && isHandEffect);

            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            int radius = Math.round(AuroraTheme.radius());

            if (isFocusedTarget) {
                DrawUtil.drawGlow(graphics, x, y, width, height, radius, this.effect.defaultColor(), 0.45F * alpha);
                DrawUtil.fillRounded(graphics, x, y, width, height, radius,
                        ColorUtil.fade(this.effect.defaultColor(), 0.22F * alpha));
            }
            DrawUtil.fillGlass(graphics, x, y, width, height, radius, pressAmount(), hoverAmount());
            if (isFocusedTarget) {
                DrawUtil.fillRounded(graphics, x, y + 4, 3, height - 8, 1,
                        ColorUtil.fade(AuroraTheme.accent(), alpha));
            }

            // Живое анимированное превью шейдера слева в карточке
            int previewColor = this.effect.defaultColor();
            float previewIntensity = this.effect.defaultIntensity();
            float previewSpeed = this.effect.defaultSpeed();
            float previewOpacity = this.effect.defaultOpacity();
            if (isFocusedTarget) {
                if (activeTarget == Target.BLOCK && blockMod != null) {
                    previewColor = blockMod.shaderColor();
                    previewIntensity = blockMod.shaderIntensity();
                    previewSpeed = blockMod.shaderSpeed();
                    previewOpacity = blockMod.shaderOpacity();
                } else if (activeTarget == Target.HANDS && handMod != null) {
                    previewColor = handMod.shaderColor();
                    previewIntensity = handMod.shaderIntensity();
                    previewSpeed = handMod.shaderSpeed();
                    previewOpacity = handMod.shaderOpacity();
                }
            }

            GuiShaderPreviewRenderer.renderCardPreview(
                    graphics, x + 6, y + 6, SWATCH_WIDTH, height - 12,
                    this.effect, previewColor, previewIntensity, previewSpeed, previewOpacity,
                    FrameClock.elapsedSeconds(), alpha
            );

            // Заголовок, бейджи БЛОК / РУКИ и описание эффекта
            Font font = Minecraft.getInstance().font;
            int textX = x + SWATCH_WIDTH + 12;
            int textMaxW = Math.max(40, width - SWATCH_WIDTH - 18);

            graphics.drawString(font, this.effect.cardTitle(), textX, y + 5,
                    ColorUtil.fade(isFocusedTarget ? AuroraTheme.accent() : AuroraTheme.textPrimary(), alpha), false);

            // Бейджи справа вверху карточки
            int badgeRight = x + width - 6;
            if (isHandEffect) {
                String hTag = "РУКИ";
                int tagW = font.width(hTag) + 6;
                badgeRight -= tagW;
                DrawUtil.fillRounded(graphics, badgeRight, y + 4, tagW, 10, 3,
                        ColorUtil.fade(0xFF9B5DE5, 0.55F * alpha));
                graphics.drawString(font, hTag, badgeRight + 3, y + 5,
                        ColorUtil.fade(0xFFFFFFFF, alpha), false);
                badgeRight -= 3;
            }
            if (isBlockEffect) {
                String bTag = "БЛОК";
                int tagW = font.width(bTag) + 6;
                badgeRight -= tagW;
                DrawUtil.fillRounded(graphics, badgeRight, y + 4, tagW, 10, 3,
                        ColorUtil.fade(0xFF00BBF9, 0.55F * alpha));
                graphics.drawString(font, bTag, badgeRight + 3, y + 5,
                        ColorUtil.fade(0xFFFFFFFF, alpha), false);
            }

            List<FormattedCharSequence> lines = font.split(Component.literal(this.effect.description()), textMaxW);
            int maxLines = Math.min(2, lines.size());
            for (int i = 0; i < maxLines; i++) {
                graphics.drawString(font, lines.get(i), textX, y + 18 + i * (font.lineHeight + 1),
                        ColorUtil.fade(AuroraTheme.textSecondary(), 0.90F * alpha), false);
            }

            renderRipple(graphics, x, y, width, height, radius, this.effect.defaultColor());
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            beginFeedback(event.x(), event.y());
            // Обычный клик выбирает эффект с его эталонной палитрой, чтобы каждый из 16 эффектов сразу раскрывался во всей красе
            selectEffect(this.effect, true);
        }
    }
}
