#!/usr/bin/env python3
"""
Рисует все растровые ресурсы главного меню AetherGlass.

Скрипт единственный источник правды для каталога
``client/src/main/resources/assets/aetherglass/textures/gui``: что он
сохраняет, то и читает игра. Размер каждого файла жёстко связан с числом
в Java-коде, поэтому размеры объявлены здесь же и проверяются тестом
``MenuAssetTest`` — рассинхрон «код ждёт 426, а на диске 1376» больше
невозможен без падения сборки.

Что именно рисуется
-------------------
1. ``menu_glass_logo.png``  (426×426)  — стеклянная пластина левой панели
   меню. Спрайт вырезан ровно по форме, поэтому девять частей
   (``drawNineSlice`` в ``AuroraMenuScreen``) растягиваются без искажения
   углов.
2. ``menu_glass_panel.png`` (715×506)  — то же для правой панели-дока.
3. ``menu_backdrop*.png`` (4 имени × 4 ступени размытия) — фоны галереи
   ``BackdropGallery``. Ступени ``b0…b3`` соответствуют таблице ``SIZE``
   в Java: 1376×768, 688×384, 344×192, 172×96.

Как считается стекло
--------------------
Поле расстояний до формы (sdf) даёт нормаль к поверхности; у кромки фон
выбирается со сдвигом вдоль нормали, и сдвиг разный для каналов RGB —
отсюда цветная кайма настоящего преломления, а не нарисованная рамка.

Экономия памяти
---------------
Фоны — гладкие градиенты, поэтому они сохраняются в палитре (≤256 цветов):
визуально разница незаметна, а файл в разы меньше. Все четыре ступени
одного фона в сумме весят меньше, чем раньше весила одна картинка.

Запуск:  python3 tools/make_menu_assets.py
"""

from __future__ import annotations

import os

import numpy as np
from PIL import Image

# ── пути ────────────────────────────────────────────────────────────
HERE = os.path.dirname(os.path.abspath(__file__))
AURORA = os.path.dirname(HERE)
GUI = os.path.join(
    AURORA, "client", "src", "main", "resources", "assets", "aetherglass",
    "textures", "gui")

# ── размеры: обязаны совпадать с константами в Java ─────────────────
# AuroraMenuScreen.TEX_LOGO_SIZE
LOGO_SIZE = 426
# AuroraMenuScreen.TEX_PANEL_W / TEX_PANEL_H
PANEL_W, PANEL_H = 715, 506
# BackdropGallery.SIZE
BACKDROP_SIZES = [(1376, 768), (688, 384), (344, 192), (172, 96)]
# BackdropGallery.NAMES
BACKDROP_NAMES = ["menu_backdrop", "menu_backdrop_aurora",
                  "menu_backdrop_glass", "menu_backdrop_night"]

# ── палитра тёмной зимней ночи ──────────────────────────────────────
SKY_TOP = np.array([0.030, 0.038, 0.075])    # почти чёрный сине-фиолетовый
SKY_MID = np.array([0.075, 0.098, 0.190])    # холодная синева
SKY_LOW = np.array([0.135, 0.125, 0.235])    # фиолетовый у горизонта
GLOW_HORIZON = np.array([0.30, 0.42, 0.72])  # мягкое свечение над горизонтом
AURORA_A = np.array([0.24, 0.78, 0.66])      # бирюзовая лента
AURORA_B = np.array([0.44, 0.36, 0.86])      # фиолетовая лента

BASE_W, BASE_H = BACKDROP_SIZES[0]


# ── служебное ───────────────────────────────────────────────────────
def _normalize(vx, vy):
    length = np.sqrt(vx * vx + vy * vy)
    safe = np.where(length > 1e-6, length, 1.0)
    return (np.where(length > 1e-6, vx / safe, 0.0),
            np.where(length > 1e-6, vy / safe, 0.0))


def _squircle_sdf(x, y, cx, cy, ax, ay, n=4.0):
    """Поле расстояний до суперэллипса: <0 внутри, 0 на кромке, >0 снаружи."""
    u = np.abs((x - cx) / ax)
    v = np.abs((y - cy) / ay)
    f = (u ** n + v ** n) ** (1.0 / n)
    return (f - 1.0) * min(ax, ay)


def _save(rgb, path, alpha=None, palette=True):
    """Сохраняет кадр. Плавные градиенты пишутся в палитре — так заметно меньше."""
    image = Image.fromarray((np.clip(rgb, 0.0, 1.0) * 255).astype(np.uint8), "RGB")
    if alpha is not None:
        image.putalpha(
            Image.fromarray((np.clip(alpha, 0.0, 1.0) * 255).astype(np.uint8), "L"))
        image.save(path, optimize=True)
        return image
    if palette:
        image = image.quantize(colors=256, method=Image.Quantize.MEDIANCUT,
                               dither=Image.Dither.FLOYDSTEINBERG)
    image.save(path, optimize=True)
    return image


