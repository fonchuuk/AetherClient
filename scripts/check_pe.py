#!/usr/bin/env python3
"""Проверяет, что файл — настоящий Windows-исполняемый, а не переименованный jar.

Задача требует: «не переименовывай JAR в EXE». Проверка на глаз здесь не
работает — у zip и у PE разные начала, но оба могут открыться архиватором,
если к zip приписать заголовок. Поэтому разбираются именно поля PE:
сигнатуры, машина, число секций, подсистема и каталог ресурсов.

Запуск:  python3 scripts/check_pe.py файл1.exe файл2.exe …
Код возврата 1 — хотя бы один файл не PE.
"""

from __future__ import annotations

import struct
import sys

MZ = b"MZ"
PE_SIG = b"PE\x00\x00"

MACHINES = {0x014C: "i386", 0x8664: "x86-64", 0x01C0: "ARM", 0xAA64: "ARM64"}
SUBSYSTEMS = {1: "native", 2: "GUI", 3: "console", 7: "posix-console"}

# Индексы каталогов данных в необязательном заголовке.
RESOURCE_DIR = 2


def parse(path: str) -> list[str]:
    """Возвращает список претензий к файлу. Пустой список — файл в порядке."""
    problems: list[str] = []
    with open(path, "rb") as handle:
        data = handle.read()

    name = path.rsplit("/", 1)[-1]
    if len(data) < 0x40:
        return [f"{name}: файл короче заголовка DOS ({len(data)} байт)"]
    if data[:2] != MZ:
        problems.append(f"{name}: нет сигнатуры MZ — это не PE-файл")
        return problems

    # ZIP начинается с «PK\x03\x04»: переименованный jar провалился бы на MZ,
    # но jar, к которому приписали заголовок, доходит до этой проверки.
    if data[0x40:0x44] == b"PK\x03\x04":
        problems.append(f"{name}: сразу за заголовком идёт архив ZIP — похоже на jar")

    pe = struct.unpack_from("<I", data, 0x3C)[0]
    if data[pe:pe + 4] != PE_SIG:
        problems.append(f"{name}: нет сигнатуры PE по смещению {pe:#x}")
        return problems

    machine = struct.unpack_from("<H", data, pe + 4)[0]
    sections = struct.unpack_from("<H", data, pe + 6)[0]
    optional_size = struct.unpack_from("<H", data, pe + 20)[0]
    optional = pe + 24
    if machine not in MACHINES:
        problems.append(f"{name}: неизвестная машина {machine:#06x}")
    if not 1 <= sections <= 96:
        problems.append(f"{name}: подозрительное число секций: {sections}")

    magic = struct.unpack_from("<H", data, optional)[0]
    pe32_plus = magic == 0x20B
    if magic not in (0x10B, 0x20B):
        problems.append(f"{name}: неизвестная магия необязательного заголовка {magic:#06x}")
        return problems

    subsystem = struct.unpack_from("<H", data, optional + 68)[0]
    if subsystem not in SUBSYSTEMS:
        problems.append(f"{name}: неизвестная подсистема {subsystem}")

    # Каталог ресурсов: там иконка и сведения о версии файла. Без него
    # проводник Windows показывает пустую пиктограмму и «Нет данных».
    directory_offset = optional + (112 if pe32_plus else 96)
    resource_rva, resource_size = struct.unpack_from("<II", data,
                                                     directory_offset + RESOURCE_DIR * 8)
    if resource_rva == 0 or resource_size == 0:
        problems.append(f"{name}: нет каталога ресурсов — иконки и версии файла не будет")

    names = []
    section_table = optional + optional_size
    for index in range(sections):
        at = section_table + index * 40
        names.append(data[at:at + 8].rstrip(b"\0").decode("latin1", "replace"))
    if ".text" not in names:
        problems.append(f"{name}: нет секции кода .text ({' '.join(names)})")

    print(f"  {name}: PE{'32+' if pe32_plus else '32'} "
          f"{MACHINES.get(machine, hex(machine))}, "
          f"подсистема {SUBSYSTEMS.get(subsystem, subsystem)}, "
          f"секций {sections} ({' '.join(names)}), "
          f"ресурсы {resource_size} байт, всего {len(data)} байт")
    return problems


def main(argv: list[str]) -> int:
    if not argv:
        print("нужно указать хотя бы один файл", file=sys.stderr)
        return 2
    print("=== разбор заголовков PE ===")
    all_problems: list[str] = []
    for path in argv:
        all_problems.extend(parse(path))
    if all_problems:
        print("Проблемы:")
        for problem in all_problems:
            print("  " + problem)
        return 1
    print(f"Все файлы ({len(argv)}) — настоящие исполняемые Windows.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
