"""
Рисует иконку AetherGlass: тёмное стекло, холодный фиолетово-синий градиент,
блик по верхней кромке и светлая буква «А».

Никаких чужих картинок: фигуры строятся полигонами, сглаживание —
прорисовкой в 4× размере и уменьшением. Результат — .ico с набором
стандартных размеров (16…256) и контрольный PNG 256×256.

Запуск:  python3 tools/make_icon.py
"""
from PIL import Image, ImageDraw, ImageFilter

SIZE = 1024
PNG = 256
LAUNCHER_ICO = "assets/icons/launcher.ico"
LAUNCHER_PNG = "assets/icons/launcher-256.png"
SETUP_ICO = "assets/icons/setup.ico"
SETUP_PNG = "assets/icons/setup-256.png"

TOP = (46, 38, 104)      # фиолетовый сверху
BOTTOM = (9, 13, 34)     # глубокий синий снизу
GLASS = (150, 180, 255)  # холодный блик
GLYPH = (226, 235, 255)  # почти белый

HIGHLIGHT = 46           # прозрачность блика по верхней кромке
HALO = 90                # прозрачность свечения буквы


def gradient(size):
    """Диагональный градиент: светлый верхний левый угол, тёмный нижний правый."""
    ramp = Image.linear_gradient("L").rotate(-35, expand=True).resize((size, size), Image.BICUBIC)
    red = ramp.point(lambda v: TOP[0] + (BOTTOM[0] - TOP[0]) * v // 255)
    green = ramp.point(lambda v: TOP[1] + (BOTTOM[1] - TOP[1]) * v // 255)
    blue = ramp.point(lambda v: TOP[2] + (BOTTOM[2] - TOP[2]) * v // 255)
    return Image.merge("RGB", (red, green, blue))


def rounded(size, radius):
    """Маска скруглённого квадрата; край мягкий, чтобы не было «лестницы»."""
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return mask.filter(ImageFilter.GaussianBlur(size * 0.004))


def fade(layer, alpha):
    """Уменьшает прозрачность слоя в указанное число раз (0…255)."""
    red, green, blue, old = layer.split()
    return Image.merge("RGBA", (red, green, blue, old.point(lambda v: v * alpha // 255)))


def glyph_letter(size, thickness):
    """Буква «А»: лаунчер."""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    paint = ImageDraw.Draw(layer)
    apex = (size * 0.50, size * 0.26)
    paint.line([apex, (size * 0.25, size * 0.78)], fill=GLYPH + (255,), width=thickness, joint="curve")
    paint.line([apex, (size * 0.75, size * 0.78)], fill=GLYPH + (255,), width=thickness, joint="curve")
    paint.line([(size * 0.35, size * 0.63), (size * 0.65, size * 0.63)],
               fill=GLYPH + (255,), width=max(2, int(thickness * 0.8)))
    return layer


def glyph_box(size, thickness):
    """Коробка со стрелкой вниз: установщик."""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    paint = ImageDraw.Draw(layer)
    # крышка коробки
    paint.rounded_rectangle((size * 0.22, size * 0.24, size * 0.78, size * 0.40),
                            radius=int(size * 0.05), fill=GLYPH + (235,))
    # корпус коробки
    paint.rounded_rectangle((size * 0.26, size * 0.38, size * 0.74, size * 0.80),
                            radius=int(size * 0.05), outline=GLYPH + (255,),
                            width=thickness, fill=(0, 0, 0, 0))
    # стрелка вниз внутри корпуса
    paint.line([(size * 0.50, size * 0.46), (size * 0.50, size * 0.70)],
               fill=GLYPH + (255,), width=thickness)
    paint.line([(size * 0.38, size * 0.60), (size * 0.50, size * 0.71)],
               fill=GLYPH + (255,), width=thickness, joint="curve")
    paint.line([(size * 0.62, size * 0.60), (size * 0.50, size * 0.71)],
               fill=GLYPH + (255,), width=thickness, joint="curve")
    return layer


def compose(glyph):
    """Собирает иконку: подложка, блик, глиф, кромка."""
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.paste(gradient(SIZE), (0, 0), rounded(SIZE, int(SIZE * 0.24)))

    shine = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    ImageDraw.Draw(shine).rounded_rectangle((SIZE * 0.13, SIZE * 0.08, SIZE * 0.87, SIZE * 0.22),
                                            radius=int(SIZE * 0.09), fill=GLASS + (HIGHLIGHT,))
    canvas.alpha_composite(fade(shine.filter(ImageFilter.GaussianBlur(SIZE * 0.012)), HIGHLIGHT))

    halo = fade(glyph.filter(ImageFilter.GaussianBlur(SIZE * 0.03)), HALO)
    canvas.alpha_composite(halo)
    canvas.alpha_composite(glyph)

    edge = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    ImageDraw.Draw(edge).rounded_rectangle((SIZE * 0.015, SIZE * 0.015, SIZE * 0.985, SIZE * 0.985),
                                           radius=int(SIZE * 0.23), outline=GLASS + (120,),
                                           width=max(2, int(SIZE * 0.010)))
    canvas.alpha_composite(fade(edge.filter(ImageFilter.GaussianBlur(SIZE * 0.003)), 140))
    return canvas


def save(canvas, ico, png, title):
    """Сохраняет .ico и контрольный PNG, печатая проверочные числа."""
    small = canvas.resize((PNG, PNG), Image.LANCZOS)
    small.save(png)
    canvas.save(ico, format="ICO", bitmap_format="png",
                sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])

    pixels = small.load()
    visible = []
    for y in range(PNG):
        for x in range(PNG):
            red, green, blue, alpha = pixels[x, y]
            if alpha > 200:
                visible.append((red + green + blue) / 3.0)
    bright = sum(1 for value in visible if value > 170)
    print(title, "→", ico)
    print("  средняя яркость: %.0f" % (sum(visible) / len(visible)))
    print("  доля светлых пикселей: %.1f%%" % (100.0 * bright / len(visible)))


def main():
    thickness = int(SIZE * 0.075)
    save(compose(glyph_letter(SIZE, thickness)), LAUNCHER_ICO, LAUNCHER_PNG, "иконка лаунчера")
    save(compose(glyph_box(SIZE, thickness)), SETUP_ICO, SETUP_PNG, "иконка установщика")


if __name__ == "__main__":
    main()
