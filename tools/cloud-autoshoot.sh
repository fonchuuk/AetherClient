#!/usr/bin/env bash
# Облачный стенд авто-скриншотов AetherGlass (без участия ПК пользователя).
# Все сгенерированные скриншоты, состояния и логи выгружаются СТРОГО в приватный репозиторий:
# fonchuuk/WorcspasePrivateArena
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

python3 "$REPO_ROOT/tools/render_ui_screenshots.py"

mkdir -p "$REPO_ROOT/bridge/telemetry"
cp -f "$REPO_ROOT/screenshots/"*.png "$REPO_ROOT/bridge/telemetry/"

JAR_SHA="$(sha256sum "$REPO_ROOT/AetherGlass.jar" | awk '{print $1}')"
JAR_BLOB="$(git hash-object "$REPO_ROOT/AetherGlass.jar")"
BUILD_ID="$(date -u +%Y%m%d-%H%M%S)"

cat <<JSON > "$REPO_ROOT/bridge/state.json"
{
  "build_id": "${BUILD_ID}",
  "mc_version": "1.21.11",
  "jar_sha256": "${JAR_SHA}",
  "jar_git_blob": "${JAR_BLOB}",
  "screenshots": [
    "screenshots/01_main_menu_frosted_glass.png",
    "screenshots/02_right_shift_modules_compact_sliders.png",
    "screenshots/03_right_shift_accounts_tab.png",
    "screenshots/04_world_shaders_preview.png"
  ],
  "target_repository": "fonchuuk/WorcspasePrivateArena"
}
JSON

cat <<JSON > "$REPO_ROOT/bridge/command.json"
{
  "id": "cmd-${BUILD_ID}",
  "action": "sync_and_capture",
  "restart_client": false,
  "upload_target": "fonchuuk/WorcspasePrivateArena"
}
JSON

PRIVATE_ONLY=1 bash "$REPO_ROOT/tools/push-private.sh"
