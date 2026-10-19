#!/usr/bin/env bash
# Прогон игры до самого мира: меню -> одиночная игра -> новый мир.
# Нужен, чтобы эффекты (частицы, рука, блок, небо) вообще начали вызываться:
# в главном меню события отрисовки мира не приходят, поэтому вылет там не поймать.
#
# Управление — клавиатурой: Tab переводит фокус, Enter нажимает. Состояние
# определяется не «на глаз», а по журналу: игра сама пишет, когда дошла до
# создания мира и подготовки спавна.
set -uo pipefail
cd /home/user/aurora

export JAVA_HOME=/home/user/tools/jdk21
export PATH="$JAVA_HOME/bin:$PATH"
export DISPLAY=:99
LOG=/tmp/intoworld.log
: > "$LOG"

if ! pgrep -x Xvfb >/dev/null; then
  Xvfb :99 -screen 0 854x480x24 >/tmp/xvfb.log 2>&1 &
  sleep 3
fi

rm -rf /tmp/harness && mkdir -p /tmp/harness
javac -nowarn -encoding UTF-8 -cp launcher/build/classes/java/main:launcher/build/resources/main \
  -d /tmp/harness tools/harness/HeadlessRun.java 2>&1 | head -10

echo "[драйвер] запускаю игру" | tee -a "$LOG"
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe \
  java -Xmx900m \
  -cp launcher/build/classes/java/main:launcher/build/resources/main:/tmp/harness \
  HeadlessRun build/harness-run 854 480 > "$LOG" 2>&1 &
GAME=$!

# Ждём главного меню: стенд печатает READY, когда дошёл.
echo "[драйвер] жду главного меню" | tee -a "$LOG"
for _ in $(seq 1 120); do
  grep -q "READY" "$LOG" 2>/dev/null && break
  kill -0 "$GAME" 2>/dev/null || break
  sleep 5
done
grep -q "READY" "$LOG" && echo "[драйвер] меню на экране" | tee -a "$LOG" \
  || { echo "[драйвер] меню не дождались"; tail -20 "$LOG"; kill -9 "$GAME"; exit 1; }

# Ищем окно игры и переключаем фокус на него.
WIN=""
for _ in $(seq 1 30); do
  WIN=$(xdotool search --onlyvisible --name "Minecraft" 2>/dev/null | head -1)
  [ -n "$WIN" ] && break
  sleep 2
done
echo "[драйвер] окно: '${WIN:-не найдено}'" | tee -a "$LOG"
[ -n "$WIN" ] && xdotool windowfocus --sync "$WIN"

press() {  # $1 = что нажимаем, $2 = сколько ждём
  [ -n "$WIN" ] && xdotool windowfocus --sync "$WIN" 2>/dev/null
  xdotool key --clearmodifiers "$1" 2>/dev/null
  sleep "${2:-3}"
}

# Продвигаемся к миру. После каждого шага проверяем журнал: как только игра
# заговорила про создание мира, дальнейшие нажатия уже не нужны.
reached() { grep -qa "Preparing start region\|Creating world\|Started .*%\|Loaded .*entities" "$LOG"; }

for step in 1 2 3 4 5 6 7 8 9 10; do
  reached && { echo "[драйвер] мир пошёл готовиться на шаге $step" | tee -a "$LOG"; break; }
  press Tab 1
  press Return 4
  echo "[драйвер] шаг $step: Tab+Enter" >> "$LOG"
done

# Даём миру время на создание и несколько секунд реальной отрисовки.
sleep 120

echo "[драйвер] останавливаю" | tee -a "$LOG"
kill -9 "$GAME" 2>/dev/null
sleep 2

echo "=== ИТОГ ==="
if grep -qa "Preparing start region\|Started .*%" "$LOG"; then
  echo "МИР СОЗДАН И ГРУЗИЛСЯ"
else
  echo "ДО МИРА НЕ ДОШЛИ"
fi
echo "--- вылеты AetherGlass ---"
grep -a -i "aetherglass" "$LOG" | grep -a -i "error\|exception\|caused by" | head -10 || echo "  нет"
echo "--- последние строки ---"
grep -v "at knot//\|at java.base/" "$LOG" | tail -15 | cut -c1-200
