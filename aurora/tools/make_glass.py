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

# ── геометрия пластины ──────────────────────────────────────────────
CX, CY = 0.285 * W, 0.500 * H          # центр: слева, там будет заголовок
AX, AY = 0.205 * W, 0.330 * H          # полуоси
SUPER = 4.0                            # 2 = эллипс, 4 = мягкий «сквиркл»,
                                       # больше 6 — уже прямоугольник.
                                       # Берём 4: формы органичные, без углов.

BEVEL = 40.0        # толщина кромки, где стекло загибается
STRENGTH = 46.0     # сила преломления в пикселях у самой кромки
CHROMA = 0.055      # разброс каналов: цветная кайма
BASE_ALPHA = 0.30   # прозрачность середины
EDGE_ALPHA = 0.62   # прозрачность у кромки: материала больше
RIM_SIGMA = 3.2     # толщина светлого ободка
RIM_GAIN = 1.60     # сила ободка: он должен читаться на тёмном фоне
GLOW = 13.0         # мягкое свечение сразу за ободком, внутрь пластины
SHADOW = 26.0       # размах внешней тени


def squircle_sdf(x, y, cx, cy, ax, ay, n=SUPER):
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


def glass_over(bg, offsets_scale=1.0):
    """Положить стеклянную пластину на фон bg (float 0..1). Возвращает
    итоговую картинку и отдельный слой стекла с прозрачностью."""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)
    d = squircle_sdf(xx, yy, CX, CY, AX, AY)

    # Нормаль к поверхности: градиент поля расстояний.
    gy, gx = np.gradient(d)
    nx, ny = normalize(gx, gy)

    # Вес кромки: 1 на самом краю, 0 в глубине плоской середины.
    edge = np.clip((BEVEL + d) / BEVEL, 0.0, 1.0) ** 1.7

    off_x = nx * edge * STRENGTH * offsets_scale
    off_y = ny * edge * STRENGTH * offsets_scale

    # Преломление: каждый канал со своим сдвигом — отсюда цветная кайма.
    out = np.zeros_like(bg)
    for channel, spread in enumerate((1.0 + CHROMA, 1.0, 1.0 - CHROMA)):
        out[:, :, channel] = sample(bg, xx + off_x * spread,
                                    yy + off_y * spread)[:, :, channel]

    # Прозрачность: у кромки стекло плотнее, в середине — чище.
    inside = np.clip(-d / 1.6 + 0.5, 0.0, 1.0)          # сглаженная маска
    alpha = (BASE_ALPHA + (EDGE_ALPHA - BASE_ALPHA) * edge) * inside

    # Мягкий подсвет и лёгкая подмесь цвета — стекло не бывает нейтральным.
    out[:, :, 0] = out[:, :, 0] * 1.02 + 0.020
    out[:, :, 1] = out[:, :, 1] * 1.02 + 0.026
    out[:, :, 2] = out[:, :, 2] * 1.05 + 0.062

    # Тонкий светлый ободок там, где кромка смотрит на свет (сверху-слева).
    light_x, light_y = -0.62, -0.78
    facing = np.clip(nx * light_x + ny * light_y, 0.0, 1.0) ** 1.6
    rim = np.exp(-(d ** 2) / (2.0 * RIM_SIGMA ** 2)) * facing
    out += rim[:, :, None] * np.array([0.85, 0.90, 1.00]) * RIM_GAIN
    # Сразу за ободком, внутрь, — мягкое свечение: так у стекла читается
    # толщина, а не просто нарисованная линия.
    glow = np.exp(d / GLOW) * (d < 0) * facing * 0.22
    out += glow[:, :, None]

    # Внутренняя тень снизу: толща стекла там толще.
    lower = np.clip(ny, 0.0, 1.0) ** 1.3 * edge
    out -= (lower * 0.16)[:, :, None]

    # Диагональный блик — одна мягкая полоса, не «пластиковая» плёнка.
    sheen = np.clip((xx / W + yy / H) * 1.35 - 0.55, 0.0, 1.0)
    sheen = (sheen ** 3) * (1.0 - edge) * 0.16
    out += sheen[:, :, None]

    out = np.clip(out, 0.0, 1.0)

    # Внешняя тень: стекло висит над фоном.
    shadow = np.exp(-np.clip(d, 0.0, None) / SHADOW) * (1.0 - inside) * 0.42
    alpha_out = np.clip(alpha + shadow, 0.0, 1.0)
    shadow_rgb = np.array([0.02, 0.03, 0.07])

    glass_rgb = np.where(alpha[:, :, None] > 1e-4,
                         out, shadow_rgb)

    composed = bg * (1.0 - alpha_out[:, :, None]) + glass_rgb * alpha_out[:, :, None]
    return np.clip(composed, 0.0, 1.0), glass_rgb, alpha_out, d, edge


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

    composed, glass_rgb, glass_alpha, d, edge = glass_over(bg)

    # 1) Как просил пользователь: серый фон, а поверх — стекло, у которого
    #    видно бока и прозрачность. Именно так прозрачность и проверяют.
    grey = np.full((H, W, 3), 0.0)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)
    # мягкая серая «студийная» подложка с лёгкой шахматкой: по ней сразу
    # видно, что стекло и правда прозрачное, а не серое.
    checker = (((xx // 26).astype(int) + (yy // 26).astype(int)) % 2) * 0.06
    grey[:, :, 0] = 0.30 + checker
    grey[:, :, 1] = 0.31 + checker
    grey[:, :, 2] = 0.34 + checker
    grey_composed = grey * (1.0 - glass_alpha[:, :, None]) + \
        glass_rgb * glass_alpha[:, :, None]

    gui = "/home/user/aurora/client/src/main/resources/assets/aetherglass/textures/gui"
    save(composed, gui + "/menu_backdrop.png")
    save(glass_rgb, gui + "/menu_glass.png", alpha=glass_alpha)
    save(grey_composed, "/home/user/aurora/docs/menu-glass-preview.png")

    # ── проверка числами ────────────────────────────────────────────
    inside_mask = d < -6
    print("=== проверка стекла ===")
    print(f"пикселей внутри пластины      : {int(inside_mask.sum())}")
    print(f"преломление в середине (макс) : {float(edge[inside_mask].max()):.4f}  "
          f"(должно быть около нуля)")
    rim_band = (np.abs(d) < 4)
    print(f"преломление у кромки (средн)  : {float(edge[rim_band].mean()):.4f}  "
          f"(должно быть заметно больше)")
    print(f"прозрачность: середина        : {float(glass_alpha[inside_mask].mean()):.3f}")
    print(f"прозрачность: кромка          : {float(glass_alpha[rim_band].mean()):.3f}")
    print(f"прозрачность: снаружи         : {float(glass_alpha[d > SHADOW * 2].mean()):.3f}")
    centre = composed[int(CY) - 40:int(CY) + 40, int(CX) - 40:int(CX) + 40]
    edge_px = composed[int(CY), int(CX - AX) + 2:int(CX - AX) + 12]
    print(f"яркость середина / кромка     : "
          f"{centre.mean():.3f} / {edge_px.mean():.3f}  (кромка ярче)")


if __name__ == "__main__":
    main()
