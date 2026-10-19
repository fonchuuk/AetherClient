@echo off
chcp 65001 >nul
title AetherGlass Private Bridge (fonchuuk/WorcspasePrivateArena)
echo ============================================================================
echo   AetherGlass Bridge ^<--^> Private Repo (fonchuuk/WorcspasePrivateArena)
echo   Все сборки, логи, краши и скриншоты синхронизируются ТОЛЬКО с приваткой.
echo ============================================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Continue'; " ^
  "$Repo = 'fonchuuk/WorcspasePrivateArena'; " ^
  "$Token = $env:AETHER_PRIVATE_TOKEN; " ^
  "if (-not $Token) { $Token = Read-Host 'Вставьте приватный токен (github_pat_...) для fonchuuk/WorcspasePrivateArena' }; " ^
  "$Headers = @{ Authorization = \"Bearer $Token\"; Accept = 'application/vnd.github+json'; 'User-Agent' = 'AetherBridge' }; " ^
  "$GameDir = Join-Path $env:LOCALAPPDATA 'AetherGlass\game'; " ^
  "$ModsDir = Join-Path $GameDir 'mods'; " ^
  "$LogsDir = Join-Path $GameDir 'logs'; " ^
  "$ScreensDir = Join-Path $GameDir 'screenshots'; " ^
  "New-Item -ItemType Directory -Force -Path $ModsDir, $LogsDir, $ScreensDir | Out-Null; " ^
  "$LastBuildSha = ''; $LastCmdId = ''; $LastScreenName = ''; " ^
  "function Send-FileToPrivate($LocalPath, $RemotePath, $CommitMsg) { " ^
  "  if (-not (Test-Path $LocalPath)) { return }; " ^
  "  $Bytes = [System.IO.File]::ReadAllBytes($LocalPath); " ^
  "  $B64 = [Convert]::ToBase64String($Bytes); " ^
  "  $Url = \"https://api.github.com/repos/$Repo/contents/$RemotePath\"; " ^
  "  $Sha = $null; " ^
  "  try { $Exist = Invoke-RestMethod -Uri $Url -Headers $Headers -Method Get; $Sha = $Exist.sha } catch {}; " ^
  "  $Body = @{ message = $CommitMsg; content = $B64; branch = 'main' }; " ^
  "  if ($Sha) { $Body.sha = $Sha }; " ^
  "  Invoke-RestMethod -Uri $Url -Headers $Headers -Method Put -Body ($Body | ConvertTo-Json -Compress) | Out-Null; " ^
  "  Write-Host \"[Bridge] Выгружено в приватку: $RemotePath\" -ForegroundColor Cyan; " ^
  "}; " ^
  "Write-Host '[Bridge] Мост запущен! Слушаю приватный репозиторий...' -ForegroundColor Green; " ^
  "while ($true) { " ^
  "  try { " ^
  "    $StateRaw = Invoke-RestMethod -Uri \"https://api.github.com/repos/$Repo/contents/bridge/state.json?ref=main\" -Headers $Headers -Method Get; " ^
  "    $StateJson = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($StateRaw.content)) | ConvertFrom-Json; " ^
  "    if ($StateJson.jar_sha256 -and $StateJson.jar_sha256 -ne $LastBuildSha) { " ^
  "      Write-Host \"[Bridge] Обнаружена новая сборка ($($StateJson.build_id)). Скачиваю AetherGlass.jar...\" -ForegroundColor Yellow; " ^
  "      $JarBlob = Invoke-RestMethod -Uri \"https://api.github.com/repos/$Repo/git/blobs/$($StateJson.jar_git_blob)\" -Headers $Headers -Method Get; " ^
  "      $JarBytes = [Convert]::FromBase64String($JarBlob.content); " ^
  "      [System.IO.File]::WriteAllBytes((Join-Path $ModsDir 'aetherglass-1.21.11.jar'), $JarBytes); " ^
  "      $LastBuildSha = $StateJson.jar_sha256; " ^
  "      Write-Host '[Bridge] Свежий мод установлен в папку mods!' -ForegroundColor Green; " ^
  "    }; " ^
  "    $LatestScreen = Get-ChildItem -Path $ScreensDir -Filter '*.png' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1; " ^
  "    if ($LatestScreen -and $LatestScreen.Name -ne $LastScreenName) { " ^
  "      Send-FileToPrivate $LatestScreen.FullName 'bridge/telemetry/latest_screenshot.png' \"Auto-upload screenshot $($LatestScreen.Name)\"; " ^
  "      $LastScreenName = $LatestScreen.Name; " ^
  "    }; " ^
  "    $LogFile = Join-Path $LogsDir 'latest.log'; " ^
  "    if (Test-Path $LogFile) { " ^
  "      $Tail = (Get-Content $LogFile -Tail 120) -join \"`n\"; " ^
  "      if ($Tail -match 'Exception|Error|MixinApplyError') { " ^
  "        Send-FileToPrivate $LogFile 'bridge/telemetry/latest.log' 'Auto-upload latest.log with error'; " ^
  "      } " ^
  "    } " ^
  "  } catch { }; " ^
  "  Start-Sleep -Seconds 5; " ^
  "}"
