#!/usr/bin/env python3
"""
Генератор аналитических SDF (Signed Distance Field) 9-slice текстур 256x256 (corner slice = 64x64)
для идеально гладких углов HUD и меню без пиксельных ступенек (Блок 2).
"""
import json
import math
from pathlib import Path
from PIL import Image

GUI_DIR = Path(__file__).resolve().parent.parent / "client/src/main/resources/assets/aetherglass/textures/gui"
GUI_DIR.mkdir(parents=True, exist_ok=True)


def write_mcmeta(png_path: Path):
    mcmeta = png_path.with_name(png_path.name + ".mcmeta")
    mcmeta.write_text(json.dumps({"texture": {"blur": True, "clamp": True}}, indent=2) + "\n", encoding="utf-8")


def smoothstep(edge0: float, edge1: float, x: float) -> float:
    t = max(0.0, min(1.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)


def sd_rounded_box(px: float, py: float, cx: float, cy: float, half_w: float, half_h: float, r: float) -> float:
    qx = abs(px - cx) - (half_w - r)
    qy = abs(py - cy) - (half_h - r)
    out_dist = math.hypot(max(qx, 0.0), max(qy, 0.0))
    in_dist = min(max(qx, qy), 0.0)
    return out_dist + in_dist - r


def generate_nine_slice_fill(size: int = 256, corner_r: float = 64.0):
    """Белая аналитическая SDF 9-slice заливка (corner = 64x64, без отступа)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pix = img.load()
    cx = cy = size * 0.5
    half = size * 0.5
    aa = 2.2

    for y in range(size):
        py = y + 0.5
        for x in range(size):
            px = x + 0.5
            d = sd_rounded_box(px, py, cx, cy, half, half, corner_r)
            alpha = 1.0 - smoothstep(-aa, 0.35, d)
            a = int(round(max(0.0, min(1.0, alpha)) * 255.0))
            if a > 0:
                pix[x, y] = (255, 255, 255, a)

    out = GUI_DIR / "nine_slice_fill.png"
    img.save(out, optimize=True)
    write_mcmeta(out)
    print("Saved", out)


def generate_nine_slice_border(size: int = 256, corner_r: float = 64.0, stroke_w: float = 9.0):
    """Белая аналитическая SDF 9-slice обводка (corner = 64x64, без ступенек)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pix = img.load()
    cx = cy = size * 0.5
    half = size * 0.5
    aa = 2.2

    for y in range(size):
        py = y + 0.5
        for x in range(size):
            px = x + 0.5
            d = sd_rounded_box(px, py, cx, cy, half, half, corner_r)
            outer_a = 1.0 - smoothstep(-aa, 0.35, d)
            inner_a = smoothstep(-stroke_w - aa, -stroke_w + aa * 0.6, d)
            alpha = outer_a * inner_a
            a = int(round(max(0.0, min(1.0, alpha)) * 255.0))
            if a > 0:
                pix[x, y] = (255, 255, 255, a)

    out = GUI_DIR / "nine_slice_border.png"
    img.save(out, optimize=True)
    write_mcmeta(out)
    print("Saved", out)


def generate_nine_slice_shadow(size: int = 256, corner_r: float = 52.0, margin: float = 20.0):
    """Мягкая гауссова SDF-тень вокруг скруглённой панели (corner slice = 64x64)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pix = img.load()
    cx = cy = size * 0.5
    half = size * 0.5 - margin

    for y in range(size):
        py = y + 0.5
        for x in range(size):
            px = x + 0.5
            d = sd_rounded_box(px, py, cx, cy, half, half, corner_r)
            if d <= 0.0:
                alpha = 0.90
            else:
                alpha = 0.90 * math.exp(-(d * d) / (2.0 * 9.0 * 9.0))
            a = int(round(max(0.0, min(1.0, alpha)) * 255.0))
            if a > 0:
                pix[x, y] = (255, 255, 255, a)

    out = GUI_DIR / "nine_slice_shadow.png"
    img.save(out, optimize=True)
    write_mcmeta(out)
    print("Saved", out)


def generate_nine_slice_glass_panel(size: int = 256, corner_r: float = 64.0):
    """Композитная 9-slice текстура выпуклого матового стекла (corner = 64x64)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pix = img.load()
    cx = cy = size * 0.5
    half = size * 0.5
    aa = 2.2
    stroke_w = 8.0

    for y in range(size):
        py = y + 0.5
        vy = max(0.0, min(1.0, py / float(size)))
        for x in range(size):
            px = x + 0.5
            vx = max(0.0, min(1.0, px / float(size)))
            d = sd_rounded_box(px, py, cx, cy, half, half, corner_r)
            outer_a = 1.0 - smoothstep(-aa, 0.35, d)
            if outer_a <= 0.001:
                continue

            r = 36.0 * (1.0 - vy) + 18.0 * vy
            g = 52.0 * (1.0 - vy) + 28.0 * vy
            b = 78.0 * (1.0 - vy) + 46.0 * vy
            base_a = 0.74 * (1.0 - 0.12 * vy)

            dome = max(0.0, 1.0 - vy * 2.35)
            dome = dome * dome * 0.25
            r = r * (1.0 - dome) + 240.0 * dome
            g = g * (1.0 - dome) + 248.0 * dome
            b = b * (1.0 - dome) + 255.0 * dome
            base_a = min(0.92, base_a + dome * 0.32)

            rim_factor = smoothstep(-stroke_w * 2.0, -1.0, d)
            diag = (1.0 - vy * 0.65) * 0.7 + (1.0 - vx * 0.35) * 0.3
            rim_r = 228.0 * diag + 125.0 * (1.0 - diag)
            rim_g = 245.0 * diag + 208.0 * (1.0 - diag)
            rim_b = 255.0

            border_ring = smoothstep(-stroke_w - aa, -stroke_w + aa * 0.6, d)
            mix_rim = min(1.0, rim_factor * 0.28 + border_ring * 0.70)

            fr = int(round(max(0.0, min(255.0, r * (1.0 - mix_rim) + rim_r * mix_rim))))
            fg = int(round(max(0.0, min(255.0, g * (1.0 - mix_rim) + rim_g * mix_rim))))
            fb = int(round(max(0.0, min(255.0, b * (1.0 - mix_rim) + rim_b * mix_rim))))
            fa = int(round(max(0.0, min(255.0, (base_a * (1.0 - border_ring * 0.35) + border_ring * 0.88) * outer_a * 255.0))))
            pix[x, y] = (fr, fg, fb, fa)

    out = GUI_DIR / "nine_slice_glass_panel.png"
    img.save(out, optimize=True)
    write_mcmeta(out)
    print("Saved", out)


def generate_slider_knob(size: int = 64):
    """Идеально гладкий круглый стеклянный бегунок для ползунков (SDF круг)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pix = img.load()
    cx = cy = size * 0.5
    radius = size * 0.5 - 4.0
    aa = 1.4

    for y in range(size):
        py = y + 0.5
        vy = (py - (cy - radius)) / (radius * 2.0)
        for x in range(size):
            px = x + 0.5
            d = math.hypot(px - cx, py - cy) - radius
            if d > 3.5:
                continue
            if d > 0.0:
                sa = math.exp(-(d * d) / 3.0) * 0.35
                pix[x, y] = (10, 20, 36, int(round(sa * 255.0)))
                continue
            outer_a = 1.0 - smoothstep(-aa, 0.3, d)
            ring = smoothstep(-5.0, -1.0, d)
            top_shine = max(0.0, 1.0 - max(0.0, vy) * 1.4)
            r = int(round(220 + 35 * top_shine * (1.0 - ring * 0.2)))
            g = int(round(242 + 13 * top_shine))
            b = 255
            a = int(round(outer_a * 255.0))
            pix[x, y] = (r, g, b, a)

    out = GUI_DIR / "slider_knob_sdf.png"
    img.save(out, optimize=True)
    write_mcmeta(out)
    print("Saved", out)


if __name__ == "__main__":
    generate_nine_slice_fill()
    generate_nine_slice_border()
    generate_nine_slice_shadow()
    generate_nine_slice_glass_panel()
    generate_slider_knob()
