#!/usr/bin/env python3
"""Ступени размытия для фонов главного меню.

Идея: размытие считается не в игре, а заранее — здесь, на этапе сборки.
В игре остаётся только плавно сводить две соседние ступени, что стоит
ровно два вызова отрисовки и ничего не стоит по процессору.

Побочная выгода: размытые ступени хранятся в меньшем разрешении. Ступень 3
— это 172×96 пикселей, растянутая на весь экран. На таком размытии потеря
разрешения не видна, а места файл занимает в семьдесят раз меньше полного.
"""

import os
from PIL import Image, ImageFilter

SRC = "/home/user/aurora/client/src/main/resources/assets/aetherglass/textures/gui"
OUT = SRC

# (индекс, масштаб, радиус размытия)
# Радиус задан в пикселях уже уменьшенной картинки: ступень уменьшена
# вдвое, значит радиус 3 на ней выглядит как 6 на исходной.
LEVELS = (
    (0, 1 / 1, 1.2),
    (1, 1 / 2, 2.6),
    (2, 1 / 4, 3.4),
    (3, 1 / 8, 4.2),
)

NAMES = ("menu_backdrop", "menu_backdrop_aurora", "menu_backdrop_glass", "menu_backdrop_night")


def main():
    total_new = 0
    for name in NAMES:
        path = f"{SRC}/{name}.png"
        if not os.path.exists(path):
            print(f"  ! нет файла {name}.png — пропуск")
            continue
        src = Image.open(path).convert("RGB")
        for index, scale, radius in LEVELS:
            if index == 0:
                im = src.filter(ImageFilter.GaussianBlur(radius))
            else:
                w = max(1, round(src.width * scale))
                h = max(1, round(src.height * scale))
                # Сначала уменьшаем, потом размываем: так размытие
                # размазывает именно то, что видно после растяжения.
                im = src.resize((w, h), Image.BOX).filter(ImageFilter.GaussianBlur(radius))
            out = f"{OUT}/{name}_b{index}.png"
            im.save(out, optimize=True)
            total_new += os.path.getsize(out)
            print(f"  {os.path.basename(out):38s} {im.width:5d}x{im.height:<4d} "
                  f"{os.path.getsize(out)/1024:7.0f} КБ")
    print(f"\nИтого ступеней: {len(NAMES)*len(LEVELS)} файлов, {total_new/1024/1024:.2f} МБ")


if __name__ == "__main__":
    main()
