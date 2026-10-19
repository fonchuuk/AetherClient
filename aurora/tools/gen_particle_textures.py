#!/usr/bin/env python3
"""Генератор градиентных текстур частиц в стиле Liquid Glass (64x64 RGBA)."""

import math
import os
import struct
import zlib

SIZE = 64
HALF = (SIZE - 1) / 2.0


def clamp01(v: float) -> float:
    return max(0.0, min(1.0, v))


def smoothstep(edge0: float, edge1: float, x: float) -> float:
    if edge0 == edge1:
        return 1.0 if x >= edge1 else 0.0
    t = clamp01((x - edge0) / (edge1 - edge0))
    return t * t * (3.0 - 2.0 * t)


def write_png(path: str, evaluate) -> None:
    rows = []
    for y in range(SIZE):
        row = bytearray([0])  # фильтр None
        ny = (y - HALF) / HALF
        for x in range(SIZE):
            nx = (x - HALF) / HALF
            r, g, b, a = evaluate(nx, ny)
            row.extend((
                max(0, min(255, int(round(r * 255.0)))),
                max(0, min(255, int(round(g * 255.0)))),
                max(0, min(255, int(round(b * 255.0)))),
                max(0, min(255, int(round(a * 255.0)))),
            ))
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as stream:
        stream.write(png)


def liquid_glass_palette(core: float, rim: float, bloom: float, angle: float):
    """Формирует переливающийся градиент Liquid Glass (горячее ядро + стеклянный блик + мягкое гало)."""
    caustic = 0.5 + 0.5 * math.sin(angle * 3.0 + 0.7)
    r = clamp01(0.48 * bloom + 0.78 * rim * (0.75 + 0.25 * caustic) + 0.98 * core)
    g = clamp01(0.82 * bloom + 0.94 * rim + 0.99 * core)
    b = clamp01(0.98 * bloom + 1.00 * rim + 1.00 * core)
    a = clamp01(bloom * 0.52 + rim * 0.78 + core * 0.96)
    # Гарантируем мягкое затухание к краям спрайта
    return r, g, b, a


def circle(x: float, y: float):
    d = math.hypot(x, y)
    if d >= 1.0:
        return 0.0, 0.0, 0.0, 0.0
    angle = math.atan2(y, x)
    core = math.exp(-d * d * 9.5)
    # Стеклянное преломляющее кольцо с солнечным бликом сверху-слева
    glint = max(0.0, (-0.65 * x - 0.65 * y) / (d + 0.25))
    rim_band = math.exp(-((d - 0.46) / 0.13) ** 2) * (0.65 + 0.35 * glint)
    bloom = (1.0 - smoothstep(0.0, 0.98, d)) ** 1.85
    return liquid_glass_palette(core, rim_band, bloom, angle)


def star(x: float, y: float):
    d = math.hypot(x, y)
    if d >= 1.0:
        return 0.0, 0.0, 0.0, 0.0
    angle = math.atan2(y, x)
    # 4 главных луча + 4 диагональных микро-луча в стиле кристаллической звезды
    ax, ay = abs(x), abs(y)
    main_rays = math.exp(-min(ax, ay) * 13.5) * (1.0 - smoothstep(0.0, 0.96, d))
    diag = abs(ax - ay) * 0.7071
    sub_rays = math.exp(-diag * 16.0) * (1.0 - smoothstep(0.0, 0.68, d)) * 0.65
    core = math.exp(-d * d * 14.0) + main_rays * 0.75
    rim = sub_rays + math.exp(-((d - 0.32) / 0.11) ** 2) * 0.45
    bloom = (1.0 - smoothstep(0.0, 0.96, d)) ** 2.1 * 0.75 + main_rays * 0.45
    return liquid_glass_palette(clamp01(core), clamp01(rim), clamp01(bloom), angle)


def spark(x: float, y: float):
    d = math.hypot(x, y)
    if d >= 1.0:
        return 0.0, 0.0, 0.0, 0.0
    angle = math.atan2(y, x)
    # Вытянутая энергетическая искра с диагональным преломлением
    u = (x * 0.866 - y * 0.5)
    v = (x * 0.5 + y * 0.866)
    streak = math.exp(-(v * v * 28.0 + u * u * 3.2))
    cross = math.exp(-(u * u * 30.0 + v * v * 7.5)) * 0.65
    core = math.exp(-d * d * 16.0) + streak * 0.82
    rim = cross + math.exp(-((d - 0.38) / 0.12) ** 2) * streak
    bloom = (1.0 - smoothstep(0.0, 0.95, d)) ** 2.2 * 0.65 + streak * 0.5
    return liquid_glass_palette(clamp01(core), clamp01(rim), clamp01(bloom), angle)


