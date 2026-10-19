package com.aetherglass.client.render.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Простая геометрия: грани и ребра параллелепипеда, отдельные четырёхугольники. */
public final class ShapeBuffer {
    private ShapeBuffer() {
    }

    /** Шесть граней блока: полупрозрачная «подсветка» выбранного блока. */
    public static void addBoxFaces(PoseStack.Pose pose, VertexConsumer consumer, float minX, float minY, float minZ,
                                   float maxX, float maxY, float maxZ, int color) {
        addQuad(pose, consumer, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, color);
        addQuad(pose, consumer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        addQuad(pose, consumer, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, color);
        addQuad(pose, consumer, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, color);
        addQuad(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color);
        addQuad(pose, consumer, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, color);
    }

    /**
     * Шесть граней блока с вертикальным градиентом: сверху светлее, снизу
     * темнее. Так выглядит «жидкое стекло» — заливка не плоская, а объёмная.
     *
     * <p>Порядок вершин в каждом четырёхугольнике подобран так, чтобы нижние
     * две вершины получили цвет {@code bottom}, а верхние — {@code top}.</p>
     */
    public static void addGradientBoxFaces(PoseStack.Pose pose, VertexConsumer consumer, float minX, float minY,
                                           float minZ, float maxX, float maxY, float maxZ, int top, int bottom) {
        addGradientQuad(pose, consumer, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ,
                bottom, top);
        addGradientQuad(pose, consumer, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ,
                bottom, top);
        addGradientQuad(pose, consumer, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ,
                bottom, top);
        addGradientQuad(pose, consumer, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ,
                bottom, top);
        addQuad(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, top);
        addQuad(pose, consumer, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, bottom);
    }

    /** Двенадцать рёбер блока: тонкие полосы, чтобы контур читался на любом фоне. */
    public static void addBoxEdges(PoseStack.Pose pose, VertexConsumer consumer, float minX, float minY, float minZ,
                                   float maxX, float maxY, float maxZ, float thickness, int color) {
        float half = thickness * 0.5F;
        // Четыре вертикальных ребра.
        addBox(pose, consumer, minX - half, minY - half, minZ - half, minX + half, maxY + half, minZ + half, color);
        addBox(pose, consumer, maxX - half, minY - half, minZ - half, maxX + half, maxY + half, minZ + half, color);
        addBox(pose, consumer, minX - half, minY - half, maxZ - half, minX + half, maxY + half, maxZ + half, color);
        addBox(pose, consumer, maxX - half, minY - half, maxZ - half, maxX + half, maxY + half, maxZ + half, color);
        // Верхние рёбра.
        addBox(pose, consumer, minX - half, maxY - half, minZ - half, maxX + half, maxY + half, minZ + half, color);
        addBox(pose, consumer, minX - half, maxY - half, maxZ - half, maxX + half, maxY + half, maxZ + half, color);
        addBox(pose, consumer, minX - half, maxY - half, minZ - half, minX + half, maxY + half, maxZ + half, color);
        addBox(pose, consumer, maxX - half, maxY - half, minZ - half, maxX + half, maxY + half, maxZ + half, color);
        // Нижние рёбра.
        addBox(pose, consumer, minX - half, minY - half, minZ - half, maxX + half, minY + half, minZ + half, color);
        addBox(pose, consumer, minX - half, minY - half, maxZ - half, maxX + half, minY + half, maxZ + half, color);
        addBox(pose, consumer, minX - half, minY - half, minZ - half, minX + half, minY + half, maxZ + half, color);
        addBox(pose, consumer, maxX - half, minY - half, minZ - half, maxX + half, minY + half, maxZ + half, color);
    }

    /**
     * Тонкий параллелепипед вдоль произвольного ребра, параллельного одной из осей.
     *
     * <p>Именно так рисуются рёбра сложных форм: ребро берётся из настоящей формы
     * блока, а толщина задаётся настройкой.</p>
     */
    public static void addEdge(PoseStack.Pose pose, VertexConsumer consumer, float x0, float y0, float z0,
                               float x1, float y1, float z1, float thickness, int color) {
        float half = thickness * 0.5F;
        addBox(pose, consumer,
                Math.min(x0, x1) - half, Math.min(y0, y1) - half, Math.min(z0, z1) - half,
                Math.max(x0, x1) + half, Math.max(y0, y1) + half, Math.max(z0, z1) + half, color);
    }

    /** Параллелепипед: только внешние грани, без внутренних перегородок. */
    public static void addBox(PoseStack.Pose pose, VertexConsumer consumer, float minX, float minY, float minZ,
                              float maxX, float maxY, float maxZ, int color) {
        addBoxFaces(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, color);
    }

    /**
     * Четырёхугольник по четырём вершинам (обход против часовой стрелки).
     *
     * <p>Все координаты уже в системе координат кадра: вызывающий сам переводит их
     * относительно камеры.</p>
     */
    public static void addQuad(PoseStack.Pose pose, VertexConsumer consumer, float x0, float y0, float z0,
                               float x1, float y1, float z1, float x2, float y2, float z2,
                               float x3, float y3, float z3, int color) {
        addGradientQuad(pose, consumer, x0, y0, z0, x1, y1, z1, x2, y2, z2,
                x3, y3, z3, color, color);
    }

    /** Четырёхугольник с явно заданной нормалью грани. */
    public static void addQuad(PoseStack.Pose pose, VertexConsumer consumer,
                               float x0, float y0, float z0,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               float x3, float y3, float z3,
                               float nx, float ny, float nz, int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        vertex(pose, consumer, x0, y0, z0, color, nx, ny, nz);
        vertex(pose, consumer, x1, y1, z1, color, nx, ny, nz);
        vertex(pose, consumer, x2, y2, z2, color, nx, ny, nz);
        vertex(pose, consumer, x3, y3, z3, color, nx, ny, nz);
    }

    /** Текстурированный четырёхугольник (билборд частицы) со всеми 6 атрибутами NEW_ENTITY. */
    public static void addTexturedQuad(PoseStack.Pose pose, VertexConsumer consumer,
                                       float x0, float y0, float z0, float u0, float v0,
                                       float x1, float y1, float z1, float u1, float v1,
                                       float x2, float y2, float z2, float u2, float v2,
                                       float x3, float y3, float z3, float u3, float v3,
                                       int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        consumer.addVertex(pose, x0, y0, z0).setColor(color).setUv(u0, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, x1, y1, z1).setColor(color).setUv(u1, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, x2, y2, z2).setColor(color).setUv(u2, v2)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, x3, y3, z3).setColor(color).setUv(u3, v3)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** Четырёхугольник с градиентом: для мягких свечений и «жидких» бликов. */
    public static void addGradientQuad(PoseStack.Pose pose, VertexConsumer consumer, float x0, float y0, float z0,
                                       float x1, float y1, float z1, float x2, float y2, float z2,
                                       float x3, float y3, float z3, int inner, int outer) {
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        float bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 1.0E-8F) {
            nx /= length;
            ny /= length;
            nz /= length;
        } else {
            ny = 1.0F;
        }
        vertex(pose, consumer, x0, y0, z0, inner, nx, ny, nz);
        vertex(pose, consumer, x1, y1, z1, outer, nx, ny, nz);
        vertex(pose, consumer, x2, y2, z2, outer, nx, ny, nz);
        vertex(pose, consumer, x3, y3, z3, inner, nx, ny, nz);
    }

