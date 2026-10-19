#!/usr/bin/env bash
# Полный цикл сборки: JDK 21 → мод → лаунчер и установщик → нативный exe.
#
# Зачем отдельный скрипт, если есть Gradle: песочница не держит между
# сессиями ни JDK (207 МБ), ни каталог build/, поэтому цикл приходится
# повторять целиком. Скрипт идемпотентен: что уже стоит, то не качается.
#
# Все пути считаются от расположения самого скрипта — раньше здесь был
# жёстко вписан /home/user/aurora, и на любом другомcheckout скрипт падал.
#
# Запуск:  bash tools/rebuild.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # каталог репозитория
TOOLS="${TOOLS_DIR:-$HOME/tools}"                        # куда складывать тулчейны
AURORA="$ROOT/aurora"

echo "репозиторий: $ROOT"
echo "тулчейны:    $TOOLS"

# ── 1/5 JDK 21 ──────────────────────────────────────────────────────
echo "=== 1/5 JDK 21 ==="
# Проверяем не наличие файла, а что JDK действительно работает: снимок
# воркспейса обрезает большие файлы, оставляя структуру каталогов, и тогда
# javac есть на диске, но падает без libjli.so.
if ! "$TOOLS/jdk21/bin/javac" -version >/dev/null 2>&1; then
  rm -rf "$TOOLS/jdk21"
  mkdir -p "$TOOLS"
  curl -fsSL --retry 3 -o /tmp/jdk21.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p "$TOOLS/jdk21"
  tar -xzf /tmp/jdk21.tar.gz -C "$TOOLS/jdk21" --strip-components=1
  rm -f /tmp/jdk21.tar.gz
fi
export JAVA_HOME="$TOOLS/jdk21"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.cache/gradle}"
export PATH="$JAVA_HOME/bin:$PATH"
javac -version

# ── 2/5 Go 1.23 ─────────────────────────────────────────────────────
echo "=== 2/5 Go ==="
if ! "$TOOLS/go/bin/go" version >/dev/null 2>&1; then
  curl -fsSL --retry 3 https://go.dev/dl/go1.23.6.linux-amd64.tar.gz | tar -xz -C "$TOOLS"
fi
export PATH="$TOOLS/go/bin:$PATH"
export GOPATH="${GOPATH:-$HOME/.cache/gopath}"
export GOCACHE="${GOCACHE:-$HOME/.cache/go-build}"
export GOTOOLCHAIN=local
go version

# ── 3/5 кросс-компилятор launch4j ───────────────────────────────────
echo "=== 3/5 launch4j ==="
cd "$AURORA"
if [ ! -f tools/launch4j/ldscripts/i386pe.x ]; then
  bash tools/setup-launch4j.sh
else
  echo "тулчейн уже собран: tools/launch4j"
fi

# ── 4/5 мод, лаунчер и установщик ───────────────────────────────────
echo "=== 4/5 gradle ==="
python3 "$AURORA/tools/make_menu_assets.py"
python3 "$AURORA/tools/gen_particle_textures.py"
chmod +x gradlew
./gradlew --no-daemon --max-workers=1 --console=plain \
  test :client:remapJar :launcher:exe :installer:exe dist 2>&1 | tail -20
JAR="$AURORA/client/build/libs/AetherGlass.jar"
[ -f "$JAR" ] || { echo "не собран $JAR" >&2; exit 1; }
echo "мод: $JAR ($(stat -c%s "$JAR") байт)"
echo "текстур меню внутри: $(unzip -l "$JAR" | grep -c 'textures/gui/.*\.png')"

# ── 5/5 нативный лаунчер ────────────────────────────────────────────
echo "=== 5/5 нативный лаунчер ==="
# Мод вшивается в exe на этапе компиляции, поэтому копия обязана быть свежей:
# иначе получится рабочий лаунчер со старым модом внутри, и половину вечера
# уйдёт на поиски «почему не починилось».
mkdir -p launcher-go/core/assets
cp -f "$JAR" launcher-go/core/assets/AetherGlass.jar
cd launcher-go
bash ./build.sh 2>&1 | tail -8

echo
echo "=== ГОТОВО ==="
cp -f "$AURORA/dist/AetherGlass.jar" "$ROOT/AetherGlass.jar"
cp -f "$AURORA/dist/AetherGlassLauncher.jar" "$ROOT/AetherGlassLauncher.jar"
cp -f "$AURORA/dist/AetherGlassLauncher.exe" "$ROOT/AetherGlassLauncher.exe"
cp -f "$AURORA/dist/AetherGlass-Setup.exe" "$ROOT/AetherGlass-Setup.exe"
(cd "$ROOT" && sha256sum AetherGlass.jar AetherGlassLauncher.exe AetherGlassLauncher.jar AetherGlass-Setup.exe AetherGlass-Launcher.exe > CHECKSUMS.txt)
cat "$ROOT/CHECKSUMS.txt"
python3 "$ROOT/scripts/check_pe.py" \
  "$AURORA/dist/AetherGlassLauncher.exe" \
  "$AURORA/dist/AetherGlass-Setup.exe" \
  "$ROOT/AetherGlass-Launcher.exe"