def diamond(x: float, y: float):
    d = math.hypot(x, y)
    if d >= 1.0:
        return 0.0, 0.0, 0.0, 0.0
    angle = math.atan2(y, x)
    # Кристалл с вогнутыми гранями (superellipse p=0.78) и внутренним градиентом граней
    m = (abs(x) ** 0.82 + abs(y) ** 0.82) ** (1.0 / 0.82)
    if m >= 0.98:
        return 0.0, 0.0, 0.0, 0.0
    core = math.exp(-m * m * 10.5)
    facet_rim = math.exp(-((m - 0.52) / 0.09) ** 2) * (0.75 + 0.25 * math.cos(angle * 4.0))
    bloom = (1.0 - smoothstep(0.0, 0.95, m)) ** 1.75
    return liquid_glass_palette(core, facet_rim, bloom, angle)


def ring(x: float, y: float):
    d = math.hypot(x, y)
    if d >= 1.0:
        return 0.0, 0.0, 0.0, 0.0
    angle = math.atan2(y, x)
    # Двойное преломляющее кольцо Liquid Glass с солнечным бликом
    glint = 0.65 + 0.35 * math.cos(angle + 2.2)
    outer_ring = math.exp(-((d - 0.60) / 0.10) ** 2)
    inner_ring = math.exp(-((d - 0.36) / 0.08) ** 2) * 0.55
    core = outer_ring * glint * 0.85 + math.exp(-d * d * 24.0) * 0.35
    rim = outer_ring + inner_ring
    bloom = math.exp(-((d - 0.54) / 0.22) ** 2) * 0.72
    return liquid_glass_palette(clamp01(core), clamp01(rim), clamp01(bloom), angle)


def snowflake(x: float, y: float):
    d = math.hypot(x, y)
    if d >= 1.0:
        return 0.0, 0.0, 0.0, 0.0
    angle = math.atan2(y, x)
    # 6-лучевая кристаллическая снежинка с дендритными веточками и мягким свечением
    branch = max(0.0, math.cos(angle * 6.0)) ** 7.0
    sub_branch = max(0.0, math.cos(angle * 12.0)) ** 5.0 * math.exp(-((d - 0.52) / 0.16) ** 2)
    hex_ring = math.exp(-((d - 0.34) / 0.08) ** 2) * (0.6 + 0.4 * max(0.0, math.cos(angle * 6.0)))
    envelope = 1.0 - smoothstep(0.08, 0.92, d)
    core = math.exp(-d * d * 14.0) + branch * envelope * 0.78
    rim = hex_ring + sub_branch * 0.85
    bloom = (1.0 - smoothstep(0.0, 0.96, d)) ** 2.0 * (0.45 + 0.55 * branch)
    return liquid_glass_palette(clamp01(core), clamp01(rim), clamp01(bloom), angle)


def heart(x: float, y: float):
    d = math.hypot(x, y)
    if d >= 1.0:
        return 0.0, 0.0, 0.0, 0.0
    angle = math.atan2(y, x)
    hx = x * 1.25
    hy = -y * 1.25 + 0.18
    a = hx * hx + hy * hy - 0.34
    val = max(0.0, a * a * a - (hx * hx) * (hy * hy * hy))
    core = math.exp(-val * 22.0) * (1.0 - smoothstep(0.25, 0.94, d))
    rim = math.exp(-((val - 0.02) / 0.04) ** 2) * 0.75
    bloom = math.exp(-d * d * 3.6) * 0.65
    return liquid_glass_palette(clamp01(core), clamp01(rim), clamp01(bloom), angle)


def main() -> None:
    target = os.path.join(
        os.path.dirname(__file__),
        "..",
        "client",
        "src",
        "main",
        "resources",
        "assets",
        "aetherglass",
        "textures",
        "particle",
    )
    shapes = {
        "circle.png": circle,
        "star.png": star,
        "spark.png": spark,
        "diamond.png": diamond,
        "square.png": diamond,
        "ring.png": ring,
        "snowflake.png": snowflake,
        "heart.png": heart,
    }
    for name, fn in shapes.items():
        write_png(os.path.join(target, name), fn)


if __name__ == "__main__":
    main()
