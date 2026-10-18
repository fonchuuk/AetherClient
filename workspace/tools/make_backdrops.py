#!/usr/bin/env python3
"""Фоны главного меню AetherGlass.

Три картинки в одной гамме: глубокий тёмно-синий с мятно-фиолетовыми
пятнами света. Все мягкие и низкоконтрастные: в игре поверх них идёт
своё размытие и виньетка, поэтому мелкие детали всё равно не читаются,
а вот крупные пятна задают настроение.

Палитра взята не с потолка, а замерена с существующего menu_backdrop.png:
средний цвет (24, 31, 60), средняя яркость 38. Держимся рядом.
"""

import numpy as np
from PIL import Image, ImageFilter

W, H = 1376, 768
OUT = "/home/user/aurora/client/src/main/resources/assets/aetherglass/textures/gui"


def field(h, w, scale, seed):
    """Мягкое случайное поле: шум, размытый до крупных пятен."""
    rng = np.random.default_rng(seed)
    small = (h // scale, w // scale)
    n = rng.random(small).astype(np.float32)
    im = Image.fromarray((n * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    im = im.filter(ImageFilter.GaussianBlur(scale * 0.9))
    return np.asarray(im).astype(np.float32) / 255.0


def glow(h, w, cx, cy, radius, strength):
    """Круглое пятно света с мягким краем."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt((xx - cx * w) ** 2 + (yy - cy * h) ** 2) / (radius * max(w, h))
    return np.exp(-(d ** 2) * 3.0) * strength


def grade(r, g, b, brightness=37.0, gamma=1.05):
    """Сводит три канала в картинку и подгоняет яркость под общую гамму.

    Синий канал приподнимается: замер существующего menu_backdrop.png дал
    (24, 31, 60) — синего почти вдвое больше зелёного. Без этого подъёма
    новые фоны выглядели бы серыми рядом со старым.
    """
    # Множители подобраны по замеру: синего ~1.9× к красному, ~1.9× к зелёному.
    img = np.stack([r * 1.00, g * 1.02, b * 1.55], axis=2)
    img = np.clip(img, 0.0, 1.0)
    cur = float(img.mean() * 255.0)
    if cur > 0.5:
        img = img * (brightness / cur)
    img = np.clip(img, 0.0, 1.0) ** gamma
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB")


def backdrop_aurora():
    """Северное сияние: вертикальные мягкие полосы сверху."""
    yy = np.linspace(0, 1, H, dtype=np.float32)[:, None]
    xx = np.linspace(0, 1, W, dtype=np.float32)[None, :]
    base = np.full((H, W), 0.055, np.float32)
    f = field(H, W, 48, 11)
    # Полосы идут волной и гаснут книзу — как настоящее сияние.
    wave = np.sin(xx * 6.0 + f * 3.4) * 0.5 + 0.5
    fall = np.exp(-(yy ** 1.4) * 2.2)
    band = (wave ** 2.2) * fall * 0.85
    r = base + band * 0.34 + glow(H, W, 0.24, 0.30, 0.34, 0.17)
    g = base + band * 0.56 + glow(H, W, 0.72, 0.22, 0.30, 0.15)
    b = base + band * 0.82 + glow(H, W, 0.50, 0.66, 0.42, 0.26)
    return grade(r, g, b)


def backdrop_glass():
    """Стеклянные грани: крупные мягкие скосы с подсветкой по кромке."""
    f1 = field(H, W, 60, 23)
    f2 = field(H, W, 96, 41)
    yy = np.linspace(0, 1, H, dtype=np.float32)[:, None]
    xx = np.linspace(0, 1, W, dtype=np.float32)[None, :]
    # Диагональные грани: яркая линия там, где скос ловит свет.
    diag = (xx * 0.65 + yy * 0.35)
    edge = np.exp(-((np.mod(diag * 3.0 + f1 * 0.8, 1.0) - 0.5) ** 2) * 26.0)
    base = np.full((H, W), 0.05, np.float32) + f2 * 0.045
    r = base + edge * 0.28 + glow(H, W, 0.18, 0.70, 0.36, 0.18)
    g = base + edge * 0.52 + glow(H, W, 0.80, 0.34, 0.32, 0.14)
    b = base + edge * 0.74 + glow(H, W, 0.52, 0.18, 0.40, 0.24)
    return grade(r, g, b)


def backdrop_night():
    """Глубокая ночь: почти чёрное поле и редкие боке."""
    f = field(H, W, 128, 77)
    base = np.full((H, W), 0.035, np.float32) + f * 0.05
    r, g, b = base.copy(), base.copy(), base.copy()
    rng = np.random.default_rng(5)
    for i in range(9):
        cx, cy = rng.random(), rng.random() * 0.8
        rad = 0.05 + rng.random() * 0.16
        st = 0.05 + rng.random() * 0.10
        hue = rng.random()
        r += glow(H, W, cx, cy, rad, st * (0.60 if hue < 0.5 else 0.95))
        g += glow(H, W, cx, cy, rad, st * (0.90 if hue >= 0.5 else 0.70))
        b += glow(H, W, cx, cy, rad, st * 1.20)
    return grade(r, g, b, brightness=36.0)


def main():
    for name, fn in (
        ("menu_backdrop_aurora", backdrop_aurora),
        ("menu_backdrop_glass", backdrop_glass),
        ("menu_backdrop_night", backdrop_night),
    ):
        im = fn()
        im = im.filter(ImageFilter.GaussianBlur(1.1))  # финальное смягчение
        path = f"{OUT}/{name}.png"
        im.save(path, optimize=True)
        a = np.asarray(im).astype(float)
        import os
        print(f"{name}.png  {im.size[0]}x{im.size[1]}  "
              f"{os.path.getsize(path)/1024:.0f} КБ  "
              f"средний RGB {a.reshape(-1,3).mean(0).round(1)}  яркость {a.mean():.0f}")


if __name__ == "__main__":
    main()
