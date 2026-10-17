#!/usr/bin/env python3
"""
Рисует настоящее жидкое стекло: полупрозрачную пластину, у которой видны
бока — то есть преломление фона по кромке считается по-настоящему,
а не имитируется размытием.

Как это работает:
  1. Поле расстояний до формы (sdf): ноль — на кромке, минус — внутри.
  2. Градиент этого поля — нормаль к поверхности. Там, где поверхность
     загибается (у кромки), она и работает линзой.
  3. Фон выбирается со сдвигом вдоль нормали: сдвиг максимулен у кромки
     и равен нулю в середине, потому что середина пластины плоская.
     Сдвиг разный для красного, зелёного и синего — отсюда цветная кайма,
     как у настоящего стекла.
  4. Сверху — тонкий светлый ободок, внутренняя тень снизу, мягкий блик
     по диагонали и внешняя тень для глубины.

Запуск:  python3 tools/make_glass.py
"""

import numpy as np
from PIL import Image

W, H = 1376, 768

# ── геометрия: две стеклянные формы ─────────────────────────────────
# Слева — овал под логотип (SUPER=2 ровно эллипс, «более овальная форма»),
# по центру — крупный скруглённый прямоугольник под все надписи.
# Обе заметно больше прежних: так и просили.
SHAPES = [
    dict(name="logo",  cx=0.190 * W, cy=0.400 * H, ax=0.155 * W, ay=0.270 * H,
         super_n=2.0, bevel=46.0, strength=52.0, base=0.26, edge=0.58),
    dict(name="panel", cx=0.620 * W, cy=0.500 * H, ax=0.260 * W, ay=0.330 * H,
         super_n=7.0, bevel=54.0, strength=58.0, base=0.28, edge=0.62),
]

BEVEL = 40.0        # толщина кромки, где стекло загибается
STRENGTH = 46.0     # сила преломления в пикселях у самой кромки
CHROMA = 0.055      # разброс каналов: цветная кайма
BASE_ALPHA = 0.30   # прозрачность середины
EDGE_ALPHA = 0.62   # прозрачность у кромки: материала больше
RIM_SIGMA = 3.2     # толщина светлого ободка
RIM_GAIN = 1.60     # сила ободка: он должен читаться на тёмном фоне
GLOW = 13.0         # мягкое свечение сразу за ободком, внутрь пластины
SHADOW = 26.0       # размах внешней тени


def squircle_sdf(x, y, cx, cy, ax, ay, n=4.0):
    """Поле расстояний: <0 внутри, 0 на кромке, >0 снаружи."""
    u = np.abs((x - cx) / ax)
    v = np.abs((y - cy) / ay)
    f = (u ** n + v ** n) ** (1.0 / n)
    return (f - 1.0) * min(ax, ay)


def normalize(vx, vy):
    """Единичная нормаль; в центре, где градиент вырождается, — ноль."""
    length = np.sqrt(vx * vx + vy * vy)
    safe = np.where(length > 1e-6, length, 1.0)
    nx = np.where(length > 1e-6, vx / safe, 0.0)
    ny = np.where(length > 1e-6, vy / safe, 0.0)
    return nx, ny


def sample(bg, xs, ys):
    """Взять цвет фона в точках (xs, ys) с защитой от выхода за кадр."""
    h, w, _ = bg.shape
    xi = np.clip(np.rint(xs).astype(np.int64), 0, w - 1)
    yi = np.clip(np.rint(ys).astype(np.int64), 0, h - 1)
    return bg[yi, xi, :]


