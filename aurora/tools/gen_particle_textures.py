#!/usr/bin/env python3
"""Генератор текстур частиц клиента Aurora.

Создаёт четыре спрайта (круг, квадрат, звезда, кольцо) с мягкими краями в формате
RGBA PNG. Текстуры нужны потому, что частицы рисуются обычными спрайтами: без
альфа-канала нельзя получить плавное затухание.

Запуск:  python3 tools/gen_particle_textures.py
Результат: client/src/main/resources/assets/aetherglass/textures/particle/*.png
"""

import math
import os
import struct
import zlib

SIZE = 64


def write_png(path: str, pixels: bytearray) -> None:
    """Сохраняет RGBA-массив в PNG без внешних зависимостей."""
    raw = bytearray()
    stride = SIZE * 4
    for y in range(SIZE):
        raw.append(0)  # фильтр None для каждой строки
        raw.extend(pixels[y * stride:(y + 1) * stride])

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0)
    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', header)
           + chunk(b'IDAT', zlib.compress(bytes(raw), 9))
           + chunk(b'IEND', b''))
    with open(path, 'wb') as handle:
        handle.write(png)


def smoothstep(edge0: float, edge1: float, x: float) -> float:
    t = max(0.0, min(1.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)


def circle(x: float, y: float) -> float:
    d = math.hypot(x, y)
    return 1.0 - smoothstep(0.55, 1.0, d)


def square(x: float, y: float) -> float:
    d = max(abs(x), abs(y))
    # Слегка скруглённые углы: смесь квадрата и круга.
    return (1.0 - smoothstep(0.62, 1.0, d)) * (0.55 + 0.45 * (1.0 - smoothstep(0.2, 1.0, math.hypot(x, y))))


def star(x: float, y: float) -> float:
    angle = math.atan2(y, x)
    radius = math.hypot(x, y)
    spikes = 4.0
    core = 0.28 + 0.72 * abs(math.cos(angle * spikes / 2.0)) ** 6
    return (1.0 - smoothstep(0.45, 1.0, radius)) * core


def ring(x: float, y: float) -> float:
    radius = math.hypot(x, y)
    return (1.0 - smoothstep(0.55, 0.85, abs(radius - 0.62))) * (1.0 - smoothstep(0.85, 1.0, radius))


def heart(x: float, y: float) -> float:
    # Классическое сердце: (x² + y² − 1)³ − x²·y³ < 0
    a = x * x + y * y - 0.75
    value = a * a * a - x * x * (y ** 3) * 1.6
    return 1.0 - smoothstep(-0.35, 0.0, value)


def spark(x: float, y: float) -> float:
    radius = math.hypot(x, y)
    angle = math.atan2(y, x)
    cross = max(abs(x), abs(y))
    spike = 1.0 - smoothstep(0.05, 0.42, cross * (0.35 + 0.65 * abs(math.cos(angle * 2.0))))
    core = 1.0 - smoothstep(0.0, 0.30, radius)
    return max(spike * (1.0 - smoothstep(0.35, 0.95, radius)), core)


def white(x: float, y: float) -> float:
    # Полностью белый спрайт: подложка для цветной геометрии (свечения, контуры).
    return 1.0


SHAPES = {
    'circle': circle,
    'square': square,
    'star': star,
    'ring': ring,
    'heart': heart,
    'spark': spark,
    'white': white,
}


def main() -> None:
    out_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                           'client', 'src', 'main', 'resources', 'assets',
                           'aetherglass', 'textures', 'particle')
    os.makedirs(out_dir, exist_ok=True)
    for name, function in SHAPES.items():
        pixels = bytearray(SIZE * SIZE * 4)
        for py in range(SIZE):
            for px in range(SIZE):
                # Нормированные координаты в диапазоне [-1, 1] с центром в середине спрайта.
                nx = (px + 0.5) / SIZE * 2.0 - 1.0
                ny = (py + 0.5) / SIZE * 2.0 - 1.0
                value = max(0.0, min(1.0, function(nx, ny)))
                alpha = int(round(255 * value))
                offset = (py * SIZE + px) * 4
                pixels[offset] = 255
                pixels[offset + 1] = 255
                pixels[offset + 2] = 255
                pixels[offset + 3] = alpha
        path = os.path.join(out_dir, name + '.png')
        write_png(path, pixels)
        print('создано', path, os.path.getsize(path), 'байт')


if __name__ == '__main__':
    main()
