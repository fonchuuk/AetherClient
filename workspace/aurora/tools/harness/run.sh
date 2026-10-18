#!/usr/bin/env bash
# Запуск игры без монитора: Xvfb + программный OpenGL (llvmpipe).
# Нужен, чтобы убедиться, что клиент реально грузится и доходит до меню,
# а не просто «должен бы» по коду.
set -uo pipefail
cd /home/user/aurora

export JAVA_HOME=/home/user/tools/jdk21
export PATH="$JAVA_HOME/bin:$PATH"

# Экран: виртуальный монитор 854x480, 24 бита на пиксель.
if ! pgrep -x Xvfb >/dev/null; then
  Xvfb :99 -screen 0 854x480x24 >/tmp/xvfb.log 2>&1 &
  sleep 3
fi
echo "Xvfb pid: $(pgrep -x Xvfb | head -1)"

# Стенд компилируется против классов лаунчера — тех же, что и у игрока.
rm -rf /tmp/harness && mkdir -p /tmp/harness
javac -nowarn -cp launcher/build/classes/java/main:launcher/build/resources/main -d /tmp/harness \
  tools/harness/HeadlessRun.java 2>&1 | head -20
echo "=== стенд скомпилирован ==="
ls -la client/build/libs/

echo "=== ПОЕХАЛИ ==="
DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe \
  java -Xmx900m \
  -cp launcher/build/classes/java/main:launcher/build/resources/main:/tmp/harness \
  HeadlessRun build/harness-run 854 480 > /tmp/aurora-harness.log 2>&1
echo "=== игра вышла с кодом $? ==="
