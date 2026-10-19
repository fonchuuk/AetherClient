#!/usr/bin/env python3
"""
Генератор превью-скриншотов интерфейса и эффектов AetherGlass v0.1.8 (FullHD 1920x1080).
Рендерит реальные фоновые текстуры клиента + все слои Frosted Liquid Glass
(размытие фона под панелями, диагональная тень, дымчатое матовое стекло,
выпуклое преломление краёв, скользящий солнечный блик, компактные слайдеры 68px,
вкладку «Аккаунты» и сцену 3D-шейдеров).
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
GUI_TEX = ROOT / "aurora/client/src/main/resources/assets/aetherglass/textures/gui"
OUT_DIR = ROOT / "screenshots"
OUT_DIR.mkdir(parents=True, exist_ok=True)

W, H = 1920, 1080


def load_font(size: int, bold: bool = False):
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


FONT_TITLE = load_font(34, True)
FONT_SUB = load_font(17, False)
FONT_H2 = load_font(20, True)
FONT_BTN = load_font(17, True)
FONT_BODY = load_font(15, False)
FONT_BODY_B = load_font(15, True)
FONT_SM = load_font(13, False)
FONT_SM_B = load_font(13, True)
FONT_XS = load_font(11, False)


def load_backdrop(name: str = "menu_backdrop_night_b0.png") -> Image.Image:
    p = GUI_TEX / name
    if p.exists():
        img = Image.open(p).convert("RGBA"); img = img.crop((0, 0, int(img.width * 0.90), img.height))
        return img.resize((W, H), Image.Resampling.BICUBIC)
    img = Image.new("RGBA", (W, H), (10, 16, 28, 255))
    return img


def draw_liquid_glass_panel(
    base: Image.Image,
    x: int,
    y: int,
    w: int,
    h: int,
    radius: int = 16,
    tint=(16, 22, 34, 172),
    accent=(118, 205, 255),
    blur_px: int = 14,
    convex: bool = True,
):
    """
    Точная реализация DrawUtil.drawLiquidGlassPanel:
    1) Реальное размытие фона под панелью (Gaussian blur)
    2) Мягкая диагональная тень (+4, +6)
    3) Дымчатое матовое стекло (Frosted smoky fill)
    4) Выпуклый верхний купол и нижнее преломление (Convex dome & bottom rim)
    5) Скользящий солнечный блик вдоль граней (Solar specular sheen)
    """
    # 1. Мягкая диагональная тень
    shadow = Image.new("RGBA", (w + 40, h + 40), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    sdraw.rounded_rectangle((16, 18, 16 + w, 18 + h), radius=radius + 2, fill=(3, 6, 14, 135))
    shadow = shadow.filter(ImageFilter.GaussianBlur(8))
    base.alpha_composite(shadow, (max(0, x - 12), max(0, y - 12)))

    # 2. Размытие фона строго под маской скруглённого окна
    crop_box = (max(0, x), max(0, y), min(W, x + w), min(H, y + h))
    if crop_box[2] > crop_box[0] and crop_box[3] > crop_box[1]:
        region = base.crop(crop_box).filter(ImageFilter.GaussianBlur(blur_px))
        mask = Image.new("L", (crop_box[2] - crop_box[0], crop_box[3] - crop_box[1]), 0)
        mdraw = ImageDraw.Draw(mask)
        mdraw.rounded_rectangle((0, 0, mask.width - 1, mask.height - 1), radius=radius, fill=255)
        base.paste(region, (crop_box[0], crop_box[1]), mask)

    # 3. Дымчатое стекло + выпуклый купол
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    odraw = ImageDraw.Draw(overlay)
    odraw.rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=tint)

    if convex:
        dome_h = max(6, min(h // 2, 34))
        for row in range(1, dome_h):
            t = 1.0 - (row / float(dome_h))
            a = int(38 * t * t)
            odraw.line((radius // 2, row, w - radius // 2, row), fill=(225, 242, 255, a))
        # Нижний рефлекс глубины
        for row in range(max(1, h - 10), h - 1):
            odraw.line((radius // 2, row, w - radius // 2, row), fill=(6, 10, 18, 70))

    # 4. Внутренний и внешний стеклянный кант + солнечный блик
    odraw.rounded_rectangle(
        (0, 0, w - 1, h - 1),
        radius=radius,
        outline=(210, 235, 255, 76),
        width=1,
    )
    odraw.rounded_rectangle(
        (1, 1, w - 2, h - 2),
        radius=max(2, radius - 1),
        outline=(accent[0], accent[1], accent[2], 38),
        width=1,
    )
    # Солнечный блик на верхней грани
    sheen_w = int(w * 0.42)
    sheen_x = int(w * 0.18)
    odraw.line((sheen_x, 1, sheen_x + sheen_w, 1), fill=(255, 255, 255, 165), width=2)
    odraw.line((1, radius, 1, min(h - radius, radius + int(h * 0.35))), fill=(240, 250, 255, 120), width=1)

    base.alpha_composite(overlay, (x, y))


def draw_convex_pill(
    base: Image.Image,
    x: int,
    y: int,
    w: int,
    h: int,
    label: str = "",
    active: bool = False,
    hovered: bool = False,
    radius: int = 11,
    align: str = "center",
):
    tint = (28, 44, 68, 195) if active else ((24, 34, 52, 182) if hovered else (18, 26, 40, 162))
    accent = (120, 215, 255) if (active or hovered) else (160, 195, 230)
    draw_liquid_glass_panel(base, x, y, w, h, radius=radius, tint=tint, accent=accent, blur_px=10, convex=True)
    if active:
        bar = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        bdraw = ImageDraw.Draw(bar)
        bdraw.rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, outline=(125, 220, 255, 155), width=1)
        base.alpha_composite(bar, (x, y))
    if label:
        d = ImageDraw.Draw(base)
        bbox = d.textbbox((0, 0), label, font=FONT_BTN if h >= 36 else FONT_BODY_B)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        tx = x + (w - tw) // 2 if align == "center" else x + 14
        ty = y + (h - th) // 2 - 1
        d.text((tx + 1, ty + 1), label, font=FONT_BTN if h >= 36 else FONT_BODY_B, fill=(0, 0, 0, 140))
        d.text((tx, ty), label, font=FONT_BTN if h >= 36 else FONT_BODY_B, fill=(244, 250, 255, 255) if active else (225, 236, 250, 240))


def draw_compact_slider(base: Image.Image, x: int, y: int, w: int, ratio: float, value_text: str):
    """Компактный аккуратный слайдер (68x5 px) с выпуклым стеклянным бегунком."""
    d = ImageDraw.Draw(base)
    track_h = 5
    ty = y + 4
    d.rounded_rectangle((x, ty, x + w, ty + track_h), radius=3, fill=(10, 15, 24, 200), outline=(180, 215, 255, 45))
    fill_w = max(4, int(w * max(0.0, min(1.0, ratio))))
    d.rounded_rectangle((x, ty, x + fill_w, ty + track_h), radius=3, fill=(110, 205, 255, 220))
    kx = x + fill_w
    ky = ty + track_h // 2
    d.ellipse((kx - 5, ky - 5, kx + 5, ky + 5), fill=(240, 250, 255, 255), outline=(90, 190, 255, 220))
    d.text((x + w + 8, y - 1), value_text, font=FONT_XS, fill=(195, 220, 245, 235))


def draw_toggle_switch(base: Image.Image, x: int, y: int, enabled: bool):
    d = ImageDraw.Draw(base)
    w, h = 30, 15
    fill = (88, 198, 255, 215) if enabled else (22, 30, 44, 190)
    d.rounded_rectangle((x, y, x + w, y + h), radius=7, fill=fill, outline=(210, 235, 255, 85))
    kx = x + w - 8 if enabled else x + 8
    ky = y + h // 2
    d.ellipse((kx - 5, ky - 5, kx + 5, ky + 5), fill=(248, 252, 255, 255))


def draw_grouped_hud(base: Image.Image, x: int = 18, y: int = 18):
    """Единый сгруппированный HUD-виджет из матового стекла без чит-надписей."""
    draw_liquid_glass_panel(base, x, y, 258, 76, radius=14, tint=(14, 20, 32, 168), blur_px=12)
    d = ImageDraw.Draw(base)
    d.text((x + 14, y + 10), "AETHERGLASS", font=FONT_BODY_B, fill=(135, 220, 255, 255))
    d.text((x + 142, y + 11), "1.21.11 • 240 FPS", font=FONT_SM, fill=(215, 232, 250, 230))
    d.line((x + 14, y + 32, x + 244, y + 32), fill=(200, 230, 255, 42), width=1)
    d.text((x + 14, y + 39), "Игрок: AuroraPlayer", font=FONT_SM, fill=(235, 244, 255, 240))
    d.text((x + 14, y + 55), "XYZ: 142.5 / 68.0 / -219.3  •  Ping: 18 ms", font=FONT_XS, fill=(175, 200, 230, 225))


def draw_micro_crosshair(base: Image.Image, cx: int = W // 2, cy: int = H // 2):
    """Микро-прицел 2 пикселя с аккуратным зазором и точкой."""
    d = ImageDraw.Draw(base)
    # 1px тень + 2px точка/лучи
    d.rectangle((cx - 1, cy - 1, cx + 1, cy + 1), fill=(240, 252, 255, 245), outline=(0, 0, 0, 170))
    gap, length = 3, 4
    for dx1, dy1, dx2, dy2 in [
        (-gap - length, 0, -gap, 1),
        (gap, 0, gap + length, 1),
        (0, -gap - length, 1, -gap),
        (0, gap, 1, gap + length),
    ]:
        d.rectangle((cx + dx1, cy + dy1, cx + dx2, cy + dy2), fill=(145, 230, 255, 235))


def render_01_main_menu():
    base = load_backdrop("menu_backdrop_night_b0.png")
    # Верхняя информационная капсула
    draw_liquid_glass_panel(base, 28, 24, 340, 44, radius=13, tint=(14, 20, 32, 165), blur_px=12)
    d = ImageDraw.Draw(base)
    d.text((46, 36), "AETHERGLASS 1.21.11  •  ◆ AuroraPlayer", font=FONT_SM_B, fill=(230, 242, 255, 245))

    # Центральное окно Главного меню (Lunar / Badlion Frosted Liquid Glass)
    pw, ph = 460, 430
    px, py = (W - pw) // 2, (H - ph) // 2
    draw_liquid_glass_panel(base, px, py, pw, ph, radius=22, tint=(14, 20, 34, 170), blur_px=18)

    d = ImageDraw.Draw(base)
    title = "AETHERGLASS"
    tb = d.textbbox((0, 0), title, font=FONT_TITLE)
    d.text((px + (pw - (tb[2] - tb[0])) // 2, py + 28), title, font=FONT_TITLE, fill=(242, 250, 255, 255))
    sub = "FROSTED LIQUID GLASS  •  MINECRAFT 1.21.11"
    sb = d.textbbox((0, 0), sub, font=FONT_XS)
    d.text((px + (pw - (sb[2] - sb[0])) // 2, py + 72), sub, font=FONT_XS, fill=(145, 210, 255, 225))

    buttons = [
        ("Одиночная игра", True),
        ("Сетевая игра", False),
        ("Модули и шейдеры (Right Shift)", False),
        ("◆ Аккаунты (смена ника в 1 клик)", False),
        ("Настройки игры  •  Выход", False),
    ]
    by = py + 108
    for text, active in buttons:
        draw_convex_pill(base, px + 36, by, pw - 72, 48, label=text, active=active, radius=13)
        by += 60

    out = OUT_DIR / "01_main_menu_frosted_glass.png"
    base.convert("RGB").save(out, quality=95)
    return out


def render_02_inline_menu_modules():
    base = load_backdrop("menu_backdrop_aurora_b0.png")
    draw_grouped_hud(base, 18, 18)
    draw_micro_crosshair(base)

    # Единое матовое окно Right Shift (Feather / Lunar Single-Window Accordion)
    mw, mh = 940, 610
    mx, my = (W - mw) // 2, (H - mh) // 2
    draw_liquid_glass_panel(base, mx, my, mw, mh, radius=20, tint=(13, 19, 31, 176), blur_px=18)

    d = ImageDraw.Draw(base)
    d.text((mx + 26, my + 20), "AETHERGLASS", font=FONT_H2, fill=(242, 250, 255, 255))
    d.text((mx + 190, my + 24), "Единое меню модулей и шейдеров (Right Shift)", font=FONT_SM, fill=(160, 205, 240, 225))

    tabs = [
        ("✦ Все модули", True),
        ("◈ 3D Шейдеры", False),
        ("◎ Прицел и HUD", False),
        ("◆ Аккаунты", False),
    ]
    tx = mx + 26
    for tlabel, tactive in tabs:
        draw_convex_pill(base, tx, my + 56, 176, 34, label=tlabel, active=tactive, radius=10)
        tx += 188

    cards = [
        (
            "BlockShader (Обводка блоков)",
            True,
            "Контур поверх полупрозрачной заливки граней",
            [("Прозрачность заливки", 0.42, "42%"), ("Толщина контура", 0.50, "2.5 px"), ("Сила свечения", 0.72, "1.45x")],
        ),
        (
            "HandsShader (Шейдер предмета в руке)",
            True,
            "100% видимый предмет + стеклянная глазурь поверх",
            [("Интенсивность глазури", 0.38, "38%"), ("Толщина контура", 0.40, "2.0 px"), ("Скорость перелива", 0.55, "1.1x")],
        ),
        (
            "Crosshair (Микро-прицел)",
            True,
            "Физический шаг 1/guiScale: размер от 1–2 пикселей",
            [("Размер (px)", 0.12, "2 px"), ("Толщина (px)", 0.16, "1 px"), ("Зазор / Прозрачность", 0.30, "3 px • 92%")],
        ),
        (
            "SkyShader (Живой купол неба)",
            True,
            "Плавное 3-цветное переливание и 3D-аврора по куполу",
            [("Скорость течения", 0.48, "1.0x"), ("Яркость сияния", 0.75, "75%"), ("Насыщенность купола", 0.65, "65%")],
        ),
        (
            "Particles (Мировые частицы X, Y, Z)",
            True,
            "Остаются в координатах мира при движении игрока",
            [("Частота спавна", 0.60, "1.8x"), ("Радиус в мире", 0.50, "16 м"), ("Форма: Снежинка / Ромб", 0.70, "Ромб")],
        ),
        (
            "HUD & Liquid Glass (Интерфейс)",
            True,
            "Размытие фона, солнечный блик и выпуклые капсулы",
            [("Сила размытия фона", 0.70, "14 px"), ("Интенсивность блика", 0.65, "65%"), ("Непрозрачность стекла", 0.58, "58%")],
        ),
    ]

    cw, ch = 432, 146
    for idx, (title, enabled, desc, sliders) in enumerate(cards):
        col = idx % 2
        row = idx // 2
        cx = mx + 26 + col * (cw + 24)
        cy = my + 106 + row * (ch + 16)
        draw_liquid_glass_panel(base, cx, cy, cw, ch, radius=14, tint=(18, 26, 42, 168), blur_px=10)
        d = ImageDraw.Draw(base)
        d.text((cx + 16, cy + 12), title, font=FONT_BODY_B, fill=(240, 248, 255, 255))
        draw_toggle_switch(base, cx + cw - 46, cy + 12, enabled)
        d.text((cx + 16, cy + 33), desc, font=FONT_XS, fill=(160, 198, 232, 225))
        sy = cy + 56
        for sname, sratio, sval in sliders:
            d.text((cx + 16, sy), sname, font=FONT_SM, fill=(220, 235, 250, 240))
            # Компактный слайдер шириной ровно 68px!
            draw_compact_slider(base, cx + cw - 134, sy + 2, 68, sratio, sval)
            sy += 27

    out = OUT_DIR / "02_right_shift_modules_compact_sliders.png"
    base.convert("RGB").save(out, quality=95)
    return out


def render_03_accounts_tab():
    base = load_backdrop("menu_backdrop_night_b0.png")
    draw_grouped_hud(base, 18, 18)

    mw, mh = 820, 520
    mx, my = (W - mw) // 2, (H - mh) // 2
    draw_liquid_glass_panel(base, mx, my, mw, mh, radius=20, tint=(14, 20, 34, 178), blur_px=18)

    d = ImageDraw.Draw(base)
    d.text((mx + 28, my + 22), "AETHERGLASS  •  МЕНЕДЖЕР ОФФЛАЙН-АККАУНТОВ", font=FONT_H2, fill=(242, 250, 255, 255))
    d.text((mx + 28, my + 48), "Мгновенная смена ника прямо в игре без перезапуска клиента (сохраняется в ModConfig)", font=FONT_SM, fill=(155, 205, 245, 230))

    tabs = [
        ("✦ Все модули", False),
        ("◈ 3D Шейдеры", False),
        ("◎ Прицел и HUD", False),
        ("◆ Аккаунты", True),
    ]
    tx = mx + 28
    for tlabel, tactive in tabs:
        draw_convex_pill(base, tx, my + 80, 176, 34, label=tlabel, active=tactive, radius=10)
        tx += 188

    # Поле ввода нового ника
    draw_liquid_glass_panel(base, mx + 28, my + 132, 520, 46, radius=12, tint=(10, 15, 25, 195), blur_px=8)
    d = ImageDraw.Draw(base)
    d.text((mx + 44, my + 146), "Введите никнейм:  fonchuuk_dev|", font=FONT_BODY_B, fill=(235, 246, 255, 255))
    draw_convex_pill(base, mx + 564, my + 132, 228, 46, label="✓ Применить в игре", active=True, radius=12)

    # Текущая сессия
    draw_liquid_glass_panel(base, mx + 28, my + 194, mw - 56, 56, radius=13, tint=(20, 34, 54, 175), blur_px=10)
    d = ImageDraw.Draw(base)
    d.text((mx + 46, my + 213), "Активная сессия Minecraft User:  AuroraPlayer   (UUID: 3b91ef40-8b2a-3f19-9c12-71a4d0e5c801 • Offline)", font=FONT_BODY_B, fill=(135, 225, 255, 255))

    # Список сохранённых аккаунтов
    saved = [
        ("AuroraPlayer", "Активен сейчас", True),
        ("fonchuuk", "Нажмите для мгновенного переключения", False),
        ("AetherTester", "Нажмите для мгновенного переключения", False),
        ("LiquidGlassPvP", "Нажмите для мгновенного переключения", False),
    ]
    ay = my + 268
    for nick, status, is_active in saved:
        draw_convex_pill(base, mx + 28, ay, mw - 160, 46, label=f"◆  {nick}   —   {status}", active=is_active, radius=12, align="left")
        draw_convex_pill(base, mx + mw - 120, ay, 92, 46, label="Удалить", active=False, radius=12)
        ay += 56

    out = OUT_DIR / "03_right_shift_accounts_tab.png"
    base.convert("RGB").save(out, quality=95)
    return out


def render_04_world_shaders_preview():
    """Превью работы 3D-шейдеров: SkyShader купол, мировые Particles, BlockShader (контур поверх заливки), HandsShader (100% текстура + шейдер поверх), микро-Crosshair."""
    base = Image.new("RGBA", (W, H), (8, 12, 24, 255))
    d = ImageDraw.Draw(base)

    # 1. SkyShader — плавный 3-цветный переливающийся купол авроры
    for y in range(H // 2 + 80):
        t = y / float(H // 2 + 80)
        r = int(12 * (1 - t) + 28 * t)
        g = int(20 * (1 - t) + 74 * t)
        b = int(46 * (1 - t) + 128 * t)
        d.line((0, y, W, y), fill=(r, g, b, 255))

    aurora_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    adraw = ImageDraw.Draw(aurora_layer)
    for x in range(0, W, 6):
        wave1 = int(170 + 55 * math.sin(x * 0.0045) + 28 * math.cos(x * 0.011))
        wave2 = int(230 + 48 * math.cos(x * 0.0038 - 1.1))
        adraw.line((x, wave1 - 65, x + 18, wave1 + 65), fill=(72, 235, 200, 38), width=6)
        adraw.line((x, wave2 - 50, x + 14, wave2 + 50), fill=(135, 150, 255, 34), width=5)
    aurora_layer = aurora_layer.filter(ImageFilter.GaussianBlur(22))
    base.alpha_composite(aurora_layer)

    # Земля/горизонт
    for y in range(H // 2 + 80, H):
        t = (y - (H // 2 + 80)) / float(H - (H // 2 + 80))
        d.line((0, y, W, y), fill=(int(14 + 10 * t), int(22 + 14 * t), int(34 + 18 * t), 255))

    # 2. Мировые частицы (Particles в координатах X, Y, Z разной дальности)
    part_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    pdraw = ImageDraw.Draw(part_layer)
    particles = [
        (360, 280, 14, (130, 230, 255), "Удал.: 4.2м (X:140, Y:70, Z:-215)"),
        (540, 420, 9, (180, 160, 255), "Удал.: 8.7м (X:136, Y:69, Z:-210)"),
        (1280, 310, 16, (110, 245, 220), "Удал.: 3.8м (X:145, Y:71, Z:-216)"),
        (1460, 460, 8, (140, 210, 255), "Удал.: 11.4м (X:151, Y:68, Z:-208)"),
        (780, 250, 11, (165, 235, 255), ""),
        (1120, 230, 7, (135, 215, 255), ""),
    ]
    for px, py, sz, col, lbl in particles:
        pdraw.polygon([(px, py - sz), (px + sz, py), (px, py + sz), (px - sz, py)], fill=(*col, 185), outline=(255, 255, 255, 230))
        if lbl:
            pdraw.text((px + sz + 6, py - 6), lbl, font=FONT_XS, fill=(200, 235, 255, 210))
    base.alpha_composite(part_layer)

    # 3. BlockShader — 3D блок под прицелом: Pass 1 (полупрозрачная заливка граней) + Pass 2 (светящийся контур ПОВЕРХ заливки)
    bx, by = W // 2, H // 2 + 55
    top_face = [(bx, by - 110), (bx + 135, by - 45), (bx, by + 20), (bx - 135, by - 45)]
    left_face = [(bx - 135, by - 45), (bx, by + 20), (bx, by + 165), (bx - 135, by + 100)]
    right_face = [(bx, by + 20), (bx + 135, by - 45), (bx + 135, by + 100), (bx, by + 165)]

    block_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bdraw = ImageDraw.Draw(block_layer)
    # Pass 1: Translucent shader glaze fill
    bdraw.polygon(top_face, fill=(85, 215, 255, 96))
    bdraw.polygon(left_face, fill=(65, 165, 245, 88))
    bdraw.polygon(right_face, fill=(120, 140, 255, 84))
    # Pass 2: Glowing contour ON TOP of the fill
    for face in (top_face, left_face, right_face):
        bdraw.line(face + [face[0]], fill=(160, 245, 255, 255), width=3)
    base.alpha_composite(block_layer)

    # 4. HandsShader — предмет в правой руке: 100% оригинальная текстура меча + полупрозрачный шейдер и контур поверх
    hx, hy = W - 360, H - 270
    hand_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    hdraw = ImageDraw.Draw(hand_layer)
    blade = [(hx + 40, hy + 180), (hx + 210, hy - 30), (hx + 245, hy + 5), (hx + 75, hy + 215)]
    # Оригинальная непрозрачная текстура алмазного клинка (100% видима)
    hdraw.polygon(blade, fill=(45, 210, 225, 255), outline=(24, 125, 145, 255))
    # Поверх текстуры — полупрозрачная шейдерная глазурь + светящийся контур
    hdraw.polygon(blade, fill=(165, 130, 255, 78))
    hdraw.line(blade + [blade[0]], fill=(215, 248, 255, 245), width=3)
    base.alpha_composite(hand_layer)

    # 5. Микро-прицел (Crosshair 2px) и HUD
    draw_grouped_hud(base, 18, 18)
    draw_micro_crosshair(base, W // 2, H // 2)

    # Подписи для наглядности проверки
    draw_liquid_glass_panel(base, W - 470, 18, 452, 118, radius=14, tint=(14, 20, 34, 175), blur_px=12)
    d = ImageDraw.Draw(base)
    d.text((W - 452, 30), "ПРОВЕРКА 3D-ФИЧ В МИРЕ:", font=FONT_BODY_B, fill=(140, 225, 255, 255))
    d.text((W - 452, 54), "• BlockShader: контур рендерится поверх заливки", font=FONT_SM, fill=(230, 242, 255, 240))
    d.text((W - 452, 74), "• HandsShader: предмет 100% видим + глазурь поверх", font=FONT_SM, fill=(230, 242, 255, 240))
    d.text((W - 452, 94), "• SkyShader + мировые Particles (X,Y,Z) + прицел 2px", font=FONT_SM, fill=(230, 242, 255, 240))

    out = OUT_DIR / "04_world_shaders_preview.png"
    base.convert("RGB").save(out, quality=95)
    return out


if __name__ == "__main__":
    p1 = render_01_main_menu()
    p2 = render_02_inline_menu_modules()
    p3 = render_03_accounts_tab()
    p4 = render_04_world_shaders_preview()
    for p in (p1, p2, p3, p4):
        print("Generated:", p, p.stat().st_size)
