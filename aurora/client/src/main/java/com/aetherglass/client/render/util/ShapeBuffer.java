package com.aetherglass.client.render.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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
        consumer.addVertex(pose, x0, y0, z0).setColor(color).setUv(0.5F, 0.5F);
        consumer.addVertex(pose, x1, y1, z1).setColor(color).setUv(0.5F, 0.5F);
        consumer.addVertex(pose, x2, y2, z2).setColor(color).setUv(0.5F, 0.5F);
        consumer.addVertex(pose, x3, y3, z3).setColor(color).setUv(0.5F, 0.5F);
    }

    /** Четырёхугольник с градиентом: для мягких свечений и «жидких» бликов. */
    public static void addGradientQuad(PoseStack.Pose pose, VertexConsumer consumer, float x0, float y0, float z0,
                                       float x1, float y1, float z1, float x2, float y2, float z2,
                                       float x3, float y3, float z3, int inner, int outer) {
        consumer.addVertex(pose, x0, y0, z0).setColor(inner).setUv(0.5F, 0.5F);
        consumer.addVertex(pose, x1, y1, z1).setColor(outer).setUv(0.5F, 0.5F);
        consumer.addVertex(pose, x2, y2, z2).setColor(outer).setUv(0.5F, 0.5F);
        consumer.addVertex(pose, x3, y3, z3).setColor(inner).setUv(0.5F, 0.5F);
    }
}
