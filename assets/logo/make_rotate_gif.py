"""Вращение неонового логотипа A вокруг вертикальной оси (Y) с перспективой, блик-свипом и свечением."""
import numpy as np
from PIL import Image, ImageFilter
import subprocess, sys

SRC = "/home/user/uploads/file_00000000586881f49fde9cfa3535dc8e.png"
OUT_DIR = "/home/user/logo"

def load_letter():
    im = Image.open(SRC).convert("RGB")
    a = np.asarray(im).astype(np.float32) / 255.0
    lum = a.max(axis=2)
    # вырезаем букву по яркости: фон почти чёрный (~0.04)
    alpha = np.clip((lum - 0.085) / 0.75, 0.0, 1.0) ** 0.85
    ys, xs = np.where(alpha > 0.45)
    box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    rgb = a[box[1]:box[3], box[0]:box[2]]
    alp = alpha[box[1]:box[3], box[0]:box[2]]
    # нормализуем к квадрату, чтобы не тянуть пропорции
    h, w = alp.shape
    side = max(h, w)
    pad_l = (side - w) // 2
    pad_t = (side - h) // 2
    rgb = np.pad(rgb, ((pad_t, side - h - pad_t), (pad_l, side - w - pad_l), (0, 0)))
    alp = np.pad(alp, ((pad_t, side - h - pad_t), (pad_l, side - w - pad_l)))
    return rgb, alp

def rotate_plane(rgb, alp, theta, depth=2.2):
    """theta в радианах: поворот плоскости вокруг вертикальной оси с перспективой."""
    h, w = alp.shape
    cx = (w - 1) / 2.0
    half = w / 2.0
    cos_t, sin_t = np.cos(theta), np.sin(theta)
    # прямые проекции опорных столбцов
    xs = np.arange(w, dtype=np.float32)
    nx = (xs - cx) / half                       # -1..1
    xo = nx * cos_t / (1.0 + nx * sin_t / depth)  # перспективная проекция
    persp = 1.0 / (1.0 + nx * sin_t / depth)      # ближе → больше
    order = np.argsort(xo)
    xo_sorted = xo[order]
    # обратное сэмплирование только по видимой части
    out_w = w
    x_out = ((np.arange(out_w, dtype=np.float32) - (out_w - 1) / 2.0) / (out_w / 2.0))
    idx = np.interp(x_out, xo_sorted, order.astype(np.float32),
                    left=np.nan, right=np.nan)
    valid = ~np.isnan(idx)
    col = np.zeros((h, out_w), dtype=np.float32)
    col_rgb = np.zeros((h, out_w, 3), dtype=np.float32)
    if valid.any():
        i0 = np.clip(np.floor(idx[valid]).astype(np.int32), 0, w - 1)
        i1 = np.clip(i0 + 1, 0, w - 1)
        f = (idx[valid] - i0)[None, :]
        col[:, valid] = alp[:, i0] * (1 - f) + alp[:, i1] * f
        col_rgb[:, valid, :] = rgb[:, i0, :] * (1 - f[..., None]) + rgb[:, i1, :] * f[..., None]
        pvis = persp[i0] * (1 - f[0]) + persp[i1] * f[0]
        bright = np.clip(pvis ** 0.9, 0.55, 1.85)
        col_rgb[:, valid, :] *= bright[None, :, None]
    return col_rgb, col

def frame(rgb, alp, t, size=512):
    """t: 0..1 фаза полного оборота."""
    theta = t * 2 * np.pi
    back = np.sin(theta) < 0
    a = theta % np.pi
    if a > np.pi / 2:                # смотрим на обратную сторону — зеркалим
        a = np.pi - a
        back = not back
    crgb, calp = rotate_plane(rgb, alp, a)
    if back:
        crgb = crgb[:, ::-1] * 0.62
        calp = calp[:, ::-1]
    # блик, бегущий по грани
    h, w = calp.shape
    sweep = (np.cos(a) * 0.5 + 0.5)
    xg = np.linspace(0, 1, w)[None, :]
    glare = np.exp(-((xg - (0.55 - 0.55 * sweep)) ** 2) / 0.012) * (0.55 if not back else 0.15)
    crgb = crgb + glare[..., None] * np.array([0.62, 0.45, 1.0], dtype=np.float32) * calp[..., None]
    crgb = np.clip(crgb, 0, 1.35)

    # мягкое дыхание масштаба (2%)
    scale = 1.0 + 0.02 * np.cos(2 * np.pi * t)
    img = np.zeros((size, size, 3), dtype=np.float32)
    img[:] = np.array([0.043, 0.031, 0.075], dtype=np.float32)

    # перенос на холст
    target = int(size * 0.74 * scale)
    ay = Image.fromarray((calp * 255).astype(np.uint8)).resize((target, target), Image.LANCZOS)
    aimg = Image.fromarray((crgb * 255).clip(0, 255).astype(np.uint8)).resize((target, target), Image.LANCZOS)
    am = np.asarray(ay).astype(np.float32) / 255.0
    ac = np.asarray(aimg).astype(np.float32) / 255.0
    ox = (size - target) // 2
    oy = (size - target) // 2

    # свечение: размытая копия буквы в неоне
    glow_src = Image.fromarray(((ac * (am[..., None] ** 1.0)) * 255).clip(0, 255).astype(np.uint8))
    g1 = glow_src.filter(ImageFilter.GaussianBlur(target * 0.035))
    g2 = glow_src.filter(ImageFilter.GaussianBlur(target * 0.11))
    glow = (np.asarray(g1).astype(np.float32) / 255.0) * 0.55 + (np.asarray(g2).astype(np.float32) / 255.0) * 0.75
    glow = np.clip(glow, 0, 1) * np.array([0.72, 0.30, 1.0], dtype=np.float32)

    img[oy:oy + target, ox:ox + target] += glow * 0.85
    img[oy:oy + target, ox:ox + target] = (img[oy:oy + target, ox:ox + target] * (1 - am[..., None]) + ac * am[..., None])

    # виньетка + лёгкий шум, чтобы градиенты не полосились в GIF
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32) / size
    vig = 1.0 - 0.25 * (((xx - 0.5) ** 2 + (yy - 0.5) ** 2) / 0.5)
    img *= vig[..., None]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))

def main():
    rgb, alp = load_letter()
    frames_n = int(sys.argv[1]) if len(sys.argv) > 1 else 60
    size = int(sys.argv[2]) if len(sys.argv) > 2 else 512
    name = sys.argv[3] if len(sys.argv) > 3 else "aethervisuals_rotate.gif"
    frames = [frame(rgb, alp, i / frames_n, size) for i in range(frames_n)]
    pal = frames[0].quantize(colors=128, method=Image.MEDIANCUT, dither=Image.Dither.NONE)
    pal = frames[0].convert("RGB").quantize(colors=128, method=Image.MEDIANCUT)
    quants = [f.quantize(palette=pal, dither=Image.Dither.NONE) for f in frames]
    out = f"{OUT_DIR}/{name}"
    quants[0].save(out, save_all=True, append_images=quants[1:], duration=45, loop=0, optimize=True, disposal=1)
    import os
    print(out, os.path.getsize(out) // 1024, "KB", frames_n, "frames", size, "px")

main()
