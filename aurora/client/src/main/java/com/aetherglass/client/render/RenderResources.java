package com.aetherglass.client.render;

import com.aetherglass.client.utils.AuroraLog;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Собственный буфер вершин для эффектов.
 *
 * <p>Буфер создаётся один раз, когда эффекты действительно нужны, и освобождается при
 * выключении модулей и при завершении работы клиента. Это важно: буфер занимает
 * память вне «кучи» Java, поэтому её нужно вернуть системе вручную.</p>
 */
public final class RenderResources {
    private static ByteBufferBuilder builder;
    private static MultiBufferSource.BufferSource source;

    private RenderResources() {
    }

    /**
     * Возвращает общий источник буферов, создавая его при первом обращении.
     *
     * @return буфер или {@code null}, если выделить его не удалось
     */
    public static synchronized MultiBufferSource.BufferSource acquire() {
        if (source == null) {
            try {
                builder = new ByteBufferBuilder(RenderType.TRANSIENT_BUFFER_SIZE);
                source = MultiBufferSource.immediate(builder);
            } catch (Throwable throwable) {
                AuroraLog.error("не удалось выделить видеопамять под эффекты", throwable);
                source = null;
                builder = null;
                return null;
            }
        }
        return source;
    }

    /** Освобождает буфер; следующий вызов {@link #acquire()} создаст его заново. */
    public static synchronized void release() {
        try {
            if (builder != null) {
                builder.close();
            }
        } catch (Throwable throwable) {
            AuroraLog.error("освобождение буфера эффектов", throwable);
        } finally {
            builder = null;
            source = null;
        }
    }

    public static synchronized boolean allocated() {
        return builder != null;
    }
}
