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

    /** Счётчик ошибок подряд и признак «хватит»: журнал не должен забиваться. */
    private static final int MAX_FAILURES = 12;
    private static int failures;
    private static boolean giveUp;

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
        try {
            FrameClock.tick();
            ModuleManager.get().update(FrameClock.deltaSeconds());
        } catch (Throwable throwable) {
            // Исключение, выброшенное из события Fabric, уронило бы кадр, а
            // за ним и всю игру. Наши эффекты такого не стоят: пишем в журнал
            // и спокойно рисуем дальше.
            fail("обновление кадра эффектов", throwable);
        }
    }

    /** Частицы рисуются в мире до полупрозрачной геометрии: так они правильно смешиваются. */
    private static void renderParticles(WorldRenderContext context) {
        ParticleModule module = ModuleManager.get().typed("particles", ParticleModule.class);
        if (module == null || !module.isEnabled() || module.system().alive() == 0) {
            return;
        }
        if (giveUp) {
            return;
        }
        MultiBufferSource.BufferSource buffers = RenderResources.acquire();
        if (buffers == null) {
            return;
        }
        PoseStack pose = context.matrices();
        pose.pushPose();
        boolean broken = false;
        try {
            Camera camera = context.gameRenderer().getMainCamera();
            Vec3 origin = camera.position();
            pose.translate(-origin.x, -origin.y, -origin.z);
            module.system().render(pose, buffers, camera, module.fadeSeconds(), module.fadeSeconds());
        } catch (Throwable throwable) {
            broken = true;
            fail("отрисовка частиц", throwable);
        } finally {
            // Стек матриц возвращаем при любом исходе: оставленный в нём
            // лишний слой сломал бы отрисовку уже следующего кадра, и виноваты
            // в этом были бы не мы, а выглядело бы как наш вылет.
            pose.popPose();
        }
        if (broken) {
            // Рисование упало — буфер мог остаться незакрытым. Закрывать его
            // теперь нельзя, поэтому просто выбрасываем: следующий кадр
            // создаст чистый.
            RenderResources.release();
            return;
        }
        try {
            buffers.endBatch();
        } catch (Throwable throwable) {
            RenderResources.release();
            fail("закрытие порции буфера частиц", throwable);
        }
    }

    /** Подсветка блока под прицелом. */
    private static void renderBlocks(WorldRenderContext context) {
        BlockEffectsModule module = ModuleManager.get().typed("blocks", BlockEffectsModule.class);
        if (module == null || !module.isEnabled() || giveUp) {
            return;
        }
        PoseStack pose = context.matrices();
        pose.pushPose();
        try {
            Camera camera = context.gameRenderer().getMainCamera();
            Vec3 origin = camera.position();
            pose.translate(-origin.x, -origin.y, -origin.z);
            BlockEffectRenderer.render(pose, camera, module);
        } catch (Throwable throwable) {
            // Рисование не дошло до endBatch, значит буфер мог остаться
            // незакрытым. Выбрасываем его: следующий кадр создаст чистый,
            // иначе игра упадёт уже на чужом, испорченном состоянии.
            RenderResources.release();
            fail("подсветка блока", throwable);
        } finally {
            pose.popPose();
        }
    }

    /**
     * Запись об ошибке. Первые несколько пишутся полностью — по ним понятно,
     * что именно сломалось. Если ошибка повторяется каждый кадр, журнал не
     * забивается: эффекты просто отключаются, о чём пишется один раз.
     */
    private static void fail(String where, Throwable throwable) {
        failures++;
        if (failures <= 3) {
            AuroraLog.error(where, throwable);
        }
        if (failures == MAX_FAILURES) {
            giveUp = true;
            AuroraLog.error(where + ": повторилось " + MAX_FAILURES +
                    " раз подряд, эффекты в мире отключены до перезапуска клиента");
        }
    }
}
