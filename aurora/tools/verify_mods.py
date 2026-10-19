#!/usr/bin/env python3
"""Проверка манифеста модов по живому API Modrinth.

Зачем: версии и контрольные суммы в `launcher/src/main/resources/mods.json`
не должны устаревать незаметно. Скрипт для каждой записи:

  1. запрашивает список версий проекта с фильтром game_versions=["1.21.11"],
     loaders=["fabric"];
  2. находит версию, номер которой совпадает с записанной в манифесте;
  3. сверяет имя файла, URL, размер, SHA-1 и SHA-512;
  4. проверяет, что файл действительно доступен (HTTP HEAD).

Ничего не скачивает целиком и ничего не меняет. Завершается с кодом 1,
если хоть одна запись не совпала.

Запуск:  python3 tools/verify_mods.py
"""

from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.modrinth.com/v2"
GAME_VERSION = "1.21.11"
LOADER = "fabric"
MANIFEST = Path(__file__).resolve().parent.parent / "launcher" / "src" / "main" / "resources" / "mods.json"
TIMEOUT = 30


def get_json(url: str) -> object:
    request = urllib.request.Request(url, headers={"User-Agent": "aetherglass/verify-mods"})
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return json.load(response)


def head_ok(url: str) -> tuple[int, int]:
    request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "aetherglass/verify-mods"})
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            return response.status, int(response.headers.get("Content-Length", "0") or 0)
    except urllib.error.HTTPError as error:
        return error.code, 0


def main() -> int:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    mods = manifest["mods"]
    failures = 0

    print(f"Проверяется {len(mods)} модов для Minecraft {GAME_VERSION} ({LOADER})")
    print("-" * 78)

    for index, mod in enumerate(mods, start=1):
        mod_id = mod["id"]
        pinned = mod["version"]
        url = f"{API}/project/{mod_id}/version?game_versions=%5B%22{GAME_VERSION}%22%5D&loaders=%5B%22{LOADER}%22%5D"
        try:
            versions = get_json(url)
        except Exception as error:  # сеть недоступна — это отдельная ситуация
            print(f"{index:>2}. {mod_id:<18} ОШИБКА запроса: {error}")
            failures += 1
            continue
        if not isinstance(versions, list) or not versions:
            print(f"{index:>2}. {mod_id:<18} НЕТ сборки под {GAME_VERSION} для {LOADER}")
            failures += 1
            continue

        match = next((v for v in versions if v.get("version_number") == pinned), None)
        if match is None:
            available = ", ".join(v.get("version_number", "?") for v in versions[:5])
            print(f"{index:>2}. {mod_id:<18} версия {pinned} не найдена. Есть: {available}")
            failures += 1
            continue

        file = next((f for f in match.get("files", []) if f.get("filename") == mod["file"]["name"]), None)
        if file is None:
            file = match.get("files", [{}])[0]
        problems = []
        if file.get("url") != mod["file"]["url"]:
            problems.append("URL не совпадает")
        if file.get("size") != mod["file"]["size"]:
            problems.append(f"размер {file.get('size')} != {mod['file']['size']}")
        for algorithm in ("sha1", "sha512"):
            actual = (file.get("hashes") or {}).get(algorithm)
            if actual and actual != mod["file"][algorithm]:
                problems.append(f"{algorithm} не совпадает")

        status, length = head_ok(mod["file"]["url"])
        if status != 200:
            problems.append(f"файл недоступен (HTTP {status})")

        if problems:
            failures += 1
            print(f"{index:>2}. {mod_id:<18} {pinned:<28} ПЛОХО: {'; '.join(problems)}")
        else:
            print(f"{index:>2}. {mod_id:<18} {pinned:<28} ок  "
                  f"{mod['file']['name']} ({length or mod['file']['size']} байт)")

    print("-" * 78)
    fabric_api = manifest.get("fabric_api", {})
    if fabric_api:
        print(f"Отдельно: Fabric API {fabric_api.get('version')} (в десятку оптимизаций не входит)")

    if failures:
        print(f"Провалено проверок: {failures}. Манифест нужно обновить.")
        return 1
    print("Все записи манифеста совпадают с живым API.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
