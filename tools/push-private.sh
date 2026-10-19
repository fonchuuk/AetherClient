#!/usr/bin/env bash
# Пайплайн выгрузки воркспейса, скриншотов, моста и телеметрии СТРОГО в приватный репозиторий
# fonchuuk/WorcspasePrivateArena (без хранения токенов в .git/config или /home/user).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

TOK_PRIV="${GITHUB_PRIVATE_TOKEN:-}"
if [ -z "$TOK_PRIV" ] && [ -f "/var/tmp/.tok_private" ]; then
  TOK_PRIV="$(cat /var/tmp/.tok_private)"
fi

TOK_REL="${GITHUB_RELEASE_TOKEN:-}"
if [ -z "$TOK_REL" ] && [ -f "/var/tmp/.tok_release" ]; then
  TOK_REL="$(cat /var/tmp/.tok_release)"
fi

PRIVATE_REPO="${PRIVATE_REPO:-fonchuuk/WorcspasePrivateArena}"
RELEASE_REPO="${RELEASE_REPO:-fonchuuk/AetherClient}"
PRIVATE_ONLY="${PRIVATE_ONLY:-1}"

git add -A
git add -f tools/push-private.sh tools/rebuild.sh tools/AetherBridge.bat tools/cloud-autoshoot.sh tools/render_ui_screenshots.py screenshots bridge 2>/dev/null || true
LAST_MSG="Workspace sync (private): screenshots, AetherBridge.bat, cloud-autoshoot.sh & v0.1.8 build"

if [ -n "$TOK_PRIV" ]; then
  echo "=== Выгрузка всех файлов воркспейса, моста и скриншотов в приватный репозиторий: ${PRIVATE_REPO} ==="
  AUTH_PRIV="$(printf 'x-access-token:%s' "$TOK_PRIV" | base64 -w0)"
  TMP_IDX="$(mktemp /var/tmp/git-idx.XXXXXX)"
  cp .git/index "$TMP_IDX"
  GIT_INDEX_FILE="$TMP_IDX" git rm -r --cached --ignore-unmatch .github/workflows >/dev/null
  TREE_PRIV="$(GIT_INDEX_FILE="$TMP_IDX" git write-tree)"
  rm -f "$TMP_IDX"
  SHA_PRIV="$(git -c user.name="fonchuuk" -c user.email="fonchuuk@users.noreply.github.com" \
    commit-tree "$TREE_PRIV" -m "$LAST_MSG")"
  git -c "http.https://github.com/.extraheader=AUTHORIZATION: basic ${AUTH_PRIV}" \
    push --force "https://github.com/${PRIVATE_REPO}.git" \
    "${SHA_PRIV}:refs/heads/main" "${SHA_PRIV}:refs/heads/master"
  echo "=== Успешно выгружено в приватку ${PRIVATE_REPO} (commit ${SHA_PRIV}) ==="
fi

if [ "$PRIVATE_ONLY" = "0" ] && [ -n "$TOK_REL" ]; then
  echo "=== Синхронизация кода с публичным релизным репозиторием (без bridge/ и screenshots/): ${RELEASE_REPO} ==="
  AUTH_REL="$(printf 'x-access-token:%s' "$TOK_REL" | base64 -w0)"
  git -c "http.https://github.com/.extraheader=AUTHORIZATION: basic ${AUTH_REL}" \
    fetch --depth=1 "https://github.com/${RELEASE_REPO}.git" master:refs/remotes/release/master
  PARENT_SHA="$(git rev-parse refs/remotes/release/master)"
  TMP_IDX="$(mktemp /var/tmp/git-idx.XXXXXX)"
  cp .git/index "$TMP_IDX"
  GIT_INDEX_FILE="$TMP_IDX" git rm -r --cached --ignore-unmatch bridge screenshots tools/AetherBridge.bat tools/cloud-autoshoot.sh tools/render_ui_screenshots.py >/dev/null
  TREE_REL="$(GIT_INDEX_FILE="$TMP_IDX" git write-tree)"
  rm -f "$TMP_IDX"
  SHA_REL="$(git -c user.name="fonchuuk" -c user.email="fonchuuk@users.noreply.github.com" \
    commit-tree "$TREE_REL" -p "$PARENT_SHA" -m "Release v0.1.8: Frosted Liquid Glass & Technical Fixes")"
  git -c "http.https://github.com/.extraheader=AUTHORIZATION: basic ${AUTH_REL}" \
    push "https://github.com/${RELEASE_REPO}.git" "${SHA_REL}:refs/heads/master"
fi
