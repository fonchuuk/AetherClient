package com.aetherglass.client.render.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Проекция точки мира на экран без выделения памяти ({@code out} — переиспользуемый массив).
 *
 * <p>Считается вручную от позиции глаз игрока и его угла обзора: никаких матриц и
 * аллокаций в кадре, поэтому подходит для маркеров, цифр урона и полосок HP.</p>
 */
public final class Projector {
    private Projector() {
    }

    /**
     * @param out массив длиной ≥ 3: {@code [x, y, depth]} в координатах GUI
     * @return {@code true}, если точка перед камерой и попала в экран
     */
    public static boolean toScreen(double x, double y, double z, float partialTick,
                                   int screenWidth, int screenHeight, float[] out) {
        if (out == null || out.length < 3) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null) {
            return false;
        }
        Vec3 eye = player.getEyePosition(partialTick);
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;

        float yaw = (float) Math.toRadians(player.getViewYRot(partialTick));
        float pitch = (float) Math.toRadians(player.getViewXRot(partialTick));
        float sinYaw = (float) Math.sin(yaw);
        float cosYaw = (float) Math.cos(yaw);
        float sinPitch = (float) Math.sin(pitch);
        float cosPitch = (float) Math.cos(pitch);

        // Вперёд в мире: (-sinYaw·cosPitch, -sinPitch, cosYaw·cosPitch); вправо: (cosYaw, 0, sinYaw)
        float side = (float) dx * cosYaw + (float) dz * sinYaw;
        float depth = (float) dx * sinYaw * cosPitch + (float) dy * sinPitch - (float) dz * cosYaw * cosPitch;
        if (depth > -0.08F) {
            return false;
        }
        float up = (float) dx * sinYaw * sinPitch + (float) dy * cosPitch - (float) dz * cosYaw * sinPitch;

        double fov = Math.toRadians(mc.options.fov().get());
        float scale = (float) (1.0 / Math.tan(fov * 0.5));
        float aspect = screenWidth / (float) Math.max(1, screenHeight);
        float ndcX = side / -depth * scale / aspect;
        float ndcY = up / -depth * scale;
        if (ndcX < -1.25F || ndcX > 1.25F || ndcY < -1.25F || ndcY > 1.25F) {
            return false;
        }
        out[0] = (ndcX * 0.5F + 0.5F) * screenWidth;
        out[1] = (0.5F - ndcY * 0.5F) * screenHeight;
        out[2] = -depth;
        return true;
    }
}
