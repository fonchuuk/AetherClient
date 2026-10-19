#!/usr/bin/env python3
"""
Рисует все растровые ресурсы главного меню AetherGlass в эстетике Liquid Glass.

Скрипт — единственный источник правды для каталога
``client/src/main/resources/assets/aetherglass/textures/gui``: что он
сохраняет, то и читает игра. Размеры проверяются тестом ``MenuAssetTest``.

Что именно рисуется
-------------------
1. ``menu_glass_logo.png``  (426×426)  — линзовая стеклянная пластина левой
   панели меню (скруглённый прямоугольник без отступа тени, чтобы 9-slice и
   кромка совпадали пиксель-в-пиксель без овалов внутри прямоугольника).
2. ``menu_glass_panel.png`` (715×506)  — то же для правой панели-дока.
3. ``menu_backdrop*.png`` (4 имени × 4 ступени размытия) — глубокие, сочные
   фоны Liquid Glass с северным сиянием и преломлением света в воде (без
   странных тёмных кругов и овалов).
"""

from __future__ import annotations

import os

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
AURORA = os.path.dirname(HERE)
GUI = os.path.join(
    AURORA, "client", "src", "main", "resources", "assets", "aetherglass",
    "textures", "gui")

LOGO_SIZE = 426
PANEL_W, PANEL_H = 715, 506
BACKDROP_SIZES = [(1376, 768), (688, 384), (344, 192), (172, 96)]
BACKDROP_NAMES = ["menu_backdrop", "menu_backdrop_aurora",
                  "menu_backdrop_glass", "menu_backdrop_night"]

# Сочная глубокая палитра Liquid Glass (океанический индиго, бирюза, аквамарин и неон)
SKY_TOP = np.array([0.024, 0.048, 0.115])     # глубокий сапфировый верх
SKY_MID = np.array([0.048, 0.118, 0.245])     # насыщенная океаническая синева
SKY_LOW = np.array([0.085, 0.195, 0.340])     # светящаяся глубина у горизонта
GLOW_HORIZON = np.array([0.18, 0.58, 0.88])   # яркое лазурное свечение
AURORA_A = np.array([0.16, 0.86, 0.78])       # сочная аквамариновая волна
AURORA_B = np.array([0.38, 0.48, 0.96])       # неоново-кобальтовая волна
CAUSTIC_C = np.array([0.42, 0.88, 1.00])      # солнечный блик преломления

BASE_W, BASE_H = BACKDROP_SIZES[0]
BLUR = (0.8, 2.4, 3.2, 4.0)


def _normalize(vx, vy):
    length = np.sqrt(vx * vx + vy * vy)
    safe = np.where(length > 1e-6, length, 1.0)
    return (np.where(length > 1e-6, vx / safe, 0.0),
            np.where(length > 1e-6, vy / safe, 0.0))


def _rounded_rect_sdf(x, y, width, height, radius):
    """Точное знаковое поле расстояний (SDF) скруглённого прямоугольника [0..width, 0..height]."""
    cx = width * 0.5
    cy = height * 0.5
    hx = width * 0.5 - radius - 1.0
    hy = height * 0.5 - radius - 1.0
    qx = np.abs(x - cx) - hx
    qy = np.abs(y - cy) - hy
    out_dist = np.sqrt(np.maximum(qx, 0.0) ** 2 + np.maximum(qy, 0.0) ** 2)
    in_dist = np.minimum(np.maximum(qx, qy), 0.0)
    return out_dist + in_dist - radius


def _save(rgb, path, alpha=None):
    image = Image.fromarray((np.clip(rgb, 0.0, 1.0) * 255).astype(np.uint8), "RGB")
    if alpha is not None:
        image.putalpha(
            Image.fromarray((np.clip(alpha, 0.0, 1.0) * 255).astype(np.uint8), "L"))
    image.save(path, optimize=True)
    return image


def _grid(height, width):
    yy, xx = np.mgrid[0:height, 0:width].astype(np.float64)
    return yy, xx


