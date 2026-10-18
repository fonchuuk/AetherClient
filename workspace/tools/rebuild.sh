#!/usr/bin/env bash
# Полный цикл: JDK 21 -> сборка мода -> встройка в ресурсы лаунчера -> exe.
# Песочница не держит между сессиями ни JDK (207 МБ), ни build/ (он в списке
# исключений снимка), поэтому цикл приходится повторять целиком.
set -uo pipefail
cd /home/user/aurora

echo "=== 1/4 JDK 21 ==="
# Проверяем не наличие файла, а то, что JDK действительно работает:
# снимок воркспейса обрезает большие файлы, оставляя структуру каталогов,
# и тогда javac есть на диске, но падает без libjli.so.
if ! /home/user/tools/jdk21/bin/javac -version >/dev/null 2>&1; then
  rm -rf /home/user/tools/jdk21
  mkdir -p /home/user/tools
  curl -fsSL --retry 3 -o /tmp/jdk21.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p /home/user/tools/jdk21
  tar -xzf /tmp/jdk21.tar.gz -C /home/user/tools/jdk21 --strip-components=1
  rm -f /tmp/jdk21.tar.gz
fi
export JAVA_HOME=/home/user/tools/jdk21
export GRADLE_USER_HOME=/home/user/.cache/gradle
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/javac" -version

echo "=== 2/4 сборка мода ==="
chmod +x gradlew
./gradlew --configure-on-demand :client:remapJar :launcher:classes \
  --console=plain -Dorg.gradle.jvmargs="-Xmx1100m" 2>&1 | tail -25
JAR=$(ls client/build/libs/*.jar 2>/dev/null | grep -v sources | head -1)
echo "собран: $JAR ($(stat -c%s "$JAR" 2>/dev/null) байт)"

echo "=== 3/4 встройка мода в лаунчер ==="
cp -f "$JAR" launcher-go/core/assets/AetherGlass.jar
ls -la launcher-go/core/assets/AetherGlass.jar
unzip -l launcher-go/core/assets/AetherGlass.jar | grep -c "textures/gui"

echo "=== 4/4 exe ==="
export PATH=/home/user/tools/go/bin:$PATH
export GOPATH=/home/user/.cache/gopath GOCACHE=/home/user/.cache/go-build
export GOTOOLCHAIN=local GOFLAGS=-mod=mod
if [ ! -x /home/user/tools/go/bin/go ]; then
  curl -fsSL --retry 3 https://go.dev/dl/go1.23.6.linux-amd64.tar.gz | tar -xz -C /home/user/tools
fi
cd launcher-go
bash ./build.sh 2>&1 | tail -10
ls -la /home/user/AetherGlass-Launcher.exe
sha256sum /home/user/AetherGlass-Launcher.exe
echo "=== ГОТОВО ==="
