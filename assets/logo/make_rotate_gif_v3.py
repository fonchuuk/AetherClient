"""Вращение неонового логотипа A с настоящим объёмом: буква выдавливается в толщину,
срезы собираются от дальнего к ближнему, поэтому при повороте видны боковые грани,
блики на гранях и неоновый контур силуэта.

Запуск: python3 logo/make_rotate_gif_v3.py [кадры] [размер] [имя.gif]
"""
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

SRC = "/home/user/uploads/file_00000000586881f49fde9cfa3535dc8e.png"
OUT_DIR = "/home/user/logo"

# --- геометрия сцены -------------------------------------------------------
THICKNESS = 0.19          # толщина буквы относительно её половины ширины
TILT = 0.115               # наклон камеры сверху (рад) — видно верхнюю грань
CAM_DIST = 2.4            # расстояние до камеры: меньше — сильнее перспектива
SLICES = 96               # число срезов выдавливания
FILL = 0.66               # доля кадра под букву

BG = np.array([0.040, 0.028, 0.072], dtype=np.float32)
NEON = np.array([0.72, 0.30, 1.00], dtype=np.float32)
BODY = np.array([0.30, 0.10, 0.52], dtype=np.float32)


def load_letter():
    im = Image.open(SRC).convert("RGB")
    a = np.asarray(im).astype(np.float32) / 255.0
    lum = a.max(axis=2)
    alpha = np.clip((lum - 0.085) / 0.75, 0.0, 1.0) ** 0.85
    ys, xs = np.where(alpha > 0.45)
    box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    rgb = a[box[1]:box[3], box[0]:box[2]]
    alp = alpha[box[1]:box[3], box[0]:box[2]]
    h, w = alp.shape
    side = max(h, w)
    pl, pt = (side - w) // 2, (side - h) // 2
    rgb = np.pad(rgb, ((pt, side - h - pt), (pl, side - w - pl), (0, 0)))
    alp = np.pad(alp, ((pt, side - h - pt), (pl, side - w - pl)))
    return rgb, alp


def _homography(theta, z, size, f_px, half):
    ct, st = np.cos(theta), np.sin(theta)
    cp, sp = np.cos(TILT), np.sin(TILT)
    # (X, Y) -> экран; Z задан смещением среза
    # wx = X*ct + z*st                     (после поворота вокруг Y)
    # wy = Y*cp + X*st*sp - z*ct*sp        (наклон вокруг X)
    # den = CAM_DIST - Y*sp + X*st*cp - z*ct*cp
    a11, a12, a13 = f_px * ct, 0.0, f_px * z * st
    a21, a22, a23 = f_px * st * sp, f_px * cp, -f_px * z * ct * sp
    a31, a32, a33 = st * cp, -sp, CAM_DIST - z * ct * cp
    H = np.array([[a11, a12, a13], [a21, a22, a23], [a31, a32, a33]], dtype=np.float64)
    # перевод в пиксели кадра: sx = size/2 + X_px, sy = size/2 - Y_px
    T = np.array([[1.0, 0.0, size / 2.0], [0.0, -1.0, size / 2.0], [0.0, 0.0, 1.0]])
    return T @ H


def _sample(rgb, alp, sx, sy, half, size):
    """Билинейная выборка плоскости по координатам X,Y (в единицах half)."""
    px = sx * half + half
    py = -sy * half + half
    h, w = alp.shape
    ok = (px >= 0) & (px <= w - 1.001) & (py >= 0) & (py <= h - 1.001)
    x0 = np.clip(np.floor(px), 0, w - 2)
    y0 = np.clip(np.floor(py), 0, h - 2)
    fx = np.clip(px - x0, 0, 1)[..., None]
    fy = np.clip(py - y0, 0, 1)[..., None]
    x0 = x0.astype(np.int32)
    y0 = y0.astype(np.int32)
    a00 = alp[y0, x0][..., None]
    a10 = alp[y0, x0 + 1][..., None]
    a01 = alp[y0 + 1, x0][..., None]
    a11 = alp[y0 + 1, x0 + 1][..., None]
    c00 = rgb[y0, x0]
    c10 = rgb[y0, x0 + 1]
    c01 = rgb[y0 + 1, x0]
    c11 = rgb[y0 + 1, x0 + 1]
    a = ((a00 * (1 - fx) + a10 * fx) * (1 - fy) + (a01 * (1 - fx) + a11 * fx) * fy)[..., 0]
    c = (c00 * (1 - fx) + c10 * fx) * (1 - fy) + (c01 * (1 - fx) + c11 * fx) * fy
    a = np.where(ok, a, 0.0).astype(np.float32)
    c = np.where(ok[..., None], c, 0.0).astype(np.float32)
    return c, a


