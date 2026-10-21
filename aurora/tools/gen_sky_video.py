#!/usr/bin/env python3
"""Генератор видео-панорамы неба для купола SkyShader.

Выход — один PNG-лист: 12 кадров по 512x192, уложенных вертикально (512x2304).
Кадр — равноугольная (equirectangular) панорама: столбцы = азимут 0..360°,
строки = высота от зенита (верх) до горизонта (низ). Купол в `SkyEffectBlender`
берёт UV из этой развёртки и переключает номер кадра по времени, поэтому на кадр
не тратится ни одной операции шейдера и ни одной загрузки текстуры.

Почему лист, а не отдельные файлы: один батч, одна текстура, ноль переключений
состояния. Почему 512x192: купол всё равно смягчён атмосферой, а лист остаётся
дешёвым по видеопамяти (512x2304 RGBA ≈ 4.7 МБ).

Расчёт полностью векторный (numpy): 12 кадров собираются за доли секунды.
"""
from __future__ import annotations

import math
import os

import numpy as np
from PIL import Image, ImageFilter

W, H = 512, 192
FRAMES = 12
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "client", "src", "main", "resources", "assets", "aetherglass",
                       "textures", "sky")

# зенит, середина неба, цвет сияния, цвет горизонта
PALETTES = {
    "aurora": ((6, 12, 30), (10, 42, 74), (52, 240, 190), (126, 92, 255)),
    "nebula": ((9, 6, 28), (36, 16, 68), (217, 70, 239), (56, 189, 248)),
    "sunset": ((24, 10, 34), (70, 22, 64), (255, 107, 107), (255, 179, 71)),
    "prism": ((5, 14, 30), (10, 40, 60), (0, 245, 212), (255, 42, 133)),
    "night": ((4, 8, 22), (12, 26, 58), (76, 201, 240), (114, 9, 183)),
}


def build_frame(name: str, frame: int) -> Image.Image:
    zenith_bg, mid_bg, glow_a, glow_b = PALETTES[name]
    phase = frame / FRAMES * math.tau

    u = (np.arange(W, dtype=np.float32) / W)[None, :]            # 1 x W
    v = (np.arange(H, dtype=np.float32) / (H - 1))[:, None]      # H x 1
    ang = u * math.tau

    up_w = np.clip(1.0 - v, 0.0, 1.0) ** 1.5
    horizon_w = np.clip((v - 0.55) / 0.45, 0.0, 1.0)
    t_mid = np.clip(v / 0.62, 0.0, 1.0)

    # 1. Базовый градиент купола: зенит -> середина -> горизонт
    base = np.zeros((H, W, 3), dtype=np.float32)
    for i in range(3):
        base[:, :, i] = zenith_bg[i] + (mid_bg[i] - zenith_bg[i]) * t_mid

    # 2. Полярное сияние: два занавеса, замкнутых по азимуту (без шва на 0°/360°)
    wave1 = np.sin(ang * 2.0 + phase) * 0.5 + np.sin(ang * 3.0 - phase * 0.7) * 0.3
    wave2 = np.sin(ang * 1.0 - phase * 1.3) * 0.5 + np.sin(ang * 5.0 + phase * 0.4) * 0.2
    curtain1 = np.clip(1.0 - np.abs(v - (0.22 + wave1 * 0.10)) / 0.20, 0.0, 1.0) ** 1.7
    curtain1 *= np.clip(0.72 + 0.28 * np.sin(ang * 4.0 + phase * 2.0), 0.0, 1.0)
    curtain2 = np.clip(1.0 - np.abs(v - (0.46 + wave2 * 0.13)) / 0.24, 0.0, 1.0) ** 2.0
    curtain2 *= np.clip(0.55 + 0.45 * np.cos(ang * 3.0 - phase * 1.6), 0.0, 1.0)

    for i in range(3):
        base[:, :, i] = base[:, :, i] * (1.0 - 0.80 * curtain1) + glow_a[i] * 0.80 * curtain1
        base[:, :, i] = base[:, :, i] * (1.0 - 0.66 * curtain2) + glow_b[i] * 0.66 * curtain2

    # 3. Тёплое свечение у горизонта
    haze = (horizon_w ** 2.2) * (0.34 + 0.20 * np.sin(ang * 2.0 - phase * 0.9))
    for i in range(3):
        base[:, :, i] = base[:, :, i] * (1.0 - haze) + glow_b[i] * haze * 0.55

    # 4. Мягкое затухание к горизонту — стык с землёй без резкой линии
    fade = 0.55 + 0.45 * (1.0 - horizon_w)
    base *= fade[:, :, None]

    img = np.clip(base, 0, 255).astype(np.uint8)

    # 5. Звёзды: фиксированные положения, мерцание по фазе кадра
    rng = np.random.default_rng(7219)
    count = 190
    sx = (rng.random(count) * W).astype(np.int32)
    sy = ((rng.random(count) ** 1.6) * H * 0.72).astype(np.int32)
    size = np.where(rng.random(count) < 0.86, 1, 2)
    bright = 0.30 + rng.random(count) * 0.70
    twinkle = rng.random(count) * math.tau
    k = bright * (0.62 + 0.38 * np.sin(phase * 2.0 + twinkle))
    colors = np.stack([np.clip(206 * k, 0, 255), np.clip(232 * k, 0, 255), np.clip(255 * k, 0, 255)], axis=1)
    for i in range(count):
        for dy in range(int(size[i])):
            for dx in range(int(size[i])):
                yy = sy[i] + dy
                if 0 <= yy < H:
                    img[yy, (sx[i] + dx) % W] = colors[i].astype(np.uint8)

    return Image.fromarray(img, "RGB")


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    for name in PALETTES:
        sheet = Image.new("RGB", (W, H * FRAMES))
        for i in range(FRAMES):
            sheet.paste(build_frame(name, i), (0, i * H))
        sheet = sheet.filter(ImageFilter.SMOOTH)   # сглаживает ступеньки градиента
        out = os.path.join(OUT_DIR, f"sky_video_{name}.png")
        sheet.save(out, optimize=True)
        print("готово:", os.path.basename(out), sheet.size, os.path.getsize(out), "байт")


if __name__ == "__main__":
    main()
