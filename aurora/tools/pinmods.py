#!/usr/bin/env python3
"""Формирует манифест модов launcher/src/main/resources/mods.json.

Данные берутся из официального API Modrinth: для каждого проекта находится сборка
под Minecraft 1.21.11 и Fabric, совпадающая с зафиксированной версией. Ничего не
придумывается — в манифест попадают только настоящие имя файла, размер и хэши.

Запуск:  python3 tools/pinmods.py
"""

import json
import urllib.parse
import urllib.request
from collections import OrderedDict

MINECRAFT = "1.21.11"
API = "https://api.modrinth.com/v2"

# slug проекта -> (зафиксированная версия, назначение на русском)
PINS = [
    ("sodium", "mc1.21.11-0.8.14-fabric",
     "Оптимизация конвейера рендеринга чанков, отсечение невидимой геометрии, снижение нагрузки на GPU/CPU"),
    ("lithium", "mc1.21.11-0.21.4-fabric",
     "Оптимизация игровой логики: физика, ИИ, блок-энтити, хэширование, поиск пути"),
    ("immediatelyfast", "1.14.3+1.21.11-fabric",
     "Пакетная отрисовка immediate-режима: HUD, текст, экраны, частицы"),
    ("entityculling", "1.11.3",
     "Отсечение сущностей, скрытых за блоками (асинхронный рейкаст)"),
    ("ferrite-core", "8.2.0-fabric",
     "Снижение потребления памяти: блок-стейты, модели, чанки"),
    ("badoptimizations", "2.4.1",
     "Точечные оптимизации ванильного кода, безопасные по геймплею"),
    ("scalablelux", "0.1.6+fabric.c25518a",
     "Многопоточный движок освещения (быстрее пересчёт света)"),
    ("c2me-fabric", "0.3.6.0.0",
     "Многопоточная генерация, загрузка и сохранение чанков (в т.ч. integrated-сервер)"),
    ("krypton", "0.2.10",
     "Оптимизация сетевого стека (Netty, сжатие, криптография)"),
    ("dynamic-fps", "3.11.6",
     "Снижение нагрузки при неактивном окне и ограничение лишних кадров"),
]

FABRIC_API = ("fabric-api", "0.141.6+1.21.11",
              "Библиотека API Fabric (доступ к событиям и реестрам игры). "
              "Обязательна для части модов, в список из 10 оптимизаций не входит")


def get_json(url: str):
    request = urllib.request.Request(url, headers={"User-Agent": "aurora-launcher/pinmods"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def versions(slug: str):
    query = urllib.parse.urlencode({
        "game_versions": json.dumps([MINECRAFT]),
        "loaders": json.dumps(["fabric"]),
    })
    return get_json(f"{API}/project/{slug}/version?{query}")


def pick(slug: str, pinned: str):
    for version in versions(slug):
        number = version.get("version_number", "")
        names = [f["filename"] for f in version.get("files", [])]
        if pinned == number or pinned in number or any(pinned in name for name in names):
            return version
    raise SystemExit(f"не найдена сборка {slug} {pinned} под {MINECRAFT}")


_SLUGS: dict[str, str] = {}


def project_slug(project_id: str) -> str:
    """Идентификатор проекта Modrinth → его слаг (короткое имя).

    Зачем: в зависимостях версии лежат внутренние идентификаторы вроде ``P7dR8mSH``,
    а в манифесте моды записаны слагами. Проверка зависимостей сравнивает их напрямую,
    поэтому и зависимости тоже приводятся к слаг.
    """
    if not project_id:
        return ""
    if project_id not in _SLUGS:
        _SLUGS[project_id] = api(f"/project/{project_id}")["slug"]
    return _SLUGS[project_id]


def entry(slug: str, version: dict, purpose: str):
    # Основной файл — тот, который не является «sources» и не требует зависимостей-модов.
    files = [f for f in version["files"] if not f["filename"].endswith("-sources.jar")]
    primary = files[0]
    return OrderedDict([
        ("id", slug),
        ("name", version.get("name", slug)),
        ("version", version.get("version_number", "?")),
        ("date", version.get("date_published", "")[:10]),
        ("purpose", purpose),
        ("environment", version.get("loaders", ["fabric"])[0] if version.get("loaders") else "fabric"),
        ("file", OrderedDict([
            ("name", primary["filename"]),
            ("url", primary["url"]),
            ("size", primary["size"]),
            ("sha1", primary["hashes"].get("sha1", "")),
            ("sha512", primary["hashes"]["sha512"]),
        ])),
        # Зависимости записываются слагами, а не внутренними идентификаторами проектов:
        # в манифесте моды тоже указаны слагами, и проверка зависимостей сравнивает их напрямую.
        ("dependencies", [project_slug(d.get("project_id")) for d in version.get("dependencies", [])
                          if d.get("dependency_type") == "required"]),
        ("project_url", f"https://modrinth.com/mod/{slug}"),
    ])


def main() -> None:
    mods = [entry(slug, pick(slug, pinned), purpose) for slug, pinned, purpose in PINS]
    slug, pinned, purpose = FABRIC_API
    manifest = OrderedDict([
        ("minecraft_version", MINECRAFT),
        ("loader", "fabric"),
        ("generated_check", "modrinth-api-v2"),
        ("fabric_api_required_by", ["entityculling", "dynamic-fps"]),
        ("fabric_api", entry(slug, pick(slug, pinned), purpose)),
        ("mods", mods),
    ])
    path = "launcher/src/main/resources/mods.json"
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"записано {path}: модов {len(mods)}")
    for mod in mods:
        print(f"  {mod['id']:16} {mod['version']:28} {mod['file']['name']}")


if __name__ == "__main__":
    main()
