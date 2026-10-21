#!/usr/bin/env python3
"""Генератор видео-панорамы неба для купола SkyShader (спокойный градиент).

Выход — один PNG-лист: 12 кадров по 512×192, уложенных вертикально (512×2304).
Кадр — равноугольная (equirectangular) панорама: столбцы = азимут 0…360°,
строки = высота от зенита (верх) до горизонта (низ).

Что изменено относительно первой версии: убраны «кислотные» занавесы сияния.
Прежний лист давал жёсткие полосы зелёного и пурпура поперёк неба — на экране
это читалось как артефакты. Теперь кадр — плавный градиент заката/рассвета:
глубокий синий у зенита, мягкий тёплый или бирюзовый свет у горизонта, и
медленно ползущее световое пятно (солнце за горизонтом). Ни одной ступеньки,
ни одной резкой границы — только гладкие косинусные переходы.

Кадры отличаются фазой этого пятна и лёгким дыханием градиента, поэтому небо
живое (видео), но спокойное.
"""
from __future__ import annotations

import math
import os

import numpy as np
from PIL import Image

W, H = 512, 192
FRAMES = 12
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "client", "src", "main", "resources", "assets", "aetherglass",
                       "textures", "sky")

# зенит, верхняя середина, нижняя середина, горизонт, цвет светила
PALETTES = {
    "aurora": ((5, 10, 26), (10, 26, 54), (18, 52, 78), (44, 96, 108), (150, 236, 214)),
    "nebula": ((8, 6, 24), (24, 16, 52), (46, 30, 84), (86, 58, 120), (208, 168, 255)),
    "sunset": ((12, 12, 34), (38, 22, 56), (96, 44, 66), (196, 92, 78), (255, 186, 122)),
    "prism": ((4, 12, 26), (8, 30, 50), (14, 58, 72), (30, 104, 110), (140, 240, 226)),
    "night": ((3, 6, 18), (8, 16, 40), (14, 28, 62), (26, 48, 92), (128, 176, 232)),
}


def build_frame(name: str, frame: int) -> Image.Image:
    zenith, upper, lower, horizon, sun = PALETTES[name]
    phase = frame / FRAMES * math.tau

    u = (np.arange(W, dtype=np.float32) / W)[None, :]
    v = (np.arange(H, dtype=np.float32) / (H - 1))[:, None]
    ang = u * math.tau

    # 1. Основа: четыре опорных цвета, соединённые гладкими косинусными весами
    stops = [zenith, upper, lower, horizon]
    weights = []
    for i in range(4):
        centre = i / 3.0
        w = np.cos(np.clip((v - centre) * 3.0, -math.pi, math.pi) * 0.5) ** 2
        weights.append(w)
    total = sum(weights)
    base = np.zeros((H, W, 3), dtype=np.float32)
    for i in range(4):
        for c in range(3):
            base[:, :, c] += np.asarray(stops[i][c], dtype=np.float32) * (weights[i] / total)

    # 2. Светило за горизонтом: медленно ползёт по азимуту, светится у горизонта
    sun_az = phase                                  # один оборот за 12 кадров
    d_az = np.abs(((u - sun_az / math.tau + 0.5) % 1.0) - 0.5) * 2.0
    glow_az = np.clip(1.0 - d_az, 0.0, 1.0) ** 2.2
    glow_v = np.clip((v - 0.42) / 0.58, 0.0, 1.0) ** 1.7
    glow = glow_az * glow_v
    for c in range(3):
        base[:, :, c] += sun[c] * glow * 0.42

    # 3. Дыхание градиента: мягкая волна яркости, чтобы кадры не были клонами
    breath = 0.5 + 0.5 * math.sin(phase * 1.0)
    base *= (0.96 + 0.06 * breath)

    # 4. Плавное затухание к горизонту: стык с землёй без резкой линии
    fade = 0.82 + 0.18 * np.clip(1.0 - v, 0.0, 1.0)
    base *= fade[:, :, None]

    return Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    for name in PALETTES:
        sheet = Image.new("RGB", (W, H * FRAMES))
        for i in range(FRAMES):
            sheet.paste(build_frame(name, i), (0, i * H))
        out = os.path.join(OUT_DIR, f"sky_video_{name}.png")
        sheet.save(out, optimize=True)
        print("готово:", os.path.basename(out), sheet.size, os.path.getsize(out), "байт")


if __name__ == "__main__":
    main()