def _deep_liquid_sky(height, width, seed=20261215):
    """Глубокий сочный фон без тёмных кругов и овалов: плавные световые потоки и звёздная пыль."""
    yy, xx = _grid(height, width)
    u = xx / max(1.0, width - 1.0)
    v = yy / max(1.0, height - 1.0)
    t = v[:, :, None]

    sky = SKY_TOP * (1.0 - t) + SKY_MID * t
    low = np.clip((v - 0.45) / 0.55, 0.0, 1.0) ** 1.35
    sky = sky * (1.0 - low[:, :, None] * 0.72) + SKY_LOW * (low[:, :, None] * 0.72)

    # Плавная диагональная волна подводного преломления света (без кругов/овалов)
    wave1 = np.sin(u * 4.2 + v * 2.8 + 0.6) * 0.5 + 0.5
    wave2 = np.cos(u * 6.5 - v * 3.6 + 1.4) * 0.5 + 0.5
    ribbon = np.exp(-(((v - 0.58 - (wave1 - 0.5) * 0.16) / 0.24) ** 2))
    sky += (ribbon * (0.35 + 0.65 * wave2))[:, :, None] * GLOW_HORIZON * 0.42

    # Мягкое верхнее свечение солнечного преломления
    top_shimmer = np.exp(-(((v - 0.18 + np.sin(u * 3.5) * 0.06) / 0.18) ** 2)) * (0.4 + 0.6 * np.cos(u * 4.0 - 0.8) ** 2)
    sky += top_shimmer[:, :, None] * np.array([0.22, 0.56, 0.88]) * 0.34

    # Тонкая световая пыль в верхней части кадра
    rng = np.random.default_rng(seed)
    count = int(width * height / 1200.0)
    sx = rng.integers(0, width, count)
    sy = rng.integers(0, int(height * 0.80), count)
    brightness = rng.uniform(0.15, 0.70, count)
    fade = np.clip(1.0 - sy / (height * 0.90), 0.15, 1.0)
    np.add.at(sky, (sy, sx), (brightness * fade)[:, None] * np.array([0.72, 0.90, 1.00]))
    return np.clip(sky, 0.0, 1.0)


def _aurora_bands(sky, height, width):
    """Сочные плавные ленты северного сияния и жидкого света."""
    yy, xx = _grid(height, width)
    u = xx / width
    v = yy / height
    out = sky.copy()
    for phase, colour, amp, y0, spread in (
            (0.0, AURORA_A, 0.36, 0.32, 0.095),
            (2.1, AURORA_B, 0.30, 0.48, 0.120)):
        wave = (np.sin(u * 5.0 + phase) * 0.5
                + np.sin(u * 10.2 + phase * 1.6) * 0.25) * 0.075
        band = np.exp(-(((v - y0 - wave) / spread) ** 2))
        curtain = band * (0.58 + 0.42 * np.sin(u * 18.0 + phase))
        out += curtain[:, :, None] * colour * amp
    return np.clip(out, 0.0, 1.0)


def _liquid_refraction_waves(sky, height, width):
    """Мягкие каустические волны жидкого стекла вместо старых кругов/овалов."""
    yy, xx = _grid(height, width)
    u = xx / width
    v = yy / height
    out = sky.copy()
    c1 = np.sin(u * 7.5 + np.cos(v * 5.2) * 1.4)
    c2 = np.cos(v * 6.8 - np.sin(u * 6.0) * 1.3)
    caustic = np.clip((c1 * c2 + 1.0) * 0.5, 0.0, 1.0) ** 3.2
    envelope = np.exp(-(((v - 0.46) / 0.34) ** 2))
    out += (caustic * envelope)[:, :, None] * CAUSTIC_C * 0.28
    return np.clip(out, 0.0, 1.0)


def _drift(sky, height, width, dx=0.03, dz=0.02, seed=5):
    rng = np.random.default_rng(seed)
    yy, xx = _grid(height, width)
    nx = (xx / width + dx + rng.uniform(0, 1)) % 1.0
    ny = np.clip(yy / height + dz, 0.0, 1.0)
    xi = np.clip(np.rint(nx * (width - 1)).astype(np.int64), 0, width - 1)
    yi = np.clip(np.rint(ny * (height - 1)).astype(np.int64), 0, height - 1)
    return sky[yi, xi, :]


def _frame_image(array):
    return Image.fromarray(
        (np.clip(array, 0.0, 1.0) * 255).astype(np.uint8), "RGB")


def _make_backdrops():
    base = _deep_liquid_sky(BASE_H, BASE_W)
    frames = {
        "menu_backdrop": _frame_image(base),
        "menu_backdrop_aurora": _frame_image(_aurora_bands(base, BASE_H, BASE_W)),
        "menu_backdrop_glass": _frame_image(_liquid_refraction_waves(base, BASE_H, BASE_W)),
        "menu_backdrop_night": _frame_image(
            _drift(_aurora_bands(_liquid_refraction_waves(base, BASE_H, BASE_W), BASE_H, BASE_W),
                   BASE_H, BASE_W, -0.05, 0.015, seed=11)),
    }
    written = []
    for name in BACKDROP_NAMES:
        image0 = frames[name]
        for level, (w, h) in enumerate(BACKDROP_SIZES):
            if level == 0:
                image = image0.filter(ImageFilter.GaussianBlur(BLUR[0]))
            else:
                image = (image0.resize((w, h), Image.BOX)
                         .filter(ImageFilter.GaussianBlur(BLUR[level])))
            path = os.path.join(GUI, f"{name}_b{level}.png")
            _save(np.asarray(image, dtype=np.float64) / 255.0, path)
            written.append(path)
    return written