def glass_shape(bg, shape):
    """Одна стеклянная форма поверх фона. Возвращает её цвет и прозрачность."""
    cx, cy = shape["cx"], shape["cy"]
    ax, ay = shape["ax"], shape["ay"]
    bevel, strength = shape["bevel"], shape["strength"]
    base_alpha, edge_alpha = shape["base"], shape["edge"]

    yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)
    d = squircle_sdf(xx, yy, cx, cy, ax, ay, shape["super_n"])

    gy, gx = np.gradient(d)
    nx, ny = normalize(gx, gy)
    edge = np.clip((bevel + d) / bevel, 0.0, 1.0) ** 1.7

    off_x = nx * edge * strength
    off_y = ny * edge * strength

    out = np.zeros_like(bg)
    for channel, spread in enumerate((1.0 + CHROMA, 1.0, 1.0 - CHROMA)):
        out[:, :, channel] = sample(bg, xx + off_x * spread,
                                    yy + off_y * spread)[:, :, channel]

    inside = np.clip(-d / 1.6 + 0.5, 0.0, 1.0)
    alpha = (base_alpha + (edge_alpha - base_alpha) * edge) * inside

    out[:, :, 0] = out[:, :, 0] * 1.02 + 0.020
    out[:, :, 1] = out[:, :, 1] * 1.02 + 0.026
    out[:, :, 2] = out[:, :, 2] * 1.05 + 0.062

    light_x, light_y = -0.62, -0.78
    facing = np.clip(nx * light_x + ny * light_y, 0.0, 1.0) ** 1.6
    rim = np.exp(-(d ** 2) / (2.0 * RIM_SIGMA ** 2)) * facing
    out += rim[:, :, None] * np.array([0.85, 0.90, 1.00]) * RIM_GAIN
    glow = np.exp(d / GLOW) * (d < 0) * facing * 0.22
    out += glow[:, :, None]

    lower = np.clip(ny, 0.0, 1.0) ** 1.3 * edge
    out -= (lower * 0.16)[:, :, None]

    sheen = np.clip((xx / W + yy / H) * 1.35 - 0.55, 0.0, 1.0)
    sheen = (sheen ** 3) * (1.0 - edge) * 0.16
    out += sheen[:, :, None]
    out = np.clip(out, 0.0, 1.0)

    shadow = np.exp(-np.clip(d, 0.0, None) / SHADOW) * (1.0 - inside) * 0.42
    alpha_out = np.clip(alpha + shadow, 0.0, 1.0)
    rgb = np.where(alpha[:, :, None] > 1e-4, out,
                   np.array([0.02, 0.03, 0.07]))
    return rgb, alpha_out


def glass_over(bg):
    """Все формы поверх фона. Возвращает итог и слои по отдельности."""
    composed = bg.copy()
    layers = []
    for shape in SHAPES:
        rgb, alpha = glass_shape(bg, shape)
        composed = composed * (1.0 - alpha[:, :, None]) + rgb * alpha[:, :, None]
        layers.append((shape["name"], rgb, alpha))
    return np.clip(composed, 0.0, 1.0), layers


def save(array, path, alpha=None):
    image = Image.fromarray((np.clip(array, 0, 1) * 255).astype(np.uint8), "RGB")
    if alpha is not None:
        image.putalpha(Image.fromarray(
            (np.clip(alpha, 0, 1) * 255).astype(np.uint8), "L"))
    image.save(path)
    return image


def main():
    base = Image.open("/tmp/menu_base.png").convert("RGB").resize((W, H),
                                                                  Image.LANCZOS)
    bg = np.asarray(base, dtype=np.float64) / 255.0
    composed, layers = glass_over(bg)

    gui = "/home/user/aurora/client/src/main/resources/assets/aetherglass/textures/gui"
    save(bg, gui + "/menu_backdrop.png")
    for name, rgb, alpha in layers:
        save(rgb, gui + "/menu_glass_" + name + ".png", alpha=alpha)

    # Проверка «на просвет», как её описал пользователь: серый фон, а поверх
    # стекло — сразу видно и бока, и прозрачность.
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)
    checker = (((xx // 26).astype(int) + (yy // 26).astype(int)) % 2) * 0.06
    grey = np.dstack([np.full((H, W), 0.30) + checker,
                      np.full((H, W), 0.31) + checker,
                      np.full((H, W), 0.34) + checker])
    preview = grey.copy()
    for _, rgb, alpha in layers:
        preview = preview * (1.0 - alpha[:, :, None]) + rgb * alpha[:, :, None]
    save(preview, "/home/user/aurora/docs/menu-glass-preview.png")

    print("=== стекло: проверка числами ===")
    for shape in SHAPES:
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)
        d = squircle_sdf(xx, yy, shape["cx"], shape["cy"], shape["ax"],
                         shape["ay"], shape["super_n"])
        _, alpha = glass_shape(bg, shape)
        deep, rim = d < -90, np.abs(d) < 3
        print(f"  {shape['name']:6s} размер {int(2*shape['ax'])}x{int(2*shape['ay'])} | "
              f"прозрачность середина {alpha[deep].mean():.2f} кромка {alpha[rim].mean():.2f} | "
              f"внутри {int((d<0).sum())} пикселей")


if __name__ == "__main__":
    main()
