#!/usr/bin/env bash
# Собирает кросс-компилятор launch4j в tools/launch4j.
#
# Зачем он нужен. Задача :launcher:exe и :installer:exe превращает jar в
# настоящий Windows-исполняемый файл: launch4j компилирует ресурсы через
# windres и линкует загрузчик через ld. В Maven-архиве launch4j лежат
# 32-битные сборки этих инструментов, которые на современной 64-битной
# системе просто не запускаются, а сценариев компоновщика там нет вовсе —
# линковка падает с «cannot open linker script file ldscripts/i386pe.x».
#
# Поэтому тулчейн собирается здесь из двух частей:
#   * объекты заголовков и импортные библиотеки Windows — из официального
#     дистрибутива launch4j (они 32-битные, i386 COFF, менять их нельзя);
#   * ld и windres — из binutils для цели i686-w64-mingw32, то есть
#     настоящие 64-битные программы, которые работают на современном Linux.
#
# В репозитории тулчейна нет: это ~10 МБ чужих двоичных файлов под GPL.
# Скрипт идемпотентен — повторный запуск ничего не ломает.
#
# Запуск:  bash tools/setup-launch4j.sh
set -euo pipefail

cd "$(dirname "$0")/.."          # каталог aurora/

VERSION="3.50"
DIST_URL="https://master.dl.sourceforge.net/project/launch4j/launch4j-3/${VERSION}/launch4j-${VERSION}-linux-x64.tgz?viasf=1"
DEST="tools/launch4j"
WORK="${TMPDIR:-/tmp}/launch4j-setup-$$"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "нужна команда $1" >&2
    exit 1
  }
}
need curl
need tar

# ── 1. ld и windres для цели i386pe ─────────────────────────────────
# Ищем уже установленный кросс-бинutils; если его нет, подсказываем пакет.
BINUTILS=""
for candidate in i686-w64-mingw32-ld i686-w64-mingw32-windres; do
  command -v "$candidate" >/dev/null 2>&1 || BINUTILS="missing"
done
if [ "$BINUTILS" = "missing" ]; then
  echo "Не найден кросс-компилятор для цели i686-w64-mingw32." >&2
  echo "Debian/Ubuntu:  sudo apt-get install -y binutils-mingw-w64-i686" >&2
  echo "Fedora:         sudo dnf install -y mingw32-binutils" >&2
  exit 1
fi

# Сценарии компоновщика лежат рядом с целевым префиксом binutils.
LDSCRIPTS=""
for dir in /usr/i686-w64-mingw32/lib/ldscripts /usr/lib/i686-w64-mingw32/lib/ldscripts; do
  if [ -f "$dir/i386pe.x" ]; then
    LDSCRIPTS="$dir"
    break
  fi
done
if [ -z "$LDSCRIPTS" ]; then
  echo "Не найден ldscripts/i386pe.x — пакет binutils для i686-w64-mingw32 установлен не полностью." >&2
  exit 1
fi

# ── 2. объекты и библиотеки из дистрибутива launch4j ────────────────
mkdir -p "$WORK"
trap 'rm -rf "$WORK"' EXIT

echo "→ дистрибутив launch4j $VERSION"
curl -fsSL --retry 3 -o "$WORK/dist.tgz" "$DIST_URL"
tar -xzf "$WORK/dist.tgz" -C "$WORK"
DIST="$WORK/launch4j"
[ -f "$DIST/head/head.o" ] || { echo "в архиве нет head/head.o" >&2; exit 1; }
[ -f "$DIST/w32api/crt2.o" ] || { echo "в архиве нет w32api/crt2.o" >&2; exit 1; }

echo "→ собираю $DEST"
mkdir -p "$DEST/bin" "$DEST/head" "$DEST/w32api" "$DEST/ldscripts"

cp "$DIST/head/"*.o "$DIST/head/LICENSE.txt" "$DEST/head/"
cp "$DIST"/w32api/*.o "$DIST"/w32api/*.a "$DEST/w32api/"
cp "$DIST/w32api/MinGW.LICENSE.txt" "$DEST/w32api/" 2>/dev/null || true
cp "$DIST/bin/COPYING" "$DEST/bin/" 2>/dev/null || true
cp "$LDSCRIPTS/i386pe."* "$DEST/ldscripts/"

# Настоящие 64-битные инструменты — под именами, которые ждёт launch4j.
cp "$(command -v i686-w64-mingw32-ld)"      "$DEST/bin/ld.real"
cp "$(command -v i686-w64-mingw32-windres)" "$DEST/bin/windres"
chmod 755 "$DEST/bin/ld.real" "$DEST/bin/windres"

# launch4j вызывает «ld -mi386pe …» из своего каталога и ищет сценарии
# по пути, зашитому в binutils при сборке пакета. Передаём свой каталог.
cat > "$DEST/bin/ld" <<'EOF'
#!/bin/sh
# Обёртка: launch4j вызывает «ld -mi386pe …», а сценарии компоновщика лежат рядом.
exec "$(dirname "$0")/ld.real" -L"$(dirname "$0")/../ldscripts" "$@"
EOF
chmod 755 "$DEST/bin/ld"

# ── 3. проверка, что всё на месте ───────────────────────────────────
echo "→ проверка"
"$DEST/bin/ld" --version | head -1
"$DEST/bin/windres" --version | head -1
for file in head/guihead.o head/consolehead.o head/head.o w32api/crt2.o \
            ldscripts/i386pe.x; do
  [ -f "$DEST/$file" ] || { echo "не хватает $file" >&2; exit 1; }
done
for lib in mingw32 mingwex gcc msvcrt moldname kernel32 user32 advapi32 shell32; do
  [ -f "$DEST/w32api/lib$lib.a" ] || { echo "не хватает w32api/lib$lib.a" >&2; exit 1; }
done

echo "готово: $DEST"
echo "дальше:  ./gradlew :launcher:exe :installer:exe"