def _glass_sprite(width, height, *, corner_radius=24.0, bevel=28.0, strength=32.0,
                  base_alpha=0.30, edge_alpha=0.68):
    """Стеклянная линза-капля, точно вписанная в прямоугольник со скруглёнными углами."""
    yy, xx = _grid(height, width)
    d = _rounded_rect_sdf(xx, yy, width, height, corner_radius)
    gy, gx = np.gradient(d)
    nx, ny = _normalize(gx, gy)
    edge = np.clip((bevel + d) / bevel, 0.0, 1.0) ** 1.65

    # Внутренний преломлённый фон водной линзы
    u = xx / max(1.0, width - 1.0)
    v = yy / max(1.0, height - 1.0)
    t = v[:, :, None]
    bg = SKY_MID * (1.0 - t) + SKY_LOW * t
    bg += np.exp(-(((u - 0.32) ** 2 + (v - 0.85) ** 2) * 5.0))[:, :, None] * GLOW_HORIZON * 0.48
    bg += np.exp(-(((u - 0.72) ** 2 + (v - 0.20) ** 2) * 6.5))[:, :, None] * CAUSTIC_C * 0.26

    # Хроматическая дисперсия вдоль нормали мениска капли
    chroma = 0.065
    out = np.zeros_like(bg)
    for channel, spread in enumerate((1.0 + chroma, 1.0, 1.0 - chroma)):
        xs = np.clip(np.rint(xx + nx * edge * strength * spread).astype(np.int64),
                     0, width - 1)
        ys = np.clip(np.rint(yy + ny * edge * strength * spread).astype(np.int64),
                     0, height - 1)
        out[:, :, channel] = bg[ys, xs, channel]

    inside = np.clip(-d / 1.2 + 0.5, 0.0, 1.0)
    alpha = (base_alpha + (edge_alpha - base_alpha) * edge) * inside

    # Кристальный оттенок капли воды
    out[:, :, 0] = out[:, :, 0] * 1.04 + 0.024
    out[:, :, 1] = out[:, :, 1] * 1.08 + 0.048
    out[:, :, 2] = out[:, :, 2] * 1.12 + 0.088

    # Яркий солнечный блик на верхней-левой фаске капли
    facing = np.clip(nx * -0.58 + ny * -0.81, 0.0, 1.0) ** 1.5
    rim = np.exp(-(d ** 2) / (2.0 * 2.6 ** 2)) * facing
    out += rim[:, :, None] * np.array([0.88, 0.96, 1.00]) * 1.65
    out += (np.exp(d / 14.0) * (d < 0) * facing * 0.26)[:, :, None]

    # Мягкая внутренняя тень (inner shadow) у нижней кромки линзы
    inner_shadow = np.clip(ny, 0.0, 1.0) ** 1.35 * edge * 0.22
    out -= inner_shadow[:, :, None]

    # Диагональный солнечный зайчик внутри линзы
    sheen = np.exp(-(((u * 0.85 + v * 0.65 - 0.42) / 0.22) ** 2)) * (1.0 - edge * 0.5)
    out += (sheen * 0.18)[:, :, None] * np.array([0.82, 0.95, 1.00])

    out = np.clip(out, 0.0, 1.0)
    alpha_out = np.clip(alpha, 0.0, 1.0)
    rgb = np.where(alpha_out[:, :, None] > 1e-4, out, np.array([0.03, 0.06, 0.12]))
    return rgb, alpha_out


def _make_glass():
    written = []
    logo = _glass_sprite(LOGO_SIZE, LOGO_SIZE, corner_radius=24.0, bevel=28.0,
                         strength=32.0, base_alpha=0.30, edge_alpha=0.68)
    panel = _glass_sprite(PANEL_W, PANEL_H, corner_radius=24.0, bevel=30.0,
                          strength=34.0, base_alpha=0.30, edge_alpha=0.68)
    for name, (rgb, alpha) in (("menu_glass_logo", logo),
                               ("menu_glass_panel", panel)):
        path = os.path.join(GUI, name + ".png")
        _save(rgb, path, alpha=alpha)
        written.append(path)
    return written


def main():
    os.makedirs(GUI, exist_ok=True)
    written = _make_backdrops() + _make_glass()

    print("=== ресурсы главного меню ===")
    total = 0
    for path in written:
        size = os.path.getsize(path)
        total += size
        with Image.open(path) as image:
            dimensions = f"{image.width}x{image.height}"
        print(f"  {os.path.basename(path):30} {dimensions:>10}  {size / 1024:7.1f} KB")
    print(f"  {'итого':30} {'':>10}  {total / 1024:7.1f} KB  ({len(written)} файлов)")


if __name__ == "__main__":
    main()
