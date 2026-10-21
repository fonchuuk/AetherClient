#!/usr/bin/env python3
"""Мягкие текстуры частиц: светящаяся капля и искра.

Старые `circle.png`/`spark.png` были жёсткими кольцами с резкой кромкой — именно
поэтому частицы в игре читались как плоские точки. Здесь профиль яркости
плавный: яркое ядро, широкий мягкий ореол, нулевая кромка. Тонировка всё равно
идёт из цвета вершины, поэтому текстура строго белая.
"""
from __future__ import annotations

import math
import os

import numpy as np
from PIL import Image

SIZE = 64
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "client", "src", "main", "resources", "assets", "aetherglass",
                       "textures", "particle")


def radial_grid():
    y, x = np.mgrid[0:SIZE, 0:SIZE].astype(np.float32)
    cx = cy = (SIZE - 1) / 2.0
    dx = (x - cx) / (SIZE / 2.0)
    dy = (y - cy) / (SIZE / 2.0)
    return dx, dy


def drop() -> Image.Image:
    """Капля: плотное ядро + гладкий ореол, без видимой кромки."""
    dx, dy = radial_grid()
    d = np.sqrt(dx * dx + dy * dy)
    d = np.clip(d, 0.0, 1.0)
    halo = np.clip(1.0 - d, 0.0, 1.0) ** 2.4          # мягкий ореол
    core = np.clip(1.0 - d / 0.42, 0.0, 1.0) ** 1.6    # яркое ядро
    alpha = np.clip(halo * 0.55 + core * 0.85, 0.0, 1.0)
    rgb = np.full((SIZE, SIZE, 3), 255.0, dtype=np.float32)
    # к краю свет чуть уходит в голубой — так капля выглядит объёмнее
    blue_shift = np.clip(d - 0.45, 0.0, 1.0) / 0.55
    rgb[:, :, 0] *= 1.0 - 0.16 * blue_shift
    rgb[:, :, 1] *= 1.0 - 0.06 * blue_shift
    out = np.dstack([rgb, alpha * 255.0]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def spark() -> Image.Image:
    """Искра: четыре луча с гауссовым затуханием и мягким центром."""
    dx, dy = radial_grid()
    ax, ay = np.abs(dx), np.abs(dy)
    beams = np.maximum(np.exp(-(ax * ax) * 42.0) * np.exp(-(ay * ay) * 3.2),
                       np.exp(-(ay * ay) * 42.0) * np.exp(-(ax * ax) * 3.2))
    core = np.clip(1.0 - np.sqrt(dx * dx + dy * dy) / 0.38, 0.0, 1.0) ** 1.5
    alpha = np.clip(beams * 0.75 + core * 0.9, 0.0, 1.0)
    rgb = np.full((SIZE, SIZE, 3), 255.0, dtype=np.float32)
    out = np.dstack([rgb, alpha * 255.0]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, image in (("circle.png", drop()), ("spark.png", spark())):
        path = os.path.join(OUT_DIR, name)
        image.save(path, optimize=True)
        print("готово:", name, image.size, os.path.getsize(path), "байт")


if __name__ == "__main__":
    main()
