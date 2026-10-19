package com.aetherglass.client.render;

import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.BlockEffectsModule;
import com.aetherglass.client.modules.impl.ParticleModule;
import com.aetherglass.client.render.block.BlockEffectRenderer;
import com.aetherglass.client.render.util.FrameClock;
import com.aetherglass.client.utils.AuroraLog;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Мост между событиями отрисовки мира Fabric API и нашими 3D-эффектами:
 * <ul>
 *   <li>{@link WorldRenderEvents#START_MAIN} — обновление таймера кадра и модулей;</li>
 *   <li>{@link WorldRenderEvents#BEFORE_BLOCK_OUTLINE} — отключение стандартной чёрной обводки,
 *       когда активен {@code BlockShader};</li>
 *   <li>{@link WorldRenderEvents#END_MAIN} — отрисовка мировых частиц ({@code Particles})
 *       и полупрозрачной стеклянной подсветки блока под прицелом ({@code BlockShader})
 *       в мировых координатах {@code (worldPos - camera.position())}.</li>
 * </ul>
 */
public final class WorldRenderDispatcher {
    private static boolean registered;

    private static final RenderFailureTracker frameFailures = new RenderFailureTracker("frame", "обновление кадра эффектов");
    private static final RenderFailureTracker particleFailures = new RenderFailureTracker("particles", "отрисовка частиц");
    private static final RenderFailureTracker blockFailures = new RenderFailureTracker("blocks", "подсветка блока");

    private WorldRenderDispatcher() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        WorldRenderEvents.START_MAIN.register(context -> updateFrame());
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outlineRenderState) -> {
            BlockEffectsModule module = ModuleManager.get().typed("blocks", BlockEffectsModule.class);
            if (module != null && module.isEnabled() && module.outlined() != null) {
                return false;
            }
            return true;
        });
        WorldRenderEvents.END_MAIN.register(context -> {
            renderParticles(context);
            renderBlocks(context);
        });
        AuroraLog.info("обработчики отрисовки мира подключены");
    }

    private static void updateFrame() {
        if (frameFailures.disabled()) {
            return;
        }
        try {
            FrameClock.tick();
            ModuleManager.get().update(FrameClock.deltaSeconds());
            frameFailures.success();
        } catch (Throwable throwable) {
            frameFailures.failure(throwable);
        }
    }

    private static void renderParticles(WorldRenderContext context) {
        ParticleModule module = ModuleManager.get().typed("particles", ParticleModule.class);
        if (module == null || !module.isEnabled() || module.system().alive() == 0) {
            return;
        }
        if (particleFailures.disabled()) {
            return;
        }
        PoseStack pose = null;
        boolean pushed = false;
        boolean completed = false;
        try {
            MultiBufferSource.BufferSource buffers = RenderResources.acquire();
            if (buffers == null) {
                throw new IllegalStateException("RenderResources.acquire() вернул null");
            }
            pose = context.matrices();
            if (pose != null) {
                pose.pushPose();
                pushed = true;
            }
            Camera camera = context.gameRenderer().getMainCamera();
            module.system().render(pose, buffers, camera, module.fadeSeconds(), module.fadeSeconds());
            buffers.endBatch();
            completed = true;
            particleFailures.success();
        } catch (Throwable throwable) {
            particleFailures.failure(throwable);
        } finally {
            try {
                if (pushed && pose != null) {
                    pose.popPose();
                }
            } finally {
                if (!completed) {
                    RenderResources.release();
                }
            }
        }
    }

    private static void renderBlocks(WorldRenderContext context) {
        BlockEffectsModule module = ModuleManager.get().typed("blocks", BlockEffectsModule.class);
        if (module == null || !module.isEnabled() || blockFailures.disabled()) {
            return;
        }
        PoseStack pose = null;
        boolean pushed = false;
        try {
            pose = context.matrices();
            if (pose != null) {
                pose.pushPose();
                pushed = true;
            }
            Camera camera = context.gameRenderer().getMainCamera();
            BlockEffectRenderer.render(pose, camera, module);
            blockFailures.success();
        } catch (Throwable throwable) {
            RenderResources.release();
            blockFailures.failure(throwable);
        } finally {
            if (pushed && pose != null) {
                pose.popPose();
            }
        }
    }
}