def render_letter(rgb, alp, t, size):
    theta = t * 2 * np.pi
    f_px = FILL * size * CAM_DIST * 0.5 / 1.0
    half = alp.shape[0] / 2.0
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32) + 0.5

    img = np.empty((size, size, 3), dtype=np.float32)
    img[:] = BG
    front_a = np.zeros((size, size), dtype=np.float32)

    ct = np.cos(theta)
    light = 0.55 + 0.45 * max(0.0, np.cos(theta - 0.9))

    for k in range(SLICES):
        zt = k / (SLICES - 1)                      # 0 = дальняя грань, 1 = передняя
        z = -THICKNESS * (1.0 - zt)
        H = _homography(theta, z, size, f_px, half)
        try:
            Hi = np.linalg.inv(H)
        except np.linalg.LinAlgError:
            continue
        u = Hi[0, 0] * xs + Hi[0, 1] * ys + Hi[0, 2]
        v = Hi[1, 0] * xs + Hi[1, 1] * ys + Hi[1, 2]
        w = Hi[2, 0] * xs + Hi[2, 1] * ys + Hi[2, 2]
        w = np.where(np.abs(w) < 1e-9, 1e-9, w)
        c, a = _sample(rgb, alp, u / w, v / w, half, size)
        if a.max() <= 0.0:
            continue

        if zt >= 0.999:                            # лицевая грань: своя текстура
            shade = 0.92 + 0.20 * max(0.0, ct)
            col = c * shade
        elif k == 0:                               # задняя грань видна при развороте
            col = c * 0.34
        else:                                      # боковая стенка: объём
            ao = 0.42 + 0.58 * abs(2 * zt - 1) ** 1.15
            col = BODY * (ao * light * 2.0)
        col = np.clip(col, 0, 1.2)
        img = img * (1 - a[..., None]) + col * a[..., None]
        front_a = a

    # блик по лицевой грани, бегущий при повороте
    band = np.linspace(0, 1, size)[None, :]
    pos = 0.18 + 0.64 * (np.cos(theta) * 0.5 + 0.5)
    glare = np.exp(-((band - pos) ** 2) / 0.010) * 0.55
    img += glare[..., None] * np.array([0.70, 0.55, 1.0], dtype=np.float32) * front_a[..., None]

    # неоновый контур силуэта + свечение
    solid = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).filter(
        ImageFilter.GaussianBlur(0.8))
    img = np.asarray(solid).astype(np.float32) / 255.0
    alpha_solid = Image.fromarray(((front_a > 0.02) * 255).astype(np.uint8))
    edge = np.asarray(alpha_solid.filter(ImageFilter.FIND_EDGES)).astype(np.float32) / 255.0
    edge = np.asarray(Image.fromarray((edge * 255).astype(np.uint8)).filter(
        ImageFilter.GaussianBlur(1.2))).astype(np.float32) / 255.0
    img += edge[..., None] * NEON * 1.15

    glow = solid.filter(ImageFilter.GaussianBlur(size * 0.030))
    glow2 = solid.filter(ImageFilter.GaussianBlur(size * 0.095))
    g = (np.asarray(glow).astype(np.float32) / 255.0) * 0.5 \
        + (np.asarray(glow2).astype(np.float32) / 255.0) * 0.9
    img = img + g * NEON * 0.45 * (g.max(axis=2, keepdims=True) > 0.02)

    # мягкая тень под буквой, чтобы объём «стоял» на месте
    yy2, xx2 = np.mgrid[0:size, 0:size].astype(np.float32)
    sh = np.exp(-(((xx2 - size * 0.5) / (size * 0.30)) ** 2
                  + ((yy2 - size * 0.86) / (size * 0.055)) ** 2))
    img *= (1.0 - 0.55 * sh)[..., None]

    # виньетка
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32) / size
    vig = 1.0 - 0.28 * (((xx - 0.5) ** 2 + (yy - 0.5) ** 2) / 0.5)
    img *= vig[..., None]
    rng = np.random.default_rng(7)
    img = img + rng.normal(0.0, 0.004, img.shape).astype(np.float32)
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 60
    size = int(sys.argv[2]) if len(sys.argv) > 2 else 512
    name = sys.argv[3] if len(sys.argv) > 3 else "aethervisuals_rotate_3d.gif"
    rgb, alp = load_letter()
    frames = [render_letter(rgb, alp, i / n, size) for i in range(n)]
    pal = frames[0].quantize(colors=96, method=Image.MEDIANCUT)
    quants = [f.quantize(palette=pal, dither=Image.Dither.NONE) for f in frames]
    out = os.path.join(OUT_DIR, name)
    quants[0].save(out, save_all=True, append_images=quants[1:], duration=45,
                   loop=0, optimize=True, disposal=1)
    print(out, os.path.getsize(out) // 1024, "KB", n, "frames", size, "px")


if __name__ == "__main__":
    main()