    /**
     * Четырёхугольник с собственным цветом в каждой из четырёх вершин и заданной нормалью.
     * Используется процедурными шейдер-эффектами (BlockShader и HandsShader) для
     * плавной аппаратной интерполяции паттерна по поверхности.
     */
    public static void addShadedQuad(PoseStack.Pose pose, VertexConsumer consumer,
                                     float x0, float y0, float z0, int c0,
                                     float x1, float y1, float z1, int c1,
                                     float x2, float y2, float z2, int c2,
                                     float x3, float y3, float z3, int c3,
                                     float nx, float ny, float nz) {
        if ((c0 >>> 24) == 0 && (c1 >>> 24) == 0 && (c2 >>> 24) == 0 && (c3 >>> 24) == 0) {
            return;
        }
        vertex(pose, consumer, x0, y0, z0, c0, nx, ny, nz);
        vertex(pose, consumer, x1, y1, z1, c1, nx, ny, nz);
        vertex(pose, consumer, x2, y2, z2, c2, nx, ny, nz);
        vertex(pose, consumer, x3, y3, z3, c3, nx, ny, nz);
    }

    /**
     * Непрерывный сегмент ребра без торцевых перегородок и нахлёстов:
     * плавно интерполирует цвет от {@code color0} к {@code color1} без «пунктирных» швов.
     */
    public static void addContinuousEdgeTube(PoseStack.Pose pose, VertexConsumer consumer,
                                             float x0, float y0, float z0,
                                             float x1, float y1, float z1,
                                             float thickness, int color0, int color1) {
        if ((color0 >>> 24) == 0 && (color1 >>> 24) == 0) {
            return;
        }
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6F) {
            return;
        }
        float tx = dx / len;
        float ty = dy / len;
        float tz = dz / len;

