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
import net.minecraft.world.phys.Vec3;

/**
 * Мост между событиями отрисовки мира Fabric API и нашими эффектами.
 *
 * <p>Регистрируются только реально существующие события: обновление состояния — в начале
 * основного прохода, частицы — перед полупрозрачной геометрией, подсветка блока — перед
 * отладочными отрисовками, как и рекомендует документация API. Никаких выдуманных хуков.</p>
 */
public final class WorldRenderDispatcher {
    private static boolean registered;

    // Ошибка одного прохода не должна отключать остальные эффекты.
    private static final RenderFailureTracker frameFailures = new RenderFailureTracker("frame", "обновление кадра эффектов");
    private static final RenderFailureTracker particleFailures = new RenderFailureTracker("particles", "отрисовка частиц");
    private static final RenderFailureTracker blockFailures = new RenderFailureTracker("blocks", "подсветка блока");

    private WorldRenderDispatcher() {
    }

    /** Подключает обработчики. Повторный вызов ничего не делает. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        WorldRenderEvents.START_MAIN.register(context -> updateFrame());
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldRenderDispatcher::renderParticles);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(WorldRenderDispatcher::renderBlocks);
        AuroraLog.info("обработчики отрисовки мира подключены");
    }

    /** Один раз за кадр: часы, контроллер цвета и состояние модулей. */
    private static void updateFrame() {
        if (frameFailures.disabled()) {
            return;
        }
        try {
            FrameClock.tick();
            ModuleManager.get().update(FrameClock.deltaSeconds());
            frameFailures.success();
        } catch (Throwable throwable) {
            // Исключение, выброшенное из события Fabric, уронило бы кадр, а
            // за ним и всю игру. Наши эффекты такого не стоят: пишем в журнал
            // и спокойно рисуем дальше.
            frameFailures.failure(throwable);
        }
    }

    /** Частицы рисуются в мире до полупрозрачной геометрии: так они правильно смешиваются. */
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
                throw new IllegalStateException("RenderResources.acquire() вернул null; причина выделения записана выше");
            }
            pose = context.matrices();
            pose.pushPose();
            pushed = true;
            Camera camera = context.gameRenderer().getMainCamera();
            Vec3 origin = camera.position();
            pose.translate(-origin.x, -origin.y, -origin.z);
            module.system().render(pose, buffers, camera, module.fadeSeconds(), module.fadeSeconds());
            buffers.endBatch();
            completed = true;
            particleFailures.success();
        } catch (Throwable throwable) {
            particleFailures.failure(throwable);
        } finally {
            try {
                if (pushed) {
                    pose.popPose();
                }
            } finally {
                if (!completed) {
                    RenderResources.release();
                }
            }
        }
    }

    /** Подсветка блока под прицелом. */
    private static void renderBlocks(WorldRenderContext context) {
        BlockEffectsModule module = ModuleManager.get().typed("blocks", BlockEffectsModule.class);
        if (module == null || !module.isEnabled() || blockFailures.disabled()) {
            return;
        }
        PoseStack pose = null;
        boolean pushed = false;
        try {
            pose = context.matrices();
            pose.pushPose();
            pushed = true;
            Camera camera = context.gameRenderer().getMainCamera();
            Vec3 origin = camera.position();
            pose.translate(-origin.x, -origin.y, -origin.z);
            BlockEffectRenderer.render(pose, camera, module);
            blockFailures.success();
        } catch (Throwable throwable) {
            // Рисование не дошло до endBatch, значит буфер мог остаться
            // незакрытым. Выбрасываем его: следующий кадр создаст чистый,
            // иначе игра упадёт уже на чужом, испорченном состоянии.
            RenderResources.release();
            blockFailures.failure(throwable);
        } finally {
            if (pushed) {
                pose.popPose();
            }
        }
    }

}