def _grid(height, width):
    yy, xx = np.mgrid[0:height, 0:width].astype(np.float64)
    return yy, xx


# ── фоны ────────────────────────────────────────────────────────────
def _night_sky(height, width, moon=(0.78, 0.24), stars=True, seed=20261215):
    """Базовый кадр: вертикальный градиент, ореол луны, свечение у горизонта."""
    yy, xx = _grid(height, width)
    t = (yy / max(1.0, height - 1.0))[:, :, None]
    sky = SKY_TOP * (1.0 - t) + SKY_MID * t
    low = np.clip((yy / max(1.0, height - 1.0) - 0.55) / 0.45, 0.0, 1.0)
    sky = sky * (1.0 - low[:, :, None] * 0.75) + SKY_LOW * (low[:, :, None] * 0.75)

    # Свечение над горизонтом: широкий мягкий эллипс.
    hx = (xx / width - 0.42) * 2.1
    hy = (yy / height - 0.94) * 3.4
    horizon = np.exp(-(hx * hx + hy * hy)) * 0.55
    sky += horizon[:, :, None] * GLOW_HORIZON

    # Луна: ядро + два ореола разного радиуса.
    mx = (xx / width - moon[0]) * (width / max(1.0, height))
    my = yy / height - moon[1]
    r = np.sqrt(mx * mx + my * my)
    halo = np.exp(-(r ** 2) / (2.0 * 0.115 ** 2)) * 0.30
    halo += np.exp(-(r ** 2) / (2.0 * 0.038 ** 2)) * 0.34
    core = np.clip(1.0 - r / 0.026, 0.0, 1.0) ** 1.5
    sky += (halo[:, :, None] * np.array([0.62, 0.70, 0.95])
            + core[:, :, None] * np.array([0.95, 0.97, 1.00]) * 0.85)

    if stars:
        rng = np.random.default_rng(seed)
        count = int(width * height / 900.0)
        sx = rng.integers(0, width, count)
        sy = rng.integers(0, int(height * 0.82), count)
        brightness = rng.uniform(0.18, 0.85, count)
        # Звезда «тает» к горизонту: там её перебивает свечение.
        fade = np.clip(1.0 - sy / (height * 0.95), 0.15, 1.0)
        np.add.at(sky, (sy, sx), (brightness * fade)[:, None]
                  * np.array([0.80, 0.86, 1.00]))
    return np.clip(sky, 0.0, 1.0)


def _aurora_bands(sky, height, width):
    """Две медленные ленты северного сияния поверх неба."""
    yy, xx = _grid(height, width)
    u = xx / width
    out = sky.copy()
    for phase, colour, amp, y0, spread in (
            (0.0, AURORA_A, 0.34, 0.34, 0.085),
            (2.1, AURORA_B, 0.26, 0.46, 0.110)):
        wave = (np.sin(u * 5.4 + phase) * 0.5
                + np.sin(u * 11.0 + phase * 1.7) * 0.25) * 0.075
        band = np.exp(-(((yy / height - y0 - wave) / spread) ** 2))
        curtain = band * (0.55 + 0.45 * np.sin(u * 26.0 + phase))
        out += curtain[:, :, None] * colour * amp
    return np.clip(out, 0.0, 1.0)


def _glass_plates(sky, height, width, seed=77):
    """Несколько полупрозрачных стеклянных пластин — декор в тон интерфейсу."""
    rng = np.random.default_rng(seed)
    out = sky.copy()
    for _ in range(5):
        cx = rng.uniform(0.10, 0.90)
        cy = rng.uniform(0.18, 0.80)
        ax = rng.uniform(0.05, 0.13)
        ay = rng.uniform(0.05, 0.14)
        yy, xx = _grid(height, width)
        d = _squircle_sdf(xx / width, yy / height, cx, cy, ax, ay, 5.0)
        inside = np.clip(-d / 0.012 + 0.5, 0.0, 1.0)
        rim = np.exp(-(d ** 2) / (2.0 * 0.006 ** 2))
        plate = (inside * 0.16 + rim * 0.30)
        out += plate[:, :, None] * np.array([0.62, 0.74, 0.98])
    return np.clip(out, 0.0, 1.0)


def _drift(sky, height, width, dx=0.03, dz=0.02, seed=5):
    """Лёгкий сдвиг кадра: галерея не должна показывать четыре одинаковых фона."""
    rng = np.random.default_rng(seed)
    yy, xx = _grid(height, width)
    nx = (xx / width + dx + rng.uniform(0, 1)) % 1.0
    ny = np.clip(yy / height + dz, 0.0, 1.0)
    xi = np.clip(np.rint(nx * (width - 1)).astype(np.int64), 0, width - 1)
    yi = np.clip(np.rint(ny * (height - 1)).astype(np.int64), 0, height - 1)
    return sky[yi, xi, :]