        // Выбираем два перпендикулярных вектора (u, v) к направлению ребра t
        float upX = Math.abs(ty) < 0.9F ? 0.0F : 1.0F;
        float upY = Math.abs(ty) < 0.9F ? 1.0F : 0.0F;
        float upZ = 0.0F;

        float ux = ty * upZ - tz * upY;
        float uy = tz * upX - tx * upZ;
        float uz = tx * upY - ty * upX;
        float uLen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (uLen > 1.0E-6F) {
            ux /= uLen;
            uy /= uLen;
            uz /= uLen;
        }
        float vx = ty * uz - tz * uy;
        float vy = tz * ux - tx * uz;
        float vz = tx * uy - ty * ux;

        float half = thickness * 0.5F;
        ux *= half;
        uy *= half;
        uz *= half;
        vx *= half;
        vy *= half;
        vz *= half;

        // 4 угла в начале (x0,y0,z0) и в конце (x1,y1,z1)
        float a0x = x0 - ux - vx, a0y = y0 - uy - vy, a0z = z0 - uz - vz;
        float b0x = x0 + ux - vx, b0y = y0 + uy - vy, b0z = z0 + uz - vz;
        float c0x = x0 + ux + vx, c0y = y0 + uy + vy, c0z = z0 + uz + vz;
        float d0x = x0 - ux + vx, d0y = y0 - uy + vy, d0z = z0 - uz + vz;

        float a1x = x1 - ux - vx, a1y = y1 - uy - vy, a1z = z1 - uz - vz;
        float b1x = x1 + ux - vx, b1y = y1 + uy - vy, b1z = z1 + uz - vz;
        float c1x = x1 + ux + vx, c1y = y1 + uy + vy, c1z = z1 + uz + vz;
        float d1x = x1 - ux + vx, d1y = y1 - uy + vy, d1z = z1 - uz + vz;

        addShadedQuad(pose, consumer, a0x, a0y, a0z, color0, b0x, b0y, b0z, color0, b1x, b1y, b1z, color1, a1x, a1y, a1z, color1, 0.0F, -1.0F, 0.0F);
        addShadedQuad(pose, consumer, b0x, b0y, b0z, color0, c0x, c0y, c0z, color0, c1x, c1y, c1z, color1, b1x, b1y, b1z, color1, 1.0F, 0.0F, 0.0F);
        addShadedQuad(pose, consumer, c0x, c0y, c0z, color0, d0x, d0y, d0z, color0, d1x, d1y, d1z, color1, c1x, c1y, c1z, color1, 0.0F, 1.0F, 0.0F);
        addShadedQuad(pose, consumer, d0x, d0y, d0z, color0, a0x, a0y, a0z, color0, a1x, a1y, a1z, color1, d1x, d1y, d1z, color1, -1.0F, 0.0F, 0.0F);
    }

    /** Полный NEW_ENTITY: незаполненные атрибуты приводят к ошибке завершения вершины. */
    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
                               int color, float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z).setColor(color).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
