package com.aetherglass.client.render.hud;

import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Locale;

/**
 * Замеры производительности для элемента «Производительность».
 *
 * <p>Показания обновляются не каждый кадр, а по окну в полсекунды: так числа не мельтешат,
 * а на сам замер тратится минимум времени. Загрузка процессора берётся у виртуальной
 * машины — это реальная доля процессорного времени процесса, а не «придуманная» цифра.</p>
 */
public final class PerformanceProbe {
    private static final float WINDOW_SECONDS = 0.5F;
    private static final com.sun.management.OperatingSystemMXBean OS =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();

    private static int frames;
    private static float windowTime;
    private static float fps;
    private static float cpu;
    private static long usedMemory = 1L;
    private static long maxMemory = 2L;
    private static int latency = -1;
    private static long sessionStart = System.currentTimeMillis();

    private PerformanceProbe() {
    }

    /** Вызывается один раз за кадр. */
    public static void sample() {
        frames++;
        windowTime += FrameClock.deltaSeconds();
        if (windowTime < WINDOW_SECONDS) {
            return;
        }
        fps = frames / windowTime;
        frames = 0;
        windowTime = 0.0F;
        double load = OS.getProcessCpuLoad();
        if (load >= 0.0D && load <= 1.0D) {
            cpu = (float) load;
        }
        usedMemory = MEMORY.getHeapMemoryUsage().getUsed();
        maxMemory = MEMORY.getHeapMemoryUsage().getMax();
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        latency = -1;
        if (player != null) {
            ClientPacketListener connection = client.getConnection();
            if (connection != null) {
                PlayerInfo info = connection.getPlayerInfo(player.getUUID());
                if (info != null) {
                    latency = info.getLatency();
                }
            }
        }
    }

    public static int fps() {
        return Math.round(fps);
    }

    /** Загрузка процессора в процентах. */
    public static int cpuPercent() {
        return Math.round(cpu * 100.0F);
    }

    public static long usedMemoryMb() {
        return usedMemory / (1024L * 1024L);
    }

    public static long maxMemoryMb() {
        return maxMemory / (1024L * 1024L);
    }

    public static int memoryPercent() {
        if (maxMemory <= 0L) {
            return 0;
        }
        return (int) (100L * usedMemory / maxMemory);
    }

    /** Задержка до сервера в миллисекундах или -1, если замерить не удалось. */
    public static int latency() {
        return latency;
    }

    /** Время с запуска клиента в формате чч:мм:сс. */
    public static String sessionTime() {
        long seconds = (System.currentTimeMillis() - sessionStart) / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600L, seconds / 60L % 60L, seconds % 60L);
    }
}