def _make_backdrops():
    base = _night_sky(BASE_H, BASE_W)
    frames = {
        "menu_backdrop": base,
        "menu_backdrop_aurora": _aurora_bands(base, BASE_H, BASE_W),
        "menu_backdrop_glass": _glass_plates(base, BASE_H, BASE_W),
        "menu_backdrop_night": _drift(_aurora_bands(base, BASE_H, BASE_W),
                                      BASE_H, BASE_W, -0.05, 0.015, seed=11),
    }
    written = []
    for name in BACKDROP_NAMES:
        full = frames[name]
        for level, (w, h) in enumerate(BACKDROP_SIZES):
            image = Image.fromarray(
                (np.clip(full, 0.0, 1.0) * 255).astype(np.uint8), "RGB")
            if level:
                # Ступени размытия — это уменьшенные копии: на сильном
                # размытии потеря разрешения неразличима, а вес падает в разы.
                image = image.resize((w, h), Image.LANCZOS)
            path = os.path.join(GUI, f"{name}_b{level}.png")
            _save(np.asarray(image, dtype=np.float64) / 255.0, path)
            written.append(path)
    return written


# ── стекло ──────────────────────────────────────────────────────────
def _glass_sprite(width, height, *, super_n, bevel, strength,
                  base_alpha, edge_alpha, tint):
    """Одна стеклянная пластина, вырезанная ровно по своей форме."""
    yy, xx = _grid(height, width)
    cx, cy = width / 2.0, height / 2.0
    # Форма занимает почти весь спрайт, но оставляет место под внешнюю тень.
    ax = width * 0.5 - strength * 0.75
    ay = height * 0.5 - strength * 0.75
    d = _squircle_sdf(xx, yy, cx, cy, ax, ay, super_n)
    gy, gx = np.gradient(d)
    nx, ny = _normalize(gx, gy)
    edge = np.clip((bevel + d) / bevel, 0.0, 1.0) ** 1.7

    # Фон пластины — ночной градиент: стеклу нужно что-то преломлять,
    # иначе кромка не читается.
    t = (yy / max(1.0, height - 1.0))[:, :, None]
    bg = SKY_MID * (1.0 - t) + SKY_LOW * t
    bg += np.exp(-(((xx / width - 0.35) ** 2 + (yy / height - 0.9) ** 2) * 6.0)
                 )[:, :, None] * GLOW_HORIZON * 0.4

    # Преломление: сдвиг вдоль нормали, разный для каналов RGB.
    chroma = 0.055
    out = np.zeros_like(bg)
    for channel, spread in enumerate((1.0 + chroma, 1.0, 1.0 - chroma)):
        xs = np.clip(np.rint(xx + nx * edge * strength * spread).astype(np.int64),
                     0, width - 1)
        ys = np.clip(np.rint(yy + ny * edge * strength * spread).astype(np.int64),
                     0, height - 1)
        out[:, :, channel] = bg[ys, xs, channel]

    inside = np.clip(-d / 1.6 + 0.5, 0.0, 1.0)
    alpha = (base_alpha + (edge_alpha - base_alpha) * edge) * inside

    # Холодный оттенок стекла.
    out[:, :, 0] = out[:, :, 0] * 1.02 + 0.020
    out[:, :, 1] = out[:, :, 1] * 1.02 + 0.026
    out[:, :, 2] = out[:, :, 2] * 1.05 + 0.062

    # Светлый ободок по кромке, обращённой к источнику света сверху слева.
    facing = np.clip(nx * -0.62 + ny * -0.78, 0.0, 1.0) ** 1.6
    rim = np.exp(-(d ** 2) / (2.0 * 3.2 ** 2)) * facing
    out += rim[:, :, None] * np.array([0.85, 0.90, 1.00]) * 1.60
    out += (np.exp(d / 13.0) * (d < 0) * facing * 0.22)[:, :, None]

    # Тень снизу и диагональный блик.
    out -= (np.clip(ny, 0.0, 1.0) ** 1.3 * edge * 0.16)[:, :, None]
    sheen = np.clip((xx / width + yy / height) * 1.35 - 0.55, 0.0, 1.0)
    out += ((sheen ** 3) * (1.0 - edge) * 0.16)[:, :, None]
    out = np.clip(out, 0.0, 1.0) * tint

    shadow = np.exp(-np.clip(d, 0.0, None) / 26.0) * (1.0 - inside) * 0.42
    alpha_out = np.clip(alpha + shadow, 0.0, 1.0)
    rgb = np.where(alpha[:, :, None] > 1e-4, out, np.array([0.02, 0.03, 0.07]))
    return rgb, alpha_out


def _make_glass():
    written = []
    logo = _glass_sprite(LOGO_SIZE, LOGO_SIZE, super_n=2.0, bevel=30.0,
                         strength=34.0, base_alpha=0.26, edge_alpha=0.58,
                         tint=np.array([1.0, 1.0, 1.0]))
    panel = _glass_sprite(PANEL_W, PANEL_H, super_n=7.0, bevel=36.0,
                          strength=40.0, base_alpha=0.28, edge_alpha=0.62,
                          tint=np.array([1.0, 1.0, 1.0]))
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
