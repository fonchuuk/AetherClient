#!/usr/bin/env bash
# Сборка нативного лаунчера под Windows (можно собирать и из Linux).
set -euo pipefail

cd "$(dirname "$0")"

export CGO_ENABLED=0
export GOTOOLCHAIN=local

# Запоминаем машину сборщика: инструменты вроде rsrc должны работать здесь.
hostOS="$(go env GOHOSTOS)"
hostArch="$(go env GOHOSTARCH)"

# Тесты запускаются на этой машине: собранный под Windows проверочный файл
# здесь не выполнить, поэтому цель переключается только перед сборкой.
echo "→ логика: тесты"
go test ./core -count=1

echo "→ проверка кода"
go vet ./...
GOOS=windows GOARCH=amd64 go vet ./...

# Кросc-компиляция: никакой Java, никаких jar, готовый exe для x86-64 Windows.
export GOOS=windows
export GOARCH=amd64

echo "→ иконка и манифест Windows"
# rsrc — инструмент, который запускается ЭТОЙ машиной, поэтому его нельзя
# собирать под Windows: собираем под хозяина, а не под цель.
export PATH="$(go env GOPATH)/bin:$PATH"
if ! command -v rsrc >/dev/null 2>&1; then
  GOOS="$hostOS" GOARCH="$hostArch" go install github.com/akavel/rsrc@v0.10.2
fi
rsrc -manifest assets/launcher.exe.manifest -ico assets/launcher.ico -o launcher.syso

echo "→ exe"
# -trimpath убирает из файла пути сборочной машины, -s -w — таблицу символов.
go build -trimpath -ldflags="-H windowsgui -s -w" -o ../../AetherGlass-Launcher.exe .

echo "готово:"
ls -la ../../AetherGlass-Launcher.exe
file ../../AetherGlass-Launcher.exe
sha256sum ../../AetherGlass-Launcher.exe
